# Native Android Kotlin — Change Tracker

Target repository: `keretamalam0/keretamalam0`
Project directory: `android-kereta-malam-cafe/`

## Status
- [x] GitHub account connection can list the `keretamalam0` account.
- [x] Read access verified on `MainActivity.kt`.
- [ ] Write access verified by this tracker commit.
- [ ] Android Gradle build run and result recorded.
- [ ] Device-level verification recorded.

## Requested feature batches
- [ ] Dashboard analytics and charts
- [ ] Cashier product grid, photos, and cart/table behavior
- [ ] Order lanes and transaction history with guarded deletion
- [ ] Shift opening/closing audit
- [ ] Activity log filters and CSV export
- [ ] Product, category, add-on, and customization management
- [ ] Tables and QR menu consistency
- [ ] Inventory adjustments and movement history
- [ ] Promotion eligibility and server-side validation
- [ ] Expenses and profit/loss based on real data
- [ ] Sales reports and submenus
- [ ] Staff roles, permissions, invitations, and backend enforcement
- [ ] Outlet settings, localization, taxes, service charges, rounding
- [ ] Payment methods and QRIS/bank/e-wallet configuration
- [ ] Bluetooth printer discovery and receipt printing
- [ ] Remove Website Management from Android; secure Super Admin separation

## Safety notes
- Native Kotlin project only; do not edit the separate `keretacafe` web repo for Android work.
- Android and Cafe Flow share Supabase; inspect schema, RPCs, and RLS before database changes.
- Do not claim a feature is complete until code and build/runtime behavior have been checked.
