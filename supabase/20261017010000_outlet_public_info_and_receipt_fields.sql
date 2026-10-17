-- Shared outlet profile fields used by Cafe Flow web POS, QR Ordering, and Android POS.
ALTER TABLE public.outlet_settings
  ADD COLUMN IF NOT EXISTS opening_hours text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS instagram text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS receipt_note text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS receipt_footer text NOT NULL DEFAULT 'Terima kasih sudah berkunjung.';
