# Kereta Malam Cafe — Change Tracker

Last updated: 2026-10-12

This tracker distinguishes **verified existing behavior** from **new work requested**. A successful APK build only proves compilation; it does not prove Supabase authentication, RLS, or production flows.

## Verified baseline / audit notes
- [x] Android project and web preview/source are both present in this repository.
- [x] GitHub Actions Android build workflow exists and writes Supabase configuration from GitHub Actions secrets.
- [x] Android Build #15 succeeded for commit `58e274e1d2ee609afd21cf49cabf16c6f0e658f8` after removing a stale `WEBSITE_MANAGEMENT` reference. This proves compilation only, not end-to-end behavior.
- [x] Android Build #18 succeeded for commit `b01e646a32046ca4fc5db1db86722a774972aec5`; this verifies the Supabase verification guard compiles, not that the runtime request succeeds against the intended client database.
- [x] Login UI was previously simplified to email/password staff login.
- [ ] Supabase login + staff profile retrieval tested against the intended production project.
- [x] Android login no longer silently grants a fallback Cashier profile when no staff record exists; it clears the session and rejects missing/inactive staff profiles (runtime test still required).
- [ ] Confirm the dedicated Kereta Malam Cafe Supabase project. The connected Supabase account currently exposes only `dbazoxwhhljpgtbnletc` (project name `cafe-flow`), while source repo config references `rvaemgwtdvncfvaerhgr`; do not reuse the shared Cafe Flow project for the client app until isolation is explicitly confirmed.
- [ ] Confirm production schema, RLS policies, RPCs, and Edge Functions match the Android repository calls.
- [x] Read-only audit of the connected `dbazoxwhhljpgtbnletc` project found 36 public tables with RLS enabled and migrations through `20261011130719`; this is the `cafe-flow` project, not yet confirmed as a dedicated Kereta Malam database.
- [ ] Security review remains open: Supabase advisors flag 15 `SECURITY DEFINER` functions executable by `anon`; several contain `auth.uid()`/permission guards, but grants and function bodies still need review and least-privilege verification. `ai_memory_messages`, `api_request_logs`, and `google_sheets_integrations` have RLS enabled with no policies (deny-by-default, but intended access should be confirmed).
- [ ] High-priority RPC review: the connected `cafe-flow` database's `create_qr_order` SECURITY DEFINER function is executable by `anon`, reads subtotal and unit prices from client-supplied JSON, and selects an outlet globally by latest settings instead of binding the request to an explicit outlet/table. Do not modify this shared database until Kereta Malam's canonical project is confirmed; fix server-side price/tenant validation in the dedicated project before production.
- [ ] Remove or clearly isolate seeded sandbox data from any live/production session. Current Android UI state initializes from `SandboxSeedData`; web preview state initializes from `sandboxSeed`.
- [x] On Android backend sync failure, clear the auth session, log out, and clear operational lists/cart/table selection rather than leaving seeded data visible as if live (commit `6af016d65e4100cf178d94d4cfb8f4e6854768bc`; included in the later successful Build #15 commit `58e274e1d2ee609afd21cf49cabf16c6f0e658f8`, so compilation was verified but runtime behavior remains untested).
- [x] Supabase configuration no longer infers verification from populated URL/key fields; a live PostgREST request to `staff_profiles` is now required before activation (commits `217795b`, `22d7f44`, `b01e646`). Android Build #18 passed; live runtime verification remains pending.

## Requested feature backlog

Status meanings: **Not started** = no implementation verified for this request; **Audit needed** = existing code may partially support it, but behavior has not been verified end-to-end; **Do not change** = user explicitly asked to preserve current behavior.

### Dashboard
- [ ] Sales total, paid orders, average transaction, busiest time.
- [ ] Minimal shortcut cards to Cashier, Orders, Products & Stock, Tables & QR.
- [ ] Blue sales trend line with filled/shadow area.
- [ ] Hourly activity blue bar chart with shadow.
- [ ] Best-selling menu section.
- [ ] Date-filtered payment breakdown with donut chart, amount and transaction counts.
- [ ] Outlet status: active products, low stock, active orders, inactive products.

### Cashier
- [ ] Product photos.
- [ ] Horizontal-scrolling menu/category list.
- [ ] Manual cart supports “No Table”.

### Orders
- [ ] Tabs: New, Processing, Ready, History.
- [ ] Enforce transitions: paid → Processing; ready → Ready; then History.
- [ ] Rejected/cancelled and completed orders in History.
- [ ] Prevent duplicate orders and verify transitions across web/Android + backend.

### Transaction history
- [ ] List all transactions.
- [ ] Delete one, selected multiple, or all transactions.
- [ ] Type-to-confirm destructive-action dialog.
- [ ] Verify safe backend permissions and audit trail before enabling permanent deletion.

### Opening/Closing and shift audit
- [ ] **Do not change the existing Opening & Closing system.**
- [ ] Add “All shifts requiring review” and “Active shifts” summary cards.
- [ ] Detect mismatch between prior closing cash and next opening cash.
- [ ] Opening/closing history opens detailed reconciliation view.
- [ ] Owner/manager verification records result and reason.

### Activity log
- [ ] Date-range filter, CSV export, refresh.
- [ ] Filters for login/logout, orders/transactions, payments, products, menu/items, promotions, outlet settings, stock movement, categories, shifts.

### Products & Stock
- [ ] Create/edit product and upload photo from gallery.
- [ ] Name, short description, price, category, stock, low-stock threshold.
- [ ] Add-ons/toppings with price and visibility.
- [ ] Sugar/ice/size customizations and per-option price.
- [ ] Active/inactive and QR-menu visibility.
- [ ] Persist and display data consistently across clients.

### Tables & QR
- [ ] Add/edit tables and active/inactive state.
- [ ] “View QR Menu” opens the correct public QR menu.
- [ ] Replace immediate real-order creation from “Simulate Incoming Order” with clearly separated demo behavior.

### Inventory
- [ ] Stock adjustment by item, new quantity, reason/note.
- [ ] Stock movement history and audit trail.
- [ ] Summary cards for active SKUs, low stock, out of stock, movements.
- [ ] Search results include product photo and stock details.
- [ ] Persist changes and verify inventory totals.

### Promotions & discounts
- [ ] Form for name, percent/nominal type and value, minimum spend, scope/product, minimum item quantity, active state.
- [ ] Validate eligibility consistently in QR ordering and Cashier.
- [ ] Allow user/cashier to choose whether to apply an eligible voucher.
- [ ] Verify quantity rule (e.g. 2 Matcha drinks) and discount cap.

### Expenses & P&L
- [ ] Audit layout, formulas, date windows, and source data before changing calculations.

### Sales reports
- [ ] Submenus: Summary, Daily Sales, Product Sales, Sales by Hour, Payment Report, Channel Report, Order Details.

### Staff & shifts
- [ ] Staff list with name/email.
- [ ] Add staff form with role.
- [ ] Owner-managed granular permissions and full-access option.
- [ ] Email invitation, activation/verification, password setup.
- [ ] List registered staff accounts.
- [ ] Enforce roles and permissions in database/API, not only by hiding UI.

### Outlet settings
- [ ] Outlet name, address, WhatsApp.
- [ ] Global language support (Indonesian, English, Chinese, Thai, Vietnamese and extensible options).
- [ ] Tax/PPN toggle and rate; service-charge toggle and rate; rounding rules.
- [ ] Apply settings consistently to cashier and QR ordering.

### Payment methods
- [ ] Owner-managed methods: cash, bank transfer, e-wallet, QRIS.
- [ ] Bank/provider, account details, instructions; QRIS image upload.
- [ ] Active/inactive and QR-order visibility.
- [ ] Persist and apply in cashier and QR ordering.

### Printer
- [ ] Discover actual nearby Bluetooth devices (no fake device list), connect, select role, test print on supported hardware.

### Super Admin / multi-owner
- [x] Remove “Management Website” menu from both web preview and Android navigation.
- [ ] Verify the intended “Alfana Alvin” account/email before assigning Super Admin; do not guess an identity.
- [ ] Super Admin operational view limited to Owner Management.
- [ ] Create owners and invite them by email to set password.
- [ ] Isolate all operational data by owner/outlet.
- [ ] Roles: Super Admin, Owner, Manager, Cashier.
- [ ] Enforce authorization and tenant isolation at database/backend level.

## Work log
- 2026-10-12: Added tracker (commit `962d054a0e99bb5219ea782564ddebe9b8980348`). Removed “Management Website” from web navigation (commit `0e789fbf2329a538dab4e9a667026d2d69f3cbbf`) and Android navigation (commit `fcf3122b2a334f01c3540300647962722e803e9c`). Hardened Android login (commit `cdc0e695c91eb38f8db7aa74438366ca32277f4f`). Build #15 succeeded for route cleanup (commit `58e274e1d2ee609afd21cf49cabf16c6f0e658f8`), which also includes the sync-failure cleanup change. New Supabase live-verification guard was added in commits `217795b`, `22d7f44`, and `b01e646`; Android Build #18 succeeded for `b01e646`, while live runtime verification remains pending. Read-only RPC review also flagged client-trusted price inputs and global outlet selection in `create_qr_order` on the connected shared `cafe-flow` project. Read-only Supabase audit found the only connected project is named `cafe-flow`, so it is not being assumed as the isolated Kereta Malam backend; security advisor findings are recorded above for follow-up. Build #14 failed because a stale route reference remained in `AllScreens.kt`; the subsequent route fix resolved that compile error.
