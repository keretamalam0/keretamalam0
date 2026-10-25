-- Modal Kas: persisted target and replenishment ledger.
create table if not exists public.cash_fund_entries (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  entry_type text not null check (entry_type in ('set_target', 'replenishment')),
  amount numeric(14,2) not null check (amount > 0),
  source text not null check (source in ('setting', 'sales_cash', 'owner_funds')),
  description text not null,
  created_at timestamptz not null default now()
);
alter table public.cash_fund_entries enable row level security;
drop policy if exists "cash_fund_entries_select_own" on public.cash_fund_entries;
create policy "cash_fund_entries_select_own" on public.cash_fund_entries
for select to authenticated using (owner_id = auth.uid());
drop policy if exists "cash_fund_entries_insert_own" on public.cash_fund_entries;
create policy "cash_fund_entries_insert_own" on public.cash_fund_entries
for insert to authenticated with check (owner_id = auth.uid());
create index if not exists cash_fund_entries_owner_created_idx
on public.cash_fund_entries (owner_id, created_at desc);
