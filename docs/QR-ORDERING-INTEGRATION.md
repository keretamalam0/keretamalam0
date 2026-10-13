# QR Ordering → Kereta Malam POS contract

This document records the verified contract between the customer QR website and the Android cashier app. It is a review checklist, not permission to enable live orders.

## Verified cashier read path

- `CafeFlowRepository.fetchOrders()` requests `orders` with nested `order_items`, `payments`, and `restaurant_tables(name)`, ordered newest-first, up to 500 records.
- The POS UI refresh routine calls `repository.fetchOrders()` and stores the result in the same `PosUiState.orders` collection used by the orders screen.
- Therefore, the POS client does not need a separate localStorage bridge or a new order transport. A QR order created in the shared database can appear in the queue only if it is written into the correct database and the signed-in cashier is authorized to read it.
- QR order payment must remain unpaid until a cashier explicitly accepts/confirms payment. The customer website must never create a payment row or mark the order paid.

## Contract for the QR website

The public website must submit only:
- registered table UUID,
- registered product UUIDs and positive quantities,
- customer name and optional note,
- stable idempotency UUID,
- source `qr` and payment state `unpaid`.

The server-side RPC must calculate all monetary values from database prices/settings. Never trust browser subtotal, unit price, discount, tax, or total. The RPC response currently wraps the created row as `{ order: { id: ... } }`.

## Release blockers confirmed from the connected database

1. `public.outlets` is empty, while `outlet_settings` has multiple owners. Do not guess which owner belongs to Kereta Malam.
2. `products` and `restaurant_tables` do not have owner/outlet columns or an explicit ownership mapping.
3. The current `create_qr_order` RPC chooses an outlet settings row globally and trusts client-supplied subtotal/item prices.
4. The `order_items` insert trigger changes stock at order creation; the rejection function marks an order cancelled but does not restore stock.
5. Existing orders have NULL `outlet_id`; no production order should be bulk-assigned to an owner without authoritative confirmation.

## Safe acceptance criteria

- [ ] Establish the correct owner and outlet from the actual Kereta Malam account, not a guessed row.
- [ ] Create explicit table/product mapping for that outlet and reject unmapped IDs.
- [ ] Replace the QR RPC with a server-priced, idempotent, outlet-validated implementation.
- [ ] Define stock reservation/restoration for QR pending, accepted, rejected, and cancelled states; test against a disposable database first.
- [ ] Verify the cashier account's RLS/permissions allow only its own outlet orders.
- [ ] Run one controlled QR order with a test table and product, confirm it appears in the cashier queue as source `qr`, remains unpaid, and is payable only from the cashier.
- [ ] Confirm rejection/cancellation restores stock correctly and duplicate submissions do not create duplicate orders.
- [ ] Keep production live flags disabled until all checks pass.

## Current implementation status

The QR website's guarded integration branch now parses the actual nested RPC order ID response and has contract tests. It intentionally remains fail-closed because the database RPC and outlet/product/table mapping are not yet safe for live traffic. No production database writes were made during this audit.