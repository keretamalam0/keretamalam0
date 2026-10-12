package id.keretamalam.cafe.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
    WEBSITE_MANAGEMENT("website-management", "Management Website", "MANAJEMEN", PermissionKey.SETTINGS, Icons.Outlined.Language)
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
                    Text(
                        text = "Kereta Malam Cafe",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "POS & QR Ordering · Android Native",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val grouped = PosModuleRoute.entries.groupBy { it.groupLabel }
                        grouped.forEach { (group, items) ->
                            Text(
                                text = group,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp)
                            )
                            items.forEach { item ->
                                val allowed = uiState.staffProfile?.canAccess(item.permission) ?: true
                                if (allowed) {
                                    NavigationDrawerItem(
                                        label = {
                                            Text(
                                                text = item.titleId,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        icon = { Icon(item.icon, contentDescription = item.titleId) },
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
                                    text = if (uiState.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                                        "Supabase Terhubung (${uiState.currentProjectId})"
                                    } else {
                                        "Supabase BELUM AKTIF (Verifikasi Project ID diperlukan)"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (uiState.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
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
                            // Pengaturan Skala Zoom Cepat (40%, 50%, 60%, 80%, 100%)
                            listOf(40, 50, 60, 80, 100).forEach { scale ->
                                TextButton(
                                    onClick = { viewModel.setUiScale(scale) }
                                ) {
                                    Text(
                                        text = "$scale%",
                                        fontWeight = if (uiState.uiScalePercent == scale) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.setThemeMode(!uiState.darkTheme) }) {
                                Icon(
                                    imageVector = if (uiState.darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    contentDescription = "Ganti Tema Terang/Gelap"
                                )
                            }
                        }
                    )
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
                        viewModel = viewModel,
                        uiState = uiState
                    )
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
