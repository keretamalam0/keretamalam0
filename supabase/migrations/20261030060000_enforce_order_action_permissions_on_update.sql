DROP POLICY IF EXISTS orders_auth_update ON public.orders;
CREATE POLICY orders_auth_update
ON public.orders
FOR UPDATE
TO authenticated
USING (
  public.has_permission('orders.update_status')
  OR public.has_permission('payments.confirm')
  OR public.has_permission('sales_history.return')
)
WITH CHECK (
  public.has_permission('orders.update_status')
  OR public.has_permission('payments.confirm')
  OR public.has_permission('sales_history.return')
);
