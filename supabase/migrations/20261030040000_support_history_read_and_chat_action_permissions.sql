CREATE OR REPLACE FUNCTION public.has_permission(permission_key text)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
  WITH current_profile AS (
    SELECT p.role, p.permissions
    FROM public.staff_profiles p
    WHERE p.user_id = auth.uid()
      AND p.is_active
    LIMIT 1
  ),
  permission_scope AS (
    SELECT role, permissions,
      CASE
        WHEN permission_key LIKE 'history.%' OR permission_key LIKE 'sales_history.%' THEN 'sales_history'
        WHEN permission_key LIKE 'dashboard.%' THEN 'dashboard'
        WHEN permission_key LIKE 'pos.%' OR permission_key LIKE 'payments.%' OR permission_key = 'payments.manage_methods' THEN 'pos'
        WHEN permission_key LIKE 'orders.%' THEN 'orders'
        WHEN permission_key LIKE 'cashier_chat.%' THEN 'cashier_chat'
        WHEN permission_key LIKE 'products.%' THEN 'products'
        WHEN permission_key LIKE 'inventory.%' THEN 'inventory'
        WHEN permission_key LIKE 'tables.%' THEN 'tables'
        WHEN permission_key LIKE 'promotions.%' THEN 'promotions'
        WHEN permission_key LIKE 'expenses.%' OR permission_key LIKE 'shift.%' THEN 'cash_fund'
        WHEN permission_key LIKE 'audit.%' THEN 'shift_audit'
        WHEN permission_key LIKE 'reports.%' OR permission_key IN ('report.view', 'report.export') THEN 'reports'
        WHEN permission_key LIKE 'profit_loss.%' THEN 'profit_loss'
        WHEN permission_key LIKE 'staff.%' THEN 'staff_management'
        WHEN permission_key LIKE 'activity_log.%' THEN 'activity_log'
        WHEN permission_key LIKE 'settings.%' THEN 'settings'
        WHEN permission_key LIKE 'printer.%' THEN 'printer_management'
        ELSE NULL
      END AS parent_key
    FROM current_profile
  )
  SELECT EXISTS (
    SELECT 1 FROM permission_scope s
    WHERE s.role = 'owner'
       OR (
         COALESCE((s.permissions ->> permission_key)::boolean,
           CASE WHEN s.parent_key IS NOT NULL
             THEN COALESCE((s.permissions ->> s.parent_key)::boolean, false)
             ELSE false
           END
         )
         AND (s.parent_key IS NULL OR COALESCE((s.permissions ->> s.parent_key)::boolean, false))
       )
  );
$function$;

DROP POLICY IF EXISTS orders_auth_select ON public.orders;
CREATE POLICY orders_auth_select
ON public.orders
FOR SELECT
TO authenticated
USING (public.has_permission('orders') OR public.has_permission('sales_history'));
