-- Keep the native cashier's QR payment confirmation compatible with orders
-- that have already moved from Baru to Diproses or Siap.
create or replace function public.approve_qr_order_with_method(
  p_order_id uuid,
  p_payment_method text,
  p_cash_received numeric default null
)
returns public.orders
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_order public.orders%rowtype;
  v_owner_id uuid;
  v_method text;
  v_cash numeric;
  v_change numeric := 0;
begin
  if auth.uid() is null then raise exception 'Sesi login tidak ditemukan'; end if;

  select o.* into v_order from public.orders o where o.id = p_order_id for update;
  if not found then raise exception 'Pesanan tidak ditemukan'; end if;

  select sp.owner_id into v_owner_id from public.staff_profiles sp
  where sp.user_id = auth.uid() and sp.is_active = true limit 1;
  if v_owner_id is null then
    select sp.owner_id into v_owner_id from public.staff_profiles sp
    where sp.id = auth.uid() and sp.is_active = true limit 1;
  end if;
  if v_owner_id is null then raise exception 'Profil staf aktif tidak ditemukan'; end if;
  if not public.has_permission('orders') or not public.has_permission('payments') then
    raise exception 'Tidak memiliki izin konfirmasi pembayaran';
  end if;

  -- Allow payment at any active kitchen stage; never accept completed/cancelled orders.
  if v_order.source <> 'qr'
     or v_order.status not in ('new', 'preparing', 'ready')
     or v_order.is_paid then
    raise exception 'Pesanan bukan order QR aktif yang menunggu pembayaran';
  end if;

  v_method := lower(trim(coalesce(p_payment_method, '')));
  if v_method not in ('cash','qris','card','bank_transfer','ewallet')
     and not exists (
       select 1 from public.payment_methods pm
       where pm.owner_id = v_owner_id and pm.code = v_method and pm.is_active = true
     ) then
    raise exception 'Metode pembayaran tidak valid atau tidak aktif';
  end if;

  if v_method = 'cash' then
    if p_cash_received is null or p_cash_received < v_order.total then
      raise exception 'Uang diterima belum mencukupi';
    end if;
    v_cash := p_cash_received;
    v_change := p_cash_received - v_order.total;
  else
    v_cash := null;
  end if;

  update public.orders
  set payment_method = v_method,
      is_paid = true,
      payment_status = 'paid',
      status = case when v_order.status = 'new' then 'preparing' else v_order.status end,
      updated_at = now()
  where id = p_order_id returning * into v_order;

  insert into public.payments (order_id, method, amount, cash_received, change_amount)
  values (p_order_id, v_method, v_order.total, v_cash, v_change);

  return v_order;
end;
$function$;
