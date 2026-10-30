# Native Android Kotlin — Change Tracker

Target repository: `keretamalam0/keretamalam0`
Project directory: `android-kereta-malam-cafe/`

## Status
- [x] GitHub account connection can list the `keretamalam0` account.
- [x] Read access verified on `MainActivity.kt`.
- [x] Write access verified by tracker commit `465013ee0bd552d5122f0c5ec7a46a9689504142`.
- [ ] Android Gradle build verified by GitHub Actions (workflow added in `857052dd6ef318cb8b69bde173ff2cfe3c8da41b`; run result must be checked after GitHub executes it).
- [ ] Device-level verification recorded.

## 2026-10-22 — Native Android Laporan Kasir
- Added a dedicated `Laporan Kasir` navigation screen in the native Android app, accessible through the cashier/orders permission and available alongside the owner’s existing `Laporan Penjualan` menu.
- Added operational date/time range inputs (default 17:00 through 01:00 next day), paid/non-cancelled transaction filtering, category totals, expandable product detail, and Makanan/Minuman summary cards using existing order/item/product/category data; no new tables or mock sales data added.
- Category sales amounts allocate each paid order total proportionally across its item line totals so category amounts reconcile to the order total when order items are present. Food/drink grouping uses category-name keywords; unrecognized categories currently default to Makanan and should be reviewed against the cafe’s category naming.
- [ ] GitHub Actions APK build and runtime/device checks must pass before distributing the APK.

## 2026-10-21 — UI and reporting changes committed
- [x] AI chat history upgraded from a small alert to a near-full-screen conversation list; pin/unpin, open, delete one, and delete all are available. History remains device-local.
- [x] Dashboard KPI cards have zero default and pressed elevation while preserving their tinted backgrounds.
- [x] Dashboard supports a custom date range (YYYY-MM-DD) and CSV export of paid, non-cancelled orders in the selected period.
- [x] Shift audit keeps custom start/end date filters and can export the currently filtered shifts to CSV.
- [x] Activity log supports custom start/end date filters and CSV export of filtered log entries.
- [ ] Android Gradle build and runtime/device checks are still required; these commits are not yet build-verified.
- [ ] Continue auditing the remaining data/report screens (expenses, payments, inventory, customer data, and other exports) for consistent custom date ranges and CSV export.

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
- [x] Order alert sound: if local cached URL is missing, the app now attempts to recover the saved URL from Supabase when a new order arrives; actual playback and live-backend persistence still require device verification.
- [ ] Bluetooth printer discovery and receipt printing
- [x] Added native Android "Chat ke Kasir" inbox screen, table-based conversations, and reply RPC integration; menu label is now explicit in the Android navigation. QR Ordering customer entry point and web cashier chat route already exist in Cafe Flow. Build, database migration deployment, RLS, and device-level end-to-end verification remain pending.
- [x] Removed Website Management UI, local CMS state/actions, and unused CMS models from Android. [ ] Secure Super Admin separation remains pending.

## Work log
- `0e7fe44b6157a2799cb65568b4e04a37400c3cc5` / `a7ca7fcf5be6b22697733b177a82a9fedb36078b`: renamed the native cashier inbox menu/screen to “Chat ke Kasir” so it is easier to find in the Android module drawer; still requires APK build and live chat/RLS verification.
- `a8561ed1eaff7b3cc85d78d80191a3783b0d446a` / `497c9be7839b7bc8539151083a1d3d880757adb7` / `95ba1b118f03aff06558cb9993858ff29f531f49`: securely persist Supabase refresh tokens, refresh/retry password changes on an Auth 401, and display a readable server diagnostic when the error response is plain text/HTML or malformed JSON. APK build and real password-change verification remain pending.
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

- Follow-up after APK #306 testing: changed password update from PATCH to PUT for Supabase Auth `/auth/v1/user` (fixes HTTP 405); added per-thread delete button with confirmation to Android “Chat ke Kasir”, matching Cafe Flow web by deleting the thread row (message rows cascade) and refreshing the inbox. Build and live Supabase/RLS verification are required.

- `fefd35d28d0ea001ced85ffd052dd3b82c3e9f11`: sidebar brand now has explicit light/dark contrast, a modest size increase and slow recurring shimmer.
- `971d052f3b7c904cad3dc10eb417ff4fc1e9bd9c`: replaced the login galaxy background with a subtle monochrome railway and a train animation moving from top to bottom behind the existing login form. APK build and visual/device verification pending.
- `5ffe0fcaf86dbc6a8f5b0364ecc6abd41b09d4b0`: expanded the login background into a layered monochrome station scene with three independently moving detailed trains (two downward, one upward), perspective rails, carriage windows, metallic shading and headlights. Explicit white input text/cursor and light labels/borders fix email/password contrast on the dark card. Build and visual/device verification pending.

## Safety notes
- Native Kotlin project only; do not edit the separate `keretacafe` web repo for Android work.
- Android and Cafe Flow share Supabase; inspect schema, RPCs, and RLS before database changes.
- Do not claim a feature is complete until code and build/runtime behavior have been checked.

- Pending verification: shift open/close now writes to `staff_shifts` using owner/staff IDs from the authenticated outlet profile; model maps the database `note` column. Confirm Android build and live RLS behavior before considering fully verified.


- 2026-10-17: added shared outlet info fields (`opening_hours`, `instagram`, `receipt_note`, `receipt_footer`) to `OutletSettings`, Android outlet settings form, and Supabase payload. Related commits: `7a845ca2638cb93ae60c27953c14266910ee9fb0`, `54493aec277ee1f86f484784a8306c677b850bec`, `71d5d940cb0fdb9a434a0ea2264acb64578e17bb`, `413692d6b120f6174b63133a8b4311d0efe34a0c`. Shared schema migration must be applied to Supabase before the new fields can save; Android build/runtime verification is pending. Web repo `yuripratama246-lang/cafe-flow` now exposes the fields in outlet settings, reads them in QR Ordering Info, and prints outlet name/address/phone plus configured receipt note/footer. Web build succeeded for commit `09f5fd7a6d2c51f9d05de081b752dfbd209fcda6`; QR Worker deployment and live DB migration are not yet verified.

- 2026-10-17: Android Pengaturan Outlet now has a dedicated **Info Cafe di QR Ordering** section for cafe name, address, contact/WhatsApp, operating hours, and Instagram, with its own save action. Receipt note/footer are separated into a dedicated receipt settings card. Both actions persist through the existing `updateOutletSettings` payload to the shared `outlet_settings` row, so web QR Ordering can read the same values. Commit: `4af845df3e022c9912830cf79ae96080abfa0c25`. GitHub Actions APK build and runtime read-after-save verification are pending; do not call this complete until build passes and the resulting APK is checked.

- 2026-10-17: Android native Produk & Stok UI refresh: removed crowded Menipis/Habis filter chips and per-card Topping/Kustomisasi/Hapus button stack; product cards are simplified and open the existing product editor on tap, with a compact active toggle. Dashboard hourly bars now use a soft blue glow and vertical blue-to-transparent gradient. Product records, photo upload, and existing ViewModel/Supabase actions are preserved. Automated APK build and device verification pending.

- 2026-10-17: fixed the final **Simpan Produk** action in native Android: the first save still keeps the editor open for customization setup; saving the already-created product now closes the editor and shows a clear success confirmation only after the save callback succeeds. Commit: `ecff9b9be5f060c44062f22c6f2bce440771ba12`. APK build and device-level verification still pending.

- 2026-10-17: redesigned native Android Inventory screen with KPI cards (active SKUs, low/out-of-stock items, current total units, movement count), searchable current stock list, low-stock prioritization, recent movement history, and a Stock Adjustment dialog with product search, stock-in, stock-out, and physical stock opname. Opname treats the entered quantity as the actual counted stock; stock-in adds the entered amount (e.g. 9 + 200 = 209). ViewModel rejects stock-out beyond available stock. Live Supabase inventory writes remain explicitly unsupported in the current ViewModel and still need implementation before this workflow is production-live. Commits: `f030b6f`, `172955c`, `99f551d`.

- 2026-10-17: Implemented Inventory live stock adjustment writes: stock-in/out and physical stock opname now update `products.stock` in Supabase and insert `inventory_movements`; failed movement insertion attempts to roll back the stock update. Inventory screen now has explicit Adjust and Hapus menu actions, with delete confirmation. Adjustment dialog stays open on save failure. Build and device verification pending.

- 2026-10-17: Fixed dashboard payment donut aggregation to use the saved order payment method, with fallbacks to customer payment method/payment records. Normalizes bank-transfer variants and bank names into Transfer Bank, and recognizes E-Wallet aliases/providers (GoPay, OVO, DANA, ShopeePay, LinkAja). Build/runtime verification pending.

## Printer Bluetooth + Supabase configuration audit (2026-10-17)

- Added runtime permission request flow for Bluetooth scanning and test printing; requests the permission set appropriate to the Android API level.
- Printer settings now load from Supabase `printer_settings` when the Printer screen opens and save via PostgREST upsert on `owner_id,printer_role`; local SharedPreferences remains the device-level fallback because a Bluetooth MAC is specific to each device.
- The "Tes Cetak" action now calls the dedicated ESC/POS test-document method rather than printing a real order as a test.
- Supabase schema was inspected directly: `printer_settings` and `printer_print_jobs` exist, RLS policies scope rows by `owner_id = auth.uid()`; at audit time settings had 0 rows and all 24 existing print jobs were failed because no kitchen printer was configured on the device.
- Build/runtime validation is pending GitHub Actions. Physical printer compatibility, paper output, and encoding still require a real ESC/POS Bluetooth printer.



- 2026-10-17: Native Android Meja & QR now generates customer menu QR links using `https://keretamalamqr.netlify.app` instead of inheriting the configured/legacy Cloudflare base URL. Updated the missing-setting fallback and repository URL builder too. GitHub Actions build and device scan verification are required before considering complete.

- 2026-10-17: Performance and cross-version UI consistency pass. Transaction deletion now uses bounded 50-order batches (child order items/payments before orders) instead of three HTTP requests per order; delete-all fetches only order IDs page-by-page instead of loading nested transaction data. The transaction history UI clears immediately and rolls back to the prior visible orders if the backend delete fails; it no longer triggers a full refresh of every POS dataset after deletion. MainActivity now synchronizes status/navigation-bar icon contrast with the app light/dark theme while retaining edge-to-edge layout for Android 13+ / target API 35. Commits: `db0197d`, `378518c`, `308380e`. GitHub Actions build and device-level checks across Android 9, 13+, portrait, and landscape are required; no runtime verification has been claimed.
- Follow-up: if a batched deletion fails partway through, transaction history now re-fetches current server orders before falling back to the previous snapshot, avoiding a false rollback display when some batches already succeeded. Commit: `ee83f58`.

- 2026-10-21: Removed the animated railway/station Canvas from the native Android login screen and removed its animation imports. Login form, dark static background, authentication call, theme toggle, and existing field contrast are retained. Build and device-level startup/login timing verification are still required.


## Android follow-up — Dashboard export, activity log, employee access, inactive products (2026-10-21)

- Removed the Dashboard's dedicated Export CSV button and its dashboard-only CSV document launcher. Dashboard date-range filtering and KPI/chart rendering remain.
- Added a **Menu Nonaktif** tab to Produk & Stok. It lists inactive products and lets the owner open a product for management/reactivation; the inactive count is shown in the tab content.
- Added category filters to Log Aktivitas for Pegawai & akses, Produk, Stok, Shift & audit, Pembayaran, and Pengaturan. Existing custom date filtering, search, reset, and filtered CSV export remain.
- Refreshed employee permission labels to make AI chat history, shift audit/date filters, activity/audit logs, and CSV export scope clearer without introducing new permission keys that would not be enforced by existing authorization code.
- GitHub commits: `b99bd66` (remove Dashboard CSV), `716f048` (inactive product menu), `6af246b` (activity log categories), `333826b` (employee permission labels).
- Build and on-device validation are pending. Activity Log only displays records emitted by the existing audit/backend logging pipeline; these UI commits themselves are not retroactively inserted as business activity records.


## Android UX follow-up (2026-10-21)

- Login screen refreshed to a light, white-card design based on the supplied visual reference; removed the login-page theme toggle and added a show/hide password eye control.
- Login failure messages now show a user-facing generic message ("Email atau password salah") instead of backend/provider diagnostics. Successful login uses a neutral welcome message.
- Employee permission controls are collapsed by default and grouped into Menu, Dashboard, and Tindakan categories to reduce scrolling while preserving existing permission keys.
- Printer settings now provide a confirmation dialog and a Hapus action for each saved cashier/kitchen printer. Removal clears the local device setting and requests deletion of its saved remote setting.
- Thermal receipt previews, cashier receipts, and kitchen tickets format ISO timestamps as Asia/Jakarta (WIB) instead of showing UTC raw timestamps.
- Commits: `8014300` (light login/password visibility), `f1a7f1f` (generic login errors/WIB preview/printer removal action), `4b397fb` (printer delete UI), `6d56cfa` (compact employee access groups), `e7f5925` (WIB formatting on actual printer payloads).
- Build and physical-device verification are pending; confirm Kotlin compilation and test login errors, printer removal persistence, and printed receipt times on a real device.


- 2026-10-21: Cashier now refreshes product toppings and customization options from Supabase whenever the native cashier screen is entered, preventing stale in-memory options from hiding newly saved add-ons/options. Runtime and APK build verification required.


## 2026-10-30 — Per-account activity log and access controls
- Reworked the native Android Activity Log into an account-first view sourced from the live staff profile list. Selecting an account shows only records where that account is the actor, with date range, activity-type dropdown, search, readable action/entity labels, and filtered per-account CSV and PDF export.
- Added the activity export permission as a child permission under the Activity Log menu in Pegawai & Shift; the export action is disabled unless the signed-in staff profile has that permission. Existing menu, dashboard, and action permission controls remain grouped and persisted through the existing staff permission update flow.
- Added `staff_profiles.email`, backfilled it from Supabase Auth, and included it in Android staff profile reads so the account list can show email addresses.
- Added a database trigger that records staff account creation and changes to role, active status, and permission settings in `audit_logs`. New staff profiles therefore appear in the account list immediately after the normal staff refresh, even before their first login; the creation event is recorded automatically.
- Changed Android audit retrieval to paginated reads so the account detail is not limited to the first 200/1,000 records. Login and logout events were already emitted by the authentication repository; the new screen now renders their action names in human-readable Indonesian and formats timestamps as WIB.
- Database migration applied successfully; the new `staff_profiles.email` column and audit trigger were verified. Android Gradle build and physical-device interaction verification are still pending. Existing historical audit rows with a null actor ID cannot be safely assigned to an account retroactively.

- Follow-up: corrected Android audit inserts so `owner_id` is the outlet owner while `actor_user_id` remains the actual signed-in account; this makes cashier activity visible inside the owner's outlet scope. Backfilled 45 existing actor-linked audit rows to their staff profile owner scope. Legacy rows with null actor IDs remain unassignable without guessing.


- 2026-10-30: requested a fresh GitHub Actions Android Native Build for the latest per-account Activity Log changes; this tracker update intentionally triggers the workflow's `docs/ANDROID_CHANGE_TRACKER.md` push path. Await successful `assembleDebug` and APK artifact upload before distribution.
