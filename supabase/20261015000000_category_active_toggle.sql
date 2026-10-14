-- Allow categories to be enabled/disabled independently without deleting products.
ALTER TABLE public.categories
  ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT true;
