-- Align stock-write enforcement with the native POS permission keys.
-- Cashiers with order + payment permission may decrement stock as part of checkout.
-- Inventory edits remain gated by the Inventory permission (legacy inventory.edit also supported).
CREATE OR REPLACE FUNCTION public.enforce_product_field_permissions()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO 'public'
AS $function$
BEGIN
  IF auth.uid() IS NULL THEN
    RETURN NEW;
  END IF;

  IF NEW.stock IS DISTINCT FROM OLD.stock
     AND NOT public.has_permission('inventory')
     AND NOT public.has_permission('inventory.edit')
     AND NOT (
       NEW.stock < OLD.stock
       AND public.has_permission('orders')
       AND public.has_permission('payments')
     ) THEN
    RAISE EXCEPTION 'Akun tidak memiliki izin mengubah stok'
      USING ERRCODE = '42501';
  END IF;

  IF (to_jsonb(NEW) - 'stock' - 'updated_at')
     IS DISTINCT FROM (to_jsonb(OLD) - 'stock' - 'updated_at')
     AND NOT public.has_permission('products.edit') THEN
    RAISE EXCEPTION 'Akun tidak memiliki izin mengubah detail produk'
      USING ERRCODE = '42501';
  END IF;

  RETURN NEW;
END;
$function$;