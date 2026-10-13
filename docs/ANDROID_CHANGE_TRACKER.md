# Native Android Kotlin — Change Tracker

Target repository: `keretamalam0/keretamalam0`
Project directory: `android-kereta-malam-cafe/`

## Status
- [x] GitHub account connection can list the `keretamalam0` account.
- [x] Read access verified on `MainActivity.kt`.
- [x] Write access verified by tracker commit `465013ee0bd552d5122f0c5ec7a46a9689504142`.
- [ ] Android Gradle build verified by GitHub Actions (workflow added in `857052dd6ef318cb8b69bde173ff2cfe3c8da41b`; run result must be checked after GitHub executes it).
- [ ] Device-level verification recorded.

## Requested feature batches
- [ ] Dashboard analytics and charts (partially implemented; date-range filter for paid revenue/payment breakdown/top products added in `bf5bd216430248d62bfb910c57cb5e845c1dab7d`; full KPI/chart review and build verification remain pending)
- [ ] Cashier product grid, photos, and cart/table behavior
- [ ] Order lanes and transaction history with guarded deletion
- [ ] Shift opening/closing audit (live writes explicitly blocked until Supabase persistence is implemented)
- [ ] Activity log filters and CSV export
- [ ] Product, category, add-on, and customization management
- [x] Removed manual “Simulasi Order QR Masuk” button; end-to-end live QR verification still pending
- [ ] Inventory adjustments and movement history (live writes explicitly blocked until Supabase persistence is implemented)
- [ ] Promotion eligibility and server-side validation
- [ ] Expenses and profit/loss based on real data
- [ ] Sales reports and submenus
- [ ] Staff roles, permissions, invitations, and backend enforcement
- [ ] Outlet settings, localization, taxes, service charges, rounding
- [ ] Payment methods and QRIS/bank/e-wallet configuration
- [ ] Bluetooth printer discovery and receipt printing
- [x] Removed Website Management UI, local CMS state/actions, and unused CMS models from Android. [ ] Secure Super Admin separation remains pending.

## Work log
- `42337a532981d5e7a917e667e601cd3f2b52a776`: disabled staff-profile switching in live mode at the UI layer; the ViewModel now permits profile switching only in explicitly labeled local sandbox mode. Staff permissions still require server-side RLS/RPC enforcement and a successful build/runtime test.
- `3c6b7723bdb66bbe61163909b50ccee0a742ddf2`: guarded `switchActiveStaffProfile` so selecting a staff row cannot change the active identity in verified backend mode; the user must log out and authenticate with the intended staff account.
- `30a4b9b2d30fcf31a3d31c1ab73dec06d80cf9a5`: dashboard date filtering now converts ISO timestamps to the device's local time zone before comparing calendar dates, avoiding UTC/local midnight mismatches. Build verification remains pending.
- `bf5bd216430248d62bfb910c57cb5e845c1dab7d`: added Dashboard date presets (today, last 7 days, last 30 days, all data) and scoped paid-revenue KPIs, payment-method breakdown, and best-selling products to the selected period. Compilation and date/time-zone behavior still require verification.
- `3cd38f63e61d5daaa1c2250bb18fcc5545ae8871`: removed unused native website CMS data models after deleting the inaccessible Website Management screen and local-only save/publish actions.
- `ba7c0f5b68df3d843393add1ab8176a53aaece0e`: removed Website CMS state and local-only mutations from `PosViewModel`.
- `a8452ca11882486ae2410639ca191f3020064e87`: removed the unused Website Management screen from `ManagementScreens.kt`; verified its screen symbol is no longer present in the app source files checked.
- `cd57604946920c67c61d0fde6db32c3924bf0b2c`: hardened encrypted credential/session storage: if `EncryptedSharedPreferences` fails, Android now fails closed instead of silently falling back to plaintext `SharedPreferences`. This security change requires build/runtime verification, including behavior on devices where the Android Keystore is unavailable or corrupted.
- `857052dd6ef318cb8b69bde173ff2cfe3c8da41b`: added `.github/workflows/android-native-build.yml` to build `assembleDebug` with JDK 17 on Android project changes/manual dispatch and upload the APK as a 14-day artifact. Build outcome is not yet verified; do not treat workflow creation as a passing build.
- `1d464676fa72305aae336c5616d95f9cc8efbc8b`: added live-backend guards to 11 operations that previously updated only in-memory state (staff creation, shifts/audit, product QR visibility, table add/delete, inventory movement, promotions, expenses, outlet settings). In live mode these now fail clearly rather than falsely implying persistence. These features remain pending real Supabase write implementations.
- `ab5503c5ee9c1bd8670d88bbd5cf3b11efcc3e0f`: removed the fake QR-order simulation button from the native table/QR screen.
- `ab5503c5ee9c1bd8670d88bbd5cf3b11efcc3e0f`: removed the fake QR-order simulation button from the native table/QR screen.
- `2376c4db330190ff6d69d28a6cfe842138d28da1`: removed hardcoded fake Bluetooth printer entries from `PosViewModel`; an empty real scan now gives an actionable pairing message. This is a small correctness fix, not full printer validation.

## Safety notes
- Native Kotlin project only; do not edit the separate `keretacafe` web repo for Android work.
- Android and Cafe Flow share Supabase; inspect schema, RPCs, and RLS before database changes.
- Do not claim a feature is complete until code and build/runtime behavior have been checked.
