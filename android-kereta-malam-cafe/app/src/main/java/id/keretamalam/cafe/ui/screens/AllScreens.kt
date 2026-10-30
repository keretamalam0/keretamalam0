package id.keretamalam.cafe.ui.screens

import id.keretamalam.cafe.R
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import coil.compose.AsyncImage

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
private fun formatTimestampWib(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    val zone = java.time.ZoneId.of("Asia/Jakarta")
    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", java.util.Locale("id", "ID"))
    val instant = runCatching { java.time.Instant.parse(raw) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    if (instant != null) return formatter.withZone(zone).format(instant)
    val local = runCatching { java.time.LocalDateTime.parse(raw.replace(' ', 'T')) }.getOrNull()
    return local?.format(formatter) ?: raw
}

@Composable
fun LoginComposeScreen(
    viewModel: PosViewModel,
    uiState: PosUiState,
    isTabletOrWide: Boolean
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "loginGlow")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF087E83), Color(0xFF005052), Color(0xFF003B3D))
                )
            )
            .imePadding()
            .padding(horizontal = if (isTabletOrWide) 32.dp else 22.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft background glow gives the screen depth without slowing app startup.
        Box(
            Modifier
                .size(if (isTabletOrWide) 340.dp else 260.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x5539C5C5), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(200.dp)
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .shadow(26.dp, RoundedCornerShape(34.dp))
                .background(Color(0xFFFFFCFD), RoundedCornerShape(34.dp))
                .padding(horizontal = if (isTabletOrWide) 34.dp else 25.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.kereta_malam_logo),
                contentDescription = "Logo Kereta Malam Cafe",
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer(scaleX = logoScale, scaleY = logoScale),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Kereta Malam Cafe",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF151719)
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "Masuk untuk mengelola operasional cafe",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6F7075),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            val activeMsg = uiState.errorMessage ?: uiState.successMessage
            if (activeMsg != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (uiState.errorMessage != null) Color(0xFFFFE8E8) else Color(0xFFE0F5EF),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            activeMsg,
                            color = if (uiState.errorMessage != null) Color(0xFF9D2020) else Color(0xFF12684D),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearBannerMessages() }) { Text("Tutup") }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Text("Email", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold, color = Color(0xFF17191B))
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Masukkan email staf", color = Color(0xFF9A9A9E)) },
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF17191B),
                    unfocusedTextColor = Color(0xFF17191B),
                    focusedBorderColor = Color(0xFF087E83),
                    unfocusedBorderColor = Color(0xFFD6D2D3),
                    focusedContainerColor = Color(0xFFF9F7F7),
                    unfocusedContainerColor = Color(0xFFF2EFEF),
                    cursorColor = Color(0xFF087E83)
                )
            )
            Spacer(Modifier.height(14.dp))
            Text("Password", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold, color = Color(0xFF17191B))
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Masukkan password", color = Color(0xFF9A9A9E)) },
                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (passwordVisible) "Sembunyikan password" else "Lihat password",
                            tint = Color(0xFF17191B)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF17191B),
                    unfocusedTextColor = Color(0xFF17191B),
                    focusedBorderColor = Color(0xFF087E83),
                    unfocusedBorderColor = Color(0xFFD6D2D3),
                    focusedContainerColor = Color(0xFFF9F7F7),
                    unfocusedContainerColor = Color(0xFFF2EFEF),
                    cursorColor = Color(0xFF087E83)
                )
            )
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = { viewModel.loginWithSupabaseAuth(email.trim(), password) },
                enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp).shadow(8.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF006D70),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF9CB8B8)
                )
            ) {
                Text(
                    if (uiState.isLoading) "Memproses..." else "Masuk  →",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color(0xFFD8D2D3), thickness = 1.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Akses khusus akun Owner dan Kasir Kereta Malam Cafe",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF77777B),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ChatKasirComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var selectedThreadId by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var threadToDelete by remember { mutableStateOf<CashierChatThread?>(null) }
    LaunchedEffect(Unit) {
        viewModel.refreshCashierChats()
        while (true) {
            kotlinx.coroutines.delay(3500)
            viewModel.refreshCashierChats(selectedThreadId.ifBlank { null })
        }
    }
    LaunchedEffect(uiState.cashierChatThreads) {
        if (selectedThreadId.isBlank() || uiState.cashierChatThreads.none { it.id == selectedThreadId }) {
            selectedThreadId = uiState.cashierChatThreads.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(selectedThreadId) {
        if (selectedThreadId.isNotBlank()) viewModel.loadCashierChatMessages(selectedThreadId)
    }
    val active = uiState.cashierChatThreads.firstOrNull { it.id == selectedThreadId }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Chat ke Kasir", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Pesan dari pelanggan lewat QR Ordering. Pilih percakapan untuk membaca dan membalas.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (uiState.backendStatus != BackendIntegrationStatus.ACTIVE_VERIFIED) {
            Card { Text("Hubungkan backend Supabase terlebih dahulu untuk menggunakan chat langsung.", Modifier.padding(16.dp)) }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.weight(0.9f).fillMaxHeight()) {
                    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (uiState.cashierChatThreads.isEmpty()) item { Text("Belum ada percakapan masuk.", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(uiState.cashierChatThreads, key = { it.id }) { thread ->
                            val activeRow = thread.id == selectedThreadId
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (activeRow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        Modifier.weight(1f).clickable { selectedThreadId = thread.id }.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text("${thread.tableName} · Meja ${thread.tableCode}", fontWeight = FontWeight.SemiBold)
                                        Text(thread.customerName, style = MaterialTheme.typography.bodySmall)
                                        Text(if (thread.status == "open") "Aktif" else "Ditutup", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { threadToDelete = thread }) {
                                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Hapus percakapan", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                Card(Modifier.weight(1.6f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(active?.let { "${it.tableName} · Meja ${it.tableCode}" } ?: "Pilih percakapan", fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.cashierChatMessages, key = { it.id }) { message ->
                                val fromCashier = message.senderRole == "cashier"
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromCashier) Arrangement.End else Arrangement.Start) {
                                    Surface(shape = RoundedCornerShape(14.dp), color = if (fromCashier) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                                        Column(Modifier.widthIn(max = 300.dp).padding(10.dp)) {
                                            Text(if (fromCashier) "Kasir" else "Customer", style = MaterialTheme.typography.labelSmall)
                                            Text(message.body)
                                        }
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = draft, onValueChange = { draft = it.take(2000) }, modifier = Modifier.weight(1f), placeholder = { Text("Balas pelanggan...") }, maxLines = 3, enabled = active?.status == "open")
                            Button(onClick = { viewModel.sendCashierChatMessage(selectedThreadId, draft); draft = "" }, enabled = active?.status == "open" && draft.isNotBlank()) { Text("Kirim") }
                        }
                    }
                }
            }
        }
    }
    threadToDelete?.let { thread ->
        AlertDialog(
            onDismissRequest = { threadToDelete = null },
            title = { Text("Hapus percakapan?") },
            text = { Text("Percakapan ${thread.tableName} · Meja ${thread.tableCode} beserta seluruh pesannya akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCashierChatThread(thread.id)
                    threadToDelete = null
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { threadToDelete = null }) { Text("Batal") }
            }
        )
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
                PosModuleRoute.CHAT_KASIR -> ChatKasirComposeScreen(viewModel, uiState)
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
                PosModuleRoute.LAPORAN_KASIR -> LaporanKasirComposeScreen(viewModel, uiState)
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
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf("") }
    var customTo by remember { mutableStateOf("") }
    val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta"))
    fun dateOf(raw: String): java.time.ZonedDateTime? = runCatching {
        runCatching { java.time.Instant.parse(raw).atZone(java.time.ZoneId.of("Asia/Jakarta")) }
            .getOrElse { java.time.OffsetDateTime.parse(raw).atZoneSameInstant(java.time.ZoneId.of("Asia/Jakarta")) }
    }.getOrNull()
    val orders = uiState.orders.filter { order ->
        val date = dateOf(order.createdAt)?.toLocalDate()
        when (range) {
            "today" -> date == today
            "7days" -> date != null && !date.isBefore(today.minusDays(6)) && !date.isAfter(today)
            "30days" -> date != null && !date.isBefore(today.minusDays(29)) && !date.isAfter(today)
            "custom" -> date != null && (customFrom.isBlank() || date.toString() >= customFrom) && (customTo.isBlank() || date.toString() <= customTo)
            else -> true
        }
    }
    val paid = orders.filter { it.isPaid && it.status != OrderStatus.CANCELLED && it.returnedAt == null }

    fun paidAmount(order: OrderWithItems): Double =
        if (order.payments.isNotEmpty()) order.payments.sumOf { it.amount } else order.total
    val revenue = paid.sumOf { paidAmount(it) }
    val average = if (paid.isEmpty()) 0.0 else revenue / paid.size
    val activeCount = uiState.orders.count { it.status == OrderStatus.NEW || it.status == OrderStatus.PREPARING || it.status == OrderStatus.READY }
    val hours = (0..23).map { h -> orders.count { dateOf(it.createdAt)?.hour == h }.toFloat() }
    val peak = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0f }
    val best = paid.flatMap { it.orderItems }.groupBy { it.productName }
        .map { e -> Triple(e.key, e.value.sumOf { it.quantity }, e.value.sumOf { it.lineTotal }) }
        .sortedByDescending { it.second }.take(5)
    val maxQty = (best.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val paymentGroups = listOf("cash" to "Tunai", "qris" to "QRIS", "transfer" to "Transfer Bank", "ewallet" to "E-Wallet", "debit" to "Kartu Debit")
    fun paymentGroup(order: OrderWithItems): String {
        val raw = (order.paymentMethod?.takeIf { it.isNotBlank() }
            ?: order.customerPaymentMethod?.takeIf { it.isNotBlank() }
            ?: order.payments.firstOrNull()?.method?.takeIf { it.isNotBlank() }
            ?: "cash").lowercase().replace("-", "_").trim()
        return when {
            raw == "cash" || raw == "tunai" || raw.contains("cash") -> "cash"
            raw.contains("ewallet") || raw.contains("e_wallet") || raw.contains("e wallet") || raw.contains("gopay") || raw.contains("go pay") || raw.contains("ovo") || raw.contains("dana") || raw.contains("shopeepay") || raw.contains("linkaja") -> "ewallet"
            raw.contains("qris") -> "qris"
            raw.contains("transfer") || raw.contains("bank_transfer") || raw.contains("bank transfer") || raw.contains("bca") || raw.contains("bni") || raw.contains("bri") || raw.contains("mandiri") || raw.contains("permata") -> "transfer"
            raw.contains("debit") || raw.contains("card") || raw.contains("kartu") -> "debit"
            else -> "other"
        }
    }
    val allPaidOrders = uiState.orders.filter { it.isPaid && it.status != OrderStatus.CANCELLED && it.returnedAt == null }
    fun paymentGroupForMethod(rawInput: String): String {
        val raw = rawInput.lowercase().replace("-", "_").trim()
        return when {
            raw == "cash" || raw == "tunai" || raw.contains("cash") -> "cash"
            raw.contains("ewallet") || raw.contains("e_wallet") || raw.contains("e wallet") || raw.contains("gopay") || raw.contains("go pay") || raw.contains("ovo") || raw.contains("dana") || raw.contains("shopeepay") || raw.contains("linkaja") -> "ewallet"
            raw.contains("qris") -> "qris"
            raw.contains("transfer") || raw.contains("bank_transfer") || raw.contains("bank transfer") || raw.contains("bca") || raw.contains("bni") || raw.contains("bri") || raw.contains("mandiri") || raw.contains("permata") || raw.contains("bank") -> "transfer"
            raw.contains("debit") || raw.contains("card") || raw.contains("kartu") -> "debit"
            else -> "other"
        }
    }
    // Source-account balances must use saved payment amounts, not order totals.
    // Legacy paid orders without payment rows retain their saved order total as fallback.
    fun historySales(method: String): Double = allPaidOrders.sumOf { order ->
        if (order.payments.isNotEmpty()) order.payments
            .filter { paymentGroupForMethod(it.method) == method }
            .sumOf { it.amount }
        else if (paymentGroup(order) == method) order.total else 0.0
    }
    fun allocated(method: String): Double {
        val source = when (method) {
            "cash" -> "sales_cash"
            "qris" -> "sales_qris"
            "transfer" -> "sales_transfer"
            else -> return 0.0
        }
        val adjustment = uiState.cashFundEntries.filter { it.entryType == "replenishment" && it.source == source }.sumOf { it.amount }
        val expenses = uiState.expenses.filter { it.cashSource == source }.sumOf { it.amount }
        return adjustment + expenses
    }
    fun remainingSales(method: String): Double = (historySales(method) - allocated(method)).coerceAtLeast(0.0)
    val allCashSales = historySales("cash")
    val availableSalesCash = remainingSales("cash")
    val historyCash = historySales("cash")
    val historyQris = historySales("qris")
    val historyTransfer = historySales("transfer")
    val remainingCash = remainingSales("cash")
    val remainingQris = remainingSales("qris")
    val remainingTransfer = remainingSales("transfer")
    val payments = paymentGroups.map { pair ->
        val matched = paid.filter { paymentGroup(it) == pair.first }
        Triple(pair.second, matched.sumOf { it.total }, matched.size)
    }.filter { it.second > 0.0 }
    val trend = (0..6).map { i ->
        val d = today.minusDays((6 - i).toLong())
        paid.filter { dateOf(it.createdAt)?.toLocalDate() == d }.sumOf { paidAmount(it) }.toFloat()
    }
    val labels = (0..6).map { today.minusDays((6 - it).toLong()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) }
    val rangeLabel = when (range) { "today" -> "Hari ini"; "7days" -> "7 hari terakhir"; "30days" -> "1 bulan"; "custom" -> "Periode kustom"; else -> "Semua data" }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Dashboard Kereta Malam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(rangeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }) { Text("Periode ▾") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            listOf("today" to "Hari ini", "7days" to "7 hari", "30days" to "1 bulan", "custom" to "Tanggal kustom", "all" to "Semua data").forEach { p ->
                                DropdownMenuItem(text = { Text(p.second) }, onClick = { range = p.first; menuOpen = false })
                            }
                        }
                    }
                }
            }
            if (range == "custom") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { val initial = runCatching { java.time.LocalDate.parse(customFrom) }.getOrDefault(today); android.app.DatePickerDialog(context, { _, y, m, d -> customFrom = java.time.LocalDate.of(y, m + 1, d).toString() }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show() }, modifier = Modifier.weight(1f)) { Text(if (customFrom.isBlank()) "Dari tanggal ▾" else "Dari: $customFrom") }
                        OutlinedButton(onClick = { val initial = runCatching { java.time.LocalDate.parse(customTo) }.getOrDefault(today); android.app.DatePickerDialog(context, { _, y, m, d -> customTo = java.time.LocalDate.of(y, m + 1, d).toString() }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show() }, modifier = Modifier.weight(1f)) { Text(if (customTo.isBlank()) "Sampai tanggal ▾" else "Sampai: $customTo") }
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
                                Text(dateOf(order.createdAt)?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM, HH:mm", java.util.Locale("id", "ID"))) ?: order.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                val barTop = size.height-h
                val barHeight = h.coerceAtLeast(2.dp.toPx())
                if (v > 0f) {
                    drawRoundRect(blue.copy(alpha = .13f), androidx.compose.ui.geometry.Offset(x-2.dp.toPx(), barTop-1.dp.toPx()), androidx.compose.ui.geometry.Size(w+4.dp.toPx(), barHeight+3.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(w/2f,w/2f))
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = if (selected == i) listOf(Color(0xFF1558C0), Color(0xFF8FB7FF), Color(0x002878F0)) else listOf(Color(0xFF2878F0), Color(0xFF6599F5), Color(0x00A8C5FA)),
                            startY = barTop,
                            endY = size.height
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(x, barTop),
                        size = androidx.compose.ui.geometry.Size(w, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w/2f,w/2f)
                    )
                } else {
                    drawRoundRect(blue.copy(alpha=.10f), androidx.compose.ui.geometry.Offset(x, size.height-2.dp.toPx()), androidx.compose.ui.geometry.Size(w, 2.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(w/2f,w/2f))
                }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
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
    val messages: List<Pair<String, Boolean>>,
    val pinned: Boolean = false
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
            item.optString("title", "Percakapan sebelumnya"), messages, item.optBoolean("pinned", false)
        )
    }
}.getOrDefault(emptyList())

private fun saveAiChatHistory(context: android.content.Context, entries: List<AiChatHistoryEntry>) {
    val array = JSONArray()
    entries.forEach { entry ->
        val item = JSONObject().put("title", entry.title).put("pinned", entry.pinned)
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
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showHistoryDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(horizontal = 8.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Riwayat percakapan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Semua chat tersimpan di perangkat ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showHistoryDialog = false }) { Text("Tutup") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Semua: ${historyEntries.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
                        Text("📌 Disematkan: ${historyEntries.count { it.pinned }}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                        if (historyEntries.isNotEmpty()) TextButton(onClick = { historyEntries.clear(); saveAiChatHistory(context, historyEntries) }) { Text("Hapus semua") }
                    }
                    if (historyEntries.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Belum ada riwayat. Mulai chat baru lalu tekan ＋ Baru untuk menyimpannya.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(historyEntries.sortedWith(compareByDescending<AiChatHistoryEntry> { it.pinned }.thenBy { historyEntries.indexOf(it) }), key = { _, entry -> entry.title + entry.messages.size.toString() }) { _, entry ->
                                OutlinedCard(onClick = {
                                    chatMessages.clear(); chatMessages.addAll(entry.messages); prefs.edit().remove("current").apply(); showHistoryDialog = false
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Text("${entry.messages.count { it.second }} pesan dari kamu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        TextButton(onClick = { val index = historyEntries.indexOfFirst { it === entry }; if (index >= 0) { historyEntries[index] = historyEntries[index].copy(pinned = !historyEntries[index].pinned); saveAiChatHistory(context, historyEntries) } }) { Text(if (entry.pinned) "📌 Lepas" else "📌 Pin") }
                                        TextButton(onClick = { historyEntries.remove(entry); saveAiChatHistory(context, historyEntries) }) { Text("Hapus") }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { showHistoryDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke chat") }
                }
            }
        }
    }
}

// Render common Markdown bold markers
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
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
                            FilledTonalButton(
                                onClick = { viewModel.addToCart(product) },
                                enabled = !product.isOutOfStock,
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("+ Tambah", style = MaterialTheme.typography.labelMedium)
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
                        textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.orderNotes,
                        onValueChange = { viewModel.setKasirOrderMeta(notes = it) },
                        label = { Text("Catatan Pesanan / Dapur") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
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
                        textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
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
                    if (uiState.taxAmount > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pajak (${uiState.outletSettings.taxRate.toInt()}%)")
                            Text(RupiahFormatter.format(uiState.taxAmount))
                        }
                    }
                    if (uiState.serviceChargeAmount > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Service charge")
                            Text(RupiahFormatter.format(uiState.serviceChargeAmount))
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

                    if (methodType == "qris" || selectedMethod?.code?.contains("qris", true) == true || selectedMethod?.name?.contains("qris", true) == true) {
                        if (selectedMethod?.imageUrl.isNullOrBlank()) {
                            Text(
                                "Foto QRIS belum diatur untuk metode ini. Tambahkan foto di Pengaturan Outlet → Metode Pembayaran.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        selectedMethod?.name ?: "QRIS",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    AsyncImage(
                                        model = selectedMethod?.imageUrl,
                                        contentDescription = "Kode QRIS untuk dipindai pelanggan",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 280.dp, max = 440.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                    Text(
                                        "Tunjukkan layar ini ke pelanggan untuk dipindai.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                                    Text("Waktu: ${formatTimestampWib(order.createdAt)} WIB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    val canRejectQrOrder = order.source == "qr" && order.status == OrderStatus.NEW
                                    val canCancelPosOrder = order.source != "qr" && order.status != OrderStatus.COMPLETED && order.status != OrderStatus.CANCELLED
                                    if (canRejectQrOrder || canCancelPosOrder) {
                                        OutlinedButton(
                                            onClick = { if (order.source == "qr") viewModel.rejectQrOrder(order) else viewModel.updateOrderStatus(order, OrderStatus.CANCELLED) { lane = "riwayat" } },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) { Text(if (order.source == "qr") "Tolak" else "Batalkan") }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    val context = LocalContext.current
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var orderToReturn by remember { mutableStateOf<OrderWithItems?>(null) }
    var returnReason by remember { mutableStateOf("") }
    var returnsOnly by remember { mutableStateOf(false) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }
    val canDeleteHistory = uiState.staffProfile.canAccessKey("history.delete")
    val canExportHistory = uiState.staffProfile.canAccessKey("history.export")
    val canReturnHistory = uiState.staffProfile.canAccessKey("sales_history.return")

    val filteredOrders = remember(uiState.orders, fromDate, toDate, search, returnsOnly) {
        uiState.orders.filter { o ->
            val datePart = (if (returnsOnly) o.returnedAt else o.createdAt)?.take(10).orEmpty()
            val inRange = (fromDate.isBlank() || datePart >= fromDate) && (toDate.isBlank() || datePart <= toDate)
            val matchQuery = search.isBlank() ||
                o.orderNo.toString().contains(search) ||
                (o.customerName ?: "").contains(search, ignoreCase = true)
            val matchReturnFilter = !returnsOnly || o.returnedAt != null
            inRange && matchQuery && matchReturnFilter
        }
    }
    val utcZone = remember { java.time.ZoneOffset.UTC }
    if (showFromDatePicker) {
        val initialMillis = runCatching { java.time.LocalDate.parse(fromDate).atStartOfDay(utcZone).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis())
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { fromDate = java.time.Instant.ofEpochMilli(it).atZone(utcZone).toLocalDate().toString() }; showFromDatePicker = false }) { Text("Pilih") } },
            dismissButton = { TextButton(onClick = { showFromDatePicker = false }) { Text("Batal") } }
        ) { DatePicker(state = picker) }
    }
    if (showToDatePicker) {
        val initialMillis = runCatching { java.time.LocalDate.parse(toDate).atStartOfDay(utcZone).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis())
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { toDate = java.time.Instant.ofEpochMilli(it).atZone(utcZone).toLocalDate().toString() }; showToDatePicker = false }) { Text("Pilih") } },
            dismissButton = { TextButton(onClick = { showToDatePicker = false }) { Text("Batal") } }
        ) { DatePicker(state = picker) }
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

    if (orderToReturn != null) {
        AlertDialog(
            onDismissRequest = { orderToReturn = null; returnReason = "" },
            title = { Text("Retur transaksi #${orderToReturn?.orderNo}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Refund penuh: ${RupiahFormatter.format(orderToReturn?.total ?: 0.0)}. Uang dikembalikan manual oleh kasir. Penjualan, diskon, dan pajak akan dikoreksi sesuai transaksi asli. Riwayat tetap disimpan.")
                    OutlinedTextField(value = returnReason, onValueChange = { returnReason = it }, label = { Text("Alasan retur") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val order = orderToReturn
                    if (order != null && returnReason.isNotBlank()) viewModel.returnPaidOrder(order, returnReason)
                    orderToReturn = null
                    returnReason = ""
                }, enabled = returnReason.isNotBlank()) { Text("Konfirmasi Retur") }
            },
            dismissButton = { TextButton(onClick = { orderToReturn = null; returnReason = "" }) { Text("Batal") } }
        )
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
                    if (canDeleteHistory) viewModel.deleteOrders(ids)
                    selectedIds = emptySet()
                }, enabled = canDeleteHistory) { Text("Ya, Hapus", color = MaterialTheme.colorScheme.error) }
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
                    if (canDeleteHistory) viewModel.deleteAllOrders()
                    selectedIds = emptySet()
                }, enabled = canDeleteHistory) { Text("Hapus Semua", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDeleteAll = false }) { Text("Jangan Hapus") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // Compact fixed toolbar: the order list scrolls independently below it.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(if (returnsOnly) "Daftar Return · ${filteredOrders.size} transaksi" else "History · ${filteredOrders.size} order", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (returnsOnly) "Nominal retur: ${RupiahFormatter.format(filteredOrders.sumOf { it.total })}" else "Lunas: ${RupiahFormatter.format(filteredOrders.filter { it.isPaid && it.returnedAt == null }.sumOf { it.total })}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { if (canExportHistory) csvLauncher.launch("history-transaksi-kereta-malam.csv") }, enabled = canExportHistory, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.FileDownload, contentDescription = "Ekspor CSV", tint = Color(0xFF2878F0))
            }
            IconButton(onClick = { if (canDeleteHistory) confirmDeleteAll = true }, enabled = canDeleteHistory, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Hapus semua data", tint = MaterialTheme.colorScheme.error)
            }
        }
        OutlinedCard(
            onClick = { returnsOnly = !returnsOnly },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = if (returnsOnly) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(if (returnsOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Return", fontWeight = FontWeight.SemiBold)
                    Text("${uiState.orders.count { it.returnedAt != null }} transaksi · ${RupiahFormatter.format(uiState.orders.filter { it.returnedAt != null }.sumOf { it.total })} nominal retur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (returnsOnly) "Lihat semua" else "Lihat daftar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tanggal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { showFromDatePicker = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(34.dp)) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (fromDate.isBlank()) "Dari" else fromDate, style = MaterialTheme.typography.labelMedium)
            }
            Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { showToDatePicker = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(34.dp)) {
                Text(if (toDate.isBlank()) "Sampai" else toDate, style = MaterialTheme.typography.labelMedium)
            }
            if (fromDate.isNotBlank() || toDate.isNotBlank()) TextButton(onClick = { fromDate = ""; toDate = "" }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.height(34.dp)) { Text("Reset") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Cari order") }, leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }, modifier = Modifier.weight(1f).heightIn(max = 58.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                selectedIds = if (filteredOrders.isNotEmpty() && filteredOrders.all { it.id in selectedIds }) selectedIds - filteredOrders.map { it.id }.toSet()
                else selectedIds + filteredOrders.map { it.id }.toSet()
            }, enabled = canDeleteHistory, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp), modifier = Modifier.height(40.dp)) {
                Text(if (filteredOrders.isNotEmpty() && filteredOrders.all { it.id in selectedIds }) "Batal pilih" else "Pilih semua", style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${selectedIds.size} dipilih", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { if (canDeleteHistory) confirmDeleteSelected = true }, enabled = canDeleteHistory && selectedIds.isNotEmpty(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
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
                        Checkbox(checked = order.id in selectedIds, enabled = canDeleteHistory, onCheckedChange = { checked ->
                            selectedIds = if (checked) selectedIds + order.id else selectedIds - order.id
                        })
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("#${order.orderNo} · ${order.customerName ?: "Walk-in"}", fontWeight = FontWeight.Bold)
                            Text("Waktu: ${formatTimestampWib(order.createdAt)} WIB", style = MaterialTheme.typography.bodySmall)
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
                            if (order.returnedAt != null) Text("DIRETUR · ${order.returnReason ?: "Tanpa alasan"} · ${order.returnedAt}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(RupiahFormatter.format(order.total), fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { viewModel.openReceiptDialog(order) }) { Text("Detail") }
                            if (order.status == OrderStatus.COMPLETED && order.isPaid && order.returnedAt == null && canReturnHistory) OutlinedButton(onClick = { orderToReturn = order; returnReason = "" }) { Text("Return") }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 6. KAS MODAL & PENGELUARAN — SESI KASIR
// ============================================================================
@Composable
private fun ShiftKasirComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    val context = LocalContext.current
    var sessionHistoryFrom by remember { mutableStateOf("") }
    var sessionHistoryTo by remember { mutableStateOf("") }
    var openingInput by remember { mutableStateOf("") }
    var entryAmount by remember { mutableStateOf("") }
    var entryDescription by remember { mutableStateOf("") }
    var entryType by remember { mutableStateOf("expense") }
    var entrySource by remember { mutableStateOf("cash_fund") }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    var recentEntriesExpanded by remember { mutableStateOf(true) }
    var expandedSessionDetails by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    val starts = uiState.cashFundEntries.filter { it.entryType == "set_target" }.sortedByDescending { it.createdAt ?: "" }
    val closes = uiState.cashFundEntries.filter { it.entryType == "close_session" }.sortedByDescending { it.createdAt ?: "" }
    val latestStart = starts.firstOrNull()
    val latestClose = closes.firstOrNull()
    val active = latestStart != null && (latestClose == null || (latestClose.createdAt ?: "") < (latestStart.createdAt ?: ""))
    val sessionStart = latestStart?.createdAt.orEmpty()
    val activeStaffShiftId = uiState.shifts.firstOrNull {
        it.staffUserId == uiState.staffProfile.userId && it.status.equals("open", true)
    }?.id
    val sessionAdjustments = if (active) uiState.cashFundEntries.filter { it.entryType == "replenishment" && (it.createdAt ?: "") >= sessionStart }.sumOf { it.amount } else 0.0
    val sessionExpenses = if (active) uiState.expenses.filter {
        val createdAt = it.createdAt ?: ""
        createdAt.isNotBlank() && createdAt >= sessionStart &&
            (activeStaffShiftId == null || it.shiftId == activeStaffShiftId)
    } else emptyList()
    val sessionModalExpenses = sessionExpenses.filter { it.cashSource == "cash_fund" }.sumOf { it.amount }
    val currentFund = ((latestStart?.amount ?: 0.0) + sessionAdjustments - sessionModalExpenses).coerceAtLeast(0.0)
    val sessionRecentExpenses = sessionExpenses.sortedByDescending { it.createdAt ?: "" }
    val sessionRecentFundEntries = if (active) uiState.cashFundEntries.filter {
        it.entryType == "replenishment" && (it.createdAt ?: "") >= sessionStart
    }.sortedByDescending { it.createdAt ?: "" } else emptyList()
    val hasSessionNotes = sessionRecentExpenses.isNotEmpty()
    val sessions = starts.filter { session ->
        val day = (session.createdAt ?: "").take(10)
        (sessionHistoryFrom.isBlank() || day >= sessionHistoryFrom) && (sessionHistoryTo.isBlank() || day <= sessionHistoryTo)
    }
    // Saldo sumber penjualan dihitung dari transaksi lunas aktual, bukan angka contoh.
    fun sourcePaymentGroup(rawInput: String): String {
        val raw = rawInput.lowercase().replace("-", "_").trim()
        return when {
            raw == "cash" || raw == "tunai" || raw.contains("cash") || raw.contains("tunai") -> "cash"
            // E-Wallet termasuk non-tunai bersama QRIS dan transfer bank.
            raw.contains("ewallet") || raw.contains("e_wallet") || raw.contains("e wallet") ||
                raw.contains("gopay") || raw.contains("go pay") || raw.contains("ovo") ||
                raw.contains("dana") || raw.contains("shopeepay") || raw.contains("linkaja") -> "transfer"
            raw.contains("qris") -> "qris"
            raw.contains("transfer") || raw.contains("bank") || raw.contains("bca") || raw.contains("bni") || raw.contains("bri") || raw.contains("mandiri") || raw.contains("permata") -> "transfer"
            else -> "other"
        }
    }
    // Saldo penjualan hanya untuk transaksi yang dibuat sejak sesi ini dimulai.
    // Transaksi sebelum tombol "Mulai Sesi" tidak boleh ikut saldo sesi aktif.
    val paidOrdersForBalance = uiState.orders.filter {
        it.isPaid && it.status != OrderStatus.CANCELLED && it.returnedAt == null &&
            sessionStart.isNotBlank() && it.createdAt >= sessionStart
    }
    fun salesBalance(method: String): Double {
        val gross = paidOrdersForBalance.sumOf { order ->
            if (order.payments.isNotEmpty()) order.payments.filter { sourcePaymentGroup(it.method) == method }.sumOf { it.amount }
            else {
                val raw = (order.paymentMethod?.takeIf { it.isNotBlank() }
                    ?: order.customerPaymentMethod?.takeIf { it.isNotBlank() } ?: "cash")
                if (sourcePaymentGroup(raw) == method) order.total else 0.0
            }
        }
        val sources = when (method) { "cash" -> setOf("sales_cash"); "qris" -> setOf("sales_qris"); else -> setOf("sales_transfer") }
        val spent = sessionExpenses.filter { it.cashSource in sources }.sumOf { it.amount }
        val movedToModal = uiState.cashFundEntries.filter {
            it.entryType == "replenishment" && it.source in sources && (it.createdAt ?: "") >= sessionStart
        }.sumOf { it.amount }
        return (gross - spent - movedToModal).coerceAtLeast(0.0)
    }
    val availableSalesCash = salesBalance("cash")
    val availableSalesQris = salesBalance("qris")
    val availableSalesTransfer = salesBalance("transfer")
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("Akhiri sesi kasir?") }, text = { Text("Sesi akan disimpan dengan saldo akhir kas modal ${RupiahFormatter.format(currentFund)}. Setelah itu, catatan baru harus dimulai dengan nominal modal awal.") }, confirmButton = { TextButton(onClick = { confirmClose = false; viewModel.endCashFundSession() }) { Text("Akhiri Sesi") } }, dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Batal") } })
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kas Modal & Pengeluaran", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Catatan per sesi/shift. Tidak mengubah dashboard penjualan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    if (active && latestStart != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("SESI AKTIF", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("Mulai: ${latestStart.createdAt ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(RupiahFormatter.format(currentFund), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Modal awal"); Text(RupiahFormatter.format(latestStart.amount), fontWeight = FontWeight.SemiBold) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Adjustment"); Text("+${RupiahFormatter.format(sessionAdjustments)}") }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Pengeluaran dari modal"); Text("−${RupiahFormatter.format(sessionModalExpenses)}") }
                        Button(onClick = { confirmClose = true }, modifier = Modifier.fillMaxWidth()) { Text("Akhiri Sesi & Simpan Saldo Akhir") }
                    } else {
                        Text("BELUM ADA SESI AKTIF", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = openingInput, onValueChange = { openingInput = it.filter(Char::isDigit) }, label = { Text("Nominal kas modal awal (Rp)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { val amount = openingInput.toDoubleOrNull() ?: 0.0; if (amount <= 0.0) viewModel.showNotice("Nominal kas modal wajib lebih dari Rp0.", isError = true) else { viewModel.saveCashFundTarget(amount); openingInput = "" } }, modifier = Modifier.fillMaxWidth()) { Text("Mulai Sesi & Simpan") }
                    }
                }
            }
        }
        if (active) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saldo Hasil Penjualan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Dihitung dari transaksi lunas yang tersimpan, dikurangi pengeluaran dan dana yang dipindahkan ke modal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tunai")
                        Text(RupiahFormatter.format(availableSalesCash), fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Non-tunai (QRIS, transfer bank, e-wallet)")
                        Text(RupiahFormatter.format(availableSalesQris + availableSalesTransfer), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (active && hasSessionNotes) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Catatan Pengeluaran Sesi Ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${sessionRecentExpenses.size + sessionRecentFundEntries.size} catatan terbaru", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { recentEntriesExpanded = !recentEntriesExpanded }) {
                            Text(if (recentEntriesExpanded) "Tutup ▴" else "Lihat ▾")
                        }
                    }
                    if (recentEntriesExpanded) {
                        val recentNotes = (sessionRecentExpenses.map { Triple(it.createdAt ?: "", "expense", it) } +
                            sessionRecentFundEntries.map { Triple(it.createdAt ?: "", "fund", it) })
                            .sortedByDescending { it.first }
                        recentNotes.forEach { note ->
                            HorizontalDivider()
                            if (note.second == "expense") {
                                val expense = note.third as Expense
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(expense.description, fontWeight = FontWeight.SemiBold)
                                        Text("${expense.category} • " + when (expense.cashSource) {
                                            "cash_fund" -> "Kas Modal"
                                            "sales_cash" -> "Tunai"
                                            "sales_qris" -> "QRIS"
                                            "sales_transfer" -> "Transfer Bank"
                                            else -> expense.cashSource
                                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(expense.createdAt ?: expense.expenseDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("−${RupiahFormatter.format(expense.amount)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                val fund = note.third as CashFundEntry
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(fund.description, fontWeight = FontWeight.SemiBold)
                                        Text("Adjustment • " + when (fund.source) {
                                            "owner_funds" -> "Dari Owner"
                                            "sales_cash" -> "Dari Tunai"
                                            "sales_qris" -> "Dari QRIS"
                                            "sales_transfer" -> "Dari Transfer Bank"
                                            else -> fund.source
                                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(fund.createdAt ?: "Waktu tidak tersedia", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("+${RupiahFormatter.format(fund.amount)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (active) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Catat Transaksi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box {
                        OutlinedButton(onClick = { typeMenuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(if (entryType == "expense") "Jenis: Pengeluaran" else "Jenis: Adjustment / Pengembalian Modal") }
                        DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("Pengeluaran") }, onClick = {
                                entryType = "expense"
                                if (entrySource !in listOf("cash_fund", "sales_cash", "sales_qris", "sales_transfer")) entrySource = "cash_fund"
                                typeMenuOpen = false
                            })
                            DropdownMenuItem(text = { Text("Adjustment / Pengembalian Modal") }, onClick = {
                                entryType = "adjustment"
                                if (entrySource !in listOf("owner_funds", "sales_cash", "sales_qris", "sales_transfer", "other")) entrySource = "owner_funds"
                                typeMenuOpen = false
                            })
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { sourceMenuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Sumber: " + when (entrySource) { "cash_fund" -> "Kas Modal"; "sales_cash" -> "Tunai"; "sales_qris", "sales_transfer" -> "Non-tunai"; "owner_funds" -> "Dari Owner — Top Up Modal"; else -> "Kas Modal" }) }
                        DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                            val options = if (entryType == "expense") listOf("cash_fund" to "Kas Modal", "sales_cash" to "Tunai (hasil penjualan cash)", "sales_transfer" to "Non-tunai (QRIS, transfer bank, e-wallet)") else listOf("owner_funds" to "Dari Owner — Top Up Modal", "sales_cash" to "Dari hasil penjualan tunai", "sales_transfer" to "Dari hasil penjualan non-tunai", "other" to "Sumber lain")
                            options.forEach { option -> DropdownMenuItem(text = { Text(option.second) }, onClick = { entrySource = option.first; sourceMenuOpen = false }) }
                        }
                    }
                    OutlinedTextField(value = entryAmount, onValueChange = { entryAmount = it.filter(Char::isDigit) }, label = { Text("Nominal (Rp)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = entryDescription, onValueChange = { entryDescription = it }, label = { Text(if (entryType == "expense") "Pengeluaran untuk apa?" else "Keterangan adjustment") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        val amount = entryAmount.toDoubleOrNull() ?: 0.0
                        if (amount <= 0.0 || entryDescription.isBlank()) viewModel.showNotice("Nominal dan keterangan wajib diisi.", isError = true)
                        else if (entryType == "expense") {
                            val method = when (entrySource) { "sales_qris", "sales_transfer" -> "transfer"; else -> "cash" }
                            viewModel.addExpense(amount, "Operasional", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta")).toString(), method, entryDescription, entrySource)
                            entryAmount = ""; entryDescription = ""
                        } else {
                            viewModel.replenishCashFund(amount, entrySource, entryDescription)
                            entryAmount = ""; entryDescription = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Simpan Catatan") }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("History Sesi Kasir", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { val d = runCatching { java.time.LocalDate.parse(sessionHistoryFrom) }.getOrDefault(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta"))); android.app.DatePickerDialog(context, { _, y, m, day -> sessionHistoryFrom = java.time.LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show() }, modifier = Modifier.weight(1f)) { Text(if (sessionHistoryFrom.isBlank()) "Dari tanggal ▾" else "Dari: $sessionHistoryFrom") }
                    OutlinedButton(onClick = { val d = runCatching { java.time.LocalDate.parse(sessionHistoryTo) }.getOrDefault(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta"))); android.app.DatePickerDialog(context, { _, y, m, day -> sessionHistoryTo = java.time.LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show() }, modifier = Modifier.weight(1f)) { Text(if (sessionHistoryTo.isBlank()) "Sampai tanggal ▾" else "Sampai: $sessionHistoryTo") }
                }
                if (sessionHistoryFrom.isNotBlank() || sessionHistoryTo.isNotBlank()) TextButton(onClick = { sessionHistoryFrom = ""; sessionHistoryTo = "" }) { Text("Hapus filter tanggal") }
            }
        }
        if (sessions.isEmpty()) item { Text("Tidak ada sesi pada rentang tanggal ini.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(sessions, key = { "session-" + it.id }) { session ->
            val startAt = session.createdAt ?: ""
            val nextStart = starts.filter { (it.createdAt ?: "") > startAt }.minByOrNull { it.createdAt ?: "" }?.createdAt
            val close = closes.firstOrNull { (it.createdAt ?: "") >= startAt && (nextStart == null || (it.createdAt ?: "") < nextStart) }
            val endAt = close?.createdAt ?: nextStart
            val adjustments = uiState.cashFundEntries.filter { it.entryType == "replenishment" && (it.createdAt ?: "") >= startAt && (endAt == null || (it.createdAt ?: "") <= endAt) }.sumOf { it.amount }
            val modalExpenses = uiState.expenses.filter { it.cashSource == "cash_fund" && (it.createdAt ?: "") >= startAt && (endAt == null || (it.createdAt ?: "") <= endAt) }.sumOf { it.amount }
            val sessionExpensesForHistory = uiState.expenses.filter { (it.createdAt ?: "") >= startAt && (endAt == null || (it.createdAt ?: "") <= endAt) }.sortedByDescending { it.createdAt ?: "" }
            val sessionFundEntriesForHistory = uiState.cashFundEntries.filter { it.entryType == "replenishment" && (it.createdAt ?: "") >= startAt && (endAt == null || (it.createdAt ?: "") <= endAt) }.sortedByDescending { it.createdAt ?: "" }
            val allExpenses = sessionExpensesForHistory.sumOf { it.amount }
            val closing = close?.amount ?: (session.amount + adjustments - modalExpenses).coerceAtLeast(0.0)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(startAt.ifBlank { "Waktu tidak tersedia" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (close != null) "Selesai" else if (session.id == latestStart?.id && active) "Aktif" else "Belum ditutup", style = MaterialTheme.typography.labelMedium, color = if (close != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Modal awal"); Text(RupiahFormatter.format(session.amount), fontWeight = FontWeight.Medium) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total pengeluaran"); Text(RupiahFormatter.format(allExpenses), fontWeight = FontWeight.Medium) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Adjustment modal"); Text(RupiahFormatter.format(adjustments), fontWeight = FontWeight.Medium) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Saldo akhir"); Text(RupiahFormatter.format(closing), fontWeight = FontWeight.Bold) }
                    if (close != null) Text("Diakhiri: ${close.createdAt ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { expandedSessionDetails = if (expandedSessionDetails == session.id) null else session.id }) { Text(if (expandedSessionDetails == session.id) "Tutup detail ▴" else "Detail sesi ▾") }
                    if (expandedSessionDetails == session.id) {
                        HorizontalDivider()
                        Text("Catatan pengeluaran", fontWeight = FontWeight.SemiBold)
                        if (sessionExpensesForHistory.isEmpty()) Text("Tidak ada pengeluaran pada sesi ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        sessionExpensesForHistory.forEach { expense ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(expense.description, fontWeight = FontWeight.Medium)
                                    Text("${expense.category} • " + when (expense.cashSource) { "cash_fund" -> "Kas Modal"; "sales_cash" -> "Tunai"; "sales_qris", "sales_transfer" -> "Non-tunai"; else -> expense.cashSource }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(expense.createdAt ?: expense.expenseDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("−${RupiahFormatter.format(expense.amount)}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (sessionFundEntriesForHistory.isNotEmpty()) {
                            HorizontalDivider(); Text("Catatan adjustment", fontWeight = FontWeight.SemiBold)
                            sessionFundEntriesForHistory.forEach { fund ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f)) { Text(fund.description, fontWeight = FontWeight.Medium); Text("Adjustment • " + when (fund.source) { "owner_funds" -> "Dari Owner"; "sales_cash" -> "Dari Tunai"; "sales_qris", "sales_transfer" -> "Dari Non-tunai"; else -> fund.source }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Text("+${RupiahFormatter.format(fund.amount)}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
// 7. AUDIT SHIFT SCREEN
// ============================================================================
@Composable
private fun AuditShiftComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var filter by remember { mutableStateOf("SEMUA") }
    var search by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var selectedShift by remember { mutableStateOf<StaffShift?>(null) }
    val context = LocalContext.current

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
    val exportAuditLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
                writer.appendLine(listOf("shift_id","kasir","status","dibuka","ditutup","modal_awal","ekspektasi_kas","kas_akhir","selisih","terverifikasi","catatan").joinToString(","))
                filtered.forEach { item ->
                    val variance = (item.closingCash ?: 0.0) - (item.expectedCash ?: 0.0)
                    writer.appendLine(listOf(item.id,item.staffName,item.status,item.openedAt,item.closedAt.orEmpty(),item.openingCash.toString(),(item.expectedCash ?: 0.0).toString(),item.closingCash?.toString().orEmpty(),variance.toString(),item.auditVerified.toString(),item.notes.orEmpty()).joinToString(",") { csv(it) })
                }
            }
        }.onFailure { android.widget.Toast.makeText(context, "Export gagal: ${it.message ?: "kesalahan file"}", android.widget.Toast.LENGTH_LONG).show() }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Hasil: ${filtered.size} shift", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { exportAuditLauncher.launch("audit-shift-${java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta"))}.csv") }, enabled = filtered.isNotEmpty() && uiState.staffProfile.canAccessKey("audit.export")) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export CSV")
            }
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
@OptIn(ExperimentalMaterial3Api::class)
private fun ActivityLogComposeScreen(uiState: PosUiState) {
    val context = LocalContext.current
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("SEMUA") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var datePickerTarget by remember { mutableStateOf<String?>(null) }

    val selectedStaff = uiState.staffList.firstOrNull { it.userId == selectedAccountId }
    val selectedLogs = remember(uiState.auditLogs, selectedAccountId) {
        uiState.auditLogs.filter { it.actorUserId == selectedAccountId }
    }
    val filteredLogs = remember(selectedLogs, search, fromDate, toDate, category) {
        selectedLogs.filter { log ->
            val instant = runCatching { java.time.Instant.parse(log.createdAt) }.getOrNull()
                ?: runCatching { java.time.OffsetDateTime.parse(log.createdAt).toInstant() }.getOrNull()
            val date = instant?.atZone(java.time.ZoneId.of("Asia/Jakarta"))?.toLocalDate()?.toString()
                ?: log.createdAt.take(10)
            val dateMatch = (fromDate.isBlank() || date >= fromDate) && (toDate.isBlank() || date <= toDate)
            val actionText = "${log.action} ${log.entityType} ${log.detailsText}".lowercase()
            val categoryMatch = when (category) {
                "LOGIN" -> listOf("login", "logout", "auth", "masuk", "keluar").any { it in actionText }
                "TRANSAKSI" -> listOf("order", "transaction", "transaksi", "payment", "pembayaran", "retur").any { it in actionText }
                "PRODUK" -> listOf("product", "produk", "category", "kategori").any { it in actionText }
                "STOK" -> listOf("inventory", "stock", "stok").any { it in actionText }
                "PEGAWAI" -> listOf("staff", "pegawai", "permission", "hak akses", "akun").any { it in actionText }
                "SHIFT" -> listOf("shift", "cash", "kas", "pengeluaran").any { it in actionText }
                "PENGATURAN" -> listOf("setting", "pengaturan", "printer", "outlet").any { it in actionText }
                else -> true
            }
            dateMatch && categoryMatch && (search.isBlank() ||
                log.action.contains(search, true) || log.entityType.contains(search, true) ||
                log.detailsText.contains(search, true))
        }.sortedByDescending { it.createdAt }
    }
    val exportLogsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
                writer.appendLine("Waktu WIB,Aktivitas,Akun,Tipe,Detail")
                filteredLogs.forEach { log ->
                    val actor = uiState.staffList.firstOrNull { it.userId == log.actorUserId }?.fullName ?: log.actorName
                    writer.appendLine(listOf(formatTimestampWib(log.createdAt), readableAuditAction(log.action), actor, readableAuditEntity(log.entityType), readableAuditDetails(log)).joinToString(",") { csv(it) })
                }
            }
            android.widget.Toast.makeText(context, "Ekspor aktivitas berhasil.", android.widget.Toast.LENGTH_SHORT).show()
        }.onFailure { android.widget.Toast.makeText(context, "Ekspor gagal. Coba lagi.", android.widget.Toast.LENGTH_LONG).show() }
    }
    val exportPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) runCatching {
            val document = android.graphics.pdf.PdfDocument()
            val bodyPaint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; textSize = 10f; isAntiAlias = true }
            val titlePaint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; textSize = 16f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) }
            val lines = buildList {
                add("LAPORAN AKTIVITAS AKUN")
                add("Nama: ${selectedStaff?.fullName.orEmpty()}")
                add("Email: ${selectedStaff?.email.orEmpty()}")
                add("Role: ${selectedStaff?.role?.labelId.orEmpty()}")
                add("Periode: ${fromDate.ifBlank { "Semua tanggal" }} s.d. ${toDate.ifBlank { "Sekarang" }}")
                add("Jenis aktivitas: $category")
                add("Jumlah aktivitas: ${filteredLogs.size}")
                add("")
                filteredLogs.forEachIndexed { index, log ->
                    val actor = uiState.staffList.firstOrNull { it.userId == log.actorUserId }?.fullName ?: log.actorName
                    add("${index + 1}. ${readableAuditAction(log.action)}")
                    add("   ${formatTimestampWib(log.createdAt)} WIB · ${readableAuditEntity(log.entityType)} · $actor")
                    val detail = readableAuditDetails(log)
                    if (detail.isNotBlank()) detail.chunked(88).forEach { add("   $it") }
                    add("")
                }
            }
            var pageNumber = 1
            var page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            var y = 48f
            lines.forEachIndexed { index, line ->
                if (y > 800f) {
                    document.finishPage(page)
                    pageNumber += 1
                    page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                    y = 48f
                }
                page.canvas.drawText(line.take(100), 40f, y, if (index == 0) titlePaint else bodyPaint)
                y += if (index == 0) 30f else 17f
            }
            document.finishPage(page)
            context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                ?: throw IllegalStateException("File tujuan tidak dapat dibuka.")
            document.close()
            android.widget.Toast.makeText(context, "PDF aktivitas berhasil diekspor.", android.widget.Toast.LENGTH_SHORT).show()
        }.onFailure { android.widget.Toast.makeText(context, "Ekspor PDF gagal. Coba lagi.", android.widget.Toast.LENGTH_LONG).show() }
    }

        if (datePickerTarget != null) {
            val initialDate = if (datePickerTarget == "from") fromDate else toDate
            val initialMillis = runCatching {
                java.time.LocalDate.parse(initialDate).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
            DatePickerDialog(
                onDismissRequest = { datePickerTarget = null },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val chosen = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                            if (datePickerTarget == "from") fromDate = chosen else toDate = chosen
                        }
                        datePickerTarget = null
                    }) { Text("Pilih") }
                },
                dismissButton = { TextButton(onClick = { datePickerTarget = null }) { Text("Batal") } }
            ) { DatePicker(state = pickerState) }
        }

    if (selectedStaff == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Log Aktivitas per Akun", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Setiap akun tetap tampil meskipun belum memiliki riwayat aktivitas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Cari nama atau email akun") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Total akun: ${uiState.staffList.size}", style = MaterialTheme.typography.labelMedium)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.staffList.filter { search.isBlank() || it.fullName.contains(search, true) || it.email.contains(search, true) }, key = { it.userId }) { staff ->
                    val count = uiState.auditLogs.count { it.actorUserId == staff.userId }
                    OutlinedCard(Modifier.fillMaxWidth().clickable { selectedAccountId = staff.userId }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(staff.fullName.trim().firstOrNull()?.uppercase() ?: "?", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(staff.fullName, fontWeight = FontWeight.Bold)
                                Text("${staff.email.ifBlank { staff.role.labelId }} · ${if (staff.isActive) "Aktif" else "Nonaktif"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$count aktivitas tercatat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Lihat aktivitas akun")
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectedAccountId = null; fromDate = ""; toDate = ""; category = "SEMUA"; search = "" }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Icon(Icons.Outlined.History, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Akun")
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(selectedStaff.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${selectedStaff.email.ifBlank { selectedStaff.role.labelId }} · ${selectedStaff.role.labelId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Cari aktivitas atau detail perubahan") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { datePickerTarget = "from" }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(if (fromDate.isBlank()) "Dari tanggal" else java.time.LocalDate.parse(fromDate).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale("id", "ID"))), maxLines = 1)
                }
                OutlinedButton(onClick = { datePickerTarget = "to" }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(if (toDate.isBlank()) "Sampai tanggal" else java.time.LocalDate.parse(toDate).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale("id", "ID"))), maxLines = 1)
                }
            }
            ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                OutlinedTextField(
                    value = mapOf("SEMUA" to "Semua aktivitas", "LOGIN" to "Login & keamanan", "TRANSAKSI" to "Transaksi & pembayaran", "PRODUK" to "Produk", "STOK" to "Stok", "PEGAWAI" to "Pegawai & hak akses", "SHIFT" to "Shift & kas", "PENGATURAN" to "Pengaturan")[category] ?: "Semua aktivitas",
                    onValueChange = {}, readOnly = true, label = { Text("Jenis aktivitas") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    listOf("SEMUA" to "Semua aktivitas", "LOGIN" to "Login & keamanan", "TRANSAKSI" to "Transaksi & pembayaran", "PRODUK" to "Produk", "STOK" to "Stok", "PEGAWAI" to "Pegawai & hak akses", "SHIFT" to "Shift & kas", "PENGATURAN" to "Pengaturan").forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { category = key; categoryMenuExpanded = false })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${filteredLogs.size} aktivitas", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { fromDate = ""; toDate = ""; search = ""; category = "SEMUA" }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Reset") }
                OutlinedButton(onClick = { exportLogsLauncher.launch("aktivitas-${selectedStaff.fullName.lowercase().replace(Regex("[^a-z0-9]+"), "-")}.csv") }, enabled = filteredLogs.isNotEmpty() && uiState.staffProfile.canAccessKey("activity_log.export"), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Text("CSV")
                }
                OutlinedButton(onClick = { exportPdfLauncher.launch("aktivitas-${selectedStaff.fullName.lowercase().replace(Regex("[^a-z0-9]+"), "-")}.pdf") }, enabled = filteredLogs.isNotEmpty() && uiState.staffProfile.canAccessKey("activity_log.export"), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("PDF")
                }
            }
            if (filteredLogs.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Belum ada aktivitas untuk akun ini pada filter yang dipilih.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredLogs, key = { it.id }) { log ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    Text(readableAuditAction(log.action), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    Text(formatTimestampWib(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(readableAuditEntity(log.entityType), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                val detail = readableAuditDetails(log)
                                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun readableAuditAction(raw: String): String {
    val action = raw.lowercase()
    return when {
        action.contains("staff_account_created") -> "Membuat akun pegawai"
        action.contains("staff_permissions_changed") -> "Mengubah hak akses pegawai"
        action.contains("staff_role_changed") -> "Mengubah jabatan pegawai"
        action.contains("staff_status_changed") -> "Mengubah status akun pegawai"
        action.contains("login") || action.contains("sign in") -> "Login ke aplikasi"
        action.contains("logout") || action.contains("sign out") -> "Keluar dari aplikasi"
        action.startsWith("products.insert") || action.startsWith("product.insert") -> "Menambahkan produk"
        action.startsWith("products.update") || action.startsWith("product.update") -> "Mengubah produk"
        action.startsWith("products.delete") || action.startsWith("product.delete") -> "Menghapus produk"
        action.startsWith("orders.insert") || action.startsWith("order.insert") -> "Membuat transaksi"
        action.startsWith("orders.update") || action.startsWith("order.update") -> "Memperbarui transaksi"
        action.contains("return") || action.contains("refund") -> "Melakukan retur transaksi"
        action.contains("payment") -> "Memproses pembayaran"
        action.contains("shift") -> "Aktivitas shift"
        action.contains("insert") || action.contains("create") || action.contains("add") -> "Menambahkan data"
        action.contains("update") || action.contains("edit") -> "Mengubah data"
        action.contains("delete") || action.contains("remove") -> "Menghapus data"
        else -> raw.replace('_', ' ').replace('.', ' ').replaceFirstChar { it.uppercase() }
    }
}

private fun readableAuditEntity(raw: String): String = when (raw.lowercase()) {
    "staff_profiles", "staff", "pegawai" -> "Akun pegawai"
    "products", "product" -> "Produk"
    "orders", "order", "order_items" -> "Transaksi / pesanan"
    "payments" -> "Pembayaran"
    "staff_shifts", "shift" -> "Shift kasir"
    "inventory_movements", "inventory" -> "Mutasi stok"
    "outlet_settings", "settings" -> "Pengaturan outlet"
    "auth" -> "Keamanan akun"
    else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun readableAuditDetails(log: AuditLog): String {
    if (log.detailsText.isNotBlank()) {
        return log.detailsText.replace(Regex("[{}\\\"]"), " ").replace(',', ' ').replace(Regex("\\s+"), " ").trim()
    }
    val details = log.details ?: return ""
    val labels = mapOf(
        "old" to "Sebelumnya", "new" to "Menjadi", "before" to "Sebelumnya", "after" to "Menjadi",
        "name" to "Nama", "price" to "Harga", "stock" to "Stok", "role" to "Jabatan",
        "is_active" to "Status", "full_name" to "Nama lengkap", "permissions" to "Hak akses",
        "amount" to "Nominal", "reason" to "Keterangan", "description" to "Deskripsi",
        "email" to "Email", "service_fee_mode" to "Mode biaya layanan", "service_fixed_amount" to "Biaya layanan tetap",
        "opening_hours" to "Jam operasional", "instagram" to "Instagram", "status" to "Status"
    )
    val parts = mutableListOf<String>()
    details.entries.forEach { (key, value) ->
        when (key) {
            "changes" -> {
                val changes = runCatching { (kotlinx.serialization.json.Json.parseToJsonElement(value.toString()) as? kotlinx.serialization.json.JsonObject) }.getOrNull()
                changes?.entries?.forEach { (field, change) ->
                    val pair = runCatching { (kotlinx.serialization.json.Json.parseToJsonElement(change.toString()) as? kotlinx.serialization.json.JsonObject) }.getOrNull()
                    val before = pair?.get("before")?.toString()?.trim('"')
                    val after = pair?.get("after")?.toString()?.trim('"')
                    val label = labels[field] ?: field.replace('_', ' ').replaceFirstChar { it.uppercase() }
                    if (before != null || after != null) parts += "$label: ${before ?: "—"} → ${after ?: "—"}"
                }
            }
            "before", "after" -> Unit
            else -> parts += "${labels[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }}: ${value.toString().trim('"')}"
        }
    }
    return parts.joinToString(" · ")
}
