DROP POLICY IF EXISTS order_items_auth_select ON public.order_items;
CREATE POLICY order_items_auth_select
ON public.order_items
FOR SELECT
TO authenticated
USING (public.has_permission('orders') OR public.has_permission('sales_history'));

DROP POLICY IF EXISTS payments_auth_select ON public.payments;
CREATE POLICY payments_auth_select
ON public.payments
FOR SELECT
TO authenticated
USING (
  public.has_permission('payments')
  OR public.has_permission('orders')
  OR public.has_permission('sales_history')
);
