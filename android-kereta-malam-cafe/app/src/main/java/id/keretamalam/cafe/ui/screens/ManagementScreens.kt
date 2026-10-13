package id.keretamalam.cafe.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import id.keretamalam.cafe.data.config.SupabaseConfigManager
import id.keretamalam.cafe.data.model.*
import id.keretamalam.cafe.hardware.PrinterRole
import id.keretamalam.cafe.util.CsvExporter
import id.keretamalam.cafe.viewmodel.PosUiState
import id.keretamalam.cafe.viewmodel.PosViewModel

// ============================================================================
// 9. PRODUK & STOK SCREEN
// ============================================================================
@Composable
fun ProdukStokComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var stockFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var deletingProduct by remember { mutableStateOf<Product?>(null) }

    val displayed = remember(uiState.products, stockFilter, searchQuery) {
        uiState.products.filter {
            val matchStock = when (stockFilter) {
                "LOW" -> it.isLowStock
                "OUT" -> it.isOutOfStock
                else -> true
            }
            val matchSearch = searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
            matchStock && matchSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(selected = stockFilter == "ALL", onClick = { stockFilter = "ALL" }, label = { Text("Semua (${uiState.products.size})") })
            FilterChip(selected = stockFilter == "LOW", onClick = { stockFilter = "LOW" }, label = { Text("Stok Menipis (${uiState.products.count { it.isLowStock }})") })
            FilterChip(selected = stockFilter == "OUT", onClick = { stockFilter = "OUT" }, label = { Text("Habis (${uiState.products.count { it.isOutOfStock }})") })
            Button(onClick = {
                editingProduct = Product(
                    id = "",
                    name = "",
                    price = 25000.0,
                    costPrice = 10000.0,
                    categoryId = uiState.categories.firstOrNull()?.id,
                    stock = 20,
                    lowStockThreshold = 5
                )
            }) {
                Text("+ Tambah Produk")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Cari Nama Produk...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = newCategoryName,
                onValueChange = { newCategoryName = it },
                label = { Text("Kategori Baru") },
                modifier = Modifier.weight(0.9f),
                singleLine = true
            )
            OutlinedButton(onClick = {
                viewModel.addCategory(newCategoryName)
                newCategoryName = ""
            }) {
                Text("+ Kategori")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayed, key = { it.id }) { prod ->
                val catName = uiState.categories.find { it.id == prod.categoryId }?.name ?: "Umum"
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${prod.name} ($catName)", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Jual: ${RupiahFormatter.format(prod.price)} · HPP: ${RupiahFormatter.format(prod.costPrice ?: 0.0)} · Stok: ${prod.stock}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = prod.showOnMenu,
                                onCheckedChange = { viewModel.toggleProductQrMenu(prod.id, it) }
                            )
                            OutlinedButton(onClick = { editingProduct = prod }) { Text("Edit") }
                            OutlinedButton(onClick = { deletingProduct = prod }) { Text("Hapus") }
                        }
                    }
                }
            }
        }
    }

    // Dialog Tambah / Edit Produk
    val targetEdit = editingProduct
    if (targetEdit != null) {
        var name by remember(targetEdit) { mutableStateOf(targetEdit.name) }
        var desc by remember(targetEdit) { mutableStateOf(targetEdit.description.orEmpty()) }
        var price by remember(targetEdit) { mutableStateOf(targetEdit.price.toLong().toString()) }
        var cost by remember(targetEdit) { mutableStateOf((targetEdit.costPrice ?: 0.0).toLong().toString()) }
        var stock by remember(targetEdit) { mutableStateOf(targetEdit.stock.toString()) }
        var threshold by remember(targetEdit) { mutableStateOf(targetEdit.lowStockThreshold.toString()) }
        var catId by remember(targetEdit) { mutableStateOf(targetEdit.categoryId ?: uiState.categories.firstOrNull()?.id) }

        Dialog(
            onDismissRequest = { editingProduct = null },
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
                    Text(if (targetEdit.id.isBlank()) "Tambah Produk Baru" else "Edit Produk", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Produk") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Deskripsi Singkat") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.categories.forEach { c ->
                            FilterChip(
                                selected = catId == c.id,
                                onClick = { catId = c.id },
                                label = { Text(c.name) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Harga Jual (Rp)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Modal / HPP (Rp)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("Stok") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = threshold, onValueChange = { threshold = it }, label = { Text("Batas Minimum") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingProduct = null }) { Text("Batal") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            viewModel.saveProductItem(
                                targetEdit.copy(
                                    name = name.trim(),
                                    description = desc.trim().ifBlank { null },
                                    categoryId = catId,
                                    price = price.toDoubleOrNull() ?: 0.0,
                                    costPrice = cost.toDoubleOrNull() ?: 0.0,
                                    stock = stock.toIntOrNull() ?: 0,
                                    lowStockThreshold = threshold.toIntOrNull() ?: 5
                                )
                            )
                            editingProduct = null
                        }) { Text("Simpan") }
                    }
                }
            }
        }
    }

    // Dialog Konfirmasi Hapus Produk
    val targetDelete = deletingProduct
    if (targetDelete != null) {
        Dialog(onDismissRequest = { deletingProduct = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Konfirmasi Hapus Produk", style = MaterialTheme.typography.titleLarge)
                    Text("Apakah Anda yakin ingin menghapus ${targetDelete.name}?")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { deletingProduct = null }) { Text("Batal") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            viewModel.deleteProductItem(targetDelete.id)
                            deletingProduct = null
                        }) { Text("Ya, Hapus") }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 10. MEJA & QR MENU SCREEN (ZXING QR BITMAP GENERATOR)
// ============================================================================
private fun generateQrImageBitmap(url: String, sizePx: Int = 512): ImageBitmap? {
    return runCatching {
        val bitMatrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp.asImageBitmap()
    }.getOrNull()
}

@Composable
fun MejaQrComposeScreen(
    viewModel: PosViewModel,
    uiState: PosUiState,
    isTabletOrWide: Boolean
) {
    val clipboard = LocalClipboardManager.current
    var newName by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var selectedTable by remember(uiState.tables) { mutableStateOf(uiState.tables.firstOrNull()) }

    val targetTable = selectedTable ?: uiState.tables.firstOrNull()
    val validQrUrl = "${uiState.outletSettings.publicWebBaseUrl.trimEnd('/')}/menu?table=${targetTable?.code ?: "M1"}"
    val qrBitmap = remember(validQrUrl) { generateQrImageBitmap(validQrUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tambah Meja Baru", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nama Meja") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = newCode, onValueChange = { newCode = it }, label = { Text("Kode (M6)") }, modifier = Modifier.weight(0.7f), singleLine = true)
                    Button(onClick = {
                        viewModel.addRestaurantTable(newName, newCode)
                        newName = ""
                        newCode = ""
                    }) { Text("+ Simpan") }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.tables.forEach { tbl ->
                val isOccupied = uiState.orders.any {
                    it.tableId == tbl.id && it.status != OrderStatus.COMPLETED && it.status != OrderStatus.CANCELLED
                }
                FilterChip(
                    selected = targetTable?.id == tbl.id,
                    onClick = { selectedTable = tbl },
                    label = { Text("${tbl.name} (${tbl.code}) · ${if (isOccupied) "Terisi" else "Kosong"}") }
                )
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("QR Ordering Pelanggan · ${targetTable?.name ?: "Meja 1"}", style = MaterialTheme.typography.titleLarge)
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "QR Code Meja ${targetTable?.code}",
                        modifier = Modifier.size(if (isTabletOrWide) 220.dp else 180.dp)
                    )
                }
                Text(validQrUrl, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(validQrUrl))
                        viewModel.showNotice("URL QR Meja disalin: $validQrUrl")
                    }) {
                        Text("Salin URL QR Valid")
                    }
                    if (targetTable != null && uiState.tables.size > 1) {
                        OutlinedButton(onClick = {
                            viewModel.deleteRestaurantTable(targetTable.id)
                        }) {
                            Text("Hapus Meja")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 11. INVENTORY SCREEN
// ============================================================================
@Composable
fun InventoryComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var selectedProductId by remember(uiState.products) { mutableStateOf(uiState.products.firstOrNull()?.id.orEmpty()) }
    var movementType by remember { mutableStateOf("in") }
    var qtyInput by remember { mutableStateOf("10") }
    var reasonInput by remember { mutableStateOf("") }

    val totalUnits = uiState.products.sumOf { it.stock }
    val totalValuation = uiState.products.sumOf { (it.costPrice ?: 0.0) * it.stock }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Total Unit Tersedia", style = MaterialTheme.typography.bodyMedium)
                        Text("$totalUnits Unit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Nilai Persediaan (HPP)", style = MaterialTheme.typography.bodyMedium)
                        Text(RupiahFormatter.format(totalValuation), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Catat Mutasi Stok / Penyesuaian Opname", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.products.forEach { prod ->
                            FilterChip(
                                selected = selectedProductId == prod.id,
                                onClick = { selectedProductId = prod.id },
                                label = { Text("${prod.name} (${prod.stock})") }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("in" to "Stok Masuk", "out" to "Stok Keluar", "adjustment" to "Opname").forEach { (code, label) ->
                            FilterChip(selected = movementType == code, onClick = { movementType = code }, label = { Text(label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = qtyInput, onValueChange = { qtyInput = it }, label = { Text("Jumlah Unit") }, modifier = Modifier.weight(0.4f), singleLine = true)
                        OutlinedTextField(value = reasonInput, onValueChange = { reasonInput = it }, label = { Text("Alasan Mutasi") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Button(onClick = {
                        viewModel.recordInventoryMovement(selectedProductId, movementType, qtyInput.toIntOrNull() ?: 0, reasonInput)
                        reasonInput = ""
                    }) { Text("Simpan Mutasi Stok") }
                }
            }
        }

        items(uiState.inventoryMovements, key = { it.id }) { mov ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${mov.productName} · ${mov.type.uppercase()}", fontWeight = FontWeight.Bold)
                        Text("${mov.reason} · Oleh ${mov.actorName} (${mov.createdAt})")
                    }
                    Text(
                        text = if (mov.quantity > 0) "+${mov.quantity} unit" else "${mov.quantity} unit",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================================
// 12. PROMOSI & DISKON SCREEN
// ============================================================================
@Composable
fun PromosiComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("percentage") }
    var value by remember { mutableStateOf("10") }
    var minPurchase by remember { mutableStateOf("50000") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Buat Promosi / Diskon Baru", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Promosi") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == "percentage", onClick = { type = "percentage" }, label = { Text("Persentase (%)") })
                        FilterChip(selected = type == "fixed", onClick = { type = "fixed" }, label = { Text("Potongan Tetap (Rp)") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Nilai Diskon") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = minPurchase, onValueChange = { minPurchase = it }, label = { Text("Minimum Belanja (Rp)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Button(onClick = {
                        viewModel.addPromotion(name, type, value.toDoubleOrNull() ?: 0.0, minPurchase.toDoubleOrNull() ?: 0.0)
                        name = ""
                    }) { Text("Simpan Promosi") }
                }
            }
        }

        items(uiState.promotions, key = { it.id }) { promo ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(promo.name, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Potongan: ${if (promo.type == "percentage") "${promo.value.toInt()}%" else RupiahFormatter.format(promo.value)} · Min: ${RupiahFormatter.format(promo.minPurchase)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { viewModel.togglePromotionActive(promo.id) }) {
                            Text(if (promo.active) "Aktif" else "Nonaktif")
                        }
                        OutlinedButton(onClick = { viewModel.deletePromotion(promo.id) }) {
                            Text("Hapus")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 13. PENGELUARAN & LABA RUGI (KEUANGAN) SCREEN
// ============================================================================
@Composable
fun KeuanganComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    val context = LocalContext.current
    var tabIndex by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Bahan baku") }
    var method by remember { mutableStateOf("cash") }
    var description by remember { mutableStateOf("") }

    val grossSales = uiState.paidOrders.sumOf { it.subtotal }
    val totalDiscounts = uiState.paidOrders.sumOf { it.discount }
    val netSales = uiState.todayRevenue
    val cogs = uiState.paidOrders.sumOf { o ->
        o.orderItems.sumOf { oi -> (oi.costPriceSnapshot ?: 0.0) * oi.quantity }
    }
    val grossProfit = netSales - cogs
    val totalExpenses = uiState.expenses.sumOf { it.amount }
    val netProfit = grossProfit - totalExpenses

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            CsvExporter.writeFinancialReportToUri(
                context,
                uri,
                "2026-10-01",
                "2026-10-12",
                netSales,
                cogs,
                totalExpenses,
                netProfit,
                uiState.expenses
            ).onSuccess { viewModel.showNotice("CSV Laporan Keuangan & Laba Rugi berhasil disimpan.") }
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Pengeluaran Operasional") })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Laporan Laba Rugi (P&L)") })
        }

        if (tabIndex == 0) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Catat Kas Keluar / Pengeluaran Baru", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Nominal (Rp)") }, modifier = Modifier.weight(0.5f), singleLine = true)
                                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Kategori") }, modifier = Modifier.weight(0.5f), singleLine = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("cash" to "Tunai", "qris" to "QRIS", "transfer" to "Transfer").forEach { (c, lbl) ->
                                    FilterChip(selected = method == c, onClick = { method = c }, label = { Text(lbl) })
                                }
                            }
                            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Keterangan Pengeluaran") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Button(onClick = {
                                viewModel.addExpense(amount.toDoubleOrNull() ?: 0.0, category, "2026-10-11", method, description)
                                amount = ""
                                description = ""
                            }) { Text("Simpan Pengeluaran") }
                        }
                    }
                }

                items(uiState.expenses, key = { it.id }) { exp ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exp.description, fontWeight = FontWeight.SemiBold)
                                Text("${exp.expenseDate} · ${exp.category} · ${exp.paymentMethod.uppercase()}")
                            }
                            Text(RupiahFormatter.format(exp.amount), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Laporan Laba Rugi (Profit & Loss)", style = MaterialTheme.typography.titleLarge)
                        Button(onClick = { csvLauncher.launch("laporan-keuangan-laba-rugi.csv") }) {
                            Text("Ekspor CSV P&L")
                        }
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Penjualan Kotor (Gross Sales)")
                        Text(RupiahFormatter.format(grossSales))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Potongan Diskon & Promo")
                        Text("-${RupiahFormatter.format(totalDiscounts)}")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Penjualan Bersih (Net Sales)", fontWeight = FontWeight.SemiBold)
                        Text(RupiahFormatter.format(netSales), fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Harga Pokok Penjualan (HPP / COGS)")
                        Text("-${RupiahFormatter.format(cogs)}")
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Laba Kotor (Gross Profit)", fontWeight = FontWeight.Bold)
                        Text(RupiahFormatter.format(grossProfit), fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Beban Pengeluaran Operasional")
                        Text("-${RupiahFormatter.format(totalExpenses)}")
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Laba / (Rugi) Bersih Operasional", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(RupiahFormatter.format(netProfit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 14. LAPORAN PENJUALAN SCREEN
// ============================================================================
@Composable
fun LaporanPenjualanComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    val context = LocalContext.current
    var period by remember { mutableStateOf("Harian") }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            CsvExporter.writeSalesReportToUri(context, uri, "Laporan Penjualan ($period)", uiState.paidOrders)
                .onSuccess { viewModel.showNotice("CSV Laporan Penjualan berhasil disimpan.") }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Laporan Penjualan Lengkap", style = MaterialTheme.typography.titleMedium)
                            Text("Total Omzet Lunas: ${RupiahFormatter.format(uiState.todayRevenue)} (${uiState.paidOrders.size} struk)")
                        }
                        Button(onClick = { csvLauncher.launch("laporan-penjualan-kereta-malam.csv") }) {
                            Text("Unduh CSV Laporan")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Harian", "Mingguan", "Bulanan", "Semua Data").forEach { p ->
                            FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p) })
                        }
                    }
                }
            }
        }

        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ringkasan per Kanal & Metode Pembayaran", style = MaterialTheme.typography.titleMedium)
                    val posRev = uiState.paidOrders.filter { it.source == "pos" }.sumOf { it.total }
                    val qrRev = uiState.paidOrders.filter { it.source == "qr" }.sumOf { it.total }
                    Text("Kanal Kasir POS: ${RupiahFormatter.format(posRev)}")
                    Text("Kanal QR Ordering Meja: ${RupiahFormatter.format(qrRev)}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    listOf("cash" to "Tunai", "qris" to "QRIS", "transfer" to "Transfer", "ewallet" to "E-Wallet").forEach { (m, lbl) ->
                        val rev = uiState.paidOrders.filter { (it.paymentMethod ?: "cash") == m }.sumOf { it.total }
                        Text("Metode $lbl: ${RupiahFormatter.format(rev)}")
                    }
                }
            }
        }

        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Performa Penjualan per Produk", style = MaterialTheme.typography.titleMedium)
                    val byProduct = uiState.paidOrders
                        .flatMap { it.orderItems }
                        .groupBy { it.productName }
                        .map { (name, items) -> Triple(name, items.sumOf { it.quantity }, items.sumOf { it.lineTotal }) }
                        .sortedByDescending { it.third }

                    byProduct.forEach { (name, qty, total) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$name ($qty unit)")
                            Text(RupiahFormatter.format(total), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 15. PEGAWAI & SHIFT (RBAC) SCREEN
// ============================================================================
@Composable
fun PegawaiComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var newName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf(StaffRole.CASHIER) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tambah Pegawai / Akun Staf Baru", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nama Lengkap") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = newEmail, onValueChange = { newEmail = it }, label = { Text("Email Staf") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(StaffRole.OWNER, StaffRole.MANAGER, StaffRole.CASHIER).forEach { r ->
                            FilterChip(selected = newRole == r, onClick = { newRole = r }, label = { Text(r.labelId) })
                        }
                    }
                    Button(onClick = {
                        viewModel.addStaffProfile(newName, newEmail, newRole)
                        newName = ""
                        newEmail = ""
                    }) { Text("+ Tambah Staf") }
                }
            }
        }

        items(uiState.staffList, key = { it.userId }) { st ->
            val isCurrent = st.userId == uiState.staffProfile.userId
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(st.fullName, fontWeight = FontWeight.Bold)
                            Text("Role: ${st.role.labelId} · ${st.email}")
                        }
                        Button(
                            enabled = !isCurrent,
                            onClick = { viewModel.switchActiveStaffProfile(st) }
                        ) {
                            Text(if (isCurrent) "Sedang Aktif" else "Gunakan Profil")
                        }
                    }

                    Text("Hak Akses Modul (RBAC):", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PermissionKey.entries.forEach { perm ->
                            val allowed = st.canAccess(perm)
                            FilterChip(
                                selected = allowed,
                                onClick = { viewModel.toggleStaffPermission(st.userId, perm.key) },
                                label = { Text(perm.key) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 16. PENGATURAN OUTLET SCREEN
// ============================================================================
@Composable
fun PengaturanOutletComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var webBaseUrl by remember(uiState.outletSettings.publicWebBaseUrl) {
        mutableStateOf(uiState.outletSettings.publicWebBaseUrl)
    }
    var outletName by remember(uiState.outletSettings.name) { mutableStateOf(uiState.outletSettings.name) }
    var outletAddress by remember(uiState.outletSettings.address) { mutableStateOf(uiState.outletSettings.address) }
    var outletPhone by remember(uiState.outletSettings.phone) { mutableStateOf(uiState.outletSettings.phone) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Identitas Outlet
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Identitas & Profil Outlet Kereta Malam Cafe", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = outletName, onValueChange = { outletName = it }, label = { Text("Nama Outlet") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = outletAddress, onValueChange = { outletAddress = it }, label = { Text("Alamat Lengkap Struk") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = outletPhone, onValueChange = { outletPhone = it }, label = { Text("Nomor Telepon / WhatsApp") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = {
                    viewModel.updateOutletSettings(
                        uiState.outletSettings.copy(
                            name = outletName.trim(),
                            address = outletAddress.trim(),
                            phone = outletPhone.trim(),
                            publicWebBaseUrl = webBaseUrl.trim()
                        )
                    )
                }) {
                    Text("Simpan Identitas Outlet")
                }
            }
        }

        // Skala Tampilan / Zoom (40%, 50%, 60%, dst)
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Pengaturan Ukuran Tampilan / Zoom Layar Android", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(40, 50, 60, 70, 80, 90, 100, 110, 120).forEach { scale ->
                        FilterChip(
                            selected = uiState.uiScalePercent == scale,
                            onClick = { viewModel.setUiScale(scale) },
                            label = { Text("$scale%") }
                        )
                    }
                }
            }
        }

        // Pajak & Layanan
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Parameter Pajak & Biaya Layanan Outlet", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aktifkan Pajak PB1 (${uiState.outletSettings.taxRate.toInt()}%)")
                    Switch(
                        checked = uiState.outletSettings.taxEnabled,
                        onCheckedChange = {
                            viewModel.updateOutletSettings(uiState.outletSettings.copy(taxEnabled = it))
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aktifkan Service Charge (${uiState.outletSettings.serviceRate.toInt()}%)")
                    Switch(
                        checked = uiState.outletSettings.serviceEnabled,
                        onCheckedChange = {
                            viewModel.updateOutletSettings(uiState.outletSettings.copy(serviceEnabled = it))
                        }
                    )
                }
            }
        }
    }
}

// ============================================================================
// 17. DUAL BLUETOOTH PRINTER ESC/POS SCREEN
// ============================================================================
@Composable
fun PrinterComposeScreen(
    viewModel: PosViewModel,
    uiState: PosUiState,
    isTabletOrWide: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Konfigurasi Dual Printer Bluetooth Thermal ESC/POS", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { viewModel.scanBluetoothPrinters() }) {
                        Text("Pindai Bluetooth")
                    }
                }
                Text("Mendukung 2 printer terpisah: Printer Struk Kasir & Printer Tiket Dapur via RFCOMM Socket (00001101-0000-1000-8000-00805F9B34FB). Pengujian fisik tetap diperlukan pada printer sebenarnya.")
                PrinterRole.entries.forEach { role ->
                    val saved = viewModel.printerManager.getSavedPrinter(role)
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(role.labelId, fontWeight = FontWeight.Bold)
                                    Text(saved?.let { "${it.name} (${it.address})" } ?: "Siap dipasangkan dari daftar perangkat Bluetooth")
                                }
                                Button(onClick = {
                                    val sample = uiState.orders.firstOrNull() ?: SandboxSeedData.initialOrders.first()
                                    viewModel.printOrderToThermal(sample, role, true)
                                }) {
                                    Text("Tes Cetak")
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                uiState.pairedBluetoothDevices.forEach { (devName, mac) ->
                                    AssistChip(
                                        onClick = { viewModel.bindPrinterDevice(role, devName, mac, true) },
                                        label = { Text("Pasangkan: $devName ($mac)") }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pratinjau Output Stream Thermal ESC/POS 58mm", style = MaterialTheme.typography.titleMedium)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.thermalPreviewText,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
