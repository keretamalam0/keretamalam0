package id.keretamalam.cafe.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.keretamalam.cafe.data.model.PermissionKey
import id.keretamalam.cafe.ui.screens.LoginComposeScreen
import id.keretamalam.cafe.ui.screens.RenderModuleScreen
import id.keretamalam.cafe.viewmodel.PosUiState
import id.keretamalam.cafe.viewmodel.PosViewModel
import kotlinx.coroutines.launch

private fun moduleIconTint(route: PosModuleRoute): androidx.compose.ui.graphics.Color = when (route) {
    PosModuleRoute.DASHBOARD -> androidx.compose.ui.graphics.Color(0xFF6F91C5) // soft blue
    PosModuleRoute.AI_ASSISTANT -> androidx.compose.ui.graphics.Color(0xFF9A83C7) // lavender
    PosModuleRoute.KASIR -> androidx.compose.ui.graphics.Color(0xFF5F9E91) // teal green
    PosModuleRoute.PESANAN -> androidx.compose.ui.graphics.Color(0xFF6D9BC7) // blue
    PosModuleRoute.HISTORY -> androidx.compose.ui.graphics.Color(0xFF8B91A7) // slate
    PosModuleRoute.SHIFT_KASIR -> androidx.compose.ui.graphics.Color(0xFFC29A5D) // muted amber
    PosModuleRoute.AUDIT_SHIFT -> androidx.compose.ui.graphics.Color(0xFF7D9B7B) // sage
    PosModuleRoute.ACTIVITY_LOG -> androidx.compose.ui.graphics.Color(0xFF8297A8) // steel blue
    PosModuleRoute.PRODUK -> androidx.compose.ui.graphics.Color(0xFF6EAA83) // green
    PosModuleRoute.MEJA -> androidx.compose.ui.graphics.Color(0xFF9A83C7) // lavender
    PosModuleRoute.INVENTORY -> androidx.compose.ui.graphics.Color(0xFF6DA99A) // sea green
    PosModuleRoute.PROMOSI -> androidx.compose.ui.graphics.Color(0xFFD39A68) // muted orange
    PosModuleRoute.KEUANGAN -> androidx.compose.ui.graphics.Color(0xFF6D9EBA) // blue teal
    PosModuleRoute.LAPORAN -> androidx.compose.ui.graphics.Color(0xFF6F91C5) // blue
    PosModuleRoute.PEGAWAI -> androidx.compose.ui.graphics.Color(0xFFB784A3) // dusty rose
    PosModuleRoute.PENGATURAN -> androidx.compose.ui.graphics.Color(0xFF8993A4) // slate
    PosModuleRoute.PRINTER -> androidx.compose.ui.graphics.Color(0xFF8A9C8C) // sage
}

enum class PosModuleRoute(
    val route: String,
    val titleId: String,
    val groupLabel: String,
    val permission: PermissionKey,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", "Dashboard", "UTAMA", PermissionKey.DASHBOARD, Icons.Outlined.Dashboard),
    AI_ASSISTANT("ai", "AI Assistant", "AI", PermissionKey.REPORTS, Icons.Outlined.SmartToy),
    KASIR("kasir", "Kasir", "TRANSAKSI", PermissionKey.ORDERS, Icons.Outlined.PointOfSale),
    PESANAN("pesanan", "Pesanan", "TRANSAKSI", PermissionKey.ORDERS, Icons.Outlined.ReceiptLong),
    HISTORY("history", "History", "TRANSAKSI", PermissionKey.ORDERS, Icons.Outlined.History),
    SHIFT_KASIR("shift-kasir", "Opening & Closing", "TRANSAKSI", PermissionKey.ORDERS, Icons.Outlined.Payments),
    AUDIT_SHIFT("audit-shift", "Audit Shift", "TRANSAKSI", PermissionKey.ORDERS, Icons.Outlined.VerifiedUser),
    ACTIVITY_LOG("activity-log", "Log Aktivitas", "TRANSAKSI", PermissionKey.SETTINGS, Icons.Outlined.ManageHistory),
    PRODUK("produk", "Produk & Stok", "PRODUK & OUTLET", PermissionKey.PRODUCTS, Icons.Outlined.Inventory2),
    MEJA("meja", "Meja & QR Menu", "PRODUK & OUTLET", PermissionKey.TABLES, Icons.Outlined.QrCode2),
    INVENTORY("inventory", "Inventory", "PRODUK & OUTLET", PermissionKey.INVENTORY, Icons.Outlined.Warehouse),
    PROMOSI("promosi", "Promosi & Diskon", "PEMASARAN", PermissionKey.PROMOTIONS, Icons.Outlined.Percent),
    KEUANGAN("keuangan", "Pengeluaran & Laba Rugi", "MANAJEMEN", PermissionKey.REPORTS, Icons.Outlined.AccountBalanceWallet),
    LAPORAN("laporan", "Laporan Penjualan", "MANAJEMEN", PermissionKey.REPORTS, Icons.Outlined.BarChart),
    PEGAWAI("pegawai", "Pegawai & Shift", "MANAJEMEN", PermissionKey.SETTINGS, Icons.Outlined.Badge),
    PENGATURAN("pengaturan", "Pengaturan Outlet", "MANAJEMEN", PermissionKey.SETTINGS, Icons.Outlined.Settings),
    PRINTER("printer", "Printer", "MANAJEMEN", PermissionKey.SETTINGS, Icons.Outlined.Print),
}


private fun localizedRouteTitle(text: String, language: String): String {
    if (language == "id") return text
    val en = mapOf("Dashboard" to "Dashboard", "AI Assistant" to "AI Assistant", "Kasir" to "Cashier", "Pesanan" to "Orders", "History" to "History", "Opening & Closing" to "Opening & Closing", "Audit Shift" to "Shift Audit", "Log Aktivitas" to "Activity Log", "Produk & Stok" to "Products & Stock", "Meja & QR Menu" to "Tables & QR Menu", "Inventory" to "Inventory", "Promosi & Diskon" to "Promotions & Discounts", "Pengeluaran & Laba Rugi" to "Expenses & Profit/Loss", "Laporan Penjualan" to "Sales Reports", "Pegawai & Shift" to "Staff & Shifts", "Pengaturan Outlet" to "Outlet Settings", "Printer" to "Printer")
    val zh = mapOf("Dashboard" to "仪表盘", "AI Assistant" to "AI 助手", "Kasir" to "收银台", "Pesanan" to "订单", "History" to "历史记录", "Opening & Closing" to "交接班", "Audit Shift" to "班次审计", "Log Aktivitas" to "活动日志", "Produk & Stok" to "商品与库存", "Meja & QR Menu" to "桌台与二维码菜单", "Inventory" to "库存管理", "Promosi & Diskon" to "促销与折扣", "Pengeluaran & Laba Rugi" to "支出与损益", "Laporan Penjualan" to "销售报表", "Pegawai & Shift" to "员工与班次", "Pengaturan Outlet" to "门店设置", "Printer" to "打印机")
    val th = mapOf("Dashboard" to "แดชบอร์ด", "AI Assistant" to "ผู้ช่วย AI", "Kasir" to "แคชเชียร์", "Pesanan" to "คำสั่งซื้อ", "History" to "ประวัติ", "Opening & Closing" to "เปิดและปิดกะ", "Audit Shift" to "ตรวจสอบกะ", "Log Aktivitas" to "บันทึกกิจกรรม", "Produk & Stok" to "สินค้าและสต็อก", "Meja & QR Menu" to "โต๊ะและเมนู QR", "Inventory" to "คลังสินค้า", "Promosi & Diskon" to "โปรโมชันและส่วนลด", "Pengeluaran & Laba Rugi" to "ค่าใช้จ่ายและกำไรขาดทุน", "Laporan Penjualan" to "รายงานยอดขาย", "Pegawai & Shift" to "พนักงานและกะ", "Pengaturan Outlet" to "ตั้งค่าร้าน", "Printer" to "เครื่องพิมพ์")
    val vi = mapOf("Dashboard" to "Tổng quan", "AI Assistant" to "Trợ lý AI", "Kasir" to "Thu ngân", "Pesanan" to "Đơn hàng", "History" to "Lịch sử", "Opening & Closing" to "Mở và đóng ca", "Audit Shift" to "Kiểm tra ca", "Log Aktivitas" to "Nhật ký hoạt động", "Produk & Stok" to "Sản phẩm & tồn kho", "Meja & QR Menu" to "Bàn & menu QR", "Inventory" to "Kho hàng", "Promosi & Diskon" to "Khuyến mãi & giảm giá", "Pengeluaran & Laba Rugi" to "Chi phí & lãi lỗ", "Laporan Penjualan" to "Báo cáo bán hàng", "Pegawai & Shift" to "Nhân viên & ca", "Pengaturan Outlet" to "Cài đặt cửa hàng", "Printer" to "Máy in")
    return when (language) { "en" -> en[text] ?: text; "zh" -> zh[text] ?: text; "th" -> th[text] ?: text; "vi" -> vi[text] ?: text; else -> text }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CafeFlowAppShell(
    viewModel: PosViewModel,
    uiState: PosUiState
) {
    var activeModule by remember { mutableStateOf(PosModuleRoute.DASHBOARD) }
    var reportMenuExpanded by remember { mutableStateOf(false) }
    var selectedReportPage by remember { mutableStateOf("Ringkasan Laporan") }
    val reportPages = listOf("Ringkasan Laporan", "Penjualan Harian", "Penjualan Produk", "Penjualan per Jam", "Laporan Pembayaran", "Laporan Kanal", "Detail Pesanan")
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Landscape phone has a wide width but short height: keep the collapsible drawer
        // and bottom navigation instead of forcing a permanent sidebar.
        val isLandscapePhone = maxWidth > maxHeight && maxHeight < 600.dp
        val isTabletOrWide = maxWidth >= 840.dp && !isLandscapePhone

        // Jika belum login, tampilkan Halaman Login Native penuh (adaptif HP & Tablet)
        if (!uiState.isLoggedIn) {
            LoginComposeScreen(
                viewModel = viewModel,
                uiState = uiState,
                isTabletOrWide = isTabletOrWide
            )
            return@BoxWithConstraints
        }

        val drawerContent: @Composable () -> Unit = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFF080A0D) else androidx.compose.ui.graphics.Color(0xFFFFFFFF)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.outletSettings.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "POS & QR Ordering · Android Native",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val grouped = PosModuleRoute.entries.groupBy { it.groupLabel }
                        grouped.forEach { (group, items) ->
                            val visible = items.filter { uiState.staffProfile.canAccess(it.permission) }
                            if (visible.isNotEmpty()) {
                                Text(group, style = MaterialTheme.typography.labelMedium, color = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFCBD5E1) else androidx.compose.ui.graphics.Color(0xFF64748B), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp))
                                visible.forEach { item ->
                                    if (item == PosModuleRoute.LAPORAN) {
                                        NavigationDrawerItem(
                                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFF203A56) else androidx.compose.ui.graphics.Color(0xFFE8EEF5), unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent),
                                            label = { Text(localizedRouteTitle(item.titleId, uiState.outletSettings.appLanguage), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            icon = { Icon(item.icon, contentDescription = item.titleId, tint = moduleIconTint(item)) },
                                            badge = { Icon(if (reportMenuExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = "Tampilkan submenu laporan") },
                                            selected = activeModule == item, onClick = { reportMenuExpanded = !reportMenuExpanded }, shape = RoundedCornerShape(10.dp)
                                        )
                                        if (reportMenuExpanded) reportPages.forEach { page ->
                                            NavigationDrawerItem(
                                                modifier = Modifier.padding(start = 20.dp),
                                                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFF203A56) else androidx.compose.ui.graphics.Color(0xFFE8EEF5), unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent),
                                                label = { Text(page, fontWeight = if (activeModule == PosModuleRoute.LAPORAN && selectedReportPage == page) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                icon = { Text("•", color = if (activeModule == PosModuleRoute.LAPORAN && selectedReportPage == page) androidx.compose.ui.graphics.Color(0xFF6F91C5) else MaterialTheme.colorScheme.onSurfaceVariant) },
                                                selected = activeModule == PosModuleRoute.LAPORAN && selectedReportPage == page,
                                                onClick = { selectedReportPage = page; activeModule = PosModuleRoute.LAPORAN; if (!isTabletOrWide) scope.launch { drawerState.close() } },
                                                shape = RoundedCornerShape(9.dp)
                                            )
                                        }
                                    } else {
                                        val badgeText = when (item) {
                                            PosModuleRoute.KASIR -> uiState.cart.sumOf { it.quantity }.takeIf { it > 0 }?.toString()
                                            PosModuleRoute.PESANAN -> uiState.activeOrdersCount.takeIf { it > 0 }?.toString()
                                            PosModuleRoute.PRODUK -> uiState.lowStockProducts.size.takeIf { it > 0 }?.toString()
                                            else -> null
                                        }
                                        NavigationDrawerItem(
                                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFF203A56) else androidx.compose.ui.graphics.Color(0xFFE8EEF5), unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent),
                                            label = { Text(localizedRouteTitle(item.titleId, uiState.outletSettings.appLanguage), fontWeight = if (activeModule == item) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            icon = { Icon(item.icon, contentDescription = localizedRouteTitle(item.titleId, uiState.outletSettings.appLanguage), tint = moduleIconTint(item)) },
                                            badge = badgeText?.let { b -> { Badge { Text(b) } } },
                                            selected = activeModule == item,
                                            onClick = { activeModule = item; if (!isTabletOrWide) scope.launch { drawerState.close() } },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.staffProfile.fullName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFFFFFFF) else androidx.compose.ui.graphics.Color(0xFF111827),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Role: ${uiState.staffProfile.role.labelId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFE2E8F0) else androidx.compose.ui.graphics.Color(0xFF475569),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(onClick = { viewModel.logoutSession() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = "Keluar / Ganti Akun"
                            )
                        }
                    }
                }
            }
        }

        val mainScaffold: @Composable () -> Unit = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        modifier = Modifier.height(if (isLandscapePhone) 56.dp else 60.dp),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFF080A0D) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                            titleContentColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFF7FAFF) else androidx.compose.ui.graphics.Color(0xFF111827),
                            navigationIconContentColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFF7FAFF) else androidx.compose.ui.graphics.Color(0xFF111827),
                            actionIconContentColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFF7FAFF) else androidx.compose.ui.graphics.Color(0xFF111827)
                        ),
                        title = {
                            Text(
                                text = localizedRouteTitle(activeModule.titleId, uiState.outletSettings.appLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Buka Navigasi")
                            }
                        },
                        actions = {
                            // Kontrol skala tetap tersedia di landscape, dengan ukuran lebih ringkas.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    modifier = Modifier.size(if (isLandscapePhone) 30.dp else 36.dp),
                                    onClick = { viewModel.setUiScale(uiState.uiScalePercent - 10) },
                                    enabled = uiState.uiScalePercent > 40
                                ) {
                                    Icon(Icons.Outlined.Remove, contentDescription = "Perkecil tampilan")
                                }
                                Text(
                                    text = "${uiState.uiScalePercent}%",
                                    style = if (isLandscapePhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                IconButton(
                                    modifier = Modifier.size(if (isLandscapePhone) 30.dp else 36.dp),
                                    onClick = { viewModel.setUiScale(uiState.uiScalePercent + 10) },
                                    enabled = uiState.uiScalePercent < 120
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = "Perbesar tampilan")
                                }
                            }
                            IconButton(modifier = Modifier.size(if (isLandscapePhone) 34.dp else 40.dp), onClick = { viewModel.setThemeMode(!uiState.darkTheme) }) {
                                Icon(
                                    imageVector = if (uiState.darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    contentDescription = "Ganti Tema Terang/Gelap"
                                )
                            }
                            IconButton(modifier = Modifier.size(if (isLandscapePhone) 34.dp else 40.dp), onClick = { viewModel.logoutSession() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = "Halaman Login / Keluar"
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    // Navigasi bawah tetap tampil dalam mode portrait maupun landscape
                    NavigationBar(
                            modifier = Modifier.navigationBarsPadding().height(if (isLandscapePhone) 44.dp else 58.dp),
                            windowInsets = WindowInsets(0.dp),
                            containerColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFF080A0D) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                            contentColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFF5F7FA) else androidx.compose.ui.graphics.Color(0xFF334155)
                        ) {
                            val quickRoutes = listOf(
                                PosModuleRoute.DASHBOARD,
                                PosModuleRoute.KASIR,
                                PosModuleRoute.PESANAN,
                                PosModuleRoute.HISTORY
                            )
                            quickRoutes.forEach { route ->
                                NavigationBarItem(
                                    selected = activeModule == route,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFDCE7F2) else androidx.compose.ui.graphics.Color(0xFF526A82),
                                        selectedTextColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFFFFFFF) else androidx.compose.ui.graphics.Color(0xFF243447),
                                        indicatorColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFF354A60) else androidx.compose.ui.graphics.Color(0xFFE8EDF2),
                                        unselectedIconColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFD1D9E3) else androidx.compose.ui.graphics.Color(0xFF7B8794),
                                        unselectedTextColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFF5F7FA) else androidx.compose.ui.graphics.Color(0xFF596575)
                                    ),
                                    onClick = { activeModule = route },
                                    icon = { Icon(route.icon, contentDescription = localizedRouteTitle(route.titleId, uiState.outletSettings.appLanguage), tint = moduleIconTint(route), modifier = Modifier.size(if (isLandscapePhone) 20.dp else 24.dp)) },
                                    label = if (isLandscapePhone) null else ({ Text(localizedRouteTitle(route.titleId, uiState.outletSettings.appLanguage), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                                )
                            }
                            NavigationBarItem(
                                selected = activeModule !in quickRoutes,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFDCE7F2) else androidx.compose.ui.graphics.Color(0xFF526A82),
                                    selectedTextColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFFFFFFF) else androidx.compose.ui.graphics.Color(0xFF243447),
                                    indicatorColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFF354A60) else androidx.compose.ui.graphics.Color(0xFFE8EDF2),
                                    unselectedIconColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFD1D9E3) else androidx.compose.ui.graphics.Color(0xFF7B8794),
                                    unselectedTextColor = if (uiState.darkTheme) androidx.compose.ui.graphics.Color(0xFFF5F7FA) else androidx.compose.ui.graphics.Color(0xFF596575)
                                ),
                                onClick = { scope.launch { drawerState.open() } },
                                icon = { Icon(Icons.Outlined.Menu, contentDescription = "Semua Modul", tint = androidx.compose.ui.graphics.Color(0xFF8993A4), modifier = Modifier.size(if (isLandscapePhone) 24.dp else 28.dp)) },
                                label = if (isLandscapePhone) null else ({ Text("Modul", fontSize = 10.sp) })
                            )
                        }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = if (uiState.darkTheme) {
                                    listOf(
                                        androidx.compose.ui.graphics.Color(0xFF09090B),
                                        androidx.compose.ui.graphics.Color(0xFF151922),
                                        androidx.compose.ui.graphics.Color(0xFF101418)
                                    )
                                } else {
                                    listOf(
                                        androidx.compose.ui.graphics.Color(0xFFF3F4F6),
                                        androidx.compose.ui.graphics.Color(0xFFEEF0F3),
                                        androidx.compose.ui.graphics.Color(0xFFF7F8FA)
                                    )
                                }
                            )
                        )
                        .padding(innerPadding)
                ) {
                    RenderModuleScreen(module = activeModule, isTabletOrWide = isTabletOrWide, onNavigateModule = { activeModule = it }, viewModel = viewModel, uiState = uiState, isLandscapePhone = isLandscapePhone, selectedReportPage = selectedReportPage, onSelectedReportPageChange = { selectedReportPage = it })

                }
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = drawerContent,
            gesturesEnabled = true,
            content = mainScaffold
        )
    }
}
