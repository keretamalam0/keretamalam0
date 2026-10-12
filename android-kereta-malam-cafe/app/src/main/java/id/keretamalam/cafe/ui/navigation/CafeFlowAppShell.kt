package id.keretamalam.cafe.ui.navigation

import androidx.compose.foundation.background
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
import id.keretamalam.cafe.data.config.BackendIntegrationStatus
import id.keretamalam.cafe.data.model.PermissionKey
import id.keretamalam.cafe.ui.screens.LoginComposeScreen
import id.keretamalam.cafe.ui.screens.RenderModuleScreen
import id.keretamalam.cafe.viewmodel.PosUiState
import id.keretamalam.cafe.viewmodel.PosViewModel
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CafeFlowAppShell(
    viewModel: PosViewModel,
    uiState: PosUiState
) {
    var activeModule by remember { mutableStateOf(PosModuleRoute.DASHBOARD) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletOrWide = maxWidth >= 840.dp

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
                drawerContainerColor = MaterialTheme.colorScheme.surface
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
                                Text(
                                    text = group,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp)
                                )
                                visible.forEach { item ->
                                    val badgeText = when (item) {
                                        PosModuleRoute.KASIR -> uiState.cart.sumOf { it.quantity }.takeIf { it > 0 }?.toString()
                                        PosModuleRoute.PESANAN -> uiState.activeOrdersCount.takeIf { it > 0 }?.toString()
                                        PosModuleRoute.PRODUK -> uiState.lowStockProducts.size.takeIf { it > 0 }?.toString()
                                        else -> null
                                    }
                                    NavigationDrawerItem(
                                        label = {
                                            Text(
                                                text = item.titleId,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        icon = { Icon(item.icon, contentDescription = item.titleId) },
                                        badge = badgeText?.let { b ->
                                            {
                                                Badge { Text(b) }
                                            }
                                        },
                                        selected = activeModule == item,
                                        onClick = {
                                            activeModule = item
                                            if (!isTabletOrWide) {
                                                scope.launch { drawerState.close() }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
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
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Role: ${uiState.staffProfile.role.labelId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        title = {
                            Column {
                                Text(
                                    text = activeModule.titleId,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "POS & QR Ordering",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            if (!isTabletOrWide) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Outlined.Menu, contentDescription = "Buka Navigasi")
                                }
                            }
                        },
                        actions = {
                            if (isTabletOrWide) {
                                listOf(40, 50, 60, 80, 100).forEach { scale ->
                                    TextButton(onClick = { viewModel.setUiScale(scale) }) {
                                        Text(
                                            text = "$scale%",
                                            fontWeight = if (uiState.uiScalePercent == scale) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { viewModel.setThemeMode(!uiState.darkTheme) }) {
                                Icon(
                                    imageVector = if (uiState.darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    contentDescription = "Ganti Tema Terang/Gelap"
                                )
                            }
                            IconButton(onClick = { viewModel.logoutSession() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = "Halaman Login / Keluar"
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    // Navigasi Bawah khusus layar HP untuk akses cepat 1 tangan
                    if (!isTabletOrWide) {
                        NavigationBar {
                            val quickRoutes = listOf(
                                PosModuleRoute.DASHBOARD,
                                PosModuleRoute.KASIR,
                                PosModuleRoute.PESANAN,
                                PosModuleRoute.HISTORY
                            )
                            quickRoutes.forEach { route ->
                                NavigationBarItem(
                                    selected = activeModule == route,
                                    onClick = { activeModule = route },
                                    icon = { Icon(route.icon, contentDescription = route.titleId) },
                                    label = { Text(route.titleId, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                            NavigationBarItem(
                                selected = activeModule !in quickRoutes,
                                onClick = { scope.launch { drawerState.open() } },
                                icon = { Icon(Icons.Outlined.Menu, contentDescription = "Semua Modul") },
                                label = { Text("Modul") }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(innerPadding)
                ) {
                    RenderModuleScreen(
                        module = activeModule,
                        isTabletOrWide = isTabletOrWide,
                        onNavigateModule = { activeModule = it },
                        viewModel = viewModel,
                        uiState = uiState
                    )

                    // Floating Bottom-Right Zoom Control (- 100% +)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            TextButton(
                                onClick = { viewModel.setUiScale(uiState.uiScalePercent - 10) },
                                enabled = uiState.uiScalePercent > 40
                            ) { Text("-") }
                            Text(
                                text = "${uiState.uiScalePercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = { viewModel.setUiScale(uiState.uiScalePercent + 10) },
                                enabled = uiState.uiScalePercent < 120
                            ) { Text("+") }
                        }
                    }
                }
            }
        }

        if (isTabletOrWide) {
            PermanentNavigationDrawer(
                drawerContent = drawerContent,
                content = mainScaffold
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = drawerContent,
                content = mainScaffold
            )
        }
    }
}
