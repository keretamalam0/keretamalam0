# Kereta Malam Cafe — Android Native POS (Kotlin + Jetpack Compose)

Aplikasi Point of Sale (POS) operasional staf **Kereta Malam Cafe** berbasis **Android Native (Kotlin 2.0 + Jetpack Compose + Material 3 + MVVM + Coroutines)** yang mengadaptasi seluruh struktur modul, tampilan, skema database, RPC, serta alur kerja dari sistem referensi **Cafe Flow**.

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
- Aplikasi **TIDAK** mengklaim integrasi Supabase sudah aktif sebelum diverifikasi pengguna.
- Secara default, status integrasi Supabase ditandai secara eksplisit sebagai **`BELUM AKTIF (Menunggu Verifikasi Project ID & Publishable Key)`**.
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

## 2. Struktur Lengkap Project Android Native (22 File)

```
android-kereta-malam-cafe/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties.example
├── gradle/wrapper/
│   └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/
        │   ├── strings.xml
        │   └── themes.xml
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
                └── screens/
                    ├── AllScreens.kt
                    └── ManagementScreens.kt
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
   - Anda juga dapat memasukkan atau memperbarui URL dan Publishable/Anon Key langsung dari layar **Login** atau menu **Pengaturan Outlet -> Konfigurasi Backend Supabase** di dalam aplikasi Android (disimpan secara terenkripsi menggunakan `EncryptedSharedPreferences`).

---

## 4. Cara Build APK Debug yang Dapat Diinstal

Pada Android Studio atau terminal lokal Anda dengan Android SDK API 35 terpasang, jalankan:

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

## 5. Rincian Fitur yang Berfungsi vs Simulasi vs Status Verifikasi Supabase

| Modul / Fitur | Status di Project Android Native | Keterangan Teknis |
| :--- | :--- | :--- |
| **Halaman Login & Manajemen Sesi (`LoginComposeScreen`)** | **Berfungsi Penuh** | Mendukung 3 alur: (1) Login Supabase Auth (`/auth/v1/token?grant_type=password`), (2) Verifikasi langsung Project ID & Publishable Key, dan (3) Masuk Mode Simulasi UI Lokal dengan pemilihan peran (`Owner`, `Manager`, `Kasir`). |
| **Adaptasi Layout HP vs Tablet (`CafeFlowAppShell`)** | **Berfungsi Penuh** | Layar Tablet (`>= 840.dp`) menggunakan `PermanentNavigationDrawer` + layout split-pane. Layar HP (`< 840.dp`) menggunakan `ModalNavigationDrawer` + `NavigationBar` bawah 5 tab (`Dashboard`, `Kasir`, `Pesanan`, `History`, `Modul`) + keranjang kasir pop-up dialog agar hemat ruang. |
| **UI Monochrome Classic, Light/Dark Mode & Skala Zoom (40%–120%)** | **Berfungsi Penuh** | Menggunakan `CompositionLocalProvider(LocalDensity)` di `MainActivity.kt` dengan pilihan cepat `40%`, `50%`, `60%`, `80%`, `100%` dan kontrol floating `- 100% +`. Semua dialog berpusat di tengah layar dengan `verticalScroll` sehingga tombol aksi tidak pernah terpotong. |
| **Kasir, Varian/Add-on Produk & Idempotency Lock (`KasirComposeScreen`)** | **Berfungsi Penuh** | Mendukung pencarian, filter kategori, modal pemilihan suhu (`Hot`/`Iced`), kadar gula (`Normal`/`Less Sugar`), ekstra add-on (`Extra Shot`, `Oat Milk`, `Cheese`), catatan item, promo/diskon, kalkulasi PPN 10% + Service 5%, dialog konfirmasi final, dan kunci `Mutex` + `idempotency_key` UUID. |
| **Pesanan, Meja & Generator QR Bitmap (`PesananComposeScreen`, `MejaQrComposeScreen`)** | **Berfungsi Penuh** | Filter status & sumber pesanan (`POS` vs `QR`), alur status (`pending -> confirmed -> preparing -> ready -> completed`), persetujuan pesanan QR dengan metode bayar, dan generator QR Code `Bitmap` asli menggunakan `com.google.zxing:core` menuju URL `?table=<uuid>&table_name=<nama>`. |
| **Produk & Stok, Inventory, dan Promosi (`ManagementScreens.kt`)** | **Berfungsi Penuh** | CRUD produk & kategori, indikator stok menipis (`stock <= min_stock`), pencatatan mutasi stok (`in`/`out`/`adjustment`) dengan pencegahan pemotongan stok ganda di klien, serta CRUD kode promo persen/nominal. |
| **Dashboard Visual, Keuangan P&L, Laporan & Ekspor CSV (`CsvExporter.kt`)** | **Berfungsi Penuh** | Grafik batang omzet 7 hari, grafik komposisi pembayaran, produk terlaris, tab Pengeluaran & Laba Rugi (Gross Sales, COGS/HPP, Gross Profit, Net Profit), dan ekspor file `.csv` nyata ke memori perangkat Android via Storage Access Framework (`ActivityResultContracts.CreateDocument("text/csv")`). |
| **Opening & Closing Shift, Audit Shift, Log Aktivitas & Pegawai RBAC** | **Berfungsi Penuh** | Buka/tutup shift kasir dengan perhitungan selisih kas fisik vs ekspektasi sistem, verifikasi audit shift oleh Manager/Owner, log aktivitas berurutan waktu, dan matriks izin RBAC per staf. |
| **Integrasi Database Supabase Produksi (`CafeFlowRepository.kt`)** | **Kode HTTP/RPC Siap — Menunggu Verifikasi Pengguna** | Seluruh request OkHttp ke `/rest/v1/*`, `/rest/v1/rpc/create_pos_order`, dan `/auth/v1/*` sudah diimplementasikan. Secara default berstatus **`INACTIVE_PENDING_VERIFICATION`** sampai pengguna memasukkan & memverifikasi Project ID serta Publishable Key. |
| **Data Awal Operasional Cafe (Ketika Supabase Belum Diverifikasi)** | **Simulasi Sandbox Lokal** | Ketika pengguna memilih **Mode Simulasi UI Lokal**, transaksi, pesanan QR masuk, mutasi stok, shift, dan pengeluaran disimpan di state memori aplikasi (`StateFlow`) dan diberi badge kuning eksplisit **`SIMULASI LOKAL · Bukan Database Produksi`**. |
| **AI Assistant (`AiAssistantComposeScreen`)** | **Simulasi Analitik Lokal / Menunggu Edge Function** | Jika Supabase Edge Function `/functions/v1/ai-assistant` belum aktif, aplikasi secara jujur menghitung ringkasan analitik dari state lokal dan memberi label bahwa respons berasal dari kalkulasi lokal, bukan klaim koneksi AI cloud palsu. |
| **Dual Bluetooth Printer Thermal ESC/POS (`EscPosBluetoothManager.kt`)** | **Kode Bluetooth RFCOMM Siap — Perlu Uji Fisik** | Mengimplementasikan pemindaian perangkat Bluetooth (`bondedDevices`), izin runtime Android 12+ (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`), soket RFCOMM SPP (`00001101-0000-1000-8000-00805F9B34FB`), format kertas 58mm (32 karakter) & 80mm (48 karakter), peran Kasir & Dapur, serta pratinjau teks struk thermal di layar. |
