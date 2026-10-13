# QR Ordering Integration — Production Gate

Status: **NOT LIVE**. The QR Ordering frontend currently uses browser-local demo orders. Do not describe it as connected to Cafe Flow POS until the checks below pass.

## Verified repositories

- QR Ordering: `malamcafe/pixel-perfect-clone` (default branch `main`)
- Cafe Flow POS: `keretamalam0/keretamalam0` (default branch `main`)
- The POS app uses Supabase tables including `orders`, `order_items`, `restaurant_tables`, and `payments`.
- The POS bridge lists two candidate Supabase projects: `rvaemgwtdvncfvaerhgr` and `dbazoxwhhljpgtbnletc`. These are candidates, not proof of the live deployment's active project.

## Non-negotiable requirements before enabling live QR checkout

1. Verify the Supabase project actually used by the deployed POS, using the deployed environment/configuration rather than a guessed URL.
2. Verify the Kereta Malam owner/outlet identity and map product and table UUIDs from that same project. Never map based on product names alone.
3. The browser must send product IDs, quantities, table ID, customer name, notes, and a request idempotency token—not trusted prices or totals.
4. A server-side RPC must load current active products, validate that the table and products belong to the verified outlet, calculate all line totals and the order total, and insert the order atomically.
5. QR order creation must not permanently decrement inventory before staff acceptance. Either reserve stock with an explicit expiry/release path or decrement once on acceptance; rejection/cancellation must not leave stock reduced.
6. The order must use `source = 'qr'`, the verified `table_id`, and the exact statuses/payment state expected by the POS UI. The cashier must see it in the correct outlet.
7. Use only a Supabase publishable/anon key in browser code. Never expose a service-role or secret key.
8. Keep the customer checkout clearly labeled DEMO until every production test below passes.

## Required acceptance tests

- Empty cart and invalid quantities are rejected.
- A forged price/total is ignored; server-side database prices are used.
- An invalid or cross-outlet product/table is rejected.
- Repeated submission with the same idempotency token creates only one order.
- A valid QR order appears in the correct POS order list with correct table, items, totals, notes, and status.
- New, accepted, rejected, and cancelled orders have the intended stock behavior; stock never decrements twice.
- A failed network request does not falsely show “order received”.
- Cashier payment confirmation creates exactly one payment record and updates the order once.

## Current blockers

- The audited Supabase project had no verified outlet rows/mapping for this restaurant, and historical orders included `outlet_id IS NULL`.
- The QR order RPC has not yet been proven safe for public checkout; client-supplied totals and stock timing need correction.
- The deployed POS project's canonical Supabase ID has not been proven from the available connection.
- End-to-end production checkout and build/test results have not been verified.

## Release decision

**Keep QR checkout in demo mode.** Do not route customer orders into production until the project/outlet mapping is proven and all acceptance tests pass. This document is a gate/checklist, not a claim that the integration has been implemented.
