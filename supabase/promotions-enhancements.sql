-- Kereta Malam Cafe: promotion rules used by POS checkout
-- Run this in Supabase SQL Editor before using promotions in LIVE_SUPABASE mode.
alter table public.promotions
  add column if not exists campaign_type text not null default 'promotion',
  add column if not exists allow_customer boolean not null default true,
  add column if not exists allow_cashier boolean not null default true,
  add column if not exists min_quantity integer not null default 1,
  add column if not exists scope text not null default 'order',
  add column if not exists product_id uuid null,
  add column if not exists min_purchase numeric(12,2) not null default 0,
  add column if not exists active boolean not null default true;

alter table public.promotions
  drop constraint if exists promotions_campaign_type_check;
alter table public.promotions
  add constraint promotions_campaign_type_check
  check (campaign_type in ('promotion', 'voucher', 'bundle', 'campaign'));

alter table public.promotions
  drop constraint if exists promotions_scope_check;
alter table public.promotions
  add constraint promotions_scope_check
  check (scope in ('order', 'product'));

alter table public.promotions
  drop constraint if exists promotions_min_quantity_check;
alter table public.promotions
  add constraint promotions_min_quantity_check
  check (min_quantity >= 1);

alter table public.promotions
  drop constraint if exists promotions_min_purchase_check;
alter table public.promotions
  add constraint promotions_min_purchase_check
  check (min_purchase >= 0);

create index if not exists promotions_active_created_at_idx
  on public.promotions (active, created_at desc);
