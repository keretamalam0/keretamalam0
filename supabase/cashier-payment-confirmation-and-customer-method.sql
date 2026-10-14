-- Applied to Supabase project dbazoxwhhljpgtbnletc on 2026-10-14.
-- Preserve the QR customer's selected method and add atomic cashier confirmation.
alter table public.orders
  add column if not exists customer_payment_method text;

update public.orders
set customer_payment_method = payment_method
where source = 'qr'
  and customer_payment_method is null;

create or replace function public.capture_qr_customer_payment_method()
returns trigger
language plpgsql
set search_path = 'pg_catalog', 'public'
as $$
begin
  if new.source = 'qr' and new.customer_payment_method is null then
    new.customer_payment_method := new.payment_method;
  end if;
  return new;
end;
$$;

drop trigger if exists orders_capture_qr_customer_payment_method on public.orders;
create trigger orders_capture_qr_customer_payment_method
before insert on public.orders
for each row execute function public.capture_qr_customer_payment_method();

create or replace function public.confirm_order_payment(
  p_order_id uuid,
  p_payment_method text,
  p_cash_received numeric default null
)
returns public.orders
language plpgsql
security definer
set search_path = 'pg_catalog', 'public'
as $$
declare
  v_order public.orders%rowtype;
  v_owner_id uuid;
  v_method text := lower(trim(coalesce(p_payment_method, '')));
  v_type text;
  v_cash numeric;
  v_change numeric := 0;
begin
  if auth.uid() is null then raise exception 'Sesi login tidak ditemukan'; end if;
  select o.* into v_order
  from public.orders o
  join public.outlets ot on ot.id = o.outlet_id
  where o.id = p_order_id
  for update of o;
  if not found then raise exception 'Pesanan tidak ditemukan'; end if;

  select sp.owner_id into v_owner_id from public.staff_profiles sp
  where sp.user_id = auth.uid() and sp.is_active = true limit 1;
  if v_owner_id is null then
    select sp.owner_id into v_owner_id from public.staff_profiles sp
    where sp.id = auth.uid() and sp.is_active = true limit 1;
  end if;
  if v_owner_id is null or not exists (
    select 1 from public.outlets ot where ot.id = v_order.outlet_id and ot.owner_id = v_owner_id
  ) then raise exception 'Pesanan tidak berada di outlet yang dapat diakses'; end if;
  if not public.has_permission('orders') or not public.has_permission('payments') then
    raise exception 'Tidak memiliki izin konfirmasi pembayaran';
  end if;
  if v_order.status not in ('new', 'preparing', 'ready') or v_order.is_paid then
    raise exception 'Pesanan tidak aktif atau sudah dibayar';
  end if;

  select pm.type into v_type from public.payment_methods pm
  where pm.owner_id = v_owner_id and lower(pm.code) = v_method and pm.is_active = true limit 1;
  if v_type is null then
    if v_method not in ('cash', 'qris', 'card', 'bank_transfer', 'ewallet') then
      raise exception 'Metode pembayaran tidak valid atau tidak aktif';
    end if;
    v_type := v_method;
  end if;

  if v_type = 'cash' or v_method = 'cash' then
    if p_cash_received is null or p_cash_received < v_order.total then raise exception 'Uang diterima belum mencukupi'; end if;
    v_cash := p_cash_received;
    v_change := p_cash_received - v_order.total;
  else
    v_cash := null;
  end if;

  update public.orders set payment_method = v_method, is_paid = true, payment_status = 'paid',
    status = case when v_order.status = 'new' then 'preparing' else v_order.status end,
    updated_at = now()
  where id = p_order_id returning * into v_order;
  insert into public.payments (order_id, method, amount, cash_received, change_amount)
  values (p_order_id, v_method, v_order.total, v_cash, v_change);
  return v_order;
end;
$$;

revoke all on function public.confirm_order_payment(uuid, text, numeric) from public, anon;
grant execute on function public.confirm_order_payment(uuid, text, numeric) to authenticated;
