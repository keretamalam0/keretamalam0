-- Restore product stock exactly once when a cashier cancels an order.
-- Run this migration in the Kereta Malam Cafe Supabase SQL Editor before deploying the app.
create or replace function public.cancel_order_and_restore_stock(p_order_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_status text;
begin
  -- SECURITY DEFINER is required because cashiers may not have direct product-edit
  -- permission. Explicitly require order-management permission before any write.
  if not coalesce(public.has_permission('orders'), false) then
    raise exception 'Tidak memiliki izin untuk membatalkan pesanan.';
  end if;

  -- Lock the order so two cancellation requests cannot restore stock twice.
  select status into v_status
  from public.orders
  where id = p_order_id
  for update;

  if not found then
    raise exception 'Order tidak ditemukan.';
  end if;

  if lower(coalesce(v_status, '')) = 'cancelled' then
    return;
  end if;

  -- Only restore stock when transitioning to cancelled. Aggregate duplicate
  -- product lines to restore the full quantity for each product.
  update public.products p
  set stock = coalesce(p.stock, 0) + item_totals.qty
  from (
    select product_id, sum(quantity)::integer as qty
    from public.order_items
    where order_id = p_order_id
      and product_id is not null
    group by product_id
  ) as item_totals
  where p.id = item_totals.product_id;

  update public.orders
  set status = 'cancelled',
      updated_at = now()
  where id = p_order_id;
end;
$$;

revoke all on function public.cancel_order_and_restore_stock(uuid) from public;
grant execute on function public.cancel_order_and_restore_stock(uuid) to authenticated;
