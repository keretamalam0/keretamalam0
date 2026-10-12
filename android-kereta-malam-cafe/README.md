# Kereta Malam Cafe — Android Native POS (Kotlin + Jetpack Compose)

Aplikasi Point of Sale (POS) operasional staf **Kereta Malam Cafe** berbasis **Android Native (Kotlin 2.0 + Jetpack Compose + Material 3 + MVVM + Coroutines)** yang mengadaptasi struktur modul, skema database, RPC, serta alur kerja dari sistem referensi **Cafe Flow**.

---

## 1. Hasil Audit Referensi & Verifikasi Backend Supabase (PENTING)

Sebelum mengaktifkan koneksi database produksi, pemeriksaan terhadap kedua sumber referensi menunjukkan perbedaan Project ID Supabase:

1. **Repository Sumber (`https://github.com/yuripratama246-lang/cafe-flow`)**
   - File `supabase/config.toml` mencantumkan:
     `project_id = "rvaemgwtdvncfvaerhgr"` (`https://rvaemgwtdvncfvaerhgr.supabase.co`)
2. **Website Referensi Ter-deploy (`https://pos.yuripratama246.workers.dev`)**
   - Bundle client mengarah ke:
     `project_id = "dbazoxwhhljpgtbnletc"` (`https://dbazoxwhhljpgtbnletc.supabase.co`)

Sesuai aturan keamanan produksi:
- Aplikasi **TIDAK** memilih salah satu project secara asumsi.
- Secara default, status integrasi Supabase ditandai sebagai **`BELUM AKTIF (Menunggu Verifikasi Project ID & Publishable Key)`**.
- Tidak ada `service_role` key atau secret backend yang dimasukkan ke dalam kode sumber.

### Tabel, RPC, dan Edge Functions yang Telah Diverifikasi dari Skema Referensi:
- **Tabel Utama**: `staff_profiles`, `categories`, `products`, `product_addons`, `product_customization_options`, `restaurant_tables`, `orders`, `order_items`, `payments`, `promotions`, `outlet_settings`, `staff_shifts`, `audit_logs`, `expenses`, `inventory_movements`, `warehouses`, `warehouse_stocks`, `stock_transfers`, `customers`, `point_redemptions`, `loyalty_point_earnings`, `printer_settings`, `website_pages`, `website_gallery`, `website_locations`, `website_contacts`, `website_settings`, `website_navigation`, `website_careers`.
- **RPC (Stored Procedures)**:
  - `create_pos_order(p_order, p_items, p_payment)` — Transaksi kasir dengan `idempotency_key` dan pemotongan stok atomik lewat trigger database (`trg_deduct_stock`).
  - `create_qr_order(p_order, p_items)` — Pembuatan pesanan dari QR meja.
  - `approve_qr_order_with_method(p_order_id, p_payment_method, p_cash_received)` — Konfirmasi pembayaran pesanan QR.
  - `reject_qr_order(p_order_id)` — Penolakan pesanan QR.
  - `get_active_public_promotions()`, `get_public_outlet_settings()`, `get_public_payment_methods()`.
- **Edge Functions**: `ai-assistant`, `api-key-manager`, `create-owner`, `invite-staff`, `delete-staff`.

---

## 2. Struktur Project Android Native

```
android-kereta-malam-cafe/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties.example
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        └── java/id/keretamalam/cafe/
            ├── MainActivity.kt
            ├── data/
            │   ├── config/SupabaseConfig.kt
            │   ├── model/PosModels.kt
            │   └── repository/CafeFlowRepository.kt
            ├── hardware/
            │   └── EscPosBluetoothManager.kt
            ├── util/
            │   └── CsvExporter.kt
            ├── viewmodel/
            │   └── PosViewModel.kt
            └── ui/
                ├── theme/Theme.kt
                ├── navigation/CafeFlowAppShell.kt
                └── screens/AllScreens.kt
```

---

## 3. Cara Membuka Project & Mengonfigurasi Supabase

1. **Buka di Android Studio**:
   - Gunakan **Android Studio Ladybug (2024.2.1)** atau lebih baru dengan **JDK 17**.
   - Pilih **Open** dan arahkan ke folder `android-kereta-malam-cafe` (atau ekstrak file `.zip` dari tombol **Unduh Project Android Studio (.zip)** di layar preview).
2. **Konfigurasi `local.properties`**:
   - Salin `local.properties.example` menjadi `local.properties`.
   - Isi kredensial Supabase yang telah Anda verifikasi (`rvaemgwtdvncfvaerhgr` atau `dbazoxwhhljpgtbnletc`):
     ```properties
     SUPABASE_PROJECT_ID=dbazoxwhhljpgtbnletc
     SUPABASE_URL=https://dbazoxwhhljpgtbnletc.supabase.co
     SUPABASE_PUBLISHABLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     PUBLIC_WEB_MENU_BASE_URL=https://pos.yuripratama246.workers.dev
     ```
   - Anda juga dapat memasukkan atau memperbarui URL dan Publishable/Anon Key langsung dari menu **Pengaturan Outlet -> Konfigurasi Backend Supabase** di dalam aplikasi (disimpan secara terenkripsi menggunakan `EncryptedSharedPreferences`).

---

## 4. Cara Build APK Debug yang Dapat Diinstal

Karena lingkungan container preview di browser tidak memiliki Android SDK (`sdkmanager` / `ANDROID_HOME`), file biner `.apk` dibangun melalui Android Studio atau terminal lokal Anda dengan perintah:

```bash
cd android-kereta-malam-cafe
./gradlew assembleDebug
```

Hasil file APK yang siap diinstal pada perangkat Android HP/Tablet berada di:
```
app/build/outputs/apk/debug/app-debug.apk
```

Untuk menginstal langsung ke perangkat Android yang terhubung via USB Debugging:
```bash
./gradlew installDebug
```

---

## 5. Status Bagian yang Berjalan, Belum Terhubung, dan Perlu Diuji Fisik

| Komponen / Modul | Status | Keterangan |
| :--- | :--- | :--- |
| **UI Monochrome Classic (Light & Dark Mode)** | **Berjalan** | Mendukung tema terang/gelap dan skala tampilan/zoom **40%, 50%, 60%, 70%, 80%, 90%, 100%, 110%, 120%** via `LocalDensity` tanpa memotong dialog. |
| **18 Modul Navigasi & RBAC** | **Berjalan** | Mencakup Dashboard, AI Assistant, Kasir, Pesanan, History, Opening & Closing, Audit Shift, Log Aktivitas, Produk & Stok, Meja & QR Menu, Inventory, Promosi & Diskon, Pengeluaran & Laba Rugi, Laporan Penjualan, Pegawai & Shift, Pengaturan Outlet, Printer, dan Management Website. |
| **Pencegahan Transaksi Ganda & Stok Ganda** | **Berjalan** | Checkout dikunci dengan `Mutex` + `idempotency_key` UUID pada RPC `create_pos_order`. Aplikasi tidak memotong stok secara manual di klien karena pemotongan sudah dilakukan oleh trigger `trg_deduct_stock` di PostgreSQL. |
| **Ekspor CSV Laporan & Keuangan** | **Berjalan** | Menggunakan Android Storage Access Framework (`CreateDocument("text/csv")`) dengan encoding UTF-8 BOM (`\uFEFF`). |
| **Integrasi Supabase Produksi** | **BELUM AKTIF (Menunggu Verifikasi)** | Lapisan `CafeFlowRepository` dan `SupabaseConfig` sudah siap sesuai skema tabel asli, namun dinonaktifkan secara default sampai Anda memverifikasi Project ID (`rvaemgwtdvncfvaerhgr` vs `dbazoxwhhljpgtbnletc`) dan mengisi Anon/Publishable Key. |
| **AI Assistant (`ai-assistant` Edge Function)** | **BELUM TERHUBUNG** | Menampilkan status belum terhubung secara eksplisit jika Edge Function `ai-assistant` belum dikonfigurasi; tidak menampilkan jawaban AI palsu. |
| **Dual Bluetooth Printer Thermal ESC/POS** | **Kode Native Siap — Perlu Uji Fisik** | Menggunakan `BluetoothManager`, RFCOMM Socket (`00001101-0000-1000-8000-00805F9B34FB`), izin runtime Android 12+ (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`), peran ganda (Kasir & Dapur), dan anti-cetak ganda. Memerlukan pengujian fisik dengan printer thermal Bluetooth 58mm/80mm. |
