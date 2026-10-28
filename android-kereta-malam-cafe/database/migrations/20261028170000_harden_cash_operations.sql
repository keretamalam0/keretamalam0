-- Non-destructive cash-controls hardening for Kereta Malam Cafe.
alter table public.expenses
  add column if not exists idempotency_key uuid,
  add column if not exists shift_id uuid references public.staff_shifts(id) on delete restrict,
  add column if not exists outlet_id uuid references public.outlets(id) on delete restrict,
  add column if not exists approved_by uuid references auth.users(id) on delete restrict;
create unique index if not exists expenses_owner_idempotency_key_uq on public.expenses(owner_id,idempotency_key) where idempotency_key is not null;
alter table public.cash_fund_entries
  add column if not exists idempotency_key uuid,
  add column if not exists created_by uuid references auth.users(id) on delete restrict,
  add column if not exists outlet_id uuid references public.outlets(id) on delete restrict;
create unique index if not exists cash_fund_entries_owner_idempotency_key_uq on public.cash_fund_entries(owner_id,idempotency_key) where idempotency_key is not null;
create unique index if not exists staff_shifts_one_open_per_staff_uq on public.staff_shifts(staff_user_id) where status='open';

create or replace function public.km_resolve_owner_id()
returns uuid language sql stable security invoker set search_path=''
as $$ select coalesce((select sp.owner_id from public.staff_profiles sp where sp.user_id=(select auth.uid()) and sp.is_active=true limit 1),(select auth.uid())) $$;

create or replace function public.km_record_operational_expense(
 p_owner_id uuid,p_amount numeric,p_category text,p_description text,p_expense_date date,
 p_payment_method text,p_cash_source text,p_idempotency_key uuid,p_shift_id uuid default null
) returns setof public.expenses language plpgsql security definer set search_path='' as $$
declare v_owner uuid; v_existing public.expenses%rowtype; v_sales numeric:=0; v_transfers numeric:=0; v_paid_out numeric:=0; v_target numeric:=0; v_fund_in numeric:=0; v_fund_out numeric:=0; v_available numeric:=0;
begin
 if (select auth.uid()) is null then raise exception 'Login diperlukan.' using errcode='28000'; end if;
 v_owner:=public.km_resolve_owner_id();
 if p_owner_id is distinct from v_owner then raise exception 'Akses outlet ditolak.' using errcode='42501'; end if;
 if p_amount is null or p_amount<=0 or p_amount>1000000000 then raise exception 'Nominal pengeluaran tidak valid.'; end if;
 if nullif(trim(p_category),'') is null or nullif(trim(p_description),'') is null then raise exception 'Kategori dan keterangan wajib diisi.'; end if;
 if p_cash_source not in ('cash_fund','sales_cash','sales_qris','sales_transfer','owner_funds','other') then raise exception 'Sumber dana tidak valid.'; end if;
 if p_idempotency_key is null then raise exception 'Kunci transaksi wajib ada.'; end if;
 perform pg_advisory_xact_lock(hashtextextended(v_owner::text||':cash',0));
 select * into v_existing from public.expenses where owner_id=v_owner and idempotency_key=p_idempotency_key limit 1;
 if found then return next v_existing; return; end if;
 if p_shift_id is not null and not exists(select 1 from public.staff_shifts s where s.id=p_shift_id and s.owner_id=v_owner and s.status='open' and s.staff_user_id=(select auth.uid())) then raise exception 'Shift aktif tidak ditemukan atau bukan milik kasir ini.' using errcode='42501'; end if;
 if p_cash_source='cash_fund' then
  select coalesce(sum(amount) filter(where entry_type='set_target'),0),coalesce(sum(amount) filter(where entry_type='replenishment'),0) into v_target,v_fund_in from public.cash_fund_entries where owner_id=v_owner;
  select coalesce(sum(amount),0) into v_fund_out from public.expenses where owner_id=v_owner and cash_source='cash_fund';
  v_available:=v_target+v_fund_in-v_fund_out;
 elsif p_cash_source in ('sales_cash','sales_qris','sales_transfer') then
  select coalesce(sum(p.amount),0) into v_sales from public.payments p join public.orders o on o.id=p.order_id
   where o.is_paid=true and o.status<>'cancelled' and o.returned_at is null
    and ((p_cash_source='sales_cash' and lower(p.method) in ('cash','tunai'))
      or (p_cash_source='sales_qris' and lower(p.method) like '%qris%')
      or (p_cash_source='sales_transfer' and (lower(p.method) like '%transfer%' or lower(p.method) like '%bank%')));
  select coalesce(sum(amount),0) into v_transfers from public.cash_fund_entries where owner_id=v_owner and entry_type='replenishment' and source=p_cash_source;
  select coalesce(sum(amount),0) into v_paid_out from public.expenses where owner_id=v_owner and cash_source=p_cash_source;
  v_available:=v_sales-v_transfers-v_paid_out;
 else v_available:=1000000000000;
 end if;
 if v_available<p_amount then raise exception 'Saldo sumber dana tidak cukup. Tersedia: %',greatest(v_available,0) using errcode='23514'; end if;
 insert into public.expenses(owner_id,amount,category,description,expense_date,payment_method,cash_source,created_by,idempotency_key,shift_id)
 values(v_owner,p_amount,trim(p_category),trim(p_description),coalesce(p_expense_date,current_date),coalesce(nullif(p_payment_method,''),'cash'),p_cash_source,(select auth.uid()),p_idempotency_key,p_shift_id)
 returning * into v_existing;
 return next v_existing;
end; $$;

create or replace function public.km_record_cash_fund_entry(
 p_owner_id uuid,p_entry_type text,p_amount numeric,p_source text,p_description text,p_idempotency_key uuid
) returns setof public.cash_fund_entries language plpgsql security definer set search_path='' as $$
declare v_owner uuid; v_existing public.cash_fund_entries%rowtype; v_target numeric:=0; v_current numeric:=0; v_sales numeric:=0; v_transfer_out numeric:=0; v_expenses numeric:=0; v_balance numeric:=0; v_available numeric:=0;
begin
 if (select auth.uid()) is null then raise exception 'Login diperlukan.' using errcode='28000'; end if;
 v_owner:=public.km_resolve_owner_id();
 if p_owner_id is distinct from v_owner then raise exception 'Akses outlet ditolak.' using errcode='42501'; end if;
 if p_amount is null or p_amount<=0 or p_amount>1000000000 then raise exception 'Nominal modal tidak valid.'; end if;
 if nullif(trim(p_description),'') is null then raise exception 'Keterangan wajib diisi.'; end if;
 if p_entry_type not in ('set_target','replenishment') then raise exception 'Jenis transaksi modal tidak valid.'; end if;
 if p_source not in ('setting','sales_cash','sales_qris','sales_transfer','owner_funds','other') then raise exception 'Sumber dana tidak valid.'; end if;
 if p_idempotency_key is null then raise exception 'Kunci transaksi wajib ada.'; end if;
 perform pg_advisory_xact_lock(hashtextextended(v_owner::text||':cash',0));
 select * into v_existing from public.cash_fund_entries where owner_id=v_owner and idempotency_key=p_idempotency_key limit 1;
 if found then return next v_existing; return; end if;
 if p_entry_type='set_target' then
  if (select auth.uid())<>v_owner and not exists(select 1 from public.staff_profiles where user_id=(select auth.uid()) and is_active=true and lower(role) in ('owner','superadmin','admin')) then raise exception 'Hanya Owner yang boleh menetapkan target modal.' using errcode='42501'; end if;
  if exists(select 1 from public.cash_fund_entries where owner_id=v_owner and entry_type='set_target') then raise exception 'Target modal sudah ditetapkan. Gunakan proses penyesuaian yang diotorisasi.'; end if;
 else
  select coalesce(sum(amount) filter(where entry_type='set_target'),0),coalesce(sum(amount) filter(where entry_type='replenishment'),0) into v_target,v_current from public.cash_fund_entries where owner_id=v_owner;
  select coalesce(sum(amount),0) into v_expenses from public.expenses where owner_id=v_owner and cash_source='cash_fund';
  v_balance:=v_target+v_current-v_expenses;
  if v_target<=0 then raise exception 'Target modal belum ditetapkan Owner.'; end if;
  if p_amount>greatest(v_target-v_balance,0) then raise exception 'Pengisian melebihi kekurangan modal. Target %, saldo sekarang %, kekurangan %',v_target,v_balance,greatest(v_target-v_balance,0); end if;
  if p_source in ('sales_cash','sales_qris','sales_transfer') then
   select coalesce(sum(p.amount),0) into v_sales from public.payments p join public.orders o on o.id=p.order_id
    where o.is_paid=true and o.status<>'cancelled' and o.returned_at is null
     and ((p_source='sales_cash' and lower(p.method) in ('cash','tunai')) or (p_source='sales_qris' and lower(p.method) like '%qris%') or (p_source='sales_transfer' and (lower(p.method) like '%transfer%' or lower(p.method) like '%bank%')));
   select coalesce(sum(amount),0) into v_transfer_out from public.cash_fund_entries where owner_id=v_owner and entry_type='replenishment' and source=p_source;
   select coalesce(sum(amount),0) into v_expenses from public.expenses where owner_id=v_owner and cash_source=p_source;
   v_available:=v_sales-v_transfer_out-v_expenses;
   if v_available<p_amount then raise exception 'Saldo penjualan sumber tidak cukup. Tersedia: %',greatest(v_available,0) using errcode='23514'; end if;
  end if;
 end if;
 insert into public.cash_fund_entries(owner_id,entry_type,amount,source,description,idempotency_key,created_by)
 values(v_owner,p_entry_type,p_amount,p_source,trim(p_description),p_idempotency_key,(select auth.uid()))
 returning * into v_existing;
 return next v_existing;
end; $$;

revoke all on function public.km_record_operational_expense(uuid,numeric,text,text,date,text,text,uuid,uuid) from public,anon;
grant execute on function public.km_record_operational_expense(uuid,numeric,text,text,date,text,text,uuid,uuid) to authenticated;
revoke all on function public.km_record_cash_fund_entry(uuid,text,numeric,text,text,uuid) from public,anon;
grant execute on function public.km_record_cash_fund_entry(uuid,text,numeric,text,text,uuid) to authenticated;
revoke delete on public.expenses from authenticated;
revoke delete on public.cash_fund_entries from authenticated;


-- Closing reconciliation and table-level write protection (all writes go through the RPCs).
alter table public.staff_shifts
  add column if not exists expected_cash numeric check (expected_cash is null or expected_cash >= 0),
  add column if not exists cash_difference numeric,
  add column if not exists discrepancy_note text,
  add column if not exists closing_approved_by uuid references auth.users(id) on delete restrict;
revoke insert, update, delete on public.expenses from authenticated;
revoke insert, update, delete on public.cash_fund_entries from authenticated;


-- Persist expected closing and protect direct table mutations.
alter table public.staff_shifts
  add column if not exists expected_cash numeric check (expected_cash is null or expected_cash >= 0),
  add column if not exists cash_difference numeric,
  add column if not exists discrepancy_note text,
  add column if not exists closing_approved_by uuid references auth.users(id) on delete restrict;
revoke insert, update, delete on public.expenses from authenticated;
revoke insert, update, delete on public.cash_fund_entries from authenticated;
drop trigger if exists audit_capture on public.expenses;
create trigger audit_capture after insert or update or delete on public.expenses for each row execute function public.capture_audit_log();
drop trigger if exists audit_capture on public.cash_fund_entries;
create trigger audit_capture after insert or update or delete on public.cash_fund_entries for each row execute function public.capture_audit_log();


-- Cashier expenses must be linked to their own active shift.
create or replace function public.km_validate_expense_shift()
returns trigger language plpgsql security definer set search_path='' as $$
declare v_role text;
begin
 select lower(role) into v_role from public.staff_profiles where user_id=(select auth.uid()) and owner_id=new.owner_id and is_active=true limit 1;
 if v_role='cashier' and new.shift_id is null then raise exception 'Pengeluaran kasir wajib terhubung dengan shift aktif.' using errcode='23514'; end if;
 if new.shift_id is not null and not exists(select 1 from public.staff_shifts s where s.id=new.shift_id and s.owner_id=new.owner_id and s.status='open' and s.staff_user_id=(select auth.uid())) then
  raise exception 'Shift pengeluaran tidak aktif atau tidak dimiliki kasir ini.' using errcode='42501';
 end if;
 return new;
end; $$;
drop trigger if exists expenses_require_active_shift on public.expenses;
create trigger expenses_require_active_shift before insert on public.expenses for each row execute function public.km_validate_expense_shift();

revoke all on function public.km_validate_expense_shift() from public, anon, authenticated;
