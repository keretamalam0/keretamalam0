package id.keretamalam.cafe.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.keretamalam.cafe.data.config.BackendIntegrationStatus
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

        // Backend configuration is intentionally hidden from the client-facing UI.
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
    var range by remember { mutableStateOf("today") }
    var menuOpen by remember { mutableStateOf(false) }
    val today = java.time.LocalDate.now()
    fun dateOf(raw: String): java.time.ZonedDateTime? = runCatching {
        runCatching { java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault()) }
            .getOrElse { java.time.OffsetDateTime.parse(raw).atZoneSameInstant(java.time.ZoneId.systemDefault()) }
    }.getOrNull()
    val orders = uiState.orders.filter { order ->
        val date = dateOf(order.createdAt)?.toLocalDate()
        when (range) {
            "today" -> date == today
            "7days" -> date != null && !date.isBefore(today.minusDays(6)) && !date.isAfter(today)
            "30days" -> date != null && !date.isBefore(today.minusDays(29)) && !date.isAfter(today)
            else -> true
        }
    }
    val paid = orders.filter { it.isPaid && it.status != OrderStatus.CANCELLED }
    val revenue = paid.sumOf { it.total }
    val average = if (paid.isEmpty()) 0.0 else revenue / paid.size
    val activeCount = uiState.orders.count { it.status == OrderStatus.NEW || it.status == OrderStatus.PREPARING || it.status == OrderStatus.READY }
    val hours = (0..23).map { h -> orders.count { dateOf(it.createdAt)?.hour == h }.toFloat() }
    val peak = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0f }
    val best = paid.flatMap { it.orderItems }.groupBy { it.productName }
        .map { e -> Triple(e.key, e.value.sumOf { it.quantity }, e.value.sumOf { it.lineTotal }) }
        .sortedByDescending { it.second }.take(5)
    val maxQty = (best.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val payments = listOf("cash" to "Cash / Tunai", "bca" to "BCA", "qris" to "QRIS", "transfer" to "Transfer", "ewallet" to "E-Wallet", "debit" to "Kartu Debit")
        .map { pair ->
            val matched = paid.filter { (it.paymentMethod ?: "cash").lowercase().let { m ->
                if (pair.first == "bca") m.contains("bca") else if (pair.first == "transfer") m == "transfer" || m == "bank_transfer" else if (pair.first == "ewallet") m == "ewallet" || m == "e_wallet" else if (pair.first == "debit") m == "debit" || m == "card" else m == pair.first || (pair.first == "cash" && m == "tunai")
            } }
            Triple(pair.second, matched.sumOf { it.total }, matched.size)
        }.filter { it.second > 0.0 }
    val trend = (0..6).map { i ->
        val d = today.minusDays((6 - i).toLong())
        paid.filter { dateOf(it.createdAt)?.toLocalDate() == d }.sumOf { it.total }.toFloat()
    }
    val labels = (0..6).map { today.minusDays((6 - it).toLong()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) }
    val rangeLabel = when (range) { "today" -> "Hari ini"; "7days" -> "7 hari terakhir"; "30days" -> "1 bulan"; else -> "Semua data" }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Dashboard Outlet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(rangeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }) { Text("Periode ▾") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            listOf("today" to "Hari ini", "7days" to "7 hari", "30days" to "1 bulan", "all" to "Semua data").forEach { p ->
                                DropdownMenuItem(text = { Text(p.second) }, onClick = { range = p.first; menuOpen = false })
                            }
                        }
                    }
                }
            }
        }
        item {
            val metrics = listOf(
                Triple("Total Penjualan", RupiahFormatter.format(revenue), "Omzet pesanan lunas") to PosModuleRoute.LAPORAN,
                Triple("Pesanan Lunas", paid.size.toString(), "Transaksi berhasil") to PosModuleRoute.PESANAN,
                Triple("Rata-rata Pesanan", RupiahFormatter.format(average), "Per transaksi lunas") to PosModuleRoute.LAPORAN,
                Triple("Jam Tersibuk", peak?.let { "%02d.00–%02d.00".format(it, (it + 1) % 24) } ?: "—", if (peak == null) "Belum ada pesanan" else hours[peak].toInt().toString() + " pesanan") to PosModuleRoute.LAPORAN
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.chunked(if (isTabletOrWide) 4 else 2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { metric ->
                            DashboardKpiCard(Modifier.weight(1f), metric.first.first, metric.first.second, metric.first.third) { onNavigateModule(metric.second) }
                        }
                        repeat((if (isTabletOrWide) 4 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Text("Akses Cepat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val links = listOf(Triple("Transaksi Baru", "Buka kasir", PosModuleRoute.KASIR), Triple("Pesanan", activeCount.toString() + " aktif", PosModuleRoute.PESANAN), Triple("Produk & Stok", uiState.products.size.toString() + " produk", PosModuleRoute.PRODUK), Triple("Meja & QR", uiState.tables.size.toString() + " meja", PosModuleRoute.MEJA))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                links.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { link ->
                            OutlinedCard(onClick = { onNavigateModule(link.third) }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(link.first, fontWeight = FontWeight.Bold)
                                    Text(link.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Buka →", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tren Omzet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("7 hari terakhir", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DashboardLineChart(trend, Modifier.fillMaxWidth().height(160.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall) } }
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aktivitas per Jam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    DashboardBarChart(hours, Modifier.fillMaxWidth().height(130.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("00", "04", "08", "12", "16", "20", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall) } }
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Menu Terlaris", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (best.isEmpty()) Text("Belum ada produk terjual pada periode ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    best.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text((index + 1).toString() + ". " + item.first, modifier = Modifier.weight(1f))
                            Text(item.second.toString() + " terjual", fontWeight = FontWeight.SemiBold)
                        }
                        Box(Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
                            Box(Modifier.fillMaxWidth(item.second.toFloat() / maxQty).height(7.dp).background(Color(0xFF2878F0), RoundedCornerShape(8.dp)))
                        }
                        Text(RupiahFormatter.format(item.third), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Metode Pembayaran", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(rangeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (payments.isEmpty()) Text("Belum ada pembayaran lunas pada periode ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardDonutChart(payments.map { it.second.toFloat() }, Modifier.size(if (isTabletOrWide) 160.dp else 120.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            payments.forEachIndexed { i, p ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(9.dp).background(DashboardChartColors[i % DashboardChartColors.size], RoundedCornerShape(50)))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.first, style = MaterialTheme.typography.bodySmall)
                                        Text(p.third.toString() + " transaksi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(RupiahFormatter.format(p.second), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status Outlet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val status = listOf(
                        Triple("Produk Aktif", uiState.products.count { it.isActive }.toString(), PosModuleRoute.PRODUK),
                        Triple("Stok Menipis", uiState.lowStockProducts.count { !it.isOutOfStock }.toString(), PosModuleRoute.PRODUK),
                        Triple("Pesanan Aktif", activeCount.toString(), PosModuleRoute.PESANAN),
                        Triple("Produk Tidak Aktif", uiState.products.count { !it.isActive }.toString(), PosModuleRoute.PRODUK)
                    )
                    status.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { item ->
                                OutlinedCard(onClick = { onNavigateModule(item.third) }, modifier = Modifier.weight(1f)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(item.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(item.second, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Transaksi Terbaru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { onNavigateModule(PosModuleRoute.HISTORY) }) { Text("Lihat semua") }
                    }
                    val recent = uiState.orders.sortedByDescending { dateOf(it.createdAt)?.toInstant()?.toEpochMilli() ?: 0L }.take(5)
                    if (recent.isEmpty()) Text("Belum ada transaksi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    recent.forEach { order ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(order.orderNumber, fontWeight = FontWeight.SemiBold)
                                Text(dateOf(order.createdAt)?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM, HH:mm")) ?: order.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(RupiahFormatter.format(order.total), fontWeight = FontWeight.Bold)
                                Text(if (order.isPaid) "LUNAS" else order.status.toString(), style = MaterialTheme.typography.labelSmall, color = if (order.isPaid) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }
}

private val DashboardChartColors = listOf(Color(0xFF2878F0), Color(0xFF16A085), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF0891B2), Color(0xFF64748B))

@Composable
private fun DashboardLineChart(values: List<Float>, modifier: Modifier = Modifier) {
    val blue = Color(0xFF2878F0)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val left = 5f; val right = size.width - 5f; val top = 12f; val bottom = size.height - 12f
        repeat(4) { i -> val y = top + (bottom - top) * i / 3f; drawLine(grid, androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y), strokeWidth = 1.dp.toPx()) }
        val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val points = values.mapIndexed { i, v -> androidx.compose.ui.geometry.Offset(left + (right-left) * i / (values.size-1).coerceAtLeast(1), bottom - (v/max).coerceIn(0f,1f) * (bottom-top)) }
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x,it.y) } }
        drawPath(path, blue.copy(alpha = 0.14f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 13.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawPath(path, blue.copy(alpha = 0.24f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawPath(path, blue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        points.forEach { drawCircle(blue, 4.dp.toPx(), it); drawCircle(Color.White, 1.6.dp.toPx(), it) }
    }
}

@Composable
private fun DashboardBarChart(values: List<Float>, modifier: Modifier = Modifier) {
    val blue = Color(0xFF2878F0); val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    Canvas(modifier) {
        val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f); val step = size.width / values.size.coerceAtLeast(1)
        repeat(3) { i -> val y = size.height * (i+1) / 4f; drawLine(grid, androidx.compose.ui.geometry.Offset(0f,y), androidx.compose.ui.geometry.Offset(size.width,y), strokeWidth = 1.dp.toPx()) }
        values.forEachIndexed { i, v ->
            val h = (v/max).coerceIn(0f,1f) * (size.height - 8.dp.toPx()); val w = step * 0.58f
            drawRoundRect(if (v > 0f) blue else blue.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(i*step+(step-w)/2f,size.height-h), androidx.compose.ui.geometry.Size(w,h.coerceAtLeast(2.dp.toPx())), androidx.compose.ui.geometry.CornerRadius(w/2f,w/2f))
        }
    }
}

@Composable
private fun DashboardDonutChart(values: List<Float>, modifier: Modifier = Modifier) {
    val total = values.sum().coerceAtLeast(1f)
    Canvas(modifier) {
        val stroke = 18.dp.toPx(); var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = v / total * 360f
            drawArc(DashboardChartColors[i % DashboardChartColors.size], start, (sweep-2f).coerceAtLeast(0f), false,
                topLeft = androidx.compose.ui.geometry.Offset(stroke/2f,stroke/2f),
                size = androidx.compose.ui.geometry.Size(size.width-stroke,size.height-stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width=stroke))
            start += sweep
        }
    }
}

@Composable
private fun DashboardKpiCard(modifier: Modifier = Modifier, title: String, value: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
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
