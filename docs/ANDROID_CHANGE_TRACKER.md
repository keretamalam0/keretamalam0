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
- [x] Shift opening/closing audit: live create/close writes now target the existing `staff_shifts` schema; Android build/RLS/device verification pending
- [ ] Shift cash calculation: per-shift cash sales now derive from cash payment timestamps after `opened_at`; expected drawer cash is opening float plus shift cash sales. UI display, cash expenses/withdrawals, build and live verification still need completion.
- [ ] Activity log filters and CSV export
- [ ] Product, category, add-on, and customization management
- [x] Removed manual “Simulasi Order QR Masuk” button; end-to-end live QR verification still pending
- [ ] Inventory adjustments and movement history (live writes explicitly blocked until Supabase persistence is implemented)
- [ ] Promotion eligibility and server-side validation
- [ ] Expenses and profit/loss based on real data
- [ ] Sales reports and submenus
- [ ] Staff roles, permissions, invitations, and backend enforcement
- [ ] Outlet settings, localization, taxes, service charges, rounding
- [ ] Payment methods and QRIS/bank/e-wallet configuration (Android form now supports active/inactive action and only shows QR photo upload for QRIS; build, persistence, and QR Ordering integration verification pending)
- [ ] Bluetooth printer discovery and receipt printing
- [x] Removed Website Management UI, local CMS state/actions, and unused CMS models from Android. [ ] Secure Super Admin separation remains pending.

## Work log
- `232d98da98c7b95755e01a2e12bc6a3681c51fd9`: outlet settings now update the screen immediately and roll back if Supabase rejects the save.
- `bd3896884d67c137f81089c45bfb7c5fd613d729`: promotion create/activate/deactivate/delete now update the local UI immediately and restore prior state on server failure.
- `51dd90d5deb2ba739f4936fc0fe188209c0bb9b0`: product save/delete and category activation now update optimistically with rollback on failed writes. Build and runtime verification still pending.
- `becd58e893ccf8791fbf8c16c40fe4e70551be97`: added optimistic order-lane updates for approve, reject, and status changes. Orders leave the visible lane immediately while Supabase writes continue; failed writes restore the previous order state and show an error. APK build and device behavior still need verification.
- `934c849217e75e18ad1403ba941ac3c1db5c7de2`: `StaffProfile.canAccess` now denies operational POS module permissions to `SUPER_ADMIN`; the global admin is treated as a separate control plane. This is client-side separation only; the actual Super Admin portal and server-side authorization/audit policy remain pending.
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

- Pending verification: shift open/close now writes to `staff_shifts` using owner/staff IDs from the authenticated outlet profile; model maps the database `note` column. Confirm Android build and live RLS behavior before considering fully verified.
