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

-- Parent menu OFF overrides any child action that remains ON.
CREATE OR REPLACE FUNCTION public.has_permission(permission_key text)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $function$
  SELECT EXISTS (
    SELECT 1
    FROM public.staff_profiles p
    WHERE p.user_id = auth.uid()
      AND p.is_active
      AND (
        p.role = 'owner'
        OR (
          COALESCE(
            (p.permissions ->> permission_key)::boolean,
            CASE
              WHEN position('.' in permission_key) > 0
                THEN COALESCE((p.permissions ->> split_part(permission_key, '.', 1))::boolean, false)
              ELSE false
            END
          )
          AND (
            position('.' in permission_key) = 0
            OR COALESCE((p.permissions ->> split_part(permission_key, '.', 1))::boolean, false)
          )
        )
      )
  );
$function$;
