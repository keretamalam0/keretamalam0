package id.keretamalam.cafe.ui.screens

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
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
import id.keretamalam.cafe.data.config.BackendIntegrationStatus
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
    var section by remember { mutableStateOf("PRODUK") }
    var stockFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var deletingProduct by remember { mutableStateOf<Product?>(null) }
    var selectedProductId by remember(uiState.products) { mutableStateOf(uiState.products.firstOrNull()?.id.orEmpty()) }
    var addonId by remember { mutableStateOf("") }
    var addonName by remember { mutableStateOf("") }
    var addonPrice by remember { mutableStateOf("0") }
    var addonActive by remember { mutableStateOf(true) }
    var optionId by remember { mutableStateOf("") }
    var optionGroup by remember { mutableStateOf("sugar") }
    var optionName by remember { mutableStateOf("") }
    var optionPrice by remember { mutableStateOf("0") }
    var optionActive by remember { mutableStateOf(true) }

    val displayed = remember(uiState.products, stockFilter, searchQuery) {
        uiState.products.filter { p ->
            val stockMatch = when (stockFilter) { "LOW" -> p.isLowStock; "OUT" -> p.isOutOfStock; else -> true }
            stockMatch && (searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true))
        }
    }
    val selectedProduct = uiState.products.firstOrNull { it.id == selectedProductId }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Produk & Stok", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Atur produk, kategori, topping, dan pilihan minuman.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("PRODUK" to "Semua Produk", "KATEGORI" to "Kelola Kategori", "TOPPING" to "Topping", "CUSTOM" to "Kustomisasi Minuman").forEach { (key, label) ->
                FilterChip(selected = section == key, onClick = { section = key }, label = { Text(label) })
            }
        }

        when (section) {
            "PRODUK" -> {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = stockFilter == "ALL", onClick = { stockFilter = "ALL" }, label = { Text("Semua (${uiState.products.size})") })
                    FilterChip(selected = stockFilter == "LOW", onClick = { stockFilter = "LOW" }, label = { Text("Menipis (${uiState.products.count { it.isLowStock }})") })
                    FilterChip(selected = stockFilter == "OUT", onClick = { stockFilter = "OUT" }, label = { Text("Habis (${uiState.products.count { it.isOutOfStock }})") })
                    Button(onClick = { editingProduct = Product(id = "", name = "", price = 0.0, costPrice = 0.0, categoryId = uiState.categories.firstOrNull()?.id, stock = 0, lowStockThreshold = 5) }) { Text("+ Tambah Produk") }
                }
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Cari nama produk") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayed, key = { it.id }) { p ->
                        val category = uiState.categories.firstOrNull { it.id == p.categoryId }?.name ?: "Tanpa kategori"
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Box(contentAlignment = Alignment.Center) { Text("FOTO", style = MaterialTheme.typography.labelSmall) }
                                    }
                                    if (!p.imageUrl.isNullOrBlank()) AsyncImage(model = p.imageUrl, contentDescription = "Foto ${p.name}", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(p.name, fontWeight = FontWeight.Bold)
                                    Text(category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(RupiahFormatter.format(p.price), fontWeight = FontWeight.SemiBold)
                                    Text("Stok ${p.stock} · Ambang ${p.lowStockThreshold}", style = MaterialTheme.typography.bodySmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Aktif", style = MaterialTheme.typography.labelSmall)
                                        Switch(checked = p.isActive, onCheckedChange = { viewModel.toggleProductActive(p.id, it) })
                                        Text("QR", style = MaterialTheme.typography.labelSmall)
                                        Switch(checked = p.showOnMenu, onCheckedChange = { viewModel.toggleProductQrMenu(p.id, it) })
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedButton(onClick = { editingProduct = p }) { Text("Edit") }
                                    OutlinedButton(onClick = { selectedProductId = p.id; section = "TOPPING" }) { Text("Topping") }
                                    TextButton(onClick = { deletingProduct = p }) { Text("Hapus") }
                                }
                            }
                        }
                    }
                    if (displayed.isEmpty()) item { Text("Belum ada produk. Tekan + Tambah Produk untuk mulai.", modifier = Modifier.padding(16.dp)) }
                }
            }
            "KATEGORI" -> {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Tambah kategori", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = newCategoryName, onValueChange = { newCategoryName = it }, label = { Text("Nama kategori") }, modifier = Modifier.weight(1f), singleLine = true)
                            Button(enabled = newCategoryName.isNotBlank(), onClick = { viewModel.addCategory(newCategoryName); newCategoryName = "" }) { Text("Tambah") }
                        }
                        Text("Kategori cepat", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Coffee", "Non Coffee", "Makanan", "Snack", "Juice", "Alkohol").forEach { name ->
                                val exists = uiState.categories.any { it.name.equals(name, true) }
                                FilterChip(selected = exists, enabled = !exists, onClick = { viewModel.addCategory(name) }, label = { Text(if (exists) "$name ✓" else "+ $name") })
                            }
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.categories, key = { it.id }) { cat ->
                        OutlinedCard { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(cat.name, fontWeight = FontWeight.SemiBold)
                            Text("${uiState.products.count { it.categoryId == cat.id }} produk", style = MaterialTheme.typography.bodySmall)
                        } }
                    }
                }
            }
            "TOPPING" -> {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (addonId.isBlank()) "Tambah topping" else "Edit topping", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Topping aktif tersedia untuk dipilih pelanggan pada produk terkait.", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.products.forEach { p -> FilterChip(selected = selectedProductId == p.id, onClick = { selectedProductId = p.id }, label = { Text(p.name) }) }
                        }
                        OutlinedTextField(value = addonName, onValueChange = { addonName = it }, label = { Text("Nama topping") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = addonPrice, onValueChange = { addonPrice = it.filter(Char::isDigit) }, label = { Text("Harga tambahan (Rp)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Aktif / tampil ke pelanggan", Modifier.weight(1f)); Switch(checked = addonActive, onCheckedChange = { addonActive = it }) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = selectedProduct != null && addonName.isNotBlank(), onClick = {
                                viewModel.saveProductAddon(ProductAddon(id = addonId, productId = selectedProductId, name = addonName.trim(), price = addonPrice.toDoubleOrNull() ?: 0.0, isActive = addonActive))
                                addonId = ""; addonName = ""; addonPrice = "0"; addonActive = true
                            }) { Text(if (addonId.isBlank()) "Tambah Topping" else "Simpan") }
                            if (addonId.isNotBlank()) TextButton(onClick = { addonId = ""; addonName = ""; addonPrice = "0"; addonActive = true }) { Text("Batal") }
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.productAddons.filter { it.productId == selectedProductId }, key = { it.id }) { addon ->
                        OutlinedCard { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.weight(1f)) { Text(addon.name, fontWeight = FontWeight.SemiBold); Text(RupiahFormatter.format(addon.price), style = MaterialTheme.typography.bodySmall) }
                            Switch(checked = addon.isActive, onCheckedChange = { viewModel.saveProductAddon(addon.copy(isActive = it)) })
                            TextButton(onClick = { addonId = addon.id; addonName = addon.name; addonPrice = addon.price.toLong().toString(); addonActive = addon.isActive }) { Text("Edit") }
                            TextButton(onClick = { viewModel.deleteProductAddon(addon.id) }) { Text("Hapus") }
                        } }
                    }
                }
            }
            else -> {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (optionId.isBlank()) "Opsi kustomisasi minuman" else "Edit opsi kustomisasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Opsi aktif tersedia pada pilihan pelanggan di QR Menu.", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.products.forEach { p -> FilterChip(selected = selectedProductId == p.id, onClick = { selectedProductId = p.id }, label = { Text(p.name) }) }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("sugar" to "Gula", "ice" to "Es", "size" to "Ukuran").forEach { (key, label) -> FilterChip(selected = optionGroup == key, onClick = { optionGroup = key }, label = { Text(label) }) }
                        }
                        OutlinedTextField(value = optionName, onValueChange = { optionName = it }, label = { Text("Nama pilihan") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = optionPrice, onValueChange = { optionPrice = it.filter(Char::isDigit) }, label = { Text("Tambahan harga (Rp)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Aktif / tersedia di QR", Modifier.weight(1f)); Switch(checked = optionActive, onCheckedChange = { optionActive = it }) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = selectedProduct != null && optionName.isNotBlank(), onClick = {
                                val order = uiState.customizationOptions.count { it.productId == selectedProductId && it.groupKey == optionGroup }
                                viewModel.saveCustomizationOption(ProductCustomizationOption(id = optionId, productId = selectedProductId, groupKey = optionGroup, name = optionName.trim(), priceDelta = optionPrice.toDoubleOrNull() ?: 0.0, isActive = optionActive, sortOrder = order))
                                optionId = ""; optionName = ""; optionPrice = "0"; optionActive = true
                            }) { Text(if (optionId.isBlank()) "Tambah Opsi" else "Simpan") }
                            if (optionId.isNotBlank()) TextButton(onClick = { optionId = ""; optionName = ""; optionPrice = "0"; optionActive = true }) { Text("Batal") }
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.customizationOptions.filter { it.productId == selectedProductId }, key = { it.id }) { option ->
                        val groupLabel = when (option.groupKey) { "sugar" -> "Gula"; "ice" -> "Es"; else -> "Ukuran" }
                        OutlinedCard { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.weight(1f)) { Text(option.name, fontWeight = FontWeight.SemiBold); Text("$groupLabel · +${RupiahFormatter.format(option.priceDelta)}", style = MaterialTheme.typography.bodySmall) }
                            Switch(checked = option.isActive, onCheckedChange = { viewModel.saveCustomizationOption(option.copy(isActive = it)) })
                            TextButton(onClick = { optionId = option.id; selectedProductId = option.productId; optionGroup = option.groupKey; optionName = option.name; optionPrice = option.priceDelta.toLong().toString(); optionActive = option.isActive }) { Text("Edit") }
                            TextButton(onClick = { viewModel.deleteCustomizationOption(option.id) }) { Text("Hapus") }
                        } }
                    }
                }
            }
        }
    }

    val targetEdit = editingProduct
    if (targetEdit != null) {
        var name by remember(targetEdit) { mutableStateOf(targetEdit.name) }
        var desc by remember(targetEdit) { mutableStateOf(targetEdit.description.orEmpty()) }
        var price by remember(targetEdit) { mutableStateOf(if (targetEdit.price > 0) targetEdit.price.toLong().toString() else "") }
        var cost by remember(targetEdit) { mutableStateOf((targetEdit.costPrice ?: 0.0).toLong().toString()) }
        var stock by remember(targetEdit) { mutableStateOf(targetEdit.stock.toString()) }
        var threshold by remember(targetEdit) { mutableStateOf(targetEdit.lowStockThreshold.toString()) }
        var catId by remember(targetEdit) { mutableStateOf(targetEdit.categoryId ?: uiState.categories.firstOrNull()?.id) }
        var imageUrl by remember(targetEdit) { mutableStateOf(targetEdit.imageUrl.orEmpty()) }
        var isActive by remember(targetEdit) { mutableStateOf(targetEdit.isActive) }
        var showOnMenu by remember(targetEdit) { mutableStateOf(targetEdit.showOnMenu) }
        var uploadingImage by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                uploadingImage = true
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when (mime.lowercase()) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                if (bytes == null) uploadingImage = false
                else viewModel.uploadProductPhoto(bytes, mime, ext) { url -> imageUrl = url; uploadingImage = false }
            }
        }
        Dialog(onDismissRequest = { editingProduct = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 520.dp).padding(12.dp)) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (targetEdit.id.isBlank()) "Tambah Produk" else "Edit Produk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama produk") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Deskripsi") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                            Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Box(contentAlignment = Alignment.Center) { Text("FOTO", style = MaterialTheme.typography.labelSmall) } }
                            if (imageUrl.isNotBlank()) AsyncImage(model = imageUrl, contentDescription = "Foto produk", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !uploadingImage) { Text(if (uploadingImage) "Mengunggah..." else if (imageUrl.isBlank()) "Upload foto" else "Ganti foto") }
                            Text("Foto maksimal 8 MB.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Kategori", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        uiState.categories.forEach { cat -> FilterChip(selected = catId == cat.id, onClick = { catId = cat.id }, label = { Text(cat.name) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = price, onValueChange = { price = it.filter(Char::isDigit) }, label = { Text("Harga jual (Rp)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = cost, onValueChange = { cost = it.filter(Char::isDigit) }, label = { Text("Modal / HPP (Rp)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = stock, onValueChange = { stock = it.filter(Char::isDigit) }, label = { Text("Stok awal") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = threshold, onValueChange = { threshold = it.filter(Char::isDigit) }, label = { Text("Ambang menipis") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Produk aktif", Modifier.weight(1f)); Switch(checked = isActive, onCheckedChange = { isActive = it }) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Tampilkan di QR Menu", Modifier.weight(1f)); Switch(checked = showOnMenu, onCheckedChange = { showOnMenu = it }) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingProduct = null }) { Text("Batal") }
                        Button(enabled = name.isNotBlank() && (price.toDoubleOrNull() ?: 0.0) > 0 && !uploadingImage, onClick = {
                            viewModel.saveProductItem(targetEdit.copy(name = name.trim(), description = desc.trim().ifBlank { null }, imageUrl = imageUrl.ifBlank { null }, categoryId = catId, price = price.toDoubleOrNull() ?: 0.0, costPrice = cost.toDoubleOrNull() ?: 0.0, stock = stock.toIntOrNull() ?: 0, lowStockThreshold = threshold.toIntOrNull() ?: 5, isActive = isActive, showOnMenu = showOnMenu))
                            editingProduct = null
                        }) { Text("Simpan Produk") }
                    }
                }
            }
        }
    }
    val targetDelete = deletingProduct
    if (targetDelete != null) {
        Dialog(onDismissRequest = { deletingProduct = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Hapus produk?", style = MaterialTheme.typography.titleLarge)
                    Text("Yakin ingin menghapus ${targetDelete.name}?")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { deletingProduct = null }) { Text("Batal") }
                        Button(onClick = { viewModel.deleteProductItem(targetDelete.id); deletingProduct = null }) { Text("Hapus") }
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
    var pendingDelete by remember { mutableStateOf<StaffProfile?>(null) }
    val isOwner = uiState.staffProfile.role == StaffRole.OWNER && uiState.staffProfile.isActive
    val modules = listOf("dashboard" to "Dashboard", "orders" to "Kasir & Pesanan", "payments" to "Pembayaran", "products" to "Produk & Stok", "inventory" to "Inventory", "tables" to "Meja & QR Menu", "reports" to "Laporan Penjualan", "customers" to "Pelanggan", "promotions" to "Promosi & Diskon", "settings" to "Pengaturan Outlet", "export_data" to "Export / Download Data")
    val metrics = listOf("dashboard.total_sales" to "Total penjualan", "dashboard.paid_orders" to "Jumlah transaksi lunas", "dashboard.average_transaction" to "Rata-rata transaksi", "dashboard.peak_hour" to "Jam tersibuk", "dashboard.trend_chart" to "Grafik tren omzet", "dashboard.hourly_activity" to "Aktivitas per jam", "dashboard.best_sellers" to "Menu terlaris", "dashboard.quick_actions" to "Akses cepat")
    pendingDelete?.let { target -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Hapus profil staf?") }, text = { Text("Profil ${target.fullName} dihapus dari daftar POS. User Supabase Auth tidak ikut terhapus; penghapusan permanen perlu fungsi admin server.") }, confirmButton = { Button(onClick = { viewModel.deleteStaffProfile(target.userId); pendingDelete = null }, enabled = isOwner && !uiState.isLoading) { Text("Hapus profil") } }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } }) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pegawai & Shift", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Hijau berarti shift sedang terbuka/pegawai bekerja. Akun aktif tidak otomatis berarti sedang bekerja.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!isOwner) Text("Pengelolaan pegawai hanya tersedia untuk Owner.", color = MaterialTheme.colorScheme.error)
                Text("Tambah Pegawai / Akun Staf Baru", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nama Lengkap") }, modifier = Modifier.weight(1f), singleLine = true, enabled = isOwner && !uiState.isLoading)
                    OutlinedTextField(value = newEmail, onValueChange = { newEmail = it }, label = { Text("Email Staf") }, modifier = Modifier.weight(1f), singleLine = true, enabled = isOwner && !uiState.isLoading)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(StaffRole.MANAGER, StaffRole.CASHIER).forEach { role -> FilterChip(selected = newRole == role, onClick = { newRole = role }, enabled = isOwner, label = { Text(role.labelId) }) } }
                Button(enabled = isOwner && !uiState.isLoading && newName.isNotBlank() && newEmail.isNotBlank(), onClick = { viewModel.addStaffProfile(newName, newEmail, newRole); newName = ""; newEmail = "" }) { Text("+ Tambah Staf") }
            } }
        }
        items(uiState.staffList, key = { it.userId }) { st ->
            val isCurrent = st.userId == uiState.staffProfile.userId
            val working = uiState.shifts.any { it.staffUserId == st.userId && it.status.equals("open", true) }
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(st.fullName, fontWeight = FontWeight.Bold)
                        Text("Role: ${st.role.labelId} · ${st.email}", style = MaterialTheme.typography.bodySmall)
                        Surface(color = when { working -> androidx.compose.ui.graphics.Color(0xFFDCFCE7); st.isActive -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.errorContainer }, shape = RoundedCornerShape(50)) {
                            Text(when { working -> "● Sedang bekerja"; st.isActive -> "● Akun aktif · tidak sedang shift"; else -> "● Nonaktif" }, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = when { working -> androidx.compose.ui.graphics.Color(0xFF166534); st.isActive -> MaterialTheme.colorScheme.onSecondaryContainer; else -> MaterialTheme.colorScheme.onErrorContainer }, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (uiState.backendStatus == BackendIntegrationStatus.LOCAL_SANDBOX_UI) TextButton(onClick = { viewModel.switchActiveStaffProfile(st) }, enabled = !isCurrent && st.isActive) { Text(if (isCurrent) "Profil ini" else "Uji profil") }
                }
                if (!isCurrent && isOwner) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(Modifier.weight(1f), enabled = !uiState.isLoading, onClick = { viewModel.setStaffActive(st.userId, !st.isActive) }) { Text(if (st.isActive) "Nonaktifkan" else "Aktifkan") }
                    OutlinedButton(Modifier.weight(1f), enabled = !uiState.isLoading, onClick = { pendingDelete = st }) { Text("Delete") }
                }
                Text("Hak akses menu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                modules.forEach { (key, label) -> PermissionToggleRow(label, st.permissions[key] == true, isOwner && !isCurrent && st.role != StaffRole.OWNER && !uiState.isLoading) { viewModel.toggleStaffPermission(st.userId, key) } }
                HorizontalDivider()
                Text("Detail Dashboard", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                metrics.forEach { (key, label) -> val allowed = if (st.permissions.containsKey(key)) st.permissions[key] == true else st.permissions["dashboard"] == true; PermissionToggleRow(label, allowed, isOwner && !isCurrent && st.role != StaffRole.OWNER && !uiState.isLoading) { viewModel.toggleStaffPermission(st.userId, key) } }
            } }
        }
    }
}
@Composable
private fun PermissionToggleRow(label: String, allowed: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = allowed, onCheckedChange = { onToggle() }, enabled = enabled)
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
    var taxRateInput by remember(uiState.outletSettings.taxRate) { mutableStateOf(uiState.outletSettings.taxRate.toString()) }
    var serviceRateInput by remember(uiState.outletSettings.serviceRate) { mutableStateOf(uiState.outletSettings.serviceRate.toString()) }
    var serviceFixedInput by remember(uiState.outletSettings.serviceFixedAmount) { mutableStateOf(uiState.outletSettings.serviceFixedAmount.toLong().toString()) }
    var serviceFeeMode by remember(uiState.outletSettings.serviceFeeMode) { mutableStateOf(uiState.outletSettings.serviceFeeMode) }
    var feeValidationError by remember { mutableStateOf<String?>(null) }
    var showPaymentForm by remember { mutableStateOf(false) }
    var editingPaymentId by remember { mutableStateOf<String?>(null) }
    var paymentName by remember { mutableStateOf("") }
    var paymentType by remember { mutableStateOf("qris") }
    var paymentProvider by remember { mutableStateOf("") }
    var paymentAccountName by remember { mutableStateOf("") }
    var paymentAccountNumber by remember { mutableStateOf("") }
    var paymentInstructions by remember { mutableStateOf("") }
    var paymentImageUrl by remember { mutableStateOf<String?>(null) }
    var paymentImageUploading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val paymentImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            paymentImageUploading = true
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when (mime.lowercase()) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) paymentImageUploading = false else viewModel.uploadPaymentPhoto(bytes, mime, ext) { url ->
                paymentImageUrl = url
                paymentImageUploading = false
            }
        }
    }

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

        // Pajak & Biaya Layanan
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("3. Parameter Pajak & Biaya Layanan Outlet", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pajak transaksi", fontWeight = FontWeight.SemiBold)
                        Text("Aktifkan atau nonaktifkan pajak untuk transaksi outlet.")
                    }
                    Switch(checked = uiState.outletSettings.taxEnabled, onCheckedChange = {
                        viewModel.updateOutletSettings(uiState.outletSettings.copy(taxEnabled = it))
                    })
                }
                OutlinedTextField(
                    value = taxRateInput,
                    onValueChange = { taxRateInput = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }; feeValidationError = null },
                    label = { Text("Persentase pajak (%)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biaya layanan / Service charge", fontWeight = FontWeight.SemiBold)
                        Text("Pilih persentase atau nominal tetap per transaksi.")
                    }
                    Switch(checked = uiState.outletSettings.serviceEnabled, onCheckedChange = {
                        viewModel.updateOutletSettings(uiState.outletSettings.copy(serviceEnabled = it))
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = serviceFeeMode == "percentage", onClick = { serviceFeeMode = "percentage" }, label = { Text("Persentase (%)") })
                    FilterChip(selected = serviceFeeMode == "fixed", onClick = { serviceFeeMode = "fixed" }, label = { Text("Nominal tetap (Rp)") })
                }
                if (serviceFeeMode == "percentage") {
                    OutlinedTextField(
                        value = serviceRateInput,
                        onValueChange = { serviceRateInput = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }; feeValidationError = null },
                        label = { Text("Service charge (%)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = serviceFixedInput,
                        onValueChange = { serviceFixedInput = it.filter { ch -> ch.isDigit() }; feeValidationError = null },
                        label = { Text("Nominal service per transaksi (Rp)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (feeValidationError != null) {
                    Text(feeValidationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = {
                    val tax = taxRateInput.replace(',', '.').toDoubleOrNull()
                    val serviceRate = serviceRateInput.replace(',', '.').toDoubleOrNull()
                    val serviceFixed = serviceFixedInput.toDoubleOrNull()
                    if (tax == null || tax < 0 || tax > 100 || serviceRate == null || serviceRate < 0 || serviceRate > 100 || serviceFixed == null || serviceFixed < 0) {
                        feeValidationError = "Nilai pajak/service harus valid. Persentase maksimal 100% dan nominal tidak boleh negatif."
                    } else {
                        feeValidationError = null
                        viewModel.updateOutletSettings(uiState.outletSettings.copy(
                            taxRate = tax,
                            serviceRate = serviceRate,
                            serviceFeeMode = serviceFeeMode,
                            serviceFixedAmount = serviceFixed
                        ))
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Simpan Pajak & Biaya Layanan")
                }
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("4. Bahasa Aplikasi", style = MaterialTheme.typography.titleMedium)
                Text("Pilih bahasa aplikasi. Pengaturan ini disimpan ke akun outlet.")
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("id" to "Indonesia", "en" to "English", "zh" to "中文", "th" to "ไทย", "vi" to "Tiếng Việt").forEach { (code, label) ->
                        FilterChip(selected = uiState.outletSettings.appLanguage == code, onClick = {
                            viewModel.updateOutletSettings(uiState.outletSettings.copy(appLanguage = code))
                        }, label = { Text(label) })
                    }
                }
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("5. Metode Pembayaran", style = MaterialTheme.typography.titleMedium)
                Text("Tambah, edit, atau hapus metode pembayaran yang digunakan outlet.")
                if (uiState.paymentMethods.isEmpty()) {
                    Text("Belum ada metode pembayaran yang ditambahkan.", style = MaterialTheme.typography.bodyMedium)
                }
                uiState.paymentMethods.forEach { method ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(method.name.ifBlank { method.provider.ifBlank { method.type.uppercase() } }, fontWeight = FontWeight.SemiBold)
                            Text(listOf(method.type.uppercase(), method.provider, method.accountNumber).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            if (!method.instructions.isNullOrBlank()) Text(method.instructions, style = MaterialTheme.typography.bodySmall)
                            if (!method.imageUrl.isNullOrBlank()) AsyncImage(model = method.imageUrl, contentDescription = "QR pembayaran", modifier = Modifier.size(88.dp))
                            Text(if (method.isActive) "Aktif" else "Nonaktif", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(onClick = {
                                editingPaymentId = method.id
                                paymentName = method.name
                                paymentType = method.type
                                paymentProvider = method.provider
                                paymentAccountName = method.accountName
                                paymentAccountNumber = method.accountNumber
                                paymentInstructions = method.instructions
                                paymentImageUrl = method.imageUrl
                                showPaymentForm = true
                            }) { Text("Edit") }
                            TextButton(onClick = { viewModel.deletePaymentMethod(method.id) }) { Text("Hapus") }
                        }
                    }
                    HorizontalDivider()
                }
                if (!showPaymentForm) {
                    Button(onClick = {
                        editingPaymentId = null
                        paymentName = ""
                        paymentType = "qris"
                        paymentProvider = ""
                        paymentAccountName = ""
                        paymentAccountNumber = ""
                        paymentInstructions = ""
                        paymentImageUrl = null
                        showPaymentForm = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Tambah Metode Pembayaran")
                    }
                } else {
                    Text(if (editingPaymentId == null) "Tambah metode pembayaran baru" else "Edit metode pembayaran", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("cash" to "Cash / Tunai", "ewallet" to "E-Wallet", "qris" to "QRIS", "bank_transfer" to "Transfer Bank", "other" to "Lainnya").forEach { (code, label) ->
                            FilterChip(selected = paymentType == code, onClick = { paymentType = code }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(value = paymentName, onValueChange = { paymentName = it }, label = { Text("Nama metode pembayaran") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = paymentProvider, onValueChange = { paymentProvider = it }, label = { Text("Provider / nama bank (mis. BCA, GoPay)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = paymentAccountName, onValueChange = { paymentAccountName = it }, label = { Text("Nama pemilik rekening (opsional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = paymentAccountNumber, onValueChange = { paymentAccountNumber = it }, label = { Text("Nomor rekening / nomor e-wallet (opsional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = paymentInstructions, onValueChange = { paymentInstructions = it }, label = { Text("Instruksi pembayaran") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { paymentImagePicker.launch("image/*") }, enabled = !paymentImageUploading) {
                            Text(if (paymentImageUploading) "Mengunggah gambar..." else "Pilih foto / QRIS")
                        }
                        if (!paymentImageUrl.isNullOrBlank()) AsyncImage(model = paymentImageUrl, contentDescription = "QRIS", modifier = Modifier.size(76.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showPaymentForm = false; editingPaymentId = null }, modifier = Modifier.weight(1f)) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                if (paymentName.isBlank()) return@Button
                                val existingMethod = uiState.paymentMethods.firstOrNull { it.id == editingPaymentId }
                                viewModel.savePaymentMethod(PaymentMethod(
                                    id = editingPaymentId ?: "",
                                    code = existingMethod?.code ?: "",
                                    name = paymentName.trim(),
                                    type = paymentType,
                                    provider = paymentProvider.trim(),
                                    accountName = paymentAccountName.trim(),
                                    accountNumber = paymentAccountNumber.trim(),
                                    instructions = paymentInstructions.trim(),
                                    imageUrl = paymentImageUrl,
                                    isActive = existingMethod?.isActive ?: true,
                                    sortOrder = existingMethod?.sortOrder ?: uiState.paymentMethods.size
                                ))
                                paymentName = ""
                                paymentProvider = ""
                                paymentAccountName = ""
                                paymentAccountNumber = ""
                                paymentInstructions = ""
                                paymentImageUrl = null
                                editingPaymentId = null
                                showPaymentForm = false
                            },
                            enabled = paymentName.isNotBlank() && !paymentImageUploading,
                            modifier = Modifier.weight(1f)
                        ) { Text("Simpan Metode") }
                    }
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
