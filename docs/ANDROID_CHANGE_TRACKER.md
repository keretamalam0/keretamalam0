# Native Android Kotlin — Change Tracker

Target repository: `keretamalam0/keretamalam0`
Project directory: `android-kereta-malam-cafe/`

## Status
- [x] GitHub account connection can list the `keretamalam0` account.
- [x] Read access verified on `MainActivity.kt`.
- [x] Write access verified by tracker commit `465013ee0bd552d5122f0c5ec7a46a9689504142`.
- [ ] Android Gradle build run and result recorded (not run from this connector session yet).
- [ ] Device-level verification recorded.

## Requested feature batches
- [ ] Dashboard analytics and charts
- [ ] Cashier product grid, photos, and cart/table behavior
- [ ] Order lanes and transaction history with guarded deletion
- [ ] Shift opening/closing audit
- [ ] Activity log filters and CSV export
- [ ] Product, category, add-on, and customization management
- [x] Removed manual “Simulasi Order QR Masuk” button; end-to-end live QR verification still pending
- [ ] Inventory adjustments and movement history
- [ ] Promotion eligibility and server-side validation
- [ ] Expenses and profit/loss based on real data
- [ ] Sales reports and submenus
- [ ] Staff roles, permissions, invitations, and backend enforcement
- [ ] Outlet settings, localization, taxes, service charges, rounding
- [ ] Payment methods and QRIS/bank/e-wallet configuration
- [ ] Bluetooth printer discovery and receipt printing
- [ ] Remove Website Management from Android; secure Super Admin separation

## Work log
- `ab5503c5ee9c1bd8670d88bbd5cf3b11efcc3e0f`: removed the fake QR-order simulation button from the native table/QR screen.
- `2376c4db330190ff6d69d28a6cfe842138d28da1`: removed hardcoded fake Bluetooth printer entries from `PosViewModel`; an empty real scan now gives an actionable pairing message. This is a small correctness fix, not full printer validation.

## Safety notes
- Native Kotlin project only; do not edit the separate `keretacafe` web repo for Android work.
- Android and Cafe Flow share Supabase; inspect schema, RPCs, and RLS before database changes.
- Do not claim a feature is complete until code and build/runtime behavior have been checked.
