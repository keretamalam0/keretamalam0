
alter table public.cash_fund_entries drop constraint if exists cash_fund_entries_entry_type_check;
alter table public.cash_fund_entries add constraint cash_fund_entries_entry_type_check check (entry_type in ('set_target','replenishment','close_session'));
alter table public.cash_fund_entries drop constraint if exists cash_fund_entries_amount_check;
alter table public.cash_fund_entries add constraint cash_fund_entries_amount_check check ((entry_type = 'close_session' and amount >= 0) or (entry_type <> 'close_session' and amount > 0));
create or replace function public.km_record_cash_fund_entry(
 p_owner_id uuid,p_entry_type text,p_amount numeric,p_source text,p_description text,p_idempotency_key uuid
) returns setof public.cash_fund_entries
language plpgsql security definer set search_path='' as $$
declare
 v_owner uuid; v_existing public.cash_fund_entries%rowtype; v_target numeric:=0;
 v_adjustments numeric:=0; v_expenses numeric:=0; v_balance numeric:=0;
 v_session_start timestamptz; v_last_close timestamptz;
begin
 if (select auth.uid()) is null then raise exception 'Login diperlukan.' using errcode='28000'; end if;
 v_owner:=public.km_resolve_owner_id();
 if p_owner_id is distinct from v_owner then raise exception 'Akses outlet ditolak.' using errcode='42501'; end if;
 if p_entry_type not in ('set_target','replenishment','close_session') then raise exception 'Jenis transaksi modal tidak valid.'; end if;
 if p_entry_type <> 'close_session' and (p_amount is null or p_amount<=0 or p_amount>1000000000) then raise exception 'Nominal tidak valid.'; end if;
 if nullif(trim(p_description),'') is null then raise exception 'Keterangan wajib diisi.'; end if;
 if p_source not in ('setting','sales_cash','sales_qris','sales_transfer','owner_funds','other') then raise exception 'Sumber dana tidak valid.'; end if;
 if p_idempotency_key is null then raise exception 'Kunci transaksi wajib ada.'; end if;
 perform pg_advisory_xact_lock(hashtextextended(v_owner::text||':cash',0));
 select * into v_existing from public.cash_fund_entries where owner_id=v_owner and idempotency_key=p_idempotency_key limit 1;
 if found then return next v_existing; return; end if;
 select created_at, amount into v_session_start, v_target from public.cash_fund_entries where owner_id=v_owner and entry_type='set_target' order by created_at desc, id desc limit 1;
 select created_at into v_last_close from public.cash_fund_entries where owner_id=v_owner and entry_type='close_session' order by created_at desc, id desc limit 1;
 if p_entry_type='set_target' then
   if v_session_start is not null and (v_last_close is null or v_session_start > v_last_close) then raise exception 'Sesi sebelumnya masih aktif. Akhiri sesi terlebih dahulu.'; end if;
   insert into public.cash_fund_entries(owner_id,entry_type,amount,source,description,idempotency_key,created_by) values(v_owner,p_entry_type,p_amount,p_source,trim(p_description),p_idempotency_key,(select auth.uid())) returning * into v_existing;
   return next v_existing; return;
 end if;
 if v_session_start is null or v_target is null or v_target<=0 or (v_last_close is not null and v_last_close>v_session_start) then raise exception 'Tidak ada sesi aktif. Mulai sesi dan simpan kas modal terlebih dahulu.'; end if;
 select coalesce(sum(amount),0) into v_adjustments from public.cash_fund_entries where owner_id=v_owner and entry_type='replenishment' and created_at>=v_session_start;
 select coalesce(sum(amount),0) into v_expenses from public.expenses where owner_id=v_owner and cash_source='cash_fund' and created_at>=v_session_start;
 v_balance:=greatest(v_target+v_adjustments-v_expenses,0);
 if p_entry_type='close_session' then
   insert into public.cash_fund_entries(owner_id,entry_type,amount,source,description,idempotency_key,created_by) values(v_owner,'close_session',v_balance,'setting',trim(p_description),p_idempotency_key,(select auth.uid())) returning * into v_existing;
   return next v_existing; return;
 end if;
 if p_amount>greatest(v_target-v_balance,0) then raise exception 'Adjustment melebihi kekurangan modal. Target %, saldo sekarang %, kekurangan %',v_target,v_balance,greatest(v_target-v_balance,0); end if;
 insert into public.cash_fund_entries(owner_id,entry_type,amount,source,description,idempotency_key,created_by) values(v_owner,p_entry_type,p_amount,p_source,trim(p_description),p_idempotency_key,(select auth.uid())) returning * into v_existing;
 return next v_existing;
end; $$;
revoke all on function public.km_record_cash_fund_entry(uuid,text,numeric,text,text,uuid) from public,anon;
grant execute on function public.km_record_cash_fund_entry(uuid,text,numeric,text,text,uuid) to authenticated;
