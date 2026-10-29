-- Align live database source constraints with the native app/RPC values.
-- Older deployments may still have expenses_source_check from the legacy schema.
do $$
declare c record;
begin
  for c in
    select conrelid::regclass as table_name, conname
    from pg_constraint
    where contype = 'c'
      and conrelid in ('public.expenses'::regclass, 'public.cash_fund_entries'::regclass)
      and (
        conname in ('expenses_source_check', 'expenses_cash_source_check', 'cash_fund_entries_source_check')
        or pg_get_constraintdef(oid) ilike '%source%'
      )
  loop
    execute format('alter table %s drop constraint %I', c.table_name, c.conname);
  end loop;
end $$;

alter table public.expenses
  add constraint expenses_cash_source_check
  check (cash_source in ('cash_fund', 'sales_cash', 'sales_qris', 'sales_transfer', 'owner_funds', 'other'));

alter table public.cash_fund_entries
  add constraint cash_fund_entries_source_check
  check (source in ('setting', 'sales_cash', 'sales_qris', 'sales_transfer', 'owner_funds', 'other'));

-- Ensure the RPC and table agree on supported sources, including QRIS, bank transfer,
-- and owner-funded replenishments. This migration does not rewrite existing transactions.
