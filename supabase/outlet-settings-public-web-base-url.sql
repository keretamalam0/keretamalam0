-- Keep the native Android and web outlet-settings payload aligned with Supabase.
-- Safe additive migration: existing outlet settings and business data are preserved.
ALTER TABLE public.outlet_settings
  ADD COLUMN IF NOT EXISTS public_web_base_url text;

-- Refresh PostgREST metadata after the schema change.
NOTIFY pgrst, 'reload schema';
