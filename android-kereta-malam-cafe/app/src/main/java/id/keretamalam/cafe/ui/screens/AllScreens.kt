package id.keretamalam.cafe.ui.screens

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import coil.compose.AsyncImage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
    uiState: PosUiState,
    isLandscapePhone: Boolean = false,
    selectedReportPage: String = "Ringkasan Laporan",
    onSelectedReportPageChange: (String) -> Unit = {}
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
                PosModuleRoute.KASIR -> KasirComposeScreen(viewModel, uiState, isTabletOrWide, isLandscapePhone)
                PosModuleRoute.PESANAN -> PesananComposeScreen(viewModel, uiState, isTabletOrWide, isLandscapePhone)
                PosModuleRoute.HISTORY -> HistoryComposeScreen(viewModel, uiState)
                PosModuleRoute.SHIFT_KASIR -> ShiftKasirComposeScreen(viewModel, uiState)
                PosModuleRoute.AUDIT_SHIFT -> AuditShiftComposeScreen(viewModel, uiState)
                PosModuleRoute.ACTIVITY_LOG -> ActivityLogComposeScreen(uiState)
                PosModuleRoute.PRODUK -> ProdukStokComposeScreen(viewModel, uiState)
                PosModuleRoute.MEJA -> MejaQrComposeScreen(viewModel, uiState, isTabletOrWide)
                PosModuleRoute.INVENTORY -> InventoryComposeScreen(viewModel, uiState)
                PosModuleRoute.PROMOSI -> PromosiComposeScreen(viewModel, uiState)
                PosModuleRoute.KEUANGAN -> KeuanganComposeScreen(viewModel, uiState)
                PosModuleRoute.LAPORAN -> LaporanPenjualanComposeScreen(viewModel, uiState, selectedReportPage, onSelectedReportPageChange)
                PosModuleRoute.PEGAWAI -> PegawaiComposeScreen(viewModel, uiState)
                PosModuleRoute.PENGATURAN -> PengaturanOutletComposeScreen(viewModel, uiState)
                PosModuleRoute.PRINTER -> PrinterComposeScreen(viewModel, uiState, isTabletOrWide)
                PosModuleRoute.MANAGEMENT_OWNER -> ManajemenOwnerComposeScreen(viewModel, uiState)
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
                                val statusIcon = when (item.first) {
                                    "Produk Aktif" -> Icons.Outlined.CheckCircle
                                    "Stok Menipis" -> Icons.Outlined.WarningAmber
                                    "Pesanan Aktif" -> Icons.Outlined.ReceiptLong
                                    else -> Icons.Outlined.VisibilityOff
                                }
                                val statusColor = when (item.first) {
                                    "Produk Aktif" -> Color(0xFF16A34A)
                                    "Stok Menipis" -> Color(0xFFF59E0B)
                                    "Pesanan Aktif" -> Color(0xFF2878F0)
                                    else -> Color(0xFFEF4444)
                                }
                                OutlinedCard(onClick = { onNavigateModule(item.third) }, modifier = Modifier.weight(1f)) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = RoundedCornerShape(10.dp), color = statusColor.copy(alpha = 0.13f), modifier = Modifier.size(34.dp)) {
                                            Box(contentAlignment = Alignment.Center) { Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp)) }
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(item.first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(item.second, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = statusColor)
                                        }
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

private val DashboardChartColors = listOf(Color(0xFF16A34A), Color(0xFF2878F0), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFF0D9488), Color(0xFFEF4444), Color(0xFFDB2777))

@Composable
private fun DashboardLineChart(values: List<Float>, modifier: Modifier = Modifier) {
    var selected by remember(values) { mutableIntStateOf(-1) }
    val blue = Color(0xFF2878F0)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(values) { detectTapGestures { p -> selected = ((p.x / size.width.coerceAtLeast(1).toFloat()) * values.size).toInt().coerceIn(0, (values.size-1).coerceAtLeast(0)) } }
            .pointerInput(values) { detectHorizontalDragGestures { change, _ -> change.consume(); selected = ((change.position.x / size.width.coerceAtLeast(1).toFloat()) * values.size).toInt().coerceIn(0, (values.size-1).coerceAtLeast(0)) } }) {
            if (values.isEmpty()) return@Canvas
            val left=7f; val right=size.width-7f; val top=16f; val bottom=size.height-12f
            repeat(4) { i -> val y=top+(bottom-top)*i/3f; drawLine(grid, androidx.compose.ui.geometry.Offset(left,y), androidx.compose.ui.geometry.Offset(right,y), strokeWidth=1.dp.toPx()) }
            val max=(values.maxOrNull() ?: 0f).coerceAtLeast(1f)
            val points=values.mapIndexed { i,v -> androidx.compose.ui.geometry.Offset(left+(right-left)*i/(values.size-1).coerceAtLeast(1), bottom-(v/max).coerceIn(0f,1f)*(bottom-top)) }
            val path=androidx.compose.ui.graphics.Path().apply {
                moveTo(points.first().x,points.first().y)
                for(i in 1 until points.size) { val a=points[i-1]; val b=points[i]; val mid=(a.x+b.x)/2f; cubicTo(mid,a.y,mid,b.y,b.x,b.y) }
            }
            drawPath(path,blue.copy(alpha=.09f),style=androidx.compose.ui.graphics.drawscope.Stroke(width=16.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
            drawPath(path,blue.copy(alpha=.18f),style=androidx.compose.ui.graphics.drawscope.Stroke(width=8.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
            drawPath(path,blue,style=androidx.compose.ui.graphics.drawscope.Stroke(width=3.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
            if(selected in points.indices) { val p=points[selected]; drawLine(blue.copy(alpha=.38f),androidx.compose.ui.geometry.Offset(p.x,top),androidx.compose.ui.geometry.Offset(p.x,bottom),strokeWidth=1.5.dp.toPx()); drawCircle(Color.White,6.dp.toPx(),p); drawCircle(blue,4.dp.toPx(),p) }
        }
        if(selected in values.indices) Surface(Modifier.align(Alignment.TopEnd).padding(5.dp),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(8.dp),shadowElevation=4.dp) {
            Text("Hari ${selected+1}: ${RupiahFormatter.format(values[selected].toDouble())}",Modifier.padding(horizontal=8.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall,color=blue)
        }
    }
}

@Composable
private fun DashboardBarChart(values: List<Float>, modifier: Modifier = Modifier) {
    var selected by remember(values) { mutableIntStateOf(-1) }
    val blue=Color(0xFF2878F0); val grid=MaterialTheme.colorScheme.outline.copy(alpha=.18f)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(values) { detectTapGestures { p -> selected=((p.x/size.width.coerceAtLeast(1).toFloat())*values.size).toInt().coerceIn(0,(values.size-1).coerceAtLeast(0)) } }
            .pointerInput(values) { detectHorizontalDragGestures { change, _ -> change.consume(); selected=((change.position.x/size.width.coerceAtLeast(1).toFloat())*values.size).toInt().coerceIn(0,(values.size-1).coerceAtLeast(0)) } }) {
            if(values.isEmpty()) return@Canvas
            val max=(values.maxOrNull() ?: 0f).coerceAtLeast(1f); val step=size.width/values.size.coerceAtLeast(1)
            repeat(3) { i -> val y=size.height*(i+1)/4f; drawLine(grid,androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(size.width,y),strokeWidth=1.dp.toPx()) }
            values.forEachIndexed { i,v ->
                val h=(v/max).coerceIn(0f,1f)*(size.height-8.dp.toPx()); val w=step*.58f; val x=i*step+(step-w)/2f
                drawRoundRect(if(selected==i) Color(0xFF1558C0) else if(v>0f) blue else blue.copy(alpha=.12f),androidx.compose.ui.geometry.Offset(x,size.height-h),androidx.compose.ui.geometry.Size(w,h.coerceAtLeast(2.dp.toPx())),androidx.compose.ui.geometry.CornerRadius(w/2f,w/2f))
            }
        }
        if(selected in values.indices) Surface(Modifier.align(Alignment.TopEnd).padding(4.dp),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(8.dp),shadowElevation=4.dp) {
            Text("${selected.toString().padStart(2,'0')}:00 · ${values[selected].toInt()} order",Modifier.padding(horizontal=8.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall,color=blue)
        }
    }
}

@Composable
private fun DashboardDonutChart(values: List<Float>, modifier: Modifier = Modifier) {
    var selected by remember(values) { mutableIntStateOf(-1) }
    val total=values.sum().coerceAtLeast(1f)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().pointerInput(values) { detectTapGestures { selected=((it.x/size.width.coerceAtLeast(1).toFloat())*values.size).toInt().coerceIn(0,(values.size-1).coerceAtLeast(0)) } }.pointerInput(values) {
            detectHorizontalDragGestures { change, _ -> change.consume(); selected=((change.position.x/size.width.coerceAtLeast(1).toFloat())*values.size).toInt().coerceIn(0,(values.size-1).coerceAtLeast(0)) }
        }) {
            val stroke=18.dp.toPx(); var startAngle=-90f
            values.forEachIndexed { i,v -> val sweep=v/total*360f; drawArc(DashboardChartColors[i%DashboardChartColors.size].copy(alpha=if(selected==-1||selected==i) 1f else .42f),startAngle,(sweep-2f).coerceAtLeast(0f),false,topLeft=androidx.compose.ui.geometry.Offset(stroke/2f,stroke/2f),size=androidx.compose.ui.geometry.Size(size.width-stroke,size.height-stroke),style=androidx.compose.ui.graphics.drawscope.Stroke(width=if(selected==i) 23.dp.toPx() else stroke)); startAngle+=sweep }
        }
        if(selected in values.indices) Surface(Modifier.align(Alignment.TopCenter).padding(4.dp),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(8.dp),shadowElevation=4.dp) { Text("${selected+1}: ${RupiahFormatter.format(values[selected].toDouble())}",Modifier.padding(horizontal=8.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall) }
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
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val cardShape = RoundedCornerShape(14.dp)
    Card(
        onClick = onClick,
        modifier = modifier.border(1.dp, tint.copy(alpha = if (dark) 0.30f else 0.20f), cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = tint.copy(alpha = if (dark) 0.15f else 0.075f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 5.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = tint.copy(alpha = if (dark) 0.25f else 0.14f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                    }
                }
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

// ============================================================================
// 2. AI ASSISTANT SCREEN
// ============================================================================
private data class AiChatHistoryEntry(
    val title: String,
    val messages: List<Pair<String, Boolean>>
)

private const val AI_WELCOME_MESSAGE =
    "Halo, saya AI Assistant dari Alvin Studio. Saya bertugas membantu operasional Kereta Malam Cafe langkah demi langkah—mulai dari kasir, pesanan, produk & stok, QR Ordering, laporan, hingga printer dan pengaturan."

private fun loadAiChatHistory(context: android.content.Context): List<AiChatHistoryEntry> = runCatching {
    val raw = context.getSharedPreferences("kereta_malam_ai_chat", android.content.Context.MODE_PRIVATE)
        .getString("history", "[]") ?: "[]"
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val messagesArray = item.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until messagesArray.length()).mapNotNull { i ->
            val message = messagesArray.optJSONObject(i) ?: return@mapNotNull null
            message.optString("text", "").takeIf { it.isNotBlank() }?.let {
                it to message.optBoolean("fromUser", false)
            }
        }
        if (messages.isEmpty()) null else AiChatHistoryEntry(
            item.optString("title", "Percakapan sebelumnya"), messages
        )
    }
}.getOrDefault(emptyList())

private fun saveAiChatHistory(context: android.content.Context, entries: List<AiChatHistoryEntry>) {
    val array = JSONArray()
    entries.forEach { entry ->
        val item = JSONObject().put("title", entry.title)
        val messages = JSONArray()
        entry.messages.forEach { (text, fromUser) ->
            messages.put(JSONObject().put("text", text).put("fromUser", fromUser))
        }
        item.put("messages", messages)
        array.put(item)
    }
    context.getSharedPreferences("kereta_malam_ai_chat", android.content.Context.MODE_PRIVATE)
        .edit().putString("history", array.toString()).apply()
}

@Composable
private fun AiAssistantComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    val context = LocalContext.current
    var promptInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
    }
    val prefs = remember(context) {
        context.getSharedPreferences("kereta_malam_ai_chat", android.content.Context.MODE_PRIVATE)
    }
    val chatMessages = remember {
        val restored = runCatching {
            val raw = prefs.getString("current", null) ?: return@runCatching null
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                item.optString("text", "").takeIf { it.isNotBlank() }?.let {
                    it to item.optBoolean("fromUser", false)
                }
            }
        }.getOrNull()
        mutableStateListOf<Pair<String, Boolean>>().apply {
            addAll(restored.orEmpty())
            if (isEmpty()) add(AI_WELCOME_MESSAGE to false)
        }
    }
    val historyEntries = remember { mutableStateListOf<AiChatHistoryEntry>().apply {
        addAll(loadAiChatHistory(context))
    } }
    val chatScrollState = rememberScrollState()
    var lastRenderedReply by remember { mutableStateOf(uiState.aiReplyText) }
    var lastRenderedError by remember { mutableStateOf(uiState.errorMessage) }

    LaunchedEffect(chatMessages.toList()) {
        val array = JSONArray()
        chatMessages.forEach { (text, fromUser) ->
            array.put(JSONObject().put("text", text).put("fromUser", fromUser))
        }
        prefs.edit().putString("current", array.toString()).apply()
    }
    LaunchedEffect(uiState.aiReplyText) {
        val reply = uiState.aiReplyText
        if (!reply.isNullOrBlank() && reply != lastRenderedReply) {
            chatMessages.add(reply to false)
            lastRenderedReply = reply
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage
        if (!error.isNullOrBlank() && error != lastRenderedError &&
            (error.contains("AI", ignoreCase = true) ||
                error.contains("assistant", ignoreCase = true) ||
                error.contains("Edge Function", ignoreCase = true))
        ) {
            chatMessages.add("Maaf, permintaan tadi belum berhasil. Detail: $error" to false)
            lastRenderedError = error
        }
    }
    LaunchedEffect(chatMessages.size, uiState.isLoading) {
        chatScrollState.animateScrollTo(chatScrollState.maxValue)
    }

    fun startNewChat() {
        val current = chatMessages.toList()
        val firstQuestion = current.firstOrNull { it.second }?.first
        if (firstQuestion != null) {
            val title = firstQuestion.replace("📷 Screenshot dilampirkan", "").trim()
                .ifBlank { "Percakapan AI" }.take(56)
            historyEntries.add(0, AiChatHistoryEntry(title, current))
            while (historyEntries.size > 50) historyEntries.removeAt(historyEntries.lastIndex)
            saveAiChatHistory(context, historyEntries)
        }
        chatMessages.clear()
        chatMessages.add(AI_WELCOME_MESSAGE to false)
        promptInput = ""
        selectedImageUri = null
        prefs.edit().remove("current").apply()
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("AI Assistant", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showHistoryDialog = true }, enabled = !uiState.isLoading) {
                Text("Riwayat")
            }
            TextButton(onClick = { startNewChat() }, enabled = !uiState.isLoading) {
                Text("＋ Baru")
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (uiState.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED)
                    MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    if (uiState.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) "Terhubung" else "Offline",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Cara proses pembayaran?", "Cara tambah produk?", "Pesanan QR di mana?", "Printer tidak mencetak")
                .forEach { quick -> AssistChip(onClick = { promptInput = quick }, label = { Text(quick) }) }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(chatScrollState).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                chatMessages.forEach { (message, fromUser) ->
                    Box(modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                        Surface(
                            modifier = Modifier.widthIn(max = 340.dp),
                            shape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (fromUser) 16.dp else 4.dp,
                                bottomEnd = if (fromUser) 4.dp else 16.dp
                            ),
                            color = if (fromUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(text = remember(message) { formatAssistantMarkdown(message) },
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                if (uiState.isLoading) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("Asisten sedang mengetik…",
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (selectedImageUri != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(model = selectedImageUri, contentDescription = "Screenshot yang akan dikirim ke AI",
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                Text("Screenshot siap dikirim ke AI", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { selectedImageUri = null }, enabled = !uiState.isLoading) { Text("Hapus") }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !uiState.isLoading,
                modifier = Modifier.heightIn(min = 52.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) { Text("＋ Foto") }
            OutlinedTextField(value = promptInput, onValueChange = { promptInput = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Tulis pesan…") },
                maxLines = 4, enabled = !uiState.isLoading, shape = RoundedCornerShape(18.dp))
            Button(
                onClick = {
                    val question = promptInput.trim()
                    val imageUri = selectedImageUri
                    if ((question.isNotEmpty() || imageUri != null) && !uiState.isLoading) {
                        val imageBytes = imageUri?.let { uri ->
                            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                        }
                        val imageBase64 = imageBytes?.takeIf { it.isNotEmpty() && it.size <= 8 * 1024 * 1024 }
                            ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                        val mimeType = imageUri?.let { context.contentResolver.getType(it) }
                            ?.takeIf { it in listOf("image/jpeg", "image/png", "image/webp") } ?: "image/jpeg"
                        if (imageUri != null && imageBase64 == null) {
                            chatMessages.add("Gambar tidak bisa dibaca atau lebih dari 8 MB. Pilih screenshot yang lebih kecil." to false)
                        } else {
                            val visibleMessage = buildString {
                                if (imageUri != null) append("📷 Screenshot dilampirkan\n")
                                append(question.ifBlank { "Tolong analisis screenshot ini dan bantu saya memahami masalahnya." })
                            }
                            chatMessages.add(visibleMessage to true)
                            promptInput = ""
                            selectedImageUri = null
                            lastRenderedReply = uiState.aiReplyText
                            lastRenderedError = uiState.errorMessage
                            viewModel.askAiAssistant(question, imageBase64, mimeType)
                        }
                    }
                },
                enabled = (promptInput.isNotBlank() || selectedImageUri != null) && !uiState.isLoading,
                modifier = Modifier.heightIn(min = 52.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) { Text(if (uiState.isLoading) "..." else "Kirim") }
        }
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Riwayat percakapan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (historyEntries.isEmpty()) {
                        Text("Belum ada riwayat. Percakapan akan tersimpan saat kamu menekan ＋ Baru.")
                    } else {
                        Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            historyEntries.forEachIndexed { index, entry ->
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = {
                                        chatMessages.clear()
                                        chatMessages.addAll(entry.messages)
                                        prefs.edit().remove("current").apply()
                                        showHistoryDialog = false
                                    }, modifier = Modifier.weight(1f)) { Text(entry.title, maxLines = 2) }
                                    TextButton(onClick = {
                                        historyEntries.removeAt(index)
                                        saveAiChatHistory(context, historyEntries)
                                    }) { Text("Hapus") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistoryDialog = false }) { Text("Tutup") } },
            dismissButton = {
                if (historyEntries.isNotEmpty()) TextButton(onClick = {
                    historyEntries.clear()
                    saveAiChatHistory(context, historyEntries)
                }) { Text("Hapus semua") }
            }
        )
    }
}

// Render common Markdown bold markers in assistant chat without showing raw ** symbols.
private fun formatAssistantMarkdown(source: String): androidx.compose.ui.text.AnnotatedString {
    val boldPattern = Regex("""\*\*(.+?)\*\*""")
    return androidx.compose.ui.text.buildAnnotatedString {
        var cursor = 0
        boldPattern.findAll(source).forEach { match ->
            append(source.substring(cursor, match.range.first))
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
            append(match.groupValues[1])
            pop()
            cursor = match.range.last + 1
        }
        append(source.substring(cursor))
    }
}

// ============================================================================
// 3. KASIR (POS) SCREEN — ADAPTIVE PHONE & TABLET
// ============================================================================
@Composable
private fun KasirComposeScreen(
    viewModel: PosViewModel,
    uiState: PosUiState,
    isTabletOrWide: Boolean,
    isLandscapePhone: Boolean
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
    var promoDropdownExpanded by remember { mutableStateOf(false) }
    var paymentDropdownExpanded by remember { mutableStateOf(false) }

    val paymentOptions = remember(uiState.paymentMethods) {
        val configured = uiState.paymentMethods.filter { it.isActive }.sortedBy { it.sortOrder }
        if (configured.isEmpty()) {
            listOf(
                PaymentMethod(code = "cash", name = "Cash / Tunai", type = "cash", provider = "Tunai"),
                PaymentMethod(code = "qris", name = "QRIS", type = "qris", provider = "QRIS"),
                PaymentMethod(code = "transfer", name = "Transfer Bank", type = "bank_transfer", provider = "Bank"),
                PaymentMethod(code = "ewallet", name = "E-Wallet", type = "ewallet", provider = "E-Wallet")
            )
        } else if (configured.none { it.type.equals("cash", true) || it.code.equals("cash", true) }) {
            configured + PaymentMethod(code = "cash", name = "Cash / Tunai", type = "cash", provider = "Tunai")
        } else configured
    }
    val selectedPaymentOption = paymentOptions.firstOrNull { it.code.equals(uiState.paymentMethod, true) }
        ?: paymentOptions.firstOrNull { it.type.equals(uiState.paymentMethod, true) }
        ?: paymentOptions.firstOrNull()
    val isCashPayment = selectedPaymentOption?.type.equals("cash", true) ||
        uiState.paymentMethod.equals("cash", true)

    val filteredProducts = remember(uiState.products, uiState.selectedCategoryId, uiState.searchQuery) {
        uiState.products.filter { p ->
            p.isActive &&
                (uiState.selectedCategoryId == "ALL" || p.categoryId == uiState.selectedCategoryId) &&
                (uiState.searchQuery.isBlank() || p.name.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val catalogContent: @Composable (Modifier) -> Unit = { mod ->
        Column(modifier = mod, verticalArrangement = Arrangement.spacedBy(if (isLandscapePhone) 5.dp else 7.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setKasirFilter(uiState.selectedCategoryId, it) },
                label = { Text("Cari menu kopi, makanan, snack...", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.fillMaxWidth().height(if (isLandscapePhone) 44.dp else 48.dp),
                singleLine = true
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    modifier = Modifier.height(if (isLandscapePhone) 30.dp else 34.dp),
                    selected = uiState.selectedCategoryId == "ALL",
                    onClick = { viewModel.setKasirFilter("ALL", uiState.searchQuery) },
                    label = { Text("Semua Menu", style = MaterialTheme.typography.labelSmall) }
                )
                uiState.categories.forEach { cat ->
                    FilterChip(
                        modifier = Modifier.height(if (isLandscapePhone) 30.dp else 34.dp),
                        selected = uiState.selectedCategoryId == cat.id,
                        onClick = { viewModel.setKasirFilter(cat.id, uiState.searchQuery) },
                        label = { Text(cat.name, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Katalog digeser horizontal; jumlah baris mengikuti tinggi layar yang tersedia.
                val productRows = when {
                    maxHeight < 270.dp -> 1
                    maxHeight < 430.dp -> 2
                    else -> 3
                }
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(productRows),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(if (isLandscapePhone) 8.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscapePhone) 5.dp else 7.dp),
                    contentPadding = PaddingValues(end = 10.dp, bottom = 6.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        Column(
                            modifier = Modifier
                                .width(if (isTabletOrWide) 190.dp else if (isLandscapePhone) 148.dp else 164.dp)
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 58.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Outlined.Inventory2,
                                    contentDescription = "Foto belum tersedia",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp)
                                )
                                if (!product.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = product.imageUrl,
                                        contentDescription = "Foto ${product.name}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                if (product.isOutOfStock) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                                        color = MaterialTheme.colorScheme.error,
                                        shape = RoundedCornerShape(5.dp)
                                    ) {
                                        Text(
                                            "HABIS",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onError,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = RupiahFormatter.format(product.price),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Stok ${product.stock}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (product.isLowStock || product.isOutOfStock)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Text("Opsi", style = MaterialTheme.typography.labelMedium)
                                }
                                FilledIconButton(
                                    onClick = { viewModel.addToCart(product) },
                                    enabled = !product.isOutOfStock,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("+", style = MaterialTheme.typography.titleMedium)
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

                    // Pilih Promosi: promo boleh dipilih manual, tetapi syarat tetap wajib terpenuhi.
                    Text("Promosi / Diskon:", style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { promoDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                uiState.promotions.firstOrNull { it.id == uiState.selectedPromotionId }?.name
                                    ?: "Tanpa Promo",
                                modifier = Modifier.weight(1f)
                            )
                            Text("▾")
                        }
                        DropdownMenu(
                            expanded = promoDropdownExpanded,
                            onDismissRequest = { promoDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tanpa Promo") },
                                onClick = {
                                    viewModel.setKasirOrderMeta(promoId = null)
                                    promoDropdownExpanded = false
                                }
                            )
                            uiState.promotions.filter { it.active && it.allowCashier }.forEach { promo ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(promo.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${if (promo.type == "percentage") "${promo.value.toInt()}%" else RupiahFormatter.format(promo.value)} · Min ${RupiahFormatter.format(promo.minPurchase)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setKasirOrderMeta(promoId = promo.id)
                                        promoDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (uiState.selectedPromotionId != null) {
                        val promoReason = uiState.promotionEligibilityReason
                        Text(
                            if (promoReason == null) "Promo memenuhi syarat · Diskon ${RupiahFormatter.format(uiState.discountAmount)}"
                            else "Promo belum bisa digunakan: $promoReason",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (promoReason == null) Color(0xFF15803D) else MaterialTheme.colorScheme.error
                        )
                    }

                    // Pilihan metode dan tujuan pembayaran dari Pengaturan Outlet.
                    Text("Metode / Tujuan Pembayaran:", style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { paymentDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedPaymentOption?.name ?: "Pilih metode pembayaran", modifier = Modifier.weight(1f))
                            Text("▾")
                        }
                        DropdownMenu(
                            expanded = paymentDropdownExpanded,
                            onDismissRequest = { paymentDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            paymentOptions.forEach { method ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(method.name.ifBlank { method.provider.ifBlank { method.type.uppercase() } })
                                            Text(
                                                when (method.type.lowercase()) {
                                                    "cash" -> "Cash / Tunai"
                                                    "bank_transfer", "transfer" -> "Transfer Bank"
                                                    "ewallet" -> "E-Wallet"
                                                    "qris" -> "QRIS"
                                                    else -> method.type.uppercase()
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setKasirOrderMeta(payMethod = method.code.ifBlank { method.type.lowercase() })
                                        paymentDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (selectedPaymentOption != null && !isCashPayment) {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(selectedPaymentOption.name, fontWeight = FontWeight.SemiBold)
                                if (selectedPaymentOption.provider.isNotBlank()) Text(selectedPaymentOption.provider, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (selectedPaymentOption.accountName.isNotBlank()) Text("Atas nama: ${selectedPaymentOption.accountName}", style = MaterialTheme.typography.bodySmall)
                                if (selectedPaymentOption.accountNumber.isNotBlank()) Text(selectedPaymentOption.accountNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (selectedPaymentOption.instructions.isNotBlank()) Text(selectedPaymentOption.instructions, style = MaterialTheme.typography.bodySmall)
                                if (!selectedPaymentOption.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = selectedPaymentOption.imageUrl,
                                        contentDescription = "QR / instruksi pembayaran",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = if (selectedPaymentOption.type.equals("qris", true) || selectedPaymentOption.code.contains("qris", true) || selectedPaymentOption.name.contains("qris", true)) 360.dp else 76.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }

                    if (isCashPayment) {
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
                    if (isCashPayment && uiState.cart.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kembalian")
                            Text(RupiahFormatter.format(uiState.changeAmount), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Button(
                        onClick = { showConfirmCheckoutDialog = true },
                        enabled = uiState.cart.isNotEmpty() && !uiState.isCheckoutProcessing && uiState.isPromotionEligible,
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

    val useSideBySideCashier = isTabletOrWide || (isLandscapePhone && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700)
    if (useSideBySideCashier) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (isLandscapePhone) 10.dp else 16.dp)) {
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

                    if (sugarOptions.isNotEmpty()) {
                        Text("Tingkat gula", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            sugarOptions.forEach { option ->
                                FilterChip(selected = customSugar == option.name, onClick = { customSugar = option.name }, label = { Text(option.name + if (option.priceDelta > 0) " +${RupiahFormatter.format(option.priceDelta)}" else "", style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                    }
                    if (iceOptions.isNotEmpty()) {
                        Text("Es / suhu", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            iceOptions.forEach { option ->
                                FilterChip(selected = customIce == option.name, onClick = { customIce = option.name }, label = { Text(option.name + if (option.priceDelta > 0) " +${RupiahFormatter.format(option.priceDelta)}" else "", style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                    }
                    if (sizeOptions.isNotEmpty()) {
                        Text("Ukuran", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            sizeOptions.forEach { option ->
                                FilterChip(selected = customCup == option.name, onClick = { customCup = option.name }, label = { Text(option.name + if (option.priceDelta > 0) " +${RupiahFormatter.format(option.priceDelta)}" else "") })
                            }
                        }
                    }
                    if (productToppings.isNotEmpty()) {
                        Text("Tambahan", style = MaterialTheme.typography.labelMedium)
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
                    if (sugarOptions.isEmpty() && iceOptions.isEmpty() && sizeOptions.isEmpty() && productToppings.isEmpty()) {
                        Text("Tidak ada opsi tambahan untuk produk ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            val selectedOptions = listOfNotNull(
                                activeOptions.firstOrNull { it.groupKey == "size" && it.name == customCup },
                                activeOptions.firstOrNull { it.groupKey == "sugar" && it.name == customSugar },
                                activeOptions.firstOrNull { it.groupKey == "ice" && it.name == customIce }
                            )
                            val addons = buildList {
                                uiState.productAddons.filter { it.productId == targetCustom.id && it.isActive && it.id in customAddonIds }.forEach { add(it) }
                                if (activeOptions.none { it.groupKey == "size" } && customCup == "Large") {
                                    add(ProductAddon(id = "ad-lg", productId = targetCustom.id, name = "Large Cup", price = 5000.0))
                                }
                                if (uiState.productAddons.none { it.productId == targetCustom.id && it.isActive } && customExtraShot) {
                                    add(ProductAddon(id = "ad-es", productId = targetCustom.id, name = "Extra Espresso Shot", price = 6000.0))
                                }
                                if (uiState.productAddons.none { it.productId == targetCustom.id && it.isActive } && customOatMilk) {
                                    add(ProductAddon(id = "ad-om", productId = targetCustom.id, name = "Oat Milk", price = 8000.0))
                                }
                            }
                            viewModel.addToCart(
                                product = targetCustom,
                                sugar = if (sugarOptions.isNotEmpty()) customSugar else "",
                                ice = if (iceOptions.isNotEmpty()) customIce else "",
                                cupSize = if (sizeOptions.isNotEmpty()) customCup else "",
                                addons = addons,
                                customizationOptions = selectedOptions,
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
    isTabletOrWide: Boolean,
    isLandscapePhone: Boolean
) {
    var lane by remember { mutableStateOf("baru") }
    var sourceFilter by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var paymentOrder by remember { mutableStateOf<OrderWithItems?>(null) }
    var selectedPaymentCode by remember { mutableStateOf("") }
    var cashReceived by remember { mutableStateOf("") }
    val lanes = listOf("baru" to "Baru", "diproses" to "Diproses", "siap" to "Siap", "riwayat" to "Riwayat")

    val paymentOptions = remember(uiState.paymentMethods) {
        val configured = uiState.paymentMethods.filter { it.isActive }.sortedBy { it.sortOrder }
        if (configured.none { it.type.equals("cash", true) || it.code.equals("cash", true) }) {
            configured + PaymentMethod(code = "cash", name = "Tunai / Cash", type = "cash", provider = "Tunai")
        } else configured
    }
    fun inLane(o: OrderWithItems, key: String): Boolean = when (key) {
        "baru" -> o.status == OrderStatus.NEW
        "diproses" -> o.status == OrderStatus.PREPARING
        "siap" -> o.status == OrderStatus.READY
        else -> o.status == OrderStatus.COMPLETED || o.status == OrderStatus.CANCELLED
    }
    fun paymentLabel(code: String?): String {
        if (code.isNullOrBlank()) return "Belum dipilih"
        val method = paymentOptions.firstOrNull {
            it.code.equals(code, true) || it.type.equals(code, true) ||
                it.name.equals(code, true) || it.provider.equals(code, true)
        }
        return method?.let { listOf(it.name, it.provider).filter { part -> part.isNotBlank() }.distinct().joinToString(" · ") }
            ?.ifBlank { code.uppercase() } ?: code.uppercase()
    }
    val filtered = uiState.orders.filter { o ->
        inLane(o, lane) && (sourceFilter == "ALL" || o.source.equals(sourceFilter, true)) &&
            (search.isBlank() || o.orderNo.toString().contains(search) || (o.customerName ?: "").contains(search, true))
    }

    val payingOrder = paymentOrder
    if (payingOrder != null) {
        val selectedMethod = paymentOptions.firstOrNull { it.code.equals(selectedPaymentCode, true) }
        val methodType = selectedMethod?.type?.lowercase().orEmpty()
        val isCash = methodType == "cash" || selectedMethod?.code.equals("cash", true)
        val isTransfer = methodType == "bank_transfer"
        val isEwallet = methodType == "ewallet"
        val receivedValue = cashReceived.toDoubleOrNull() ?: 0.0
        var paymentDropdownExpanded by remember(payingOrder.id) { mutableStateOf(false) }
        val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp

        Dialog(
            onDismissRequest = { paymentOrder = null },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .heightIn(max = (screenHeight * 0.84f).dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("Konfirmasi Pembayaran", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Order #${payingOrder.orderNo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(RupiahFormatter.format(payingOrder.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Pilihan customer: ${paymentLabel(payingOrder.customerPaymentMethod ?: payingOrder.paymentMethod)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Text("Metode pembayaran kasir", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { paymentDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                selectedMethod?.let { it.name.ifBlank { it.provider.ifBlank { it.type.uppercase() } } }
                                    ?: "Pilih metode pembayaran",
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text("▾")
                        }
                        DropdownMenu(
                            expanded = paymentDropdownExpanded,
                            onDismissRequest = { paymentDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 260.dp)
                        ) {
                            paymentOptions.forEach { method ->
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(method.name.ifBlank { method.provider.ifBlank { method.type.uppercase() } })
                                            val detail = listOf(method.provider, method.accountName, method.accountNumber)
                                                .filter { it.isNotBlank() }.distinct().joinToString(" · ")
                                            if (detail.isNotBlank()) {
                                                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedPaymentCode = method.code
                                        paymentDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (isCash) {
                        OutlinedTextField(
                            value = cashReceived,
                            onValueChange = { cashReceived = it.filter { ch -> ch.isDigit() }.take(12) },
                            label = { Text("Uang diterima (Rp)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kembalian", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                RupiahFormatter.format((receivedValue - payingOrder.total).coerceAtLeast(0.0)),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }
                        if (receivedValue < payingOrder.total) {
                            Text("Uang diterima harus minimal sebesar total tagihan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    } else if (isTransfer || isEwallet || methodType == "qris") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (isTransfer) "Tujuan transfer" else if (isEwallet) "Tujuan e-wallet" else "Pembayaran QRIS", fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(selectedMethod?.provider, selectedMethod?.name, selectedMethod?.accountName, selectedMethod?.accountNumber)
                                        .filter { !it.isNullOrBlank() }.distinct().joinToString("\n").ifBlank { "Detail tujuan belum diisi owner." },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (!selectedMethod?.instructions.isNullOrBlank()) {
                                    Text(selectedMethod?.instructions.orEmpty(), style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Pastikan pembayaran sudah diterima / diverifikasi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { paymentOrder = null }) { Text("Batal") }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            enabled = selectedMethod != null && (!isCash || receivedValue >= payingOrder.total),
                            onClick = {
                                val method = selectedMethod ?: return@Button
                                viewModel.confirmOrderPayment(
                                    payingOrder,
                                    method.code.ifBlank { method.type }.lowercase(),
                                    if (isCash) receivedValue else null
                                ) {
                                    lane = if (payingOrder.status == OrderStatus.READY) "siap" else "diproses"
                                    paymentOrder = null
                                }
                            }
                        ) { Text("Konfirmasi Bayar") }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Order #${order.orderNo}", fontWeight = FontWeight.Bold)
                                    val tableName = order.restaurantTable?.name ?: "Takeaway"
                                    val customerName = order.customerName ?: "Walk-in"
                                    Text("${order.source.uppercase()} · $tableName · $customerName", style = MaterialTheme.typography.bodyMedium)
                                    val time = order.createdAt.substringAfter("T", order.createdAt).take(5)
                                    Text("Jam: $time · ${order.createdAt.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(order.status.labelId, fontWeight = FontWeight.Bold)
                                    Text(if (order.isPaid) "LUNAS" else "BELUM LUNAS", color = if (order.isPaid) Color(0xFF15803D) else MaterialTheme.colorScheme.error)
                                }
                            }
                            Text("Metode customer: ${paymentLabel(order.customerPaymentMethod ?: order.paymentMethod)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (order.discount > 0) {
                                val appliedPromoName = uiState.promotions.firstOrNull { it.id == order.promotionId }?.name
                                Text(
                                    "PROMO DIPAKAI: ${appliedPromoName ?: "Promo/Diskon"} · Hemat ${RupiahFormatter.format(order.discount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF15803D),
                                    fontWeight = FontWeight.SemiBold
                                )
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
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Total: ${RupiahFormatter.format(order.total)}", fontWeight = FontWeight.Bold)
                                    if (order.isPaid) Text("Metode kasir: ${paymentLabel(order.paymentMethod)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedButton(onClick = { viewModel.printOrderToThermal(order, PrinterRole.KITCHEN) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text("Cetak") }
                                    OutlinedButton(onClick = { viewModel.openReceiptDialog(order) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text("Struk") }
                                }
                            }
                            if (lane != "riwayat") {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (!order.isPaid && order.status != OrderStatus.CANCELLED) {
                                        Button(onClick = {
                                            paymentOrder = order
                                            selectedPaymentCode = paymentOptions.firstOrNull {
                                                it.code.equals(order.paymentMethod, true) ||
                                                    it.type.equals(order.paymentMethod, true)
                                            }?.code ?: paymentOptions.firstOrNull { it.type.equals("cash", true) }?.code.orEmpty()
                                            cashReceived = order.total.toLong().toString()
                                        }) { Text("Tandai Bayar") }
                                    }
                                    when (order.status) {
                                        OrderStatus.NEW -> if (order.isPaid) Button(onClick = { viewModel.updateOrderStatus(order, OrderStatus.PREPARING) { lane = "diproses" } }) { Text("Mulai Proses") }
                                        OrderStatus.PREPARING -> Button(onClick = { viewModel.updateOrderStatus(order, OrderStatus.READY) { lane = "siap" } }) { Text("Tandai Siap") }
                                        OrderStatus.READY -> if (order.isPaid) Button(onClick = { viewModel.updateOrderStatus(order, OrderStatus.COMPLETED) { lane = "riwayat" } }) { Text("Selesaikan") }
                                            else Text("Konfirmasi pembayaran sebelum diselesaikan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        else -> {}
                                    }
                                    if (order.status != OrderStatus.COMPLETED && order.status != OrderStatus.CANCELLED) {
                                        OutlinedButton(
                                            onClick = { if (order.source == "qr") viewModel.rejectQrOrder(order) else viewModel.updateOrderStatus(order, OrderStatus.CANCELLED) { lane = "riwayat" } },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) { Text(if (order.source == "qr") "Ditolak" else "Batalkan") }
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

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // Compact fixed toolbar: the order list scrolls independently below it.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("History · ${filteredOrders.size} order", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Lunas: ${RupiahFormatter.format(filteredOrders.filter { it.isPaid }.sumOf { it.total })}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { csvLauncher.launch("history-transaksi-kereta-malam.csv") }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.FileDownload, contentDescription = "Ekspor CSV", tint = Color(0xFF2878F0))
            }
            IconButton(onClick = { confirmDeleteAll = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Hapus semua data", tint = MaterialTheme.colorScheme.error)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = fromDate, onValueChange = { fromDate = it.take(10) }, label = { Text("Dari tanggal") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.weight(1f).heightIn(max = 58.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = toDate, onValueChange = { toDate = it.take(10) }, label = { Text("Sampai") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.weight(1f).heightIn(max = 58.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Cari order") }, leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }, modifier = Modifier.weight(1f).heightIn(max = 58.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                selectedIds = if (filteredOrders.isNotEmpty() && filteredOrders.all { it.id in selectedIds }) selectedIds - filteredOrders.map { it.id }.toSet()
                else selectedIds + filteredOrders.map { it.id }
            }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp), modifier = Modifier.height(40.dp)) {
                Text(if (filteredOrders.isNotEmpty() && filteredOrders.all { it.id in selectedIds }) "Batal pilih" else "Pilih semua", style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${selectedIds.size} dipilih", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { confirmDeleteSelected = true }, enabled = selectedIds.isNotEmpty(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text("Hapus terpilih", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
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
                            Text("Metode: ${order.payments.firstOrNull()?.method ?: order.paymentMethod ?: "Belum ditentukan"} · Status: ${order.status.labelId}", style = MaterialTheme.typography.bodyMedium)
                            if (order.discount > 0) {
                                Text(
                                    "Promo: ${uiState.promotions.firstOrNull { it.id == order.promotionId }?.name ?: "Diskon"} · Potongan: ${RupiahFormatter.format(order.discount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF15803D)
                                )
                            }
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
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var selectedShift by remember { mutableStateOf<StaffShift?>(null) }
    val activeCount = uiState.shifts.count { it.status.equals("open", true) }
    val needsReview = uiState.shifts.count { !it.auditVerified && it.status.equals("closed", true) }
    val filtered = uiState.shifts.filter { shift ->
        val queryMatch = search.isBlank() || shift.staffName.contains(search, true) || shift.id.contains(search, true) || shift.notes.orEmpty().contains(search, true)
        val date = shift.openedAt.take(10)
        val dateMatch = (fromDate.isBlank() || date >= fromDate) && (toDate.isBlank() || date <= toDate)
        val filterMatch = when (filter) {
            "PERLU" -> !shift.auditVerified && shift.status.equals("closed", true)
            "AKTIF" -> shift.status.equals("open", true)
            else -> true
        }
        queryMatch && filterMatch && dateMatch
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
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // Compact fixed controls; only the shift rows scroll.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = filter == "SEMUA", onClick = { filter = "SEMUA" }, label = { Text("Semua ${uiState.shifts.size}", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(34.dp))
            FilterChip(selected = filter == "PERLU", onClick = { filter = "PERLU" }, label = { Text("Perlu dicek $needsReview", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(34.dp))
            FilterChip(selected = filter == "AKTIF", onClick = { filter = "AKTIF" }, label = { Text("Aktif $activeCount", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(34.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Cari kasir / ID") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f).heightIn(max = 56.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { viewModel.syncAllDataFromBackend() }, enabled = !uiState.isLoading, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Color(0xFF2878F0))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = fromDate, onValueChange = { fromDate = it.take(10) }, label = { Text("Dari") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.weight(1f).heightIn(max = 54.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = toDate, onValueChange = { toDate = it.take(10) }, label = { Text("Sampai") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.weight(1f).heightIn(max = 54.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
            if (fromDate.isNotBlank() || toDate.isNotBlank()) TextButton(onClick = { fromDate = ""; toDate = "" }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Reset", style = MaterialTheme.typography.labelSmall) }
        }
        Text("Riwayat shift", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
