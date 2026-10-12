package id.keretamalam.cafe.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.keretamalam.cafe.data.config.BackendIntegrationStatus
import id.keretamalam.cafe.data.config.SupabaseConfigManager
import id.keretamalam.cafe.data.model.RupiahFormatter
import id.keretamalam.cafe.hardware.PrinterRole
import id.keretamalam.cafe.ui.navigation.PosModuleRoute
import id.keretamalam.cafe.viewmodel.PosUiState
import id.keretamalam.cafe.viewmodel.PosViewModel

@Composable
fun RenderModuleScreen(
    module: PosModuleRoute,
    viewModel: PosViewModel,
    uiState: PosUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Banner Status Koneksi Supabase
        if (uiState.backendStatus != BackendIntegrationStatus.ACTIVE_VERIFIED) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "STATUS INTEGRASI SUPABASE: BELUM AKTIF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ditemukan 2 kandidat Project ID: '${SupabaseConfigManager.CANDIDATE_REPO_PROJECT_ID}' (di supabase/config.toml repo) dan '${SupabaseConfigManager.CANDIDATE_WORKER_PROJECT_ID}' (di bundle pos.yuripratama246.workers.dev). Silakan verifikasi Project ID & masukkan Publishable Key pada modul Pengaturan Outlet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        when (module) {
            PosModuleRoute.KASIR -> KasirNativeScreen(viewModel, uiState)
            PosModuleRoute.AI_ASSISTANT -> AiAssistantNativeScreen(uiState)
            PosModuleRoute.PENGATURAN -> PengaturanNativeScreen(viewModel, uiState)
            PosModuleRoute.PRINTER -> PrinterNativeScreen(viewModel, uiState)
            else -> StandardDataModuleScreen(module, viewModel, uiState)
        }
    }
}

@Composable
private fun KasirNativeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var showConfirmCheckoutDialog by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Daftar Produk
        LazyColumn(
            modifier = Modifier.weight(1.4f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.products, key = { it.id }) { product ->
                OutlinedCard(
                    onClick = { viewModel.addToCart(product) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = product.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Stok: ${product.stock}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = RupiahFormatter.format(product.price),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Keranjang & Pembayaran
        OutlinedCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Keranjang Kasir", style = MaterialTheme.typography.titleLarge)
                    HorizontalDivider()
                    uiState.cart.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.product.name, fontWeight = FontWeight.Medium)
                                Text(RupiahFormatter.format(item.lineTotal), style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { viewModel.updateCartItemQuantity(item.product.id, -1) }) { Text("-") }
                                Text("${item.quantity}", modifier = Modifier.padding(horizontal = 8.dp))
                                OutlinedButton(onClick = { viewModel.updateCartItemQuantity(item.product.id, 1) }) { Text("+") }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Tagihan")
                        Text(RupiahFormatter.format(uiState.grandTotal), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { showConfirmCheckoutDialog = true },
                        enabled = uiState.cart.isNotEmpty() && !uiState.isCheckoutProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isCheckoutProcessing) "Memproses ke Backend..." else "Konfirmasi & Bayar")
                    }
                }
            }
        }
    }

    // Dialog Konfirmasi di Tengah Layar (Tidak Terpotong)
    if (showConfirmCheckoutDialog) {
        Dialog(
            onDismissRequest = { if (!uiState.isCheckoutProcessing) showConfirmCheckoutDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 460.dp)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Konfirmasi Transaksi Final", style = MaterialTheme.typography.titleLarge)
                    Text("Pastikan metode pembayaran dan nominal sudah sesuai. Transaksi baru dianggap selesai setelah RPC create_pos_order mengonfirmasi.")
                    Text("Total: ${RupiahFormatter.format(uiState.grandTotal)}", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showConfirmCheckoutDialog = false }) {
                            Text("Batal")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            enabled = !uiState.isCheckoutProcessing,
                            onClick = {
                                showConfirmCheckoutDialog = false
                                viewModel.submitPosCheckout()
                            }
                        ) {
                            Text("Simpan Transaksi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAssistantNativeScreen(uiState: PosUiState) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI Assistant — Kereta Malam Cafe", style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (uiState.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                    "Status Edge Function (ai-assistant): Menunggu konfigurasi kunci AI di Supabase Edge Function. Aplikasi tidak menampilkan jawaban palsu."
                } else {
                    "Status AI Assistant: BELUM TERHUBUNG. Backend Supabase belum diverifikasi dan Edge Function ai-assistant belum aktif."
                },
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun PengaturanNativeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var projectId by remember { mutableStateOf(uiState.currentProjectId) }
    var supabaseUrl by remember { mutableStateOf(uiState.currentSupabaseUrl) }
    var anonKey by remember { mutableStateOf("") }
    var webBaseUrl by remember { mutableStateOf("https://pos.yuripratama246.workers.dev") }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Verifikasi & Konfigurasi Backend Supabase", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    projectId = SupabaseConfigManager.CANDIDATE_REPO_PROJECT_ID
                    supabaseUrl = "https://${SupabaseConfigManager.CANDIDATE_REPO_PROJECT_ID}.supabase.co"
                }) {
                    Text("Pilih Repo (${SupabaseConfigManager.CANDIDATE_REPO_PROJECT_ID})")
                }
                OutlinedButton(onClick = {
                    projectId = SupabaseConfigManager.CANDIDATE_WORKER_PROJECT_ID
                    supabaseUrl = "https://${SupabaseConfigManager.CANDIDATE_WORKER_PROJECT_ID}.supabase.co"
                }) {
                    Text("Pilih Worker (${SupabaseConfigManager.CANDIDATE_WORKER_PROJECT_ID})")
                }
            }
            OutlinedTextField(
                value = projectId,
                onValueChange = { projectId = it },
                label = { Text("Verified Project ID") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = supabaseUrl,
                onValueChange = { supabaseUrl = it },
                label = { Text("Supabase URL") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = anonKey,
                onValueChange = { anonKey = it },
                label = { Text("Supabase Publishable / Anon Key (Bukan service_role)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.verifyAndSaveSupabaseConfig(projectId, supabaseUrl, anonKey, webBaseUrl) }
            ) {
                Text("Simpan & Aktifkan Integrasi")
            }
        }
    }
}

@Composable
private fun PrinterNativeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Konfigurasi Dual Printer Bluetooth ESC/POS", style = MaterialTheme.typography.titleLarge)
            Text("Mendukung 2 Printer Terpisah: Printer Struk Kasir & Printer Tiket Dapur (Android 12+ BLUETOOTH_SCAN & BLUETOOTH_CONNECT).")
            PrinterRole.entries.forEach { role ->
                val saved = viewModel.printerManager.getSavedPrinter(role)
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(role.labelId, fontWeight = FontWeight.Bold)
                            Text(saved?.let { "${it.name} (${it.address})" } ?: "Belum ada perangkat terhubung")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardDataModuleScreen(module: PosModuleRoute, viewModel: PosViewModel, uiState: PosUiState) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(module.titleId, style = MaterialTheme.typography.titleLarge)
            if (uiState.backendStatus != BackendIntegrationStatus.ACTIVE_VERIFIED) {
                Text("Menunggu aktivasi & verifikasi kredensial Supabase. Data dummy tidak ditampilkan sebagai data produksi.")
            } else {
                Text("Terhubung ke Supabase (${uiState.currentProjectId}). Total Pesanan: ${uiState.orders.size}, Produk: ${uiState.products.size}.")
            }
        }
    }
}
