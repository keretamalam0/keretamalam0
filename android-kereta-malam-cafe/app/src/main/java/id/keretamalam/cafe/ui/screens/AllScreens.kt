package id.keretamalam.cafe.ui.screens

import coil.compose.AsyncImage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FiberNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
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
                        OutlinedButton(onClick = { viewModel.clearBannerMessages() }) {
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

            OutlinedButton(onClick = { viewModel.setThemeMode(!uiState.darkTheme) }) {
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
    val activeMsg = uiState.errorMessage ?: uiState.successMessage
    LaunchedEffect(activeMsg) {
        if (activeMsg != null) {
            kotlinx.coroutines.delay(2600)
            viewModel.clearBannerMessages()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isTabletOrWide) 16.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

        if (activeMsg != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .widthIn(max = 360.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (uiState.errorMessage != null)
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 5.dp
            ) {
                Text(
                    text = activeMsg,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.errorMessage != null)
                        MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }

    val receiptOrder = uiState.receiptDialogOrder
    if (receiptOrder != null) {
        val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
        Dialog(
            onDismissRequest = { viewModel.openReceiptDialog(null) },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .heightIn(max = (screenHeight * 0.82f).dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Struk #${receiptOrder.orderNo}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp)
                    ) {
                        Text(
                            text = viewModel.formatOrderForThermalPreview(receiptOrder, false),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState())
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { viewModel.openReceiptDialog(null) }) { Text("Tutup") }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(onClick = {
                            viewModel.printOrderToThermal(receiptOrder, PrinterRole.KITCHEN, true)
                        }) { Text("Dapur") }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(onClick = {
                            viewModel.printOrderToThermal(receiptOrder, PrinterRole.CASHIER, true)
                        }) { Text("Cetak Struk") }
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
            val metrics = buildList {
                if (uiState.staffProfile.canAccessDashboardMetric("dashboard.total_sales")) add(Triple("Total Penjualan", RupiahFormatter.format(revenue), "Omzet pesanan lunas") to PosModuleRoute.LAPORAN)
                if (uiState.staffProfile.canAccessDashboardMetric("dashboard.paid_orders")) add(Triple("Pesanan Lunas", paid.size.toString(), "Transaksi berhasil") to PosModuleRoute.PESANAN)
                if (uiState.staffProfile.canAccessDashboardMetric("dashboard.average_transaction")) add(Triple("Rata-rata Pesanan", RupiahFormatter.format(average), "Per transaksi lunas") to PosModuleRoute.LAPORAN)
                if (uiState.staffProfile.canAccessDashboardMetric("dashboard.peak_hour")) add(Triple("Jam Tersibuk", peak?.let { "%02d.00–%02d.00".format(it, (it + 1) % 24) } ?: "—", if (peak == null) "Belum ada pesanan" else hours[peak].toInt().toString() + " pesanan") to PosModuleRoute.LAPORAN)
            }
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
        if (uiState.staffProfile.canAccessDashboardMetric("dashboard.trend_chart")) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tren Omzet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("7 hari terakhir", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DashboardLineChart(trend, Modifier.fillMaxWidth().height(160.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall) } }
                }
            }
        }
        if (uiState.staffProfile.canAccessDashboardMetric("dashboard.hourly_activity")) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aktivitas per Jam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    DashboardBarChart(hours, Modifier.fillMaxWidth().height(130.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("00", "04", "08", "12", "16", "20", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall) } }
                }
            }
        }
        if (uiState.staffProfile.canAccessDashboardMetric("dashboard.best_sellers")) item {
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
                        OutlinedButton(onClick = { onNavigateModule(PosModuleRoute.HISTORY) }) { Text("Lihat semua") }
                    }
                    val recent = uiState.orders.sortedByDescending { dateOf(it.createdAt)?.toInstant()?.toEpochMilli() ?: 0L }.take(5)
                    if (recent.isEmpty()) Text("Belum ada transaksi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    recent.forEach { order ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Pesanan #${order.orderNo}", fontWeight = FontWeight.SemiBold)
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
        // Clean curve without permanent point markers.
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

private fun kpiIconFor(title: String): Pair<ImageVector, Color> = when {
    title.contains("Penjualan", ignoreCase = true) -> Icons.Outlined.AccountBalanceWallet to Color(0xFF6489B8)
    title.contains("Lunas", ignoreCase = true) -> Icons.Outlined.CheckCircle to Color(0xFF659B83)
    title.contains("Rata-rata", ignoreCase = true) -> Icons.Outlined.ReceiptLong to Color(0xFF8B80B8)
    title.contains("Jam", ignoreCase = true) -> Icons.Outlined.Schedule to Color(0xFFC09A63)
    title.contains("Pesanan", ignoreCase = true) -> Icons.Outlined.ReceiptLong to Color(0xFF6489B8)
    title.contains("Stok", ignoreCase = true) || title.contains("Persediaan", ignoreCase = true) -> Icons.Outlined.Inventory2 to Color(0xFF659B83)
    title.contains("Meja", ignoreCase = true) -> Icons.Outlined.TableRestaurant to Color(0xFF8B80B8)
    title.contains("AI", ignoreCase = true) -> Icons.Outlined.AutoAwesome to Color(0xFF8B80B8)
    else -> Icons.Outlined.TrendingUp to Color(0xFF6489B8)
}

@Composable
private fun DashboardKpiCard(modifier: Modifier = Modifier, title: String, value: String, subtitle: String, onClick: () -> Unit) {
    val (icon, tint) = kpiIconFor(title)
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = tint.copy(alpha = 0.11f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    }
                }
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
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
    var customAddonIds by remember { mutableStateOf(setOf<String>()) }
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

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (isTabletOrWide) 164.dp else 148.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 4.dp, bottom = 8.dp)
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().height(158.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(if (isTabletOrWide) 58.dp else 52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(7.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Outlined.Inventory2,
                                            contentDescription = "Foto belum tersedia",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                if (!product.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = product.imageUrl,
                                        contentDescription = "Foto ${product.name}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = RupiahFormatter.format(product.price),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = when {
                                        product.isOutOfStock -> "HABIS"
                                        product.isLowStock -> "Stok tipis: ${product.stock}"
                                        else -> "Stok: ${product.stock}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (product.isOutOfStock || product.isLowStock)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            customizingProduct = product
                                            customSugar = "Normal Sugar"
                                            customIce = "Normal Ice"
                                            customCup = "Regular"
                                            customExtraShot = false
                                            customOatMilk = false
                                            customAddonIds = emptySet()
                                            customNote = ""
                                        },
                                        enabled = !product.isOutOfStock,
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) { Text("Opsi", style = MaterialTheme.typography.labelSmall) }
                                    Button(
                                        onClick = { viewModel.addToCart(product) },
                                        enabled = !product.isOutOfStock,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) { Text("+") }
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
                            OutlinedButton(onClick = { viewModel.clearCart() }) {
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
                        (uiState.paymentMethods.filter { it.isActive }.map { it.code to it.name }.ifEmpty { listOf("cash" to "Tunai", "qris" to "QRIS", "transfer" to "Transfer", "ewallet" to "E-Wallet") }).forEach { (code, label) ->
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
                            OutlinedButton(onClick = { mobileTab = 1 }) {
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

    // Dialog kustomisasi ringkas dan bisa digulir saat layar landscape.
    val targetCustom = customizingProduct
    if (targetCustom != null) {
        val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
        Dialog(
            onDismissRequest = { customizingProduct = null },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .heightIn(max = (screenHeight * 0.84f).dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "Kustomisasi · ${targetCustom.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val productOptions = uiState.customizationOptions.filter { it.productId == targetCustom.id && it.isActive }
                    val sugarOptions = productOptions.filter { it.groupKey == "sugar" }
                    val iceOptions = productOptions.filter { it.groupKey == "ice" }
                    val sizeOptions = productOptions.filter { it.groupKey == "size" }
                    val productToppings = uiState.productAddons.filter { it.productId == targetCustom.id && it.isActive }

                    Text("Gula", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        (sugarOptions.ifEmpty { emptyList() }).forEach { option ->
                            FilterChip(selected = customSugar == option.name, onClick = { customSugar = option.name }, label = { Text(option.name + if (option.priceDelta > 0) " +${RupiahFormatter.format(option.priceDelta)}" else "", style = MaterialTheme.typography.labelSmall) })
                        }
                        if (sugarOptions.isEmpty()) listOf("Normal Sugar", "Less Sugar", "No Sugar").forEach { option ->
                            FilterChip(selected = customSugar == option, onClick = { customSugar = option }, label = { Text(option, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Text("Es / suhu", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        iceOptions.forEach { option ->
                            FilterChip(selected = customIce == option.name, onClick = { customIce = option.name }, label = { Text(option.name + if (option.priceDelta > 0) " +${RupiahFormatter.format(option.priceDelta)}" else "", style = MaterialTheme.typography.labelSmall) })
                        }
                        if (iceOptions.isEmpty()) listOf("Normal Ice", "Less Ice", "Hot / Warm").forEach { option ->
                            FilterChip(selected = customIce == option, onClick = { customIce = option }, label = { Text(option, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Text("Ukuran cup", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        sizeOptions.forEach { option ->
                            FilterChip(selected = customCup == option.name, onClick = { customCup = option.name }, label = { Text(option.name + if (option.priceDelta > 0) " +${RupiahFormatter.format(option.priceDelta)}" else "") })
                        }
                        if (sizeOptions.isEmpty()) {
                            FilterChip(selected = customCup == "Regular", onClick = { customCup = "Regular" }, label = { Text("Regular") })
                            FilterChip(selected = customCup == "Large", onClick = { customCup = "Large" }, label = { Text("Large +Rp5rb") })
                        }
                    }
                    Text("Topping tambahan", style = MaterialTheme.typography.labelMedium)
                    if (productToppings.isEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FilterChip(selected = customExtraShot, onClick = { customExtraShot = !customExtraShot }, label = { Text("Extra Shot +Rp6rb", style = MaterialTheme.typography.labelSmall) })
                            FilterChip(selected = customOatMilk, onClick = { customOatMilk = !customOatMilk }, label = { Text("Oat Milk +Rp8rb", style = MaterialTheme.typography.labelSmall) })
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            productToppings.forEach { topping ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = topping.id in customAddonIds, onCheckedChange = { checked ->
                                        customAddonIds = if (checked) customAddonIds + topping.id else customAddonIds - topping.id
                                    })
                                    Text(topping.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text("+${RupiahFormatter.format(topping.price)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customNote,
                        onValueChange = { customNote = it },
                        label = { Text("Catatan item") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { customizingProduct = null }) { Text("Batal") }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(onClick = {
                            val activeOptions = uiState.customizationOptions.filter { it.productId == targetCustom.id && it.isActive }
                            val addons = buildList {
                                val selectedSize = activeOptions.firstOrNull { it.groupKey == "size" && it.name == customCup }
                                val selectedSugar = activeOptions.firstOrNull { it.groupKey == "sugar" && it.name == customSugar }
                                val selectedIce = activeOptions.firstOrNull { it.groupKey == "ice" && it.name == customIce }
                                listOfNotNull(selectedSize, selectedSugar, selectedIce).filter { it.priceDelta > 0 }.forEach {
                                    add(ProductAddon(id = it.id, productId = targetCustom.id, name = it.name, price = it.priceDelta))
                                }
                                uiState.productAddons.filter { it.productId == targetCustom.id && it.isActive && it.id in customAddonIds }.forEach { add(it) }
                                if (activeOptions.none { it.groupKey == "size" } && customCup == "Large") add(ProductAddon(id = "ad-lg", productId = targetCustom.id, name = "Large Cup", price = 5000.0))
                                if (uiState.productAddons.none { it.productId == targetCustom.id && it.isActive } && customExtraShot) add(ProductAddon(id = "ad-es", productId = targetCustom.id, name = "Extra Espresso Shot", price = 6000.0))
                                if (uiState.productAddons.none { it.productId == targetCustom.id && it.isActive } && customOatMilk) add(ProductAddon(id = "ad-om", productId = targetCustom.id, name = "Oat Milk", price = 8000.0))
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
                            Text("Tambah", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    // Dialog checkout ringkas, tidak memenuhi layar dan aman di landscape.
    if (showConfirmCheckoutDialog) {
        val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
        Dialog(
            onDismissRequest = { if (!uiState.isCheckoutProcessing) showConfirmCheckoutDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .heightIn(max = (screenHeight * 0.82f).dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Konfirmasi pembayaran", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${uiState.tables.find { it.id == uiState.selectedTableId }?.name ?: "Takeaway"} · ${uiState.paymentMethod.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = RupiahFormatter.format(uiState.grandTotal),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showConfirmCheckoutDialog = false },
                            enabled = !uiState.isCheckoutProcessing
                        ) { Text("Batal") }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            enabled = !uiState.isCheckoutProcessing,
                            onClick = {
                                showConfirmCheckoutDialog = false
                                viewModel.submitPosCheckout()
                            }
                        ) {
                            Text(if (uiState.isCheckoutProcessing) "Memproses…" else "Bayar")
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
    var lane by remember { mutableStateOf("baru") }
    var sourceFilter by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    val lanes = listOf("baru" to "Baru", "diproses" to "Diproses", "siap" to "Siap", "riwayat" to "Riwayat")
    fun inLane(o: OrderWithItems, key: String): Boolean = when (key) {
        "baru" -> o.status == OrderStatus.NEW
        "diproses" -> o.status == OrderStatus.PREPARING
        "siap" -> o.status == OrderStatus.READY
        else -> o.status == OrderStatus.COMPLETED || o.status == OrderStatus.CANCELLED
    }
    val filtered = uiState.orders.filter { o ->
        inLane(o, lane) && (sourceFilter == "ALL" || o.source.equals(sourceFilter, true)) &&
            (search.isBlank() || o.orderNo.toString().contains(search) || (o.customerName ?: "").contains(search, true))
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lanes.forEach { (key, label) ->
                val count = uiState.orders.count { inLane(it, key) }
                val tint = when (key) {
                    "baru" -> Color(0xFF6F91C5)
                    "diproses" -> Color(0xFFC29A5D)
                    "siap" -> Color(0xFF5F9E91)
                    else -> Color(0xFF8B91A7)
                }
                val laneIcon = when (key) {
                    "baru" -> Icons.Outlined.FiberNew
                    "diproses" -> Icons.Outlined.PlayArrow
                    "siap" -> Icons.Outlined.DoneAll
                    else -> Icons.Outlined.History
                }
                FilterChip(selected = lane == key, onClick = { lane = key }, label = { Text("$label ($count)") },
                    leadingIcon = { Icon(laneIcon, contentDescription = null, tint = tint) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Cari nomor order / pelanggan") }, modifier = Modifier.weight(1f), singleLine = true)
            var sourceOpen by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { sourceOpen = true }) { Text(if (sourceFilter == "ALL") "Semua kanal ▾" else "$sourceFilter ▾") }
                DropdownMenu(expanded = sourceOpen, onDismissRequest = { sourceOpen = false }) {
                    listOf("ALL" to "Semua kanal", "pos" to "POS Kasir", "qr" to "QR Meja").forEach { (code, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { sourceFilter = code; sourceOpen = false })
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(when (lane) { "baru" -> "Belum ada pesanan baru"; "diproses" -> "Belum ada pesanan diproses"; "siap" -> "Belum ada pesanan siap"; else -> "Riwayat pesanan masih kosong" })
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { order ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("Order #${order.orderNo}", fontWeight = FontWeight.Bold)
                                    Text("${order.source.uppercase()} · ${order.restaurantTable?.name ?: "Takeaway"} · ${order.customerName ?: "Walk-in"}", style = MaterialTheme.typography.bodyMedium)
                                    Text(order.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(order.status.labelId, fontWeight = FontWeight.Bold)
                                    Text(if (order.isPaid) "LUNAS" else "BELUM LUNAS", color = if (order.isPaid) Color(0xFF15803D) else MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                            order.orderItems.forEach { item ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${item.quantity}× ${item.productName}", modifier = Modifier.weight(1f))
                                    Text(RupiahFormatter.format(item.lineTotal))
                                }
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Total: ${RupiahFormatter.format(order.total)}", fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { viewModel.printOrderToThermal(order, PrinterRole.KITCHEN) }) { Text("Cetak") }
                                    OutlinedButton(onClick = { viewModel.openReceiptDialog(order) }) { Text("Struk") }
                                }
                            }
                            if (lane != "riwayat") {
                                if (order.source == "qr" && !order.isPaid && order.status != OrderStatus.CANCELLED) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(onClick = { viewModel.approveQrOrder(order, "qris") { lane = if (order.status == OrderStatus.READY) "siap" else "diproses" } }) { Text("Konfirmasi QRIS") }
                                        OutlinedButton(onClick = { viewModel.approveQrOrder(order, "cash") { lane = if (order.status == OrderStatus.READY) "siap" else "diproses" } }) { Text("Terima Tunai") }
                                        OutlinedButton(onClick = { viewModel.rejectQrOrder(order) }) { Text("Tolak") }
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    when (order.status) {
                                        OrderStatus.NEW -> Button(onClick = { viewModel.updateOrderStatus(order, OrderStatus.PREPARING) { lane = "diproses" } }, modifier = Modifier.weight(1f)) { Text("Mulai Proses") }
                                        OrderStatus.PREPARING -> Button(onClick = { viewModel.updateOrderStatus(order, OrderStatus.READY) { lane = "siap" } }, modifier = Modifier.weight(1f)) { Text("Tandai Siap") }
                                        OrderStatus.READY -> if (order.isPaid) Button(onClick = { viewModel.updateOrderStatus(order, OrderStatus.COMPLETED) { lane = "riwayat" } }, modifier = Modifier.weight(1f)) { Text("Selesaikan & ke Riwayat") } else Text("Konfirmasi pembayaran sebelum masuk riwayat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        else -> {}
                                    }
                                    if (order.status != OrderStatus.COMPLETED && order.status != OrderStatus.CANCELLED) {
                                        OutlinedButton(onClick = { viewModel.updateOrderStatus(order, OrderStatus.CANCELLED) { lane = "riwayat" } }) { Text("Batalkan") }
                                    }
                                }
                            } else {
                                Text(if (order.status == OrderStatus.CANCELLED) "Pesanan dibatalkan" else if (order.isPaid) "Pembayaran selesai" else "Belum lunas", style = MaterialTheme.typography.bodySmall)
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
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

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
    val selectedVisibleIds = filteredOrders.map { it.id }.filter { it in selectedIds }.toSet()

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            CsvExporter.writeSalesReportToUri(context, uri, "${fromDate.ifBlank { "Semua tanggal" }} s/d ${toDate.ifBlank { "sekarang" }}", filteredOrders)
                .onSuccess { viewModel.showNotice("CSV Riwayat Transaksi berhasil disimpan.") }
                .onFailure { viewModel.showNotice(it.message ?: "Gagal menyimpan CSV.", isError = true) }
        }
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("Hapus transaksi terpilih?") },
            text = { Text("Yakin hapus ${selectedIds.size} transaksi yang dipilih? Data yang dihapus tidak bisa dikembalikan.") },
            confirmButton = {
                OutlinedButton(onClick = {
                    confirmDeleteSelected = false
                    val ids = selectedIds.toList()
                    viewModel.deleteOrders(ids)
                    selectedIds = emptySet()
                }) { Text("Ya, Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDeleteSelected = false }) { Text("Batal") } }
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Hapus SEMUA transaksi?") },
            text = { Text("Ini akan menghapus seluruh data transaksi yang tersimpan, bukan hanya hasil pencarian atau tanggal yang sedang tampil. Tindakan ini permanen dan tidak bisa dibatalkan.") },
            confirmButton = {
                OutlinedButton(onClick = {
                    confirmDeleteAll = false
                    viewModel.deleteAllOrders()
                    selectedIds = emptySet()
                }) { Text("Hapus Semua", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDeleteAll = false }) { Text("Jangan Hapus") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Riwayat Transaksi (${filteredOrders.size} Order)", style = MaterialTheme.typography.titleMedium)
                Text("Total Nilai Transaksi Lunas: ${RupiahFormatter.format(filteredOrders.filter { it.isPaid }.sumOf { it.total })}", style = MaterialTheme.typography.bodyMedium)
                Text("Kosongkan tanggal untuk melihat semua transaksi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { csvLauncher.launch("history-transaksi-kereta-malam.csv") }) { Text("Ekspor CSV") }
                OutlinedButton(onClick = { confirmDeleteAll = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Hapus Semua Data") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = fromDate, onValueChange = { fromDate = it }, label = { Text("Dari (YYYY-MM-DD)") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = toDate, onValueChange = { toDate = it }, label = { Text("Sampai (YYYY-MM-DD)") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Cari #Order / Nama") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedButton(onClick = {
                selectedIds = if (filteredOrders.isNotEmpty() && filteredOrders.all { it.id in selectedIds }) {
                    selectedIds - filteredOrders.map { it.id }.toSet()
                } else selectedIds + filteredOrders.map { it.id }
            }) { Text(if (filteredOrders.isNotEmpty() && filteredOrders.all { it.id in selectedIds }) "Batal Pilih" else "Pilih Semua") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${selectedIds.size} transaksi dipilih", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { confirmDeleteSelected = true },
                enabled = selectedIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Hapus Terpilih") }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filteredOrders, key = { it.id }) { order ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = order.id in selectedIds, onCheckedChange = { checked ->
                            selectedIds = if (checked) selectedIds + order.id else selectedIds - order.id
                        })
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("#${order.orderNo} · ${order.customerName ?: "Walk-in"}", fontWeight = FontWeight.Bold)
                            Text("Waktu: ${order.createdAt}", style = MaterialTheme.typography.bodySmall)
                            Text("Sumber: ${if (order.source.equals("qr", true)) "QR Meja" else "POS Kasir"} · Meja: ${order.restaurantTable?.name ?: "Takeaway"}", style = MaterialTheme.typography.bodyMedium)
                            Text("Metode: ${order.paymentMethod?.uppercase() ?: "Belum ditentukan"} · Status: ${order.status.labelId}", style = MaterialTheme.typography.bodyMedium)
                            Text("Dibayar: ${if (order.isPaid) "LUNAS" else "BELUM LUNAS"}", color = if (order.isPaid) Color(0xFF15803D) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(RupiahFormatter.format(order.total), fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { viewModel.openReceiptDialog(order) }) { Text("Detail") }
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
    var filter by remember { mutableStateOf("SEMUA") }
    var search by remember { mutableStateOf("") }
    var selectedShift by remember { mutableStateOf<StaffShift?>(null) }
    val activeCount = uiState.shifts.count { it.status.equals("open", true) }
    val needsReview = uiState.shifts.count { !it.auditVerified && it.status.equals("closed", true) }
    val filtered = uiState.shifts.filter { shift ->
        val queryMatch = search.isBlank() || shift.staffName.contains(search, true) || shift.id.contains(search, true) || shift.notes.orEmpty().contains(search, true)
        val filterMatch = when (filter) {
            "PERLU" -> !shift.auditVerified && shift.status.equals("closed", true)
            "AKTIF" -> shift.status.equals("open", true)
            else -> true
        }
        queryMatch && filterMatch
    }
    selectedShift?.let { shift ->
        val variance = (shift.closingCash ?: 0.0) - (shift.expectedCash ?: 0.0)
        val suspicious = shift.status.equals("closed", true) && kotlin.math.abs(variance) >= 1.0
        AlertDialog(
            onDismissRequest = { selectedShift = null },
            title = { Text("Detail Audit Shift") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kasir: ${shift.staffName}", fontWeight = FontWeight.Bold)
                    Text("ID Shift: ${shift.id}", style = MaterialTheme.typography.bodySmall)
                    Text("Status: ${shift.status.uppercase()}")
                    Text("Opening: ${shift.openedAt}")
                    Text("Modal awal: ${RupiahFormatter.format(shift.openingCash)}")
                    Text("Closing: ${shift.closedAt ?: "Belum ditutup"}")
                    Text("Ekspektasi kas: ${RupiahFormatter.format(shift.expectedCash ?: 0.0)}")
                    Text("Kas fisik akhir: ${shift.closingCash?.let { RupiahFormatter.format(it) } ?: "Belum ada"}")
                    Text("Selisih kas: ${RupiahFormatter.format(variance)}")
                    if (!shift.notes.isNullOrBlank()) Text("Catatan: ${shift.notes}")
                    if (shift.status.equals("open", true)) {
                        Text("Shift masih aktif; audit final dilakukan setelah closing.", color = Color(0xFFC29A5D))
                    } else if (suspicious) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text("Perlu pemeriksaan: ditemukan selisih kas sebesar ${RupiahFormatter.format(variance)}. Cocokkan transaksi dan uang fisik sebelum verifikasi.", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    } else {
                        Text("Tidak ditemukan selisih kas berdasarkan angka yang tersedia. Cocokkan bukti transaksi sebelum verifikasi.", color = Color(0xFF5F9E91))
                    }
                    Text(if (shift.auditVerified) "Status audit: TERVERIFIKASI" else "Status audit: MENUNGGU VERIFIKASI", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                if (!shift.auditVerified && shift.status.equals("closed", true)) {
                    OutlinedButton(onClick = { viewModel.verifyShiftAudit(shift.id); selectedShift = null }) { Text("Verifikasi Audit") }
                } else OutlinedButton(onClick = { selectedShift = null }) { Text("Tutup") }
            },
            dismissButton = {
                if (!shift.auditVerified && shift.status.equals("closed", true)) OutlinedButton(onClick = { selectedShift = null }) { Text("Kembali") }
            }
        )
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardKpiCard(Modifier.widthIn(min = 140.dp).weight(1f), "Semua Shift", "${uiState.shifts.size}", "Tercatat", onClick = { filter = "SEMUA" })
            DashboardKpiCard(Modifier.widthIn(min = 140.dp).weight(1f), "Perlu Diperiksa", "$needsReview", "Belum diverifikasi", onClick = { filter = "PERLU" })
            DashboardKpiCard(Modifier.widthIn(min = 140.dp).weight(1f), "Shift Aktif", "$activeCount", "Sedang berjalan", onClick = { filter = "AKTIF" })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Cari nama kasir / ID / catatan") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedButton(onClick = { viewModel.syncAllDataFromBackend() }, enabled = !uiState.isLoading) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (uiState.isLoading) "Memuat" else "Refresh")
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SEMUA" to "Semua Shift", "PERLU" to "Perlu Diperiksa", "AKTIF" to "Shift Aktif").forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
            }
        }
        Text("Riwayat Opening & Closing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Tidak ada shift yang cocok dengan filter.") }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { shift ->
                    val variance = (shift.closingCash ?: 0.0) - (shift.expectedCash ?: 0.0)
                    val suspicious = shift.status.equals("closed", true) && kotlin.math.abs(variance) >= 1.0
                    OutlinedCard(onClick = { selectedShift = shift }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(shift.staffName, fontWeight = FontWeight.Bold)
                                    Text("${shift.status.uppercase()} · Opening ${shift.openedAt}", style = MaterialTheme.typography.bodySmall)
                                    Text("Closing: ${shift.closedAt ?: "Belum ditutup"}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (shift.auditVerified) Text("Terverifikasi", color = Color(0xFF5F9E91), fontWeight = FontWeight.Bold)
                                else if (shift.status.equals("closed", true)) Text("Perlu diperiksa", color = if (suspicious) MaterialTheme.colorScheme.error else Color(0xFFC29A5D), fontWeight = FontWeight.Bold)
                                else Text("Aktif", color = Color(0xFF6F91C5), fontWeight = FontWeight.Bold)
                            }
                            Text("Modal awal: ${RupiahFormatter.format(shift.openingCash)} · Kas akhir: ${shift.closingCash?.let { RupiahFormatter.format(it) } ?: "-"}")
                            if (shift.status.equals("closed", true)) {
                                Text("Ekspektasi: ${RupiahFormatter.format(shift.expectedCash ?: 0.0)} · Selisih: ${RupiahFormatter.format(variance)}")
                                if (suspicious) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text("Ada selisih kas — periksa sebelum verifikasi", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (!shift.notes.isNullOrBlank()) Text("Catatan: ${shift.notes}", style = MaterialTheme.typography.bodySmall)
                            Text("Ketuk untuk detail audit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
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
