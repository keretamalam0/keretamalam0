-- Enforce the granular staff permission for full transaction returns at the database boundary.
CREATE OR REPLACE FUNCTION public.enforce_order_return_permission()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public
AS $$
BEGIN
  IF OLD.returned_at IS DISTINCT FROM NEW.returned_at
     AND NEW.returned_at IS NOT NULL
     AND NOT public.has_permission('sales_history.return') THEN
    RAISE EXCEPTION 'Akun tidak memiliki izin melakukan retur transaksi'
      USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS orders_return_permission_guard ON public.orders;
CREATE TRIGGER orders_return_permission_guard
BEFORE UPDATE OF returned_at ON public.orders
FOR EACH ROW
EXECUTE FUNCTION public.enforce_order_return_permission();
