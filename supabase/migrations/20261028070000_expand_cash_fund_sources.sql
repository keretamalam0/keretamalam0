-- Allow Adjustment to allocate sales from cash, QRIS, bank transfer, owner top-ups, or other sources.
alter table public.cash_fund_entries
  drop constraint if exists cash_fund_entries_source_check;

alter table public.cash_fund_entries
  add constraint cash_fund_entries_source_check
  check (source in ('setting', 'sales_cash', 'sales_qris', 'sales_transfer', 'owner_funds', 'other'));
