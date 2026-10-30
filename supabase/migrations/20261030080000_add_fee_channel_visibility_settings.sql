ALTER TABLE public.outlet_settings
  ADD COLUMN IF NOT EXISTS tax_show_in_cashier boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS tax_show_in_qr_ordering boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS service_show_in_cashier boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS service_show_in_qr_ordering boolean NOT NULL DEFAULT true;

CREATE OR REPLACE FUNCTION public.get_public_outlet_settings_v2()
RETURNS TABLE(
  name text, address text, phone text, opening_hours text, instagram text,
  receipt_note text, receipt_footer text, tax_rate numeric, service_rate numeric,
  service_fee_mode text, service_fixed_amount numeric,
  tax_enabled boolean, service_enabled boolean,
  tax_show_in_cashier boolean, tax_show_in_qr_ordering boolean,
  service_show_in_cashier boolean, service_show_in_qr_ordering boolean,
  cash_enabled boolean, card_enabled boolean, qris_enabled boolean
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $function$
  SELECT
    os.name::text, COALESCE(os.address, '')::text, COALESCE(os.phone, '')::text,
    COALESCE(os.opening_hours, ''), COALESCE(os.instagram, ''),
    COALESCE(os.receipt_note, ''), COALESCE(os.receipt_footer, 'Terima kasih sudah berkunjung.'),
    os.tax_rate::numeric, os.service_rate::numeric,
    COALESCE(os.service_fee_mode, 'percentage'), COALESCE(os.service_fixed_amount, 0)::numeric,
    COALESCE(os.tax_enabled, false), COALESCE(os.service_enabled, false),
    COALESCE(os.tax_show_in_cashier, true), COALESCE(os.tax_show_in_qr_ordering, true),
    COALESCE(os.service_show_in_cashier, true), COALESCE(os.service_show_in_qr_ordering, true),
    os.cash_enabled, os.card_enabled, os.qris_enabled
  FROM public.outlet_settings os
  ORDER BY os.updated_at DESC NULLS LAST, os.created_at DESC
  LIMIT 1;
$function$;
REVOKE ALL ON FUNCTION public.get_public_outlet_settings_v2() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_public_outlet_settings_v2() TO anon, authenticated;
