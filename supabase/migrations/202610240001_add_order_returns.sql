alter table public.orders
    add column if not exists returned_at timestamptz,
    add column if not exists return_reason text,
    add column if not exists returned_by text;

create index if not exists orders_returned_at_idx
    on public.orders (returned_at)
    where returned_at is not null;
