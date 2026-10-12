package id.keretamalam.cafe.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.keretamalam.cafe.data.config.BackendIntegrationStatus
import id.keretamalam.cafe.data.config.SupabaseConfigManager
import id.keretamalam.cafe.data.model.*
import id.keretamalam.cafe.hardware.PrinterRole
import id.keretamalam.cafe.ui.navigation.PosModuleRoute
import id.keretamalam.cafe.util.CsvExporter
import id.keretamalam.cafe.viewmodel.PosUiState
import id.keretamalam.cafe.viewmodel.PosViewModel

// ============================================================================
// 0. STAFF LOGIN — keep backend configuration and local sandbox out of this screen.
// ============================================================================
@Composable
fun LoginComposeScreen(
    viewModel: PosViewModel,
    uiState: PosUiState,
    isTabletOrWide: Boolean
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .padding(horizontal = if (isTabletOrWide) 32.dp else 20.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "KERETA MALAM CAFE",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Masuk ke akun staf untuk melanjutkan",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val activeMsg = uiState.errorMessage ?: uiState.successMessage
            if (activeMsg != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (uiState.errorMessage != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeMsg,
                            color = if (uiState.errorMessage != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearBannerMessages() }) {
                            Text("Tutup")
                        }
                    }
                }
            }

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email staf") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = { viewModel.loginWithSupabaseAuth(email.trim(), password) },
                        enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isLoading) "Memproses..." else "Masuk")
                    }
                    Text(
                        text = "Gunakan email dan password akun staf yang terdaftar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(onClick = { viewModel.setThemeMode(!uiState.darkTheme) }) {
                Text(if (uiState.darkTheme) "Gunakan Mode Terang" else "Gunakan Mode Gelap")
            }
        }
    }
}

@Composable
fun RenderModuleScreen(
    module: PosModuleRoute,
    isTabletOrWide: Boolean,
    onNavigateModule: (PosModuleRoute) -> Unit,
    viewModel: PosViewModel,
    uiState: PosUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isTabletOrWide) 16.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Notifikasi Sukses / Error
        val activeMsg = uiState.errorMessage ?: uiState.successMessage
        if (activeMsg != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (uiState.errorMessage != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.errorMessage != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearBannerMessages() }) {
                        Text(
                            text = "Tutup",
                            color = if (uiState.errorMessage != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        // Gate Verifikasi Supabase jika masih INACTIVE_PENDING_VERIFICATION
        if (
            uiState.backendStatus == BackendIntegrationStatus.INACTIVE_PENDING_VERIFICATION &&
            module != PosModuleRoute.PENGATURAN &&
            module != PosModuleRoute.PRINTER &&
            module != PosModuleRoute.AI_ASSISTANT
        ) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "INTEGRASI SUPABASE: BELUM AKTIF (MENUNGGU VERIFIKASI PROJECT ID)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Hasil pemeriksaan referensi menunjukkan dua Project ID berbeda: '${SupabaseConfigManager.CANDIDATE_REPO_PROJECT_ID}' (pada supabase/config.toml di repo) dan '${SupabaseConfigManager.CANDIDATE_WORKER_PROJECT_ID}' (pada bundle pos.yuripratama246.workers.dev). Aplikasi tidak memilih salah satunya secara asumsi dan tidak menampilkan data contoh sebagai data database produksi.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onNavigateModule(PosModuleRoute.PENGATURAN) }) {
                            Text("Verifikasi & Hubungkan Supabase")
                        }
                        OutlinedButton(onClick = { viewModel.enableLocalSandboxMode() }) {
                            Text("Aktifkan Mode Simulasi UI Lokal Terlabel")
                        }
                    }
                }
            }
            return@Column
        }

        when (module) {
            PosModuleRoute.DASHBOARD -> DashboardComposeScreen(uiState, isTabletOrWide, onNavigateModule)
            PosModuleRoute.AI_ASSISTANT -> AiAssistantComposeScreen(viewModel, uiState)
            PosModuleRoute.KASIR -> KasirComposeScreen(viewModel, uiState, isTabletOrWide)
            PosModuleRoute.PESANAN -> PesananComposeScreen(viewModel, uiState, isTabletOrWide)
            PosModuleRoute.HISTORY -> HistoryComposeScreen(viewModel, uiState)
            PosModuleRoute.SHIFT_KASIR -> ShiftKasirComposeScreen(viewModel, uiState)
            PosModuleRoute.AUDIT_SHIFT -> AuditShiftComposeScreen(viewModel, uiState)
            PosModuleRoute.ACTIVITY_LOG -> ActivityLogComposeScreen(uiState)
            PosModuleRoute.PRODUK -> ProdukStokComposeScreen(viewModel, uiState)
            PosModuleRoute.MEJA -> MejaQrComposeScreen(viewModel, uiState, isTabletOrWide)
            PosModuleRoute.INVENTORY -> InventoryComposeScreen(viewModel, uiState)
            PosModuleRoute.PROMOSI -> PromosiComposeScreen(viewModel, uiState)
            PosModuleRoute.KEUANGAN -> KeuanganComposeScreen(viewModel, uiState)
            PosModuleRoute.LAPORAN -> LaporanPenjualanComposeScreen(viewModel, uiState)
            PosModuleRoute.PEGAWAI -> PegawaiComposeScreen(viewModel, uiState)
            PosModuleRoute.PENGATURAN -> PengaturanOutletComposeScreen(viewModel, uiState)
            PosModuleRoute.PRINTER -> PrinterComposeScreen(viewModel, uiState, isTabletOrWide)
            PosModuleRoute.WEBSITE_MANAGEMENT -> WebsiteManagementComposeScreen(viewModel, uiState)
        }
    }

    // Dialog Struk Pembayaran & Cetak ESC/POS di Tengah Layar
    val receiptOrder = uiState.receiptDialogOrder
    if (receiptOrder != null) {
        Dialog(
            onDismissRequest = { viewModel.openReceiptDialog(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 460.dp)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Struk Pembayaran #${receiptOrder.orderNo}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        Text(
                            text = viewModel.formatOrderForThermalPreview(receiptOrder, false),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewModel.openReceiptDialog(null) }) {
                            Text("Tutup")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(onClick = {
                            viewModel.printOrderToThermal(receiptOrder, PrinterRole.KITCHEN, true)
                        }) {
                            Text("Cetak Dapur")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(onClick = {
                            viewModel.printOrderToThermal(receiptOrder, PrinterRole.CASHIER, true)
                        }) {
                            Text("Cetak Struk")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 1. DASHBOARD SCREEN (ADAPTIVE PHONE & TABLET + GRAFIK VISUAL)
// ============================================================================
@Composable
private fun DashboardComposeScreen(
    uiState: PosUiState,
    isTabletOrWide: Boolean,
    onNavigateModule: (PosModuleRoute) -> Unit
) {
    val avgOrder = if (uiState.paidOrders.isNotEmpty()) {
        uiState.todayRevenue / uiState.paidOrders.size
    } else 0.0

    val posRevenue = uiState.paidOrders.filter { it.source == "pos" }.sumOf { it.total }
    val qrRevenue = uiState.paidOrders.filter { it.source == "qr" }.sumOf { it.total }
    val totalRevSafe = uiState.todayRevenue.coerceAtLeast(1.0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 4 Kartu KPI Utama (4 kolom sejajar di Tablet, 2x2 di HP)
        item {
            if (isTabletOrWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardKpiCard(
                        modifier = Modifier.weight(1f),
                        title = "Omzet Terkonfirmasi",
                        value = RupiahFormatter.format(uiState.todayRevenue),
                        subtitle = "${uiState.paidOrders.size} transaksi lunas"
                    )
                    DashboardKpiCard(
                        modifier = Modifier.weight(1f),
                        title = "Pesanan Aktif",
                        value = "${uiState.activeOrdersCount}",
                        subtitle = "Baru, Diproses & Siap"
                    )
                    DashboardKpiCard(
                        modifier = Modifier.weight(1f),
                        title = "Rata-rata Transaksi",
                        value = RupiahFormatter.format(avgOrder),
                        subtitle = "Per struk terbayar"
                    )
                    DashboardKpiCard(
                        modifier = Modifier.weight(1f),
                        title = "Stok Menipis",
                        value = "${uiState.lowStockProducts.size} Produk",
                        subtitle = "Perlu restock segera"
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardKpiCard(
                            modifier = Modifier.weight(1f),
                            title = "Omzet Terkonfirmasi",
                            value = RupiahFormatter.format(uiState.todayRevenue),
                            subtitle = "${uiState.paidOrders.size} struk lunas"
                        )
                        DashboardKpiCard(
                            modifier = Modifier.weight(1f),
                            title = "Pesanan Aktif",
                            value = "${uiState.activeOrdersCount}",
                            subtitle = "Antrean dapur"
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardKpiCard(
                            modifier = Modifier.weight(1f),
                            title = "Rata-rata Struk",
                            value = RupiahFormatter.format(avgOrder),
                            subtitle = "Per transaksi"
                        )
                        DashboardKpiCard(
                            modifier = Modifier.weight(1f),
                            title = "Stok Menipis",
                            value = "${uiState.lowStockProducts.size} SKU",
                            subtitle = "Di bawah batas"
                        )
                    }
                }
            }
        }

        // Grafik Visual Penjualan per Kanal & Metode Pembayaran
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Grafik Distribusi Omzet & Metode Pembayaran", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { onNavigateModule(PosModuleRoute.KASIR) }) {
                            Text("Buka Kasir POS")
                        }
                    }

                    Text(
                        text = "Kanal Kasir POS (${RupiahFormatter.format(posRevenue)}) vs QR Ordering Meja (${RupiahFormatter.format(qrRevenue)})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { (posRevenue / totalRevSafe).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Rincian Metode Pembayaran:", style = MaterialTheme.typography.labelMedium)
                    listOf("cash" to "Tunai (Cash)", "qris" to "QRIS", "transfer" to "Transfer Bank", "ewallet" to "E-Wallet").forEach { (code, label) ->
                        val methodRev = uiState.paidOrders.filter { (it.paymentMethod ?: "cash") == code }.sumOf { it.total }
                        val ratio = (methodRev / totalRevSafe).toFloat().coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(RupiahFormatter.format(methodRev), fontWeight = FontWeight.SemiBold)
                            }
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Produk Terlaris & Peringatan Stok Menipis
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Top 5 Produk Terlaris Hari Ini", style = MaterialTheme.typography.titleMedium)
                    val topProducts = uiState.paidOrders
                        .flatMap { it.orderItems }
                        .groupBy { it.productName }
                        .map { (name, list) -> Triple(name, list.sumOf { it.quantity }, list.sumOf { it.lineTotal }) }
                        .sortedByDescending { it.third }

                    if (topProducts.isEmpty()) {
                        Text("Belum ada produk terjual pada periode ini.")
                    } else {
                        topProducts.take(5).forEachIndexed { idx, (name, qty, rev) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${idx + 1}. $name ($qty terjual)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = RupiahFormatter.format(rev),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Indikator Stok Menipis & Habis", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { onNavigateModule(PosModuleRoute.PRODUK) }) {
                            Text("Kelola Produk & Stok →")
                        }
                    }
                    if (uiState.lowStockProducts.isEmpty()) {
                        Text("Seluruh produk berada di atas batas minimum stok.")
                    } else {
                        uiState.lowStockProducts.forEach { prod ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${prod.name} (Min: ${prod.lowStockThreshold})")
                                Text(
                                    text = if (prod.isOutOfStock) "HABIS (0)" else "Sisa ${prod.stock}",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardKpiCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String
) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(subtitle, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ============================================================================
// 2. AI ASSISTANT SCREEN
// ============================================================================
@Composable
private fun AiAssistantComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var promptInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AI Assistant — Kereta Malam Cafe", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Mengikuti implementasi modul AI pada sistem referensi Cafe Flow yang memanggil Supabase Edge Function 'ai-assistant'.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (uiState.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED)
                                "STATUS EDGE FUNCTION: SIAP MENGHUBUNGI /functions/v1/ai-assistant"
                            else
                                "STATUS BACKEND AI: BELUM TERHUBUNG (MENUNGGU VERIFIKASI SUPABASE)",
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Aplikasi tidak menghasilkan jawaban AI palsu/simulasi. Seluruh permintaan dikirim ke Supabase Edge Function 'ai-assistant' apabila kredensial telah diverifikasi.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Text("Prompt Cepat Operasional Cafe:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Analisis omzet & jam sibuk hari ini",
                        "Rekomendasi restock produk stok menipis",
                        "Evaluasi efektivitas promo diskon malam"
                    ).forEach { quick ->
                        AssistChip(
                            onClick = { promptInput = quick },
                            label = { Text(quick) }
                        )
                    }
                }

                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    label = { Text("Tanyakan analisis penjualan, HPP, atau stok ke AI Assistant...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.askAiAssistant(promptInput) },
                    enabled = !uiState.isLoading
                ) {
                    Text(if (uiState.isLoading) "Menghubungi Edge Function..." else "Kirim ke Edge Function 'ai-assistant'")
                }

                if (!uiState.aiReplyText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.aiReplyText,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. KASIR (POS) SCREEN — ADAPTIVE PHONE & TABLET
// ============================================================================
@Composable
private fun KasirComposeScreen(
    viewModel: PosViewModel,
    uiState: PosUiState,
    isTabletOrWide: Boolean
) {
    var showConfirmCheckoutDialog by remember { mutableStateOf(false) }
    var customizingProduct by remember { mutableStateOf<Product?>(null) }
    var customSugar by remember { mutableStateOf("Normal Sugar") }
    var customIce by remember { mutableStateOf("Normal Ice") }
    var customCup by remember { mutableStateOf("Regular") }
    var customExtraShot by remember { mutableStateOf(false) }
    var customOatMilk by remember { mutableStateOf(false) }
    var customNote by remember { mutableStateOf("") }
    var mobileTab by remember { mutableStateOf(0) } // 0 = Katalog Produk, 1 = Keranjang & Bayar

    val filteredProducts = remember(uiState.products, uiState.selectedCategoryId, uiState.searchQuery) {
        uiState.products.filter { p ->
            p.isActive &&
                (uiState.selectedCategoryId == "ALL" || p.categoryId == uiState.selectedCategoryId) &&
                (uiState.searchQuery.isBlank() || p.name.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val catalogContent: @Composable (Modifier) -> Unit = { mod ->
        Column(modifier = mod, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setKasirFilter(uiState.selectedCategoryId, it) },
                label = { Text("Cari menu kopi, makanan, snack...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedCategoryId == "ALL",
                    onClick = { viewModel.setKasirFilter("ALL", uiState.searchQuery) },
                    label = { Text("Semua Menu") }
                )
                uiState.categories.forEach { cat ->
                    FilterChip(
                        selected = uiState.selectedCategoryId == cat.id,
                        onClick = { viewModel.setKasirFilter(cat.id, uiState.searchQuery) },
                        label = { Text(cat.name) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium)
                                if (!product.description.isNullOrBlank()) {
                                    Text(
                                        text = product.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${RupiahFormatter.format(product.price)} · ${
                                        when {
                                            product.isOutOfStock -> "HABIS"
                                            product.isLowStock -> "Stok Menipis: ${product.stock}"
                                            else -> "Stok: ${product.stock}"
                                        }
                                    }",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (product.isOutOfStock || product.isLowStock)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    enabled = !product.isOutOfStock,
                                    onClick = {
                                        customizingProduct = product
                                        customSugar = "Normal Sugar"
                                        customIce = "Normal Ice"
                                        customCup = "Regular"
                                        customExtraShot = false
                                        customOatMilk = false
                                        customNote = ""
                                    }
                                ) {
                                    Text("Opsi")
                                }
                                Button(
                                    enabled = !product.isOutOfStock,
                                    onClick = { viewModel.addToCart(product) }
                                ) {
                                    Text("+ Tambah")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val cartContent: @Composable (Modifier) -> Unit = { mod ->
        OutlinedCard(modifier = mod) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Keranjang Transaksi", style = MaterialTheme.typography.titleMedium)
                        if (uiState.cart.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearCart() }) {
                                Text("Kosongkan")
                            }
                        }
                    }

                    // Pilih Meja
                    Text("Pilih Meja / Layanan:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.tables.forEach { tbl ->
                            FilterChip(
                                selected = uiState.selectedTableId == tbl.id,
                                onClick = { viewModel.setKasirOrderMeta(tableId = tbl.id) },
                                label = { Text("${tbl.name} (${tbl.code})") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.customerName,
                        onValueChange = { viewModel.setKasirOrderMeta(customer = it) },
                        label = { Text("Nama Pelanggan (Walk-in)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.orderNotes,
                        onValueChange = { viewModel.setKasirOrderMeta(notes = it) },
                        label = { Text("Catatan Pesanan / Dapur") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    HorizontalDivider()

                    if (uiState.cart.isEmpty()) {
                        Text(
                            text = "Keranjang masih kosong. Pilih produk dari katalog.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        uiState.cart.forEach { item ->
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.product.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                text = "${item.quantity} x ${RupiahFormatter.format(item.unitPriceWithAddons)} = ${RupiahFormatter.format(item.lineTotal)}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(onClick = { viewModel.updateCartItemQuantity(item.cartItemId, -1) }) { Text("-") }
                                            Text("${item.quantity}", modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                                            OutlinedButton(onClick = { viewModel.updateCartItemQuantity(item.cartItemId, 1) }) { Text("+") }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = item.note,
                                        onValueChange = { viewModel.updateCartItemNote(item.cartItemId, it) },
                                        label = { Text("Catatan Item") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }

                    // Pilih Promosi
                    Text("Promosi / Diskon:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedPromotionId == null,
                            onClick = { viewModel.setKasirOrderMeta(promoId = null) },
                            label = { Text("Tanpa Promo") }
                        )
                        uiState.promotions.filter { it.active }.forEach { promo ->
                            FilterChip(
                                selected = uiState.selectedPromotionId == promo.id,
                                onClick = { viewModel.setKasirOrderMeta(promoId = promo.id) },
                                label = { Text("${promo.name} (Min ${RupiahFormatter.format(promo.minPurchase)})") }
                            )
                        }
                    }

                    // Metode Pembayaran
                    Text("Metode Pembayaran:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("cash" to "Tunai", "qris" to "QRIS", "transfer" to "Transfer", "ewallet" to "E-Wallet").forEach { (code, label) ->
                            FilterChip(
                                selected = uiState.paymentMethod == code,
                                onClick = { viewModel.setKasirOrderMeta(payMethod = code) },
                                label = { Text(label) }
                            )
                        }
                    }

                    if (uiState.paymentMethod == "cash") {
                        OutlinedTextField(
                            value = uiState.cashReceivedInput,
                            onValueChange = { viewModel.setKasirOrderMeta(cashInput = it) },
                            label = { Text("Uang Tunai Diterima (Rp)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(onClick = {
                                viewModel.setKasirOrderMeta(cashInput = uiState.grandTotal.toLong().toString())
                            }) { Text("Uang Pas") }
                            listOf(50000L, 100000L, 200000L).forEach { nominal ->
                                OutlinedButton(onClick = {
                                    viewModel.setKasirOrderMeta(cashInput = nominal.toString())
                                }) {
                                    Text(RupiahFormatter.format(nominal.toDouble()))
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal")
                        Text(RupiahFormatter.format(uiState.subtotal))
                    }
                    if (uiState.discountAmount > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Diskon Promo")
                            Text("-${RupiahFormatter.format(uiState.discountAmount)}")
                        }
                    }
                    if (uiState.taxAndServiceAmount > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pajak & Layanan")
                            Text(RupiahFormatter.format(uiState.taxAndServiceAmount))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Tagihan", fontWeight = FontWeight.Bold)
                        Text(RupiahFormatter.format(uiState.grandTotal), fontWeight = FontWeight.Bold)
                    }
                    if (uiState.paymentMethod == "cash" && uiState.cart.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kembalian")
                            Text(RupiahFormatter.format(uiState.changeAmount), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Button(
                        onClick = { showConfirmCheckoutDialog = true },
                        enabled = uiState.cart.isNotEmpty() && !uiState.isCheckoutProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (uiState.isCheckoutProcessing) "Memproses Transaksi..."
                            else "Konfirmasi & Bayar · ${RupiahFormatter.format(uiState.grandTotal)}"
                        )
                    }
                }
            }
        }
    }

    if (isTabletOrWide) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            catalogContent(Modifier.weight(1.3f).fillMaxHeight())
            cartContent(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TabRow(selectedTabIndex = mobileTab) {
                Tab(
                    selected = mobileTab == 0,
                    onClick = { mobileTab = 0 },
                    text = { Text("Katalog Menu") }
                )
                Tab(
                    selected = mobileTab == 1,
                    onClick = { mobileTab = 1 },
                    text = { Text("Keranjang (${uiState.cart.sumOf { it.quantity }})") }
                )
            }
            if (mobileTab == 0) {
                catalogContent(Modifier.weight(1f).fillMaxWidth())
                if (uiState.cart.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${uiState.cart.sumOf { it.quantity }} item · ${RupiahFormatter.format(uiState.grandTotal)}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { mobileTab = 1 }) {
                                Text("Lanjut Bayar →", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                cartContent(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    // Dialog Kustomisasi Item di Tengah Layar
    val targetCustom = customizingProduct
    if (targetCustom != null) {
        Dialog(
            onDismissRequest = { customizingProduct = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 460.dp)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Opsi Kustomisasi · ${targetCustom.name}", style = MaterialTheme.typography.titleLarge)
                    Text("Takaran Gula:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Normal Sugar", "Less Sugar", "No Sugar").forEach { s ->
                            FilterChip(selected = customSugar == s, onClick = { customSugar = s }, label = { Text(s) })
                        }
                    }
                    Text("Takaran Es / Suhu:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Normal Ice", "Less Ice", "Hot / Warm").forEach { ic ->
                            FilterChip(selected = customIce == ic, onClick = { customIce = ic }, label = { Text(ic) })
                        }
                    }
                    Text("Ukuran Cup & Add-on:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = customCup == "Regular",
                            onClick = { customCup = "Regular" },
                            label = { Text("Regular") }
                        )
                        FilterChip(
                            selected = customCup == "Large",
                            onClick = { customCup = "Large" },
                            label = { Text("Large (+Rp5.000)") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = customExtraShot,
                            onClick = { customExtraShot = !customExtraShot },
                            label = { Text("Extra Shot (+Rp6.000)") }
                        )
                        FilterChip(
                            selected = customOatMilk,
                            onClick = { customOatMilk = !customOatMilk },
                            label = { Text("Oat Milk (+Rp8.000)") }
                        )
                    }
                    OutlinedTextField(
                        value = customNote,
                        onValueChange = { customNote = it },
                        label = { Text("Catatan Khusus Item") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { customizingProduct = null }) { Text("Batal") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val addons = buildList {
                                if (customCup == "Large") add(ProductAddon(id = "ad-lg", name = "Large Cup", price = 5000.0))
                                if (customExtraShot) add(ProductAddon(id = "ad-es", name = "Extra Espresso Shot", price = 6000.0))
                                if (customOatMilk) add(ProductAddon(id = "ad-om", name = "Oat Milk", price = 8000.0))
                            }
                            viewModel.addToCart(
                                product = targetCustom,
                                sugar = customSugar,
                                ice = customIce,
                                cupSize = customCup,
                                addons = addons,
                                customNote = customNote
                            )
                            customizingProduct = null
                        }) {
                            Text("Masukkan ke Keranjang")
                        }
                    }
                }
            }
        }
    }

    // Dialog Konfirmasi Transaksi Final di Tengah Layar
    if (showConfirmCheckoutDialog) {
        Dialog(
            onDismissRequest = { if (!uiState.isCheckoutProcessing) showConfirmCheckoutDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 440.dp)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Konfirmasi Transaksi Final", style = MaterialTheme.typography.titleLarge)
                    Text("Pastikan metode pembayaran dan nominal sudah sesuai. Tombol dilindungi dari penekanan ganda (idempotency lock).")
                    Text("Meja: ${uiState.tables.find { it.id == uiState.selectedTableId }?.name ?: "Takeaway"}")
                    Text("Metode: ${uiState.paymentMethod.uppercase()}")
                    Text("Total Akhir: ${RupiahFormatter.format(uiState.grandTotal)}", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showConfirmCheckoutDialog = false }) { Text("Batal") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            enabled = !uiState.isCheckoutProcessing,
                            onClick = {
                                showConfirmCheckoutDialog = false
                                viewModel.submitPosCheckout()
                            }
                        ) {
                            Text("Konfirmasi & Simpan Transaksi")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. PESANAN (ORDERS & QR ORDERING) SCREEN
// ============================================================================
@Composable
private fun PesananComposeScreen(
    viewModel: PosViewModel,
    uiState: PosUiState,
    isTabletOrWide: Boolean
) {
    var statusFilter by remember { mutableStateOf<OrderStatus?>(null) }
    var sourceFilter by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }

    val filtered = remember(uiState.orders, statusFilter, sourceFilter, search) {
        uiState.orders.filter { o ->
            (statusFilter == null || o.status == statusFilter) &&
                (sourceFilter == "ALL" || o.source.equals(sourceFilter, ignoreCase = true)) &&
                (search.isBlank() || o.orderNo.toString().contains(search) || (o.customerName ?: "").contains(search, true))
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Cari #No Order atau Nama Pelanggan...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            if (isTabletOrWide) {
                listOf("ALL" to "Semua Kanal", "pos" to "POS Kasir", "qr" to "QR Meja").forEach { (code, label) ->
                    FilterChip(
                        selected = sourceFilter == code,
                        onClick = { sourceFilter = code },
                        label = { Text(label) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = statusFilter == null,
                onClick = { statusFilter = null },
                label = { Text("Semua Status (${uiState.orders.size})") }
            )
            OrderStatus.entries.forEach { st ->
                FilterChip(
                    selected = statusFilter == st,
                    onClick = { statusFilter = st },
                    label = { Text(st.labelId) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { order ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Order #${order.orderNo} (${order.source.uppercase()})", fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${order.restaurantTable?.name ?: "Takeaway"} · ${order.customerName ?: "Walk-in"} · ${order.createdAt}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(order.status.labelId, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (order.isPaid) "Lunas (${(order.paymentMethod ?: "cash").uppercase()})" else "Menunggu Konfirmasi",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        HorizontalDivider()
                        order.orderItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${item.quantity}x ${item.productName}${if (!item.note.isNullOrBlank()) " (${item.note})" else ""}")
                                Text(RupiahFormatter.format(item.lineTotal))
                            }
                        }
                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total: ${RupiahFormatter.format(order.total)}", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = {
                                    viewModel.printOrderToThermal(order, PrinterRole.KITCHEN)
                                }) { Text("Cetak Dapur") }
                                OutlinedButton(onClick = {
                                    viewModel.openReceiptDialog(order)
                                }) { Text("Struk") }
                            }
                        }

                        // Konfirmasi Pembayaran QR
                        if (order.source == "qr" && !order.isPaid && order.status != OrderStatus.CANCELLED) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { viewModel.approveQrOrder(order, "qris") }) {
                                    Text("Setujui QRIS")
                                }
                                OutlinedButton(onClick = { viewModel.approveQrOrder(order, "cash") }) {
                                    Text("Terima Tunai")
                                }
                                OutlinedButton(onClick = { viewModel.rejectQrOrder(order) }) {
                                    Text("Tolak")
                                }
                            }
                        }

                        // Tombol Alur Status Pesanan
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (order.status) {
                                OrderStatus.NEW -> Button(onClick = { viewModel.updateOrderStatus(order, OrderStatus.PREPARING) }) {
                                    Text("Proses ke Dapur")
                                }
                                OrderStatus.PREPARING -> Button(onClick = { viewModel.updateOrderStatus(order, OrderStatus.READY) }) {
                                    Text("Tandai Siap Saji")
                                }
                                OrderStatus.READY -> Button(onClick = { viewModel.updateOrderStatus(order, OrderStatus.COMPLETED) }) {
                                    Text("Selesaikan Order")
                                }
                                else -> {}
                            }
                            if (order.status != OrderStatus.COMPLETED && order.status != OrderStatus.CANCELLED) {
                                OutlinedButton(onClick = { viewModel.updateOrderStatus(order, OrderStatus.CANCELLED) }) {
                                    Text("Batalkan")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. HISTORY TRANSAKSI SCREEN
// ============================================================================
@Composable
private fun HistoryComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    val context = LocalContext.current
    var fromDate by remember { mutableStateOf("2026-10-01") }
    var toDate by remember { mutableStateOf("2026-10-12") }
    var search by remember { mutableStateOf("") }

    val filteredOrders = remember(uiState.orders, fromDate, toDate, search) {
        uiState.orders.filter { o ->
            val datePart = o.createdAt.take(10)
            val inRange = (fromDate.isBlank() || datePart >= fromDate) && (toDate.isBlank() || datePart <= toDate)
            val matchQuery = search.isBlank() ||
                o.orderNo.toString().contains(search) ||
                (o.customerName ?: "").contains(search, ignoreCase = true)
            inRange && matchQuery
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            CsvExporter.writeSalesReportToUri(context, uri, "$fromDate s/d $toDate", filteredOrders)
                .onSuccess { viewModel.showNotice("CSV Riwayat Transaksi berhasil disimpan.") }
                .onFailure { viewModel.showNotice(it.message ?: "Gagal menyimpan CSV.", isError = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Riwayat Transaksi (${filteredOrders.size} Order)", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Total Nilai Periode: ${RupiahFormatter.format(filteredOrders.filter { it.isPaid }.sumOf { it.total })}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = { csvLauncher.launch("history-transaksi-kereta-malam.csv") }) {
                Text("Ekspor CSV History")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fromDate,
                onValueChange = { fromDate = it },
                label = { Text("Dari (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = toDate,
                onValueChange = { toDate = it },
                label = { Text("Sampai (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Cari #Order / Nama") },
                modifier = Modifier.weight(1.2f),
                singleLine = true
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredOrders, key = { it.id }) { order ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("#${order.orderNo} · ${order.customerName ?: "Walk-in"} (${order.source.uppercase()})", fontWeight = FontWeight.Bold)
                            Text(
                                text = "${order.createdAt} · ${order.restaurantTable?.name ?: "Takeaway"} · ${order.status.labelId}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(RupiahFormatter.format(order.total), fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { viewModel.openReceiptDialog(order) }) {
                                Text("Lihat Struk")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 6. OPENING & CLOSING (SHIFT KASIR) SCREEN
// ============================================================================
@Composable
private fun ShiftKasirComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    val activeShift = uiState.shifts.find { it.status == "open" }
    val cashSales = uiState.paidOrders.filter { it.paymentMethod == "cash" }.sumOf { it.total }
    var openingInput by remember { mutableStateOf("300000") }
    var closingInput by remember { mutableStateOf("") }
    var closingNotes by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (activeShift != null) {
                val expectedCash = activeShift.openingCash + cashSales
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Shift Kasir Sedang Berjalan (OPEN)", style = MaterialTheme.typography.titleLarge)
                        Text("Kasir: ${activeShift.staffName} · Dibuka: ${activeShift.openedAt}")
                        Text("Modal Kas Awal: ${RupiahFormatter.format(activeShift.openingCash)}")
                        Text("Penjualan Tunai Shift Ini: ${RupiahFormatter.format(cashSales)}")
                        Text("Ekspektasi Kas Laci: ${RupiahFormatter.format(expectedCash)}", fontWeight = FontWeight.Bold)

                        HorizontalDivider()
                        OutlinedTextField(
                            value = closingInput,
                            onValueChange = { closingInput = it },
                            label = { Text("Hitung Fisik Kas Akhir (Rp)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = closingNotes,
                            onValueChange = { closingNotes = it },
                            label = { Text("Catatan Penutupan Shift") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = {
                            val actual = closingInput.toDoubleOrNull() ?: expectedCash
                            viewModel.closeActiveShift(activeShift.id, actual, expectedCash, closingNotes)
                        }) {
                            Text("Tutup Shift Sekarang (Closing)")
                        }
                    }
                }
            } else {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Buka Shift Kasir Baru (Opening)", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = openingInput,
                            onValueChange = { openingInput = it },
                            label = { Text("Modal Kas Awal (Rp)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(onClick = {
                            viewModel.openNewShift(openingInput.toDoubleOrNull() ?: 0.0)
                        }) {
                            Text("Buka Shift Kasir")
                        }
                    }
                }
            }
        }

        item {
            Text("Riwayat Shift Kasir", style = MaterialTheme.typography.titleMedium)
        }

        items(uiState.shifts, key = { it.id }) { sh ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${sh.staffName} · ${sh.status.uppercase()}", fontWeight = FontWeight.Bold)
                    Text("Dibuka: ${sh.openedAt}${sh.closedAt?.let { " · Ditutup: $it" } ?: ""}")
                    Text("Modal Awal: ${RupiahFormatter.format(sh.openingCash)} · Kas Akhir: ${sh.closingCash?.let { RupiahFormatter.format(it) } ?: "-"}")
                }
            }
        }
    }
}

// ============================================================================
// 7. AUDIT SHIFT SCREEN
// ============================================================================
@Composable
private fun AuditShiftComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardKpiCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Shift",
                    value = "${uiState.shifts.size}",
                    subtitle = "Tercatat"
                )
                DashboardKpiCard(
                    modifier = Modifier.weight(1f),
                    title = "Terverifikasi Audit",
                    value = "${uiState.shifts.count { it.auditVerified }}",
                    subtitle = "Sesuai SOP"
                )
            }
        }
        items(uiState.shifts, key = { it.id }) { shift ->
            val variance = (shift.closingCash ?: 0.0) - (shift.expectedCash ?: 0.0)
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${shift.staffName} (${shift.status.uppercase()})", fontWeight = FontWeight.Bold)
                        Text("Modal Awal: ${RupiahFormatter.format(shift.openingCash)} · Ekspektasi: ${RupiahFormatter.format(shift.expectedCash ?: 0.0)}")
                        if (shift.closingCash != null) {
                            Text("Kas Akhir: ${RupiahFormatter.format(shift.closingCash)} · Selisih: ${RupiahFormatter.format(variance)}")
                        }
                        if (!shift.notes.isNullOrBlank()) {
                            Text("Catatan: ${shift.notes}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (shift.auditVerified) {
                        Text("Terverifikasi", fontWeight = FontWeight.Bold)
                    } else {
                        Button(onClick = { viewModel.verifyShiftAudit(shift.id) }) {
                            Text("Verifikasi Audit")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 8. LOG AKTIVITAS SCREEN
// ============================================================================
@Composable
private fun ActivityLogComposeScreen(uiState: PosUiState) {
    var search by remember { mutableStateOf("") }
    val filteredLogs = remember(uiState.auditLogs, search) {
        uiState.auditLogs.filter {
            search.isBlank() ||
                it.action.contains(search, true) ||
                it.actorName.contains(search, true) ||
                it.entityType.contains(search, true)
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Cari aksi log, nama staf, atau entitas...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredLogs, key = { it.id }) { log ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(log.action, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(log.createdAt, style = MaterialTheme.typography.labelMedium)
                        }
                        Text("Aktor: ${log.actorName} · Entitas: ${log.entityType} (${log.entityId ?: "-"})")
                        if (log.detailsText.isNotBlank()) {
                            Text(log.detailsText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
