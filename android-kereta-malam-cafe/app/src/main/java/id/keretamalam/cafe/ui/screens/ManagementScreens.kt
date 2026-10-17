package id.keretamalam.cafe.ui.screens

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var categoryPendingDelete by remember { mutableStateOf<Category?>(null) }
    var productSaveNotice by remember { mutableStateOf<String?>(null) }
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

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("PRODUK" to "Produk", "KATEGORI" to "Kelola Kategori").forEach { (key, label) ->
                FilterChip(selected = section == key, onClick = { section = key }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(34.dp))
            }
        }

        when (section) {
            "PRODUK" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("Semua Produk · ${uiState.products.size}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { editingProduct = Product(id = "", name = "", price = 0.0, costPrice = 0.0, categoryId = uiState.categories.firstOrNull()?.id, stock = 0, lowStockThreshold = 5) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Text("+ Tambah Produk")
                    }
                }
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Cari produk") }, leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }, modifier = Modifier.fillMaxWidth().heightIn(max = 56.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayed, key = { it.id }) { p ->
                        val category = uiState.categories.firstOrNull { it.id == p.categoryId }?.name ?: "Tanpa kategori"
                        OutlinedCard(
                            onClick = { editingProduct = p },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                                    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Box(contentAlignment = Alignment.Center) { Text("FOTO", style = MaterialTheme.typography.labelSmall) }
                                    }
                                    if (!p.imageUrl.isNullOrBlank()) AsyncImage(model = p.imageUrl, contentDescription = "Foto ${p.name}", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(p.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(RupiahFormatter.format(p.price), fontWeight = FontWeight.SemiBold)
                                    Text("Stok ${p.stock}  ·  Ambang ${p.lowStockThreshold}", style = MaterialTheme.typography.bodySmall, color = if (p.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = RoundedCornerShape(7.dp), color = if (p.isActive) Color(0xFFE0F2EA) else MaterialTheme.colorScheme.surfaceVariant) {
                                            Text(if (p.isActive) "Aktif" else "Nonaktif", modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = if (p.isActive) Color(0xFF17664B) else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Surface(shape = RoundedCornerShape(7.dp), color = if (p.showOnMenu) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                                            Text(if (p.showOnMenu) "QR tampil" else "QR disembunyikan", modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = if (p.showOnMenu) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { editingProduct = p }) { Text("Kelola") }
                                    Switch(checked = p.isActive, onCheckedChange = { viewModel.toggleProductActive(p.id, it) })
                                }
                            }
                        }
                    }
                    if (displayed.isEmpty()) item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Belum ada produk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Tambah menu pertama untuk mulai mengatur harga, stok, foto, topping, dan kustomisasi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = { editingProduct = Product(id = "", name = "", price = 0.0, costPrice = 0.0, categoryId = uiState.categories.firstOrNull()?.id, stock = 0, lowStockThreshold = 5) }) { Text("+ Tambah Produk") }
                            }
                        }
                    }
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
                            Column(Modifier.weight(1f)) {
                                Text(cat.name, fontWeight = FontWeight.SemiBold)
                                Text("${uiState.products.count { it.categoryId == cat.id }} produk", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { categoryPendingDelete = cat }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
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
                            if (addonId.isNotBlank()) OutlinedButton(onClick = { addonId = ""; addonName = ""; addonPrice = "0"; addonActive = true }) { Text("Batal") }
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.productAddons.filter { it.productId == selectedProductId }, key = { it.id }) { addon ->
                        OutlinedCard { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.weight(1f)) { Text(addon.name, fontWeight = FontWeight.SemiBold); Text(RupiahFormatter.format(addon.price), style = MaterialTheme.typography.bodySmall) }
                            Switch(checked = addon.isActive, onCheckedChange = { viewModel.saveProductAddon(addon.copy(isActive = it)) })
                            OutlinedButton(onClick = { addonId = addon.id; addonName = addon.name; addonPrice = addon.price.toLong().toString(); addonActive = addon.isActive }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) { Text("Edit", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(onClick = { viewModel.deleteProductAddon(addon.id) }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Hapus", style = MaterialTheme.typography.labelSmall) }
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
                            if (optionId.isNotBlank()) OutlinedButton(onClick = { optionId = ""; optionName = ""; optionPrice = "0"; optionActive = true }) { Text("Batal") }
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.customizationOptions.filter { it.productId == selectedProductId }, key = { it.id }) { option ->
                        val groupLabel = when (option.groupKey) { "sugar" -> "Gula"; "ice" -> "Es"; else -> "Ukuran" }
                        OutlinedCard { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.weight(1f)) { Text(option.name, fontWeight = FontWeight.SemiBold); Text("$groupLabel · +${RupiahFormatter.format(option.priceDelta)}", style = MaterialTheme.typography.bodySmall) }
                            Switch(checked = option.isActive, onCheckedChange = { viewModel.saveCustomizationOption(option.copy(isActive = it)) })
                            OutlinedButton(onClick = { optionId = option.id; selectedProductId = option.productId; optionGroup = option.groupKey; optionName = option.name; optionPrice = option.priceDelta.toLong().toString(); optionActive = option.isActive }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) { Text("Edit", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(onClick = { viewModel.deleteCustomizationOption(option.id) }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Hapus", style = MaterialTheme.typography.labelSmall) }
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
        var inlineAddonId by remember(targetEdit) { mutableStateOf("") }
        var inlineAddonName by remember(targetEdit) { mutableStateOf("") }
        var inlineAddonPrice by remember(targetEdit) { mutableStateOf("0") }
        var inlineOptionId by remember(targetEdit) { mutableStateOf("") }
        var inlineOptionGroup by remember(targetEdit) { mutableStateOf("sugar") }
        var inlineOptionName by remember(targetEdit) { mutableStateOf("") }
        var inlineOptionPrice by remember(targetEdit) { mutableStateOf("0") }
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
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(0.98f).widthIn(max = 620.dp).fillMaxHeight(0.96f)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (targetEdit.id.isBlank()) "Tambah Produk" else "Edit Produk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Kelola detail, topping, dan opsi dalam satu form", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { editingProduct = null }) { Text("✕") }
                    }
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("INFORMASI PRODUK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                                    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Box(contentAlignment = Alignment.Center) { Text("FOTO", style = MaterialTheme.typography.labelSmall) } }
                                    if (imageUrl.isNotBlank()) AsyncImage(model = imageUrl, contentDescription = "Foto produk", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama produk *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !uploadingImage, modifier = Modifier.fillMaxWidth()) { Text(if (uploadingImage) "Mengunggah foto..." else if (imageUrl.isBlank()) "＋ Upload foto" else "Ganti foto") }
                                }
                            }
                            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Deskripsi (opsional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                        }
                    }
                    var categoryMenuExpanded by remember(targetEdit) { mutableStateOf(false) }
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("HARGA & PERSEDIAAN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("Kategori", style = MaterialTheme.typography.labelLarge)
                            Box {
                        OutlinedButton(
                            onClick = { categoryMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                uiState.categories.firstOrNull { it.id == catId }?.name ?: "Tanpa kategori",
                                modifier = Modifier.weight(1f)
                            )
                            Text("⌄")
                        }
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tanpa kategori") },
                                onClick = { catId = null; categoryMenuExpanded = false }
                            )
                            uiState.categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = { catId = category.id; categoryMenuExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = price, onValueChange = { price = it.filter(Char::isDigit) }, label = { Text("Harga jual (Rp)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = stock, onValueChange = { stock = it.filter(Char::isDigit) }, label = { Text("Stok awal") }, modifier = Modifier.weight(1f), singleLine = true)
                                OutlinedTextField(value = threshold, onValueChange = { threshold = it.filter(Char::isDigit) }, label = { Text("Batas stok menipis") }, modifier = Modifier.weight(1f), singleLine = true)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("Produk aktif", fontWeight = FontWeight.Medium); Text("Bisa dijual di kasir", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Switch(checked = isActive, onCheckedChange = { isActive = it })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("Tampilkan di QR Menu", fontWeight = FontWeight.Medium); Text("Pelanggan bisa melihat produk ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Switch(checked = showOnMenu, onCheckedChange = { showOnMenu = it })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("TOPPING & TAMBAHAN", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Ekstra yang bisa dipilih pelanggan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text((if (targetEdit.id.isBlank()) 0 else uiState.productAddons.count { it.productId == targetEdit.id }).toString() + " item", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("TAMBAH TOPPING", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            if (targetEdit.id.isBlank()) {
                                if (name.isBlank() || (price.toDoubleOrNull() ?: 0.0) <= 0) return@TextButton
                                viewModel.saveProductItem(targetEdit.copy(name = name.trim(), description = desc.trim().ifBlank { null }, imageUrl = imageUrl.ifBlank { null }, categoryId = catId, price = price.toDoubleOrNull() ?: 0.0, stock = stock.toIntOrNull() ?: 0, lowStockThreshold = threshold.toIntOrNull() ?: 5, isActive = isActive, showOnMenu = showOnMenu)) { saved ->
                                    viewModel.saveProductAddon(ProductAddon(productId = saved.id, name = inlineAddonName.trim(), price = inlineAddonPrice.toDoubleOrNull() ?: 0.0, isActive = true))
                                    editingProduct = saved
                                productSaveNotice = "Produk berhasil disimpan."
                                    inlineAddonName = ""; inlineAddonPrice = "0"
                                }
                            } else if (inlineAddonName.isNotBlank()) {
                                viewModel.saveProductAddon(ProductAddon(productId = targetEdit.id, name = inlineAddonName.trim(), price = inlineAddonPrice.toDoubleOrNull() ?: 0.0, isActive = true))
                                inlineAddonName = ""; inlineAddonPrice = "0"
                            }
                        }) { Text("+ Tambah") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = inlineAddonName, onValueChange = { inlineAddonName = it }, label = { Text("Nama topping") }, modifier = Modifier.weight(1.4f), singleLine = true)
                        OutlinedTextField(value = inlineAddonPrice, onValueChange = { inlineAddonPrice = it.filter(Char::isDigit) }, label = { Text("Harga (Rp)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    if (targetEdit.id.isNotBlank()) {
                        uiState.productAddons.filter { it.productId == targetEdit.id }.forEach { addon ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(addon.name); Text(RupiahFormatter.format(addon.price), style = MaterialTheme.typography.bodySmall) }
                                TextButton(onClick = { viewModel.deleteProductAddon(addon.id) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                                        HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("OPSI KUSTOMISASI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Ukuran cup, es/suhu, dan tingkat gula", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text((if (targetEdit.id.isBlank()) 0 else uiState.customizationOptions.count { it.productId == targetEdit.id }).toString() + " opsi", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("OPSI KUSTOMISASI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            if (inlineOptionName.isBlank()) return@TextButton
                            val saveOption: (Product) -> Unit = { saved ->
                                viewModel.saveCustomizationOption(ProductCustomizationOption(productId = saved.id, groupKey = inlineOptionGroup, name = inlineOptionName.trim(), priceDelta = inlineOptionPrice.toDoubleOrNull() ?: 0.0, isActive = true, sortOrder = uiState.customizationOptions.count { it.productId == saved.id && it.groupKey == inlineOptionGroup }))
                                editingProduct = saved
                                inlineOptionName = ""; inlineOptionPrice = "0"
                            }
                            if (targetEdit.id.isBlank()) {
                                if (name.isBlank() || (price.toDoubleOrNull() ?: 0.0) <= 0) return@TextButton
                                viewModel.saveProductItem(targetEdit.copy(name = name.trim(), description = desc.trim().ifBlank { null }, imageUrl = imageUrl.ifBlank { null }, categoryId = catId, price = price.toDoubleOrNull() ?: 0.0, stock = stock.toIntOrNull() ?: 0, lowStockThreshold = threshold.toIntOrNull() ?: 5, isActive = isActive, showOnMenu = showOnMenu), saveOption)
                            } else saveOption(targetEdit)
                        }) { Text("+ Tambah") }
                    }
                    var customGroupMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { customGroupMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(when (inlineOptionGroup) { "sugar" -> "Grup: Tingkat gula"; "ice" -> "Grup: Es / Suhu"; else -> "Grup: Ukuran cup" }, modifier = Modifier.weight(1f))
                            Text("⌄")
                        }
                        DropdownMenu(expanded = customGroupMenu, onDismissRequest = { customGroupMenu = false }) {
                            listOf("size" to "Ukuran / porsi", "sugar" to "Tingkat gula", "ice" to "Es / suhu", "spice" to "Tingkat kepedasan", "rice" to "Pilihan nasi", "cooking" to "Tingkat kematangan", "side" to "Lauk / tambahan", "sauce" to "Saus / sambal", "topping" to "Topping makanan").forEach { (key, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { inlineOptionGroup = key; customGroupMenu = false })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = inlineOptionName, onValueChange = { inlineOptionName = it }, label = { Text("Nama pilihan") }, modifier = Modifier.weight(1.3f), singleLine = true)
                        OutlinedTextField(value = inlineOptionPrice, onValueChange = { inlineOptionPrice = it.filter(Char::isDigit) }, label = { Text("Harga tambahan Rp") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    if (targetEdit.id.isNotBlank()) {
                        uiState.customizationOptions.filter { it.productId == targetEdit.id }.forEach { option ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(option.name); Text("${when(option.groupKey) { "sugar" -> "Gula"; "ice" -> "Es / suhu"; else -> "Ukuran cup" }} · ${RupiahFormatter.format(option.priceDelta)}", style = MaterialTheme.typography.bodySmall) }
                                TextButton(onClick = { viewModel.deleteCustomizationOption(option.id) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                                        HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingProduct = null }, modifier = Modifier.weight(0.8f)) { Text("Batal") }
                        Button(enabled = name.isNotBlank() && (price.toDoubleOrNull() ?: 0.0) > 0 && !uploadingImage, onClick = {
                            viewModel.saveProductItem(targetEdit.copy(name = name.trim(), description = desc.trim().ifBlank { null }, imageUrl = imageUrl.ifBlank { null }, categoryId = catId, price = price.toDoubleOrNull() ?: 0.0, costPrice = cost.toDoubleOrNull() ?: 0.0, stock = stock.toIntOrNull() ?: 0, lowStockThreshold = threshold.toIntOrNull() ?: 5, isActive = isActive, showOnMenu = showOnMenu)) { saved ->
                                if (targetEdit.id.isBlank()) {
                                    // First save: keep the editor open so the user can add customizations.
                                    editingProduct = saved
                                } else {
                                    // Final save: close the editor and clearly confirm success.
                                    editingProduct = null
                                    productSaveNotice = "Produk \"${saved.name}\" berhasil disimpan."
                                }
                            }
                        }) { Text(if (targetEdit.id.isBlank()) "Simpan Produk & Lanjut" else "Simpan Produk") }
                    }
                }
            }
        }
    }
    productSaveNotice?.let { message ->
        AlertDialog(onDismissRequest = { productSaveNotice = null }, title = { Text("Berhasil disimpan") }, text = { Text(message) }, confirmButton = { Button(onClick = { productSaveNotice = null }) { Text("OK") } })
    }
    categoryPendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryPendingDelete = null },
            title = { Text("Hapus kategori?") },
            text = { Text("Kategori \"${category.name}\" akan dihapus. Kategori yang masih dipakai produk tidak bisa dihapus.") },
            confirmButton = { Button(onClick = { viewModel.deleteCategoryItem(category.id); categoryPendingDelete = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { categoryPendingDelete = null }) { Text("Batal") } }
        )
    }
    val targetDelete = deletingProduct
    if (targetDelete != null) {
        Dialog(onDismissRequest = { deletingProduct = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Hapus produk?", style = MaterialTheme.typography.titleLarge)
                    Text("Yakin ingin menghapus ${targetDelete.name}?")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { deletingProduct = null }) { Text("Batal") }
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
    val validQrUrl = "https://keretamalamqr.netlify.app/menu?table=${targetTable?.code ?: "M1"}"
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
    var searchQuery by remember { mutableStateOf("") }
    var showAdjustment by remember { mutableStateOf(false) }
    var selectedProductId by remember(uiState.products) { mutableStateOf(uiState.products.firstOrNull()?.id.orEmpty()) }
    var movementType by remember { mutableStateOf("in") }
    var qtyInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("Barang datang") }
    var productSearch by remember { mutableStateOf("") }
    var productPendingDelete by remember { mutableStateOf<Product?>(null) }

    val products = uiState.products
    val activeProducts = products.count { it.isActive }
    val lowStockCount = products.count { it.isLowStock || it.isOutOfStock }
    val totalUnits = products.sumOf { it.stock }
    val displayed = remember(products, searchQuery) {
        products.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) || it.id.contains(searchQuery.trim(), ignoreCase = true) }
            .sortedWith(compareBy<Product> { !it.isOutOfStock && !it.isLowStock }.thenBy { it.name.lowercase() })
    }
    val selectedProduct = products.firstOrNull { it.id == selectedProductId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("Inventory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Pantau stok, lakukan stock adjustment, dan cek riwayat pergerakan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("SKU Aktif", style = MaterialTheme.typography.labelMedium)
                        Text("$activeProducts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Produk aktif", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedCard(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Stok Menipis", style = MaterialTheme.typography.labelMedium)
                        Text("$lowStockCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text("Termasuk stok habis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Stok Saat Ini", style = MaterialTheme.typography.labelMedium)
                        Text("$totalUnits unit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Total semua produk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedCard(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pergerakan", style = MaterialTheme.typography.labelMedium)
                        Text("${uiState.inventoryMovements.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Catatan mutasi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Button(onClick = {
                selectedProductId = products.firstOrNull()?.id.orEmpty()
                productSearch = ""
                qtyInput = ""
                reasonInput = "Barang datang"
                movementType = "in"
                showAdjustment = true
            }, modifier = Modifier.fillMaxWidth(), enabled = products.isNotEmpty()) {
                Text("＋  Stock Adjustment")
            }
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cari stok saat ini") },
                placeholder = { Text("Cari nama produk atau SKU") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
            )
        }
        item {
            Text("Stok Saat Ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (displayed.isEmpty()) {
            item { Text("Produk tidak ditemukan.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(displayed, key = { it.id }) { prod ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(prod.name, fontWeight = FontWeight.SemiBold)
                        Text("SKU: ${prod.id.takeLast(8).uppercase()} · ${if (prod.isActive) "Aktif" else "Nonaktif"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Batas stok rendah: ${prod.lowStockThreshold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${prod.stock} unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (prod.isOutOfStock) {
                            Text("Habis", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        } else if (prod.isLowStock) {
                            Text("Menipis", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        } else {
                            Text("Aman", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            selectedProductId = prod.id
                            productSearch = prod.name
                            qtyInput = ""
                            reasonInput = "Penyesuaian stok"
                            movementType = "in"
                            showAdjustment = true
                        }) { Text("Adjust") }
                        TextButton(onClick = { productPendingDelete = prod }) {
                            Text("Hapus menu", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item {
            Text("Pergerakan Stok Terbaru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (uiState.inventoryMovements.isEmpty()) {
            item { Text("Belum ada catatan pergerakan stok.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(uiState.inventoryMovements, key = { it.id }) { mov ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(mov.productName, fontWeight = FontWeight.SemiBold)
                        Text("${mov.type.uppercase()} · ${mov.reason}", style = MaterialTheme.typography.bodySmall)
                        Text("${mov.createdAt} · Oleh ${mov.actorName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (mov.quantity > 0) "+${mov.quantity}" else "${mov.quantity}", fontWeight = FontWeight.Bold, color = if (mov.quantity < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    productPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { productPendingDelete = null },
            title = { Text("Hapus menu?") },
            text = { Text("Menu ini akan dihapus dari katalog produk. Jika masih terikat transaksi atau data lain, Supabase dapat menolak penghapusan untuk menjaga riwayat.") },
            confirmButton = {
                Button(
                    onClick = {
                        productPendingDelete = null
                        viewModel.deleteProductItem(target.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Ya, hapus") }
            },
            dismissButton = { TextButton(onClick = { productPendingDelete = null }) { Text("Batal") } }
        )
    }

    if (showAdjustment) {
        AlertDialog(
            onDismissRequest = { showAdjustment = false },
            title = { Text("Stock Adjustment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = productSearch,
                        onValueChange = { productSearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Cari produk") },
                        placeholder = { Text("Contoh: Alpukat Kocok") }
                    )
                    val matchingProducts = products.filter { it.name.contains(productSearch.trim(), ignoreCase = true) }
                    if (matchingProducts.isNotEmpty()) {
                        Column(Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            matchingProducts.take(6).forEach { prod ->
                                FilterChip(
                                    selected = selectedProductId == prod.id,
                                    onClick = { selectedProductId = prod.id },
                                    label = { Text("${prod.name} · stok ${prod.stock}") }
                                )
                            }
                        }
                    }
                    selectedProduct?.let { prod ->
                        Text("Stok sekarang: ${prod.stock} unit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Jenis penyesuaian", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("in" to "Barang datang / tambah stok", "out" to "Barang keluar / kurangi stok", "adjustment" to "Opname (stok fisik aktual)").forEach { (code, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = movementType == code, onClick = {
                                    movementType = code
                                    reasonInput = when (code) { "in" -> "Barang datang"; "out" -> "Barang keluar"; else -> "Opname stok" }
                                })
                                Text(label, modifier = Modifier.clickable {
                                    movementType = code
                                    reasonInput = when (code) { "in" -> "Barang datang"; "out" -> "Barang keluar"; else -> "Opname stok" }
                                })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (movementType == "adjustment") "Jumlah stok fisik aktual" else "Jumlah unit yang ditambah/dikurangi") },
                        placeholder = { Text(if (movementType == "adjustment") "Misal: 209" else "Misal: 200") }
                    )
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Catatan / alasan") },
                        placeholder = { Text("Opname, barang datang, rusak, retur…") }
                    )
                    if (selectedProduct != null && qtyInput.toIntOrNull() != null) {
                        val amount = qtyInput.toIntOrNull() ?: 0
                        val projected = when (movementType) {
                            "in" -> selectedProduct.stock + amount
                            "out" -> selectedProduct.stock - amount
                            else -> amount
                        }
                        Text("Stok setelah disimpan: ${projected.coerceAtLeast(0)} unit", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedProduct != null && (qtyInput.toIntOrNull() ?: 0) > 0 && reasonInput.isNotBlank() &&
                        (movementType != "out" || (qtyInput.toIntOrNull() ?: 0) <= (selectedProduct?.stock ?: 0)),
                    onClick = {
                        viewModel.recordInventoryMovement(selectedProductId, movementType, qtyInput.toIntOrNull() ?: 0, reasonInput) {
                            showAdjustment = false
                        }
                    }
                ) { Text("Simpan Adjustment") }
            },
            dismissButton = { TextButton(onClick = { showAdjustment = false }) { Text("Batal") } }
        )
    }
}

// 12. PROMOSI & DISKON SCREEN
// ============================================================================
@Composable
fun PromosiComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("percentage") }
    var value by remember { mutableStateOf("10") }
    var minPurchase by remember { mutableStateOf("0") }
    var maxPurchase by remember { mutableStateOf("0") }
    var minQuantity by remember { mutableStateOf("1") }
    var scope by remember { mutableStateOf("order") }
    var productId by remember { mutableStateOf("") }
    var campaignType by remember { mutableStateOf("promotion") }
    var allowCustomer by remember { mutableStateOf(true) }
    var allowCashier by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf("all") }
    var productDropdownExpanded by remember { mutableStateOf(false) }
    var promoToDelete by remember { mutableStateOf<Promotion?>(null) }

    if (promoToDelete != null) {
        val deletingPromo = promoToDelete!!
        AlertDialog(
            onDismissRequest = { promoToDelete = null },
            title = { Text("Hapus promo ini?") },
            text = { Text("Promo \"${deletingPromo.name}\" akan dihapus permanen dari daftar promo. Transaksi lama tetap tersimpan; riwayat tetap menampilkan nominal potongannya.") },
            confirmButton = {
                Button(
                    onClick = {
                        promoToDelete = null
                        viewModel.deletePromotion(deletingPromo.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Ya, Hapus") }
            },
            dismissButton = {
                OutlinedButton(onClick = { promoToDelete = null }) { Text("Batal") }
            }
        )
    }

    val filteredPromotions = uiState.promotions.filter { promo ->
        when (selectedTab) {
            "active" -> promo.active
            "voucher", "bundle", "campaign" -> promo.campaignType == selectedTab
            else -> true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Promosi & Diskon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Kelola promo aktif, voucher, bundel, dan campaign. Promo yang tidak memenuhi syarat tidak bisa diterapkan di Kasir.")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "active" to "Promo Aktif (${uiState.promotions.count { it.active }})",
                        "voucher" to "Voucher (${uiState.promotions.count { it.campaignType == "voucher" }})",
                        "bundle" to "Bundle (${uiState.promotions.count { it.campaignType == "bundle" }})",
                        "campaign" to "Campaign (${uiState.promotions.count { it.campaignType == "campaign" }})",
                        "all" to "Semua"
                    ).forEach { (key, label) ->
                        FilterChip(selected = selectedTab == key, onClick = { selectedTab = key }, label = { Text(label) })
                    }
                }
            }
        }
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Buat Promo Baru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Promo") },
                        placeholder = { Text("Contoh: Es Maca hemat Rp3.000") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Kategori Promo", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("promotion" to "Promo", "voucher" to "Voucher", "bundle" to "Bundle", "campaign" to "Campaign").forEach { (key, label) ->
                            FilterChip(selected = campaignType == key, onClick = { campaignType = key }, label = { Text(label) })
                        }
                    }
                    Text("Jenis Diskon", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == "percentage", onClick = { type = "percentage" }, label = { Text("Persentase (%)") })
                        FilterChip(selected = type == "fixed", onClick = { type = "fixed" }, label = { Text("Nominal (Rp)") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text(if (type == "percentage") "Nilai Diskon (%)" else "Nilai Diskon (Rp)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = minPurchase,
                            onValueChange = { minPurchase = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("Minimal Belanja (Rp)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = maxPurchase,
                        onValueChange = { maxPurchase = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Maksimum Total Belanja (Rp; 0 = tanpa batas)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Berlaku Untuk", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = scope == "order", onClick = { scope = "order"; productId = "" }, label = { Text("Seluruh Belanja") })
                        FilterChip(selected = scope == "product", onClick = { scope = "product" }, label = { Text("Produk Tertentu") })
                    }
                    if (scope == "product") {
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { productDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(uiState.products.firstOrNull { it.id == productId }?.name ?: "Pilih Produk Promo", modifier = Modifier.weight(1f))
                                Text("▾")
                            }
                            DropdownMenu(expanded = productDropdownExpanded, onDismissRequest = { productDropdownExpanded = false }) {
                                uiState.products.filter { it.isActive }.forEach { product ->
                                    DropdownMenuItem(
                                        text = { Text(product.name) },
                                        onClick = { productId = product.id; productDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = minQuantity,
                            onValueChange = { minQuantity = it.filter(Char::isDigit) },
                            label = { Text("Minimal Jumlah Produk (cup/item)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    Text("Pemakaian Promo", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allowCashier, onCheckedChange = { allowCashier = it })
                        Text("Bisa dipilih kasir")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allowCustomer, onCheckedChange = { allowCustomer = it })
                        Text("Bisa dipilih customer di QR Menu")
                    }
                    Button(
                        onClick = {
                            viewModel.addPromotion(
                                name = name,
                                type = type,
                                value = value.toDoubleOrNull() ?: 0.0,
                                minPurchase = minPurchase.toDoubleOrNull() ?: 0.0,
                                maxPurchase = maxPurchase.toDoubleOrNull() ?: 0.0,
                                minQuantity = minQuantity.toIntOrNull() ?: 1,
                                scope = scope,
                                productId = productId.takeIf { scope == "product" },
                                campaignType = campaignType,
                                allowCustomer = allowCustomer,
                                allowCashier = allowCashier
                            )
                            if (name.isNotBlank() && (value.toDoubleOrNull() ?: 0.0) > 0.0 && (type != "percentage" || (value.toDoubleOrNull() ?: 0.0) <= 100.0) && (scope != "product" || productId.isNotBlank())) {
                                name = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Simpan Promo") }
                }
            }
        }
        items(filteredPromotions, key = { it.id }) { promo ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(promo.name, fontWeight = FontWeight.Bold)
                            Text(
                                "${when (promo.campaignType) { "voucher" -> "Voucher"; "bundle" -> "Bundle"; "campaign" -> "Campaign"; else -> "Promo" }} · ${if (promo.type == "percentage") "${promo.value.toInt()}%" else RupiahFormatter.format(promo.value)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (promo.active) Color(0xFFE0F2E8) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                if (promo.active) "AKTIF" else "NONAKTIF",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (promo.active) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = if (promo.scope == "product") {
                            "${uiState.products.firstOrNull { it.id == promo.productId }?.name ?: "Produk tertentu"} · minimal ${promo.minQuantity} item · belanja ${RupiahFormatter.format(promo.minPurchase)}–${if (promo.maxPurchase > 0.0) RupiahFormatter.format(promo.maxPurchase) else "tanpa batas"}"
                        } else {
                            "Seluruh belanja · minimal ${RupiahFormatter.format(promo.minPurchase)} · maksimum ${if (promo.maxPurchase > 0.0) RupiahFormatter.format(promo.maxPurchase) else "tanpa batas"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Customer: ${if (promo.allowCustomer) "Boleh" else "Tidak"} · Kasir: ${if (promo.allowCashier) "Boleh" else "Tidak"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { viewModel.togglePromotionActive(promo.id) }) {
                            Text(if (promo.active) "Nonaktifkan" else "Aktifkan")
                        }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { promoToDelete = promo },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Hapus")
                        }
                    }
                }
            }
        }
        if (filteredPromotions.isEmpty()) {
            item {
                Text("Belum ada promo pada kategori ini.", modifier = Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private val ReportChartPalette = listOf(androidx.compose.ui.graphics.Color(0xFF6F91C5), androidx.compose.ui.graphics.Color(0xFF6BAF92), androidx.compose.ui.graphics.Color(0xFFE1B36A), androidx.compose.ui.graphics.Color(0xFF9A83C7), androidx.compose.ui.graphics.Color(0xFFD58A8A), androidx.compose.ui.graphics.Color(0xFF6DAAB5))
private fun reportDate(value: String): java.time.LocalDateTime? =
    runCatching { java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(value).toLocalDateTime() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(value.take(19)) }.getOrNull()
private fun reportMoney(value: Double) = RupiahFormatter.format(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaporanPenjualanComposeScreen(viewModel: PosViewModel, uiState: PosUiState, selectedSection: String = "Ringkasan Laporan", onSelectedSectionChange: (String) -> Unit = {}) {
    val context = LocalContext.current
    var period by remember { mutableStateOf("7 Hari") }
    var search by remember { mutableStateOf("") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var customStartMillis by remember { mutableStateOf<Long?>(null) }
    var customEndMillis by remember { mutableStateOf<Long?>(null) }
    val today = java.time.LocalDate.now()
    val utc = java.time.ZoneOffset.UTC
    val customStart = customStartMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate() }
    val customEnd = customEndMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate() }
    val orders = remember(uiState.orders, period, today, customStart, customEnd) {
        uiState.orders.filter { o ->
            val date = reportDate(o.createdAt)?.toLocalDate()
            when (period) {
                "Hari Ini" -> date == today
                "7 Hari" -> date != null && !date.isBefore(today.minusDays(6)) && !date.isAfter(today)
                "30 Hari" -> date != null && !date.isBefore(today.minusDays(29)) && !date.isAfter(today)
                "Custom" -> date != null && customStart != null && customEnd != null && !date.isBefore(minOf(customStart, customEnd)) && !date.isAfter(maxOf(customStart, customEnd))
                else -> true
            }
        }
    }
    val paid = orders.filter { it.isPaid && it.status != OrderStatus.CANCELLED }
    val revenue = paid.sumOf { it.total }
    val dailyDates = remember(period, today, customStart, customEnd, uiState.orders) {
        val earliest = uiState.orders.mapNotNull { reportDate(it.createdAt)?.toLocalDate() }.minOrNull() ?: today.minusDays(6)
        val start = when (period) {
            "Hari Ini" -> today
            "7 Hari" -> today.minusDays(6)
            "30 Hari" -> today.minusDays(29)
            "Custom" -> customStart?.let { minOf(it, customEnd ?: it) } ?: today.minusDays(6)
            else -> maxOf(earliest, today.minusDays(30))
        }
        val end = if (period == "Custom") customEnd?.let { maxOf(it, customStart ?: it) } ?: today else today
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(0).coerceAtMost(30)
        (0..days).map { start.plusDays(it.toLong()) }
    }
    val daily = dailyDates.map { d -> paid.filter { reportDate(it.createdAt)?.toLocalDate() == d }.sumOf { it.total }.toFloat() }
    val products = paid.flatMap { it.orderItems }.groupBy { it.productName }.map { (n, items) -> Triple(n, items.sumOf { it.quantity }, items.sumOf { it.lineTotal }) }.sortedByDescending { it.third }
    val hourly = (0..23).map { h -> h to paid.filter { reportDate(it.createdAt)?.hour == h }.sumOf { it.total } }
    val payNames = mapOf("cash" to "Tunai", "qris" to "QRIS", "transfer" to "Transfer", "ewallet" to "E-Wallet")
    val payments = paid.groupBy { (it.paymentMethod ?: "cash").lowercase() }.map { (m, os) -> (payNames[m] ?: m.uppercase()) to os.sumOf { it.total } }.sortedByDescending { it.second }
    val channels = paid.groupBy { it.source.lowercase() }.map { (s, os) -> (when (s) { "qr" -> "QR Ordering / QR Meja"; "pos" -> "POS Kasir"; else -> s.uppercase() }) to os.sumOf { it.total } }.sortedByDescending { it.second }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) CsvExporter.writeSalesReportToUri(context, uri, "Laporan $selectedSection ($period)", if (selectedSection == "Detail Pesanan") orders else paid)
            .onSuccess { viewModel.showNotice("CSV $selectedSection berhasil disimpan.") }
            .onFailure { viewModel.showNotice(it.message ?: "Gagal menyimpan CSV.", isError = true) }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Hari Ini", "7 Hari", "30 Hari", "Semua Data").forEach { FilterChip(selected = period == it, onClick = { period = it }, label = { Text(it) }) }
            }
            OutlinedButton(onClick = { period = "Custom"; showStartDatePicker = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)) {
                Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Custom", maxLines = 1)
            }
            OutlinedButton(onClick = { csv.launch("laporan-${selectedSection.lowercase().replace(" ", "-")}.csv") }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }
        if (period == "Custom") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Text("Dari: ${customStart?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "Pilih tanggal"}")
                }
                OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Text("Sampai: ${customEnd?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "Pilih tanggal"}")
                }
            }
        }
        if (showStartDatePicker) {
            val picker = rememberDatePickerState(initialSelectedDateMillis = customStartMillis ?: today.atStartOfDay(utc).toInstant().toEpochMilli())
            DatePickerDialog(onDismissRequest = { showStartDatePicker = false }, confirmButton = {
                TextButton(onClick = { customStartMillis = picker.selectedDateMillis; showStartDatePicker = false; if (customEndMillis == null) showEndDatePicker = true }) { Text("Pilih") }
            }, dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Batal") } }) { DatePicker(state = picker) }
        }
        if (showEndDatePicker) {
            val picker = rememberDatePickerState(initialSelectedDateMillis = customEndMillis ?: customStartMillis ?: today.atStartOfDay(utc).toInstant().toEpochMilli())
            DatePickerDialog(onDismissRequest = { showEndDatePicker = false }, confirmButton = {
                TextButton(onClick = { customEndMillis = picker.selectedDateMillis; showEndDatePicker = false; period = "Custom" }) { Text("Pilih") }
            }, dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Batal") } }) { DatePicker(state = picker) }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (selectedSection) {
                "Ringkasan Laporan" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportMetric("Omzet Lunas", reportMoney(revenue), Modifier.weight(1f))
                        ReportMetric("Transaksi Lunas", paid.size.toString(), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportMetric("Rata-rata Transaksi", reportMoney(if (paid.isEmpty()) 0.0 else revenue / paid.size), Modifier.weight(1f))
                        ReportMetric("Unit Terjual", paid.sumOf { o -> o.orderItems.sumOf { it.quantity } }.toString(), Modifier.weight(1f))
                    }
                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Tren Omzet per Tanggal", style = MaterialTheme.typography.titleMedium); ReportLineChart(daily, Modifier.fillMaxWidth().height(160.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(dailyDates.firstOrNull()?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM")) ?: "-"); Text(dailyDates.lastOrNull()?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM")) ?: "-") } } }
                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Diagram Batang · Omzet Harian", style = MaterialTheme.typography.titleMedium); ReportBarChart(daily, Modifier.fillMaxWidth().height(140.dp)); dailyDates.forEachIndexed { i,d -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(d.format(java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM"))); Text(reportMoney(daily[i].toDouble()), fontWeight = FontWeight.SemiBold) } } } }
                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Diagram Bulat · Metode Pembayaran", style = MaterialTheme.typography.titleMedium); if (payments.isEmpty()) Text("Belum ada transaksi lunas pada periode ini.") else { ReportDonut(payments.map { it.second.toFloat() }, Modifier.fillMaxWidth().height(150.dp)); payments.forEachIndexed { i,p -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(10.dp), shape = RoundedCornerShape(50), color = ReportChartPalette[i % ReportChartPalette.size]) {}; Text(p.first, Modifier.weight(1f)); Text(reportMoney(p.second), fontWeight = FontWeight.SemiBold) } } } } }
                }
                "Penjualan Harian" -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Penjualan per Tanggal", style = MaterialTheme.typography.titleMedium)
                    val byDate = orders.mapNotNull { o -> reportDate(o.createdAt)?.toLocalDate()?.let { it to o } }.groupBy({ it.first }, { it.second }).toSortedMap(compareByDescending { it })
                    if (byDate.isEmpty()) Text("Tidak ada order pada periode ini.")
                    byDate.forEach { (date, os) -> HorizontalDivider(); Text(date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")), fontWeight = FontWeight.SemiBold); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Order ${os.size} · Lunas ${os.count { it.isPaid && it.status != OrderStatus.CANCELLED }} · Batal ${os.count { it.status == OrderStatus.CANCELLED }}"); Text(reportMoney(os.filter { it.isPaid && it.status != OrderStatus.CANCELLED }.sumOf { it.total }), fontWeight = FontWeight.Bold) } }
                } }
                "Penjualan Produk" -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Produk Terjual · transaksi lunas", style = MaterialTheme.typography.titleMedium); if (products.isEmpty()) Text("Belum ada produk terjual."); products.forEachIndexed { i,p -> HorizontalDivider(); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("${i+1}.", Modifier.padding(end=8.dp)); Column(Modifier.weight(1f)) { Text(p.first, fontWeight = FontWeight.SemiBold); Text("${p.second} unit", style = MaterialTheme.typography.bodySmall) }; Text(reportMoney(p.third), fontWeight = FontWeight.Bold) } } } }
                "Penjualan per Jam" -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Omzet per Jam", style = MaterialTheme.typography.titleMedium); ReportBarChart(hourly.map { it.second.toFloat() }, Modifier.fillMaxWidth().height(180.dp)); val peak = hourly.maxByOrNull { it.second }; Text(peak?.let { "Jam omzet tertinggi: ${"%02d".format(it.first)}.00–${"%02d".format((it.first+1)%24)}.00" } ?: "Belum ada data"); hourly.filter { it.second > 0 }.forEach { (h,v) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${"%02d".format(h)}.00–${"%02d".format((h+1)%24)}.00"); Text(reportMoney(v), fontWeight = FontWeight.SemiBold) } } } }
                "Laporan Pembayaran" -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Rekap Pembayaran Lunas", style = MaterialTheme.typography.titleMedium); if (payments.isEmpty()) Text("Belum ada pembayaran."); payments.forEach { p -> val n = paid.count { (payNames[it.paymentMethod?.lowercase() ?: "cash"] ?: it.paymentMethod?.uppercase() ?: "Tunai") == p.first }; HorizontalDivider(); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(p.first, fontWeight = FontWeight.SemiBold); Text("$n transaksi", style = MaterialTheme.typography.bodySmall) }; Text(reportMoney(p.second), fontWeight = FontWeight.Bold) } }; Text("Total: ${reportMoney(revenue)}", fontWeight = FontWeight.Bold) } }
                "Laporan Kanal" -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("POS Kasir vs QR Ordering", style = MaterialTheme.typography.titleMedium); if (channels.isEmpty()) Text("Belum ada data kanal.") else { ReportDonut(channels.map { it.second.toFloat() }, Modifier.fillMaxWidth().height(150.dp)); channels.forEachIndexed { i,c -> val count = paid.count { (if (it.source.equals("qr",true)) "QR Ordering / QR Meja" else if (it.source.equals("pos",true)) "POS Kasir" else it.source.uppercase()) == c.first }; HorizontalDivider(); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(10.dp), shape = RoundedCornerShape(50), color = ReportChartPalette[i % ReportChartPalette.size]) {}; Column(Modifier.weight(1f)) { Text(c.first, fontWeight = FontWeight.SemiBold); Text("$count transaksi", style = MaterialTheme.typography.bodySmall) }; Text(reportMoney(c.second), fontWeight = FontWeight.Bold) } } } } }
                else -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Detail Semua Pesanan", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Cari nomor order / pelanggan") }, singleLine = true)
                    val details = orders.filter { search.isBlank() || it.orderNo.toString().contains(search.trim(), true) || (it.customerName ?: "").contains(search.trim(), true) }.sortedByDescending { reportDate(it.createdAt)?.toInstant(java.time.ZoneOffset.UTC)?.toEpochMilli() ?: 0L }
                    Text("${details.size} order · ${details.count { it.isPaid }} lunas · ${details.count { it.status == OrderStatus.CANCELLED }} batal", style = MaterialTheme.typography.bodySmall)
                    if (details.isEmpty()) Text("Tidak ada order yang cocok.")
                    details.forEach { o -> HorizontalDivider(); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("Order #${o.orderNo} · ${o.customerName ?: "Walk-in"}", fontWeight = FontWeight.Bold); Text(reportDate(o.createdAt)?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) ?: o.createdAt, style = MaterialTheme.typography.bodySmall); Text("${if (o.source.equals("qr",true)) "QR Ordering" else "POS Kasir"} · ${o.paymentMethod?.uppercase() ?: "Metode belum ditentukan"}", style = MaterialTheme.typography.bodySmall); Text("${if (o.isPaid) "LUNAS" else if (o.status == OrderStatus.CANCELLED) "DIBATALKAN" else "BELUM LUNAS"} · ${o.status.labelId}", style = MaterialTheme.typography.labelSmall); o.orderItems.forEach { item -> Text("• ${item.quantity}× ${item.productName} — ${reportMoney(item.lineTotal)}", style = MaterialTheme.typography.bodySmall) } }; Text(reportMoney(o.total), fontWeight = FontWeight.Bold) } }
                } }
            }
        }
    }
}

@Composable private fun ReportMetric(title: String, value: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) } }
}
@Composable private fun ReportLineChart(values: List<Float>, modifier: Modifier = Modifier) {
    var selected by remember(values) { mutableIntStateOf(-1) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().pointerInput(values) {
            detectTapGestures { selected = ((it.x / size.width.coerceAtLeast(1).toFloat()) * values.size).toInt().coerceIn(0, (values.size - 1).coerceAtLeast(0)) }
        }.pointerInput(values) {
            detectHorizontalDragGestures { change, _ -> change.consume(); selected = ((change.position.x / size.width.coerceAtLeast(1).toFloat()) * values.size).toInt().coerceIn(0, (values.size - 1).coerceAtLeast(0)) }
        }) {
            if (values.isEmpty()) return@Canvas
            val l=8.dp.toPx(); val r=size.width-8.dp.toPx(); val t=10.dp.toPx(); val b=size.height-10.dp.toPx(); val max=(values.maxOrNull() ?: 0f).coerceAtLeast(1f)
            repeat(4) { i -> val y=t+(b-t)*i/3f; drawLine(grid, androidx.compose.ui.geometry.Offset(l,y), androidx.compose.ui.geometry.Offset(r,y), 1.dp.toPx()) }
            val pts=values.mapIndexed { i,v -> androidx.compose.ui.geometry.Offset(if(values.size==1)(l+r)/2 else l+(r-l)*i/(values.size-1).toFloat(), b-v/max*(b-t)) }
            if(pts.size>1) {
                val path=androidx.compose.ui.graphics.Path().apply { moveTo(pts.first().x,pts.first().y); pts.drop(1).forEach { lineTo(it.x,it.y) } }
                val shadow=androidx.compose.ui.graphics.Path().apply { moveTo(pts.first().x,pts.first().y+5.dp.toPx()); pts.drop(1).forEach { lineTo(it.x,it.y+5.dp.toPx()) } }
                drawPath(shadow, ReportChartPalette[0].copy(alpha=.18f), style=androidx.compose.ui.graphics.drawscope.Stroke(7.dp.toPx(), cap=androidx.compose.ui.graphics.StrokeCap.Round))
                drawPath(path,ReportChartPalette[0],style=androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap=androidx.compose.ui.graphics.StrokeCap.Round))
                if (selected in pts.indices) { val p=pts[selected]; drawLine(ReportChartPalette[0].copy(alpha=.4f), androidx.compose.ui.geometry.Offset(p.x,t), androidx.compose.ui.geometry.Offset(p.x,b), 1.dp.toPx()); drawCircle(ReportChartPalette[0],5.dp.toPx(),p) }
            }
        }
        if (selected in values.indices) Surface(Modifier.align(Alignment.TopStart).padding(4.dp), color=MaterialTheme.colorScheme.surface, shape=RoundedCornerShape(8.dp), shadowElevation=3.dp) {
            Text("Titik ${selected+1}: ${reportMoney(values[selected].toDouble())}", Modifier.padding(horizontal=8.dp, vertical=5.dp), style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable private fun ReportBarChart(values: List<Float>, modifier: Modifier = Modifier) {
    var selected by remember(values) { mutableIntStateOf(-1) }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().pointerInput(values) {
            detectTapGestures { selected = ((it.x / size.width.coerceAtLeast(1).toFloat()) * values.size).toInt().coerceIn(0, (values.size - 1).coerceAtLeast(0)) }
        }.pointerInput(values) {
            detectHorizontalDragGestures { change, _ -> change.consume(); selected = ((change.position.x / size.width.coerceAtLeast(1).toFloat()) * values.size).toInt().coerceIn(0, (values.size - 1).coerceAtLeast(0)) }
        }) {
            if(values.isEmpty()) return@Canvas
            val max=(values.maxOrNull() ?: 0f).coerceAtLeast(1f); val gap=3.dp.toPx(); val w=((size.width-gap*(values.size+1))/values.size).coerceAtLeast(2.dp.toPx())
            values.forEachIndexed { i,v ->
                val h=v/max*(size.height-8.dp.toPx()); val x=gap+i*(w+gap)
                drawRoundRect(ReportChartPalette[0].copy(alpha=.14f), androidx.compose.ui.geometry.Offset(x,size.height-h+4.dp.toPx()), androidx.compose.ui.geometry.Size(w,h.coerceAtLeast(2.dp.toPx())), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                drawRoundRect(if(i==selected) ReportChartPalette[0] else ReportChartPalette[0].copy(alpha=.82f), androidx.compose.ui.geometry.Offset(x,size.height-h), androidx.compose.ui.geometry.Size(w,h.coerceAtLeast(2.dp.toPx())), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
        }
        if (selected in values.indices) Surface(Modifier.align(Alignment.TopStart).padding(4.dp), color=MaterialTheme.colorScheme.surface, shape=RoundedCornerShape(8.dp), shadowElevation=3.dp) {
            Text("Data ${selected+1}: ${reportMoney(values[selected].toDouble())}", Modifier.padding(horizontal=8.dp, vertical=5.dp), style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable private fun ReportDonut(values: List<Float>, modifier: Modifier = Modifier) {
    var selected by remember(values) { mutableIntStateOf(-1) }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().pointerInput(values) {
            detectTapGestures { selected = ((it.x / size.width.coerceAtLeast(1).toFloat()) * values.size).toInt().coerceIn(0, (values.size - 1).coerceAtLeast(0)) }
        }.pointerInput(values) {
            detectHorizontalDragGestures { change, _ -> change.consume(); selected = ((change.position.x / size.width.coerceAtLeast(1).toFloat()) * values.size).toInt().coerceIn(0, (values.size - 1).coerceAtLeast(0)) }
        }) {
            val total=values.sum().coerceAtLeast(1f); val d=(minOf(size.width,size.height)-24.dp.toPx()).coerceAtLeast(1.dp.toPx()); val x=(size.width-d)/2; val y=(size.height-d)/2; var start=-90f
            values.forEachIndexed { i,v -> val sweep=v.coerceAtLeast(0f)/total*360f; drawArc(ReportChartPalette[i%ReportChartPalette.size].copy(alpha=if(selected==-1 || selected==i) 1f else .45f),start,sweep,false,androidx.compose.ui.geometry.Offset(x,y),androidx.compose.ui.geometry.Size(d,d),style=androidx.compose.ui.graphics.drawscope.Stroke(if(selected==i) 30.dp.toPx() else 24.dp.toPx())); start+=sweep }
        }
        if (selected in values.indices) Surface(Modifier.align(Alignment.TopCenter).padding(4.dp), color=MaterialTheme.colorScheme.surface, shape=RoundedCornerShape(8.dp), shadowElevation=3.dp) {
            Text("Bagian ${selected+1}: ${reportMoney(values[selected].toDouble())}", Modifier.padding(horizontal=8.dp, vertical=5.dp), style=MaterialTheme.typography.labelSmall)
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
    val modules = listOf("dashboard" to "Dashboard", "ai_assistant" to "AI Assistant", "orders" to "Kasir & Pesanan", "payments" to "Pembayaran", "shift_management" to "Buka / Tutup Shift", "shift_audit" to "Audit Shift", "activity_log" to "Log Aktivitas", "products" to "Produk & Stok", "inventory" to "Inventory", "tables" to "Meja & QR Menu", "reports" to "Laporan Penjualan", "expenses" to "Pengeluaran & Laba Rugi", "customers" to "Pelanggan & Loyalitas", "promotions" to "Promosi & Diskon", "printer_management" to "Printer", "settings" to "Pengaturan Outlet", "staff_management" to "Manajemen Pegawai", "export_data" to "Export / Download Data")
    val metrics = listOf("dashboard.total_sales" to "Total penjualan", "dashboard.paid_orders" to "Jumlah transaksi lunas", "dashboard.average_transaction" to "Rata-rata transaksi", "dashboard.peak_hour" to "Jam tersibuk", "dashboard.trend_chart" to "Grafik tren omzet", "dashboard.hourly_activity" to "Aktivitas per jam", "dashboard.best_sellers" to "Menu terlaris", "dashboard.quick_actions" to "Akses cepat")
    val actionPermissions = listOf(
        "products.edit" to "Tambah / edit produk",
        "products.delete" to "Hapus produk",
        "inventory.edit" to "Penyesuaian stok",
        "payments" to "Lihat / proses pembayaran",
        "history.delete" to "Hapus transaksi / detail riwayat",
        "staff.manage" to "Kelola pegawai dan hak akses",
        "shift.manage" to "Buka / tutup dan kelola shift",
        "audit.view" to "Lihat audit shift",
        "settings.manage" to "Ubah pengaturan outlet",
        "pos.manual_discount" to "Diskon manual di kasir",
        "pos.void_transaction" to "Batalkan transaksi",
        "pos.open_drawer" to "Buka laci kas",
        "shift.view_cash" to "Lihat nominal kas shift",
        "inventory.view_stock" to "Lihat stok",
        "inventory.adjust_stock" to "Tambah / kurangi stok",
        "inventory.purchase_order" to "Kelola pembelian stok",
        "report.view" to "Lihat laporan",
        "report.export" to "Ekspor laporan",
        "customers.export" to "Ekspor data pelanggan",
        "payments.manage_methods" to "Kelola metode pembayaran",
        "printer.manage" to "Kelola printer",
        "promotions.manage" to "Kelola promosi"
    )
    pendingDelete?.let { target -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Hapus profil staf?") }, text = { Text("Profil ${target.fullName} dihapus dari daftar POS. User Supabase Auth tidak ikut terhapus; penghapusan permanen perlu fungsi admin server.") }, confirmButton = { Button(onClick = { viewModel.deleteStaffProfile(target.userId); pendingDelete = null }, enabled = isOwner && !uiState.isLoading) { Text("Hapus profil") } }, dismissButton = { OutlinedButton(onClick = { pendingDelete = null }) { Text("Batal") } }) }
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
                    if (uiState.backendStatus == BackendIntegrationStatus.LOCAL_SANDBOX_UI) OutlinedButton(onClick = { viewModel.switchActiveStaffProfile(st) }, enabled = !isCurrent && st.isActive) { Text(if (isCurrent) "Profil ini" else "Uji profil") }
                }
                if (!isCurrent && isOwner) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.setStaffActive(st.userId, !st.isActive) }, modifier = Modifier.weight(1f), enabled = !uiState.isLoading) { Text(if (st.isActive) "Nonaktifkan" else "Aktifkan") }
                    OutlinedButton(onClick = { pendingDelete = st }, modifier = Modifier.weight(1f), enabled = !uiState.isLoading) { Text("Delete") }
                }
                Text("Hak akses menu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                modules.forEach { (key, label) -> PermissionToggleRow(label, st.permissions[key] == true, isOwner && !isCurrent && st.role != StaffRole.OWNER && !uiState.isLoading) { viewModel.toggleStaffPermission(st.userId, key) } }
                HorizontalDivider()
                Text("Detail Dashboard", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                metrics.forEach { (key, label) -> val allowed = if (st.permissions.containsKey(key)) st.permissions[key] == true else st.permissions["dashboard"] == true; PermissionToggleRow(label, allowed, isOwner && !isCurrent && st.role != StaffRole.OWNER && !uiState.isLoading) { viewModel.toggleStaffPermission(st.userId, key) } }
                HorizontalDivider()
                Text("Hak akses tindakan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Izin ini dipakai oleh aturan akses backend untuk tindakan sensitif. Berikan hanya sesuai tugas pegawai.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                actionPermissions.forEach { (key, label) ->
                    PermissionToggleRow(label, st.permissions[key] == true, isOwner && !isCurrent && st.role != StaffRole.OWNER && !uiState.isLoading) {
                        viewModel.toggleStaffPermission(st.userId, key)
                    }
                }
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
    var openingHours by remember(uiState.outletSettings.openingHours) { mutableStateOf(uiState.outletSettings.openingHours) }
    var instagram by remember(uiState.outletSettings.instagram) { mutableStateOf(uiState.outletSettings.instagram) }
    var receiptNote by remember(uiState.outletSettings.receiptNote) { mutableStateOf(uiState.outletSettings.receiptNote) }
    var receiptFooter by remember(uiState.outletSettings.receiptFooter) { mutableStateOf(uiState.outletSettings.receiptFooter) }
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
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var alertSoundUploading by remember { mutableStateOf(false) }
    var alertSoundUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val localUrl = context.getSharedPreferences("order_alert_settings", android.content.Context.MODE_PRIVATE)
            .getString("sound_url", null)
        // Server copy is the source of truth so the choice survives logout and app restarts.
        viewModel.loadOrderAlertSoundUrl { remoteUrl ->
            val resolved = remoteUrl ?: localUrl
            alertSoundUrl = resolved
            if (!resolved.isNullOrBlank()) {
                context.getSharedPreferences("order_alert_settings", android.content.Context.MODE_PRIVATE)
                    .edit().putString("sound_url", resolved).apply()
            }
        }
    }
    val alertSoundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            alertSoundUploading = true
            val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
            val ext = when (mime.lowercase()) {
                "audio/mp4", "audio/x-m4a" -> "m4a"
                "audio/wav", "audio/x-wav" -> "wav"
                "audio/ogg", "application/ogg" -> "ogg"
                "audio/aac" -> "aac"
                "audio/webm" -> "webm"
                else -> "mp3"
            }
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                alertSoundUploading = false
                viewModel.showNotice("File suara tidak bisa dibaca.", isError = true)
            } else {
                viewModel.uploadOrderAlertSound(bytes, mime, ext) { url ->
                    alertSoundUrl = url
                    context.getSharedPreferences("order_alert_settings", android.content.Context.MODE_PRIVATE)
                        .edit().putString("sound_url", url).apply()
                    alertSoundUploading = false
                }
            }
        }
    }
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
        // Info Cafe yang ditampilkan pada halaman Info Cafe QR Ordering pelanggan.
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Info Cafe di QR Ordering", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Informasi ini tampil ke pelanggan pada halaman Info Cafe saat mereka membuka QR meja. Perubahan disimpan ke outlet yang sama dengan web app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(value = outletName, onValueChange = { outletName = it }, label = { Text("Nama Cafe") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = outletAddress, onValueChange = { outletAddress = it }, label = { Text("Alamat Cafe") }, placeholder = { Text("Alamat lengkap cafe") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = outletPhone, onValueChange = { outletPhone = it }, label = { Text("Kontak / Telepon / WhatsApp") }, placeholder = { Text("08xxxxxxxxxx") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = openingHours, onValueChange = { openingHours = it }, label = { Text("Jam Operasional") }, placeholder = { Text("Setiap hari, 17.00–24.00") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = instagram, onValueChange = { instagram = it }, label = { Text("Instagram Cafe") }, placeholder = { Text("@akun_cafe") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = {
                    viewModel.updateOutletSettings(
                        uiState.outletSettings.copy(
                            name = outletName.trim(),
                            address = outletAddress.trim(),
                            phone = outletPhone.trim(),
                            openingHours = openingHours.trim(),
                            instagram = instagram.trim(),
                            receiptNote = receiptNote.trim(),
                            receiptFooter = receiptFooter.trim(),
                            publicWebBaseUrl = webBaseUrl.trim()
                        )
                    )
                }) {
                    Text("Simpan Info Cafe QR Ordering")
                }
            }
        }

        // Informasi khusus struk kasir/pelanggan, dipisahkan dari Info Cafe QR.
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Pengaturan Catatan Struk", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = receiptNote, onValueChange = { receiptNote = it }, label = { Text("Catatan struk (opsional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = receiptFooter, onValueChange = { receiptFooter = it }, label = { Text("Footer struk") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = {
                    viewModel.updateOutletSettings(
                        uiState.outletSettings.copy(
                            name = outletName.trim(),
                            address = outletAddress.trim(),
                            phone = outletPhone.trim(),
                            openingHours = openingHours.trim(),
                            instagram = instagram.trim(),
                            receiptNote = receiptNote.trim(),
                            receiptFooter = receiptFooter.trim(),
                            publicWebBaseUrl = webBaseUrl.trim()
                        )
                    )
                }) {
                    Text("Simpan Pengaturan Struk")
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
                Text("5. Suara Otomatis Pesanan Masuk", style = MaterialTheme.typography.titleMedium)
                Text("Unggah file suara pengumuman, misalnya: “Halo, halo kasir, ada pesanan masuk.” Saat aplikasi kasir terbuka dan mendeteksi pesanan baru, suara ini akan diputar otomatis. Format audio umum didukung; ukuran maksimal 10 MB.")
                Text(
                    if (alertSoundUrl.isNullOrBlank()) "Belum ada suara khusus. Pesanan baru tidak akan memutar file kustom."
                    else "Suara aktif: ${alertSoundUrl!!.substringAfterLast('/').take(42)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { alertSoundPicker.launch("audio/*") }, enabled = !alertSoundUploading) {
                        Text(if (alertSoundUploading) "Mengunggah suara..." else "Unggah File Suara")
                    }
                    if (!alertSoundUrl.isNullOrBlank()) {
                        OutlinedButton(onClick = {
                            context.getSharedPreferences("order_alert_settings", android.content.Context.MODE_PRIVATE)
                                .edit().remove("sound_url").apply()
                            alertSoundUrl = null
                        }) { Text("Hapus Suara") }
                    }
                }
                Text("Catatan: pengaturan suara tersimpan di perangkat kasir ini. Agar berbunyi, aplikasi harus berjalan dan terhubung ke internet.", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("6. Ganti Password Akun", style = MaterialTheme.typography.titleMedium)
                Text("Password akun login Supabase yang sedang aktif akan diperbarui.")
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Password baru (minimal 8 karakter)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = { confirmNewPassword = it },
                    label = { Text("Ulangi password baru") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (newPassword.length < 8) {
                            viewModel.showNotice("Password baru minimal 8 karakter.", isError = true)
                        } else if (newPassword != confirmNewPassword) {
                            viewModel.showNotice("Konfirmasi password belum sama.", isError = true)
                        } else {
                            viewModel.changePassword(newPassword)
                            newPassword = ""
                            confirmNewPassword = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.isLoggedIn && newPassword.length >= 8 && newPassword == confirmNewPassword
                ) { Text("Simpan Password Baru") }
                if (!uiState.isLoggedIn) Text("Login dengan akun Supabase untuk mengganti password.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("7. Metode Pembayaran", style = MaterialTheme.typography.titleMedium)
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
                            if (!method.imageUrl.isNullOrBlank()) AsyncImage(model = method.imageUrl, contentDescription = "Foto metode pembayaran", modifier = Modifier.size(if (method.type.equals("qris", ignoreCase = true)) 112.dp else 56.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                            Text(if (method.isActive) "Aktif" else "Nonaktif", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            OutlinedButton(onClick = {
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
                            OutlinedButton(onClick = {
                                viewModel.savePaymentMethod(method.copy(isActive = !method.isActive))
                            }) { Text(if (method.isActive) "Nonaktifkan" else "Aktifkan") }
                            OutlinedButton(onClick = { viewModel.deletePaymentMethod(method.id) }) { Text("Hapus") }
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
                            FilterChip(selected = paymentType == code, onClick = {
                                paymentType = code
                            }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(value = paymentName, onValueChange = { paymentName = it }, label = { Text("Nama metode pembayaran") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = paymentProvider, onValueChange = { paymentProvider = it }, label = { Text("Provider / nama bank (mis. BCA, GoPay)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = paymentAccountName, onValueChange = { paymentAccountName = it }, label = { Text("Nama pemilik rekening (opsional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = paymentAccountNumber, onValueChange = { paymentAccountNumber = it }, label = { Text("Nomor rekening / nomor e-wallet (opsional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = paymentInstructions, onValueChange = { paymentInstructions = it }, label = { Text("Instruksi pembayaran") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { paymentImagePicker.launch("image/*") }, enabled = !paymentImageUploading) {
                                Text(if (paymentImageUploading) "Mengunggah foto..." else "Unggah foto / logo")
                            }
                            if (!paymentImageUrl.isNullOrBlank()) AsyncImage(
                                model = paymentImageUrl,
                                contentDescription = "Foto metode pembayaran",
                                modifier = Modifier.size(if (paymentType == "qris") 112.dp else 56.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                        Text(
                            if (paymentType == "qris") "QRIS ditampilkan besar di checkout agar mudah dipindai atau di-screenshot."
                            else "Gunakan logo bank atau e-wallet. Tampil lebih kecil daripada QRIS.",
                            style = MaterialTheme.typography.bodySmall
                        )
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
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.scanBluetoothPrinters()
        } else {
            viewModel.showNotice("Izin Bluetooth diperlukan untuk mencari dan menghubungkan printer.", isError = true)
        }
    }
    LaunchedEffect(Unit) { viewModel.loadPrinterSettingsFromSupabase() }
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
                    OutlinedButton(onClick = {
                        if (viewModel.printerManager.hasBluetoothPermissions()) {
                            viewModel.scanBluetoothPrinters()
                        } else {
                            bluetoothPermissionLauncher.launch(viewModel.printerManager.requiredRuntimePermissions())
                        }
                    }) {
                        Text("Pindai Bluetooth")
                    }
                }
                Text(
                    "Cara menghubungkan: 1) Nyalakan printer thermal dan aktifkan Bluetooth. 2) Buka Pengaturan Bluetooth Android, lalu pasangkan (pair) printer terlebih dahulu. 3) Kembali ke sini dan tekan Pindai Bluetooth. 4) Pilih perangkat pada bagian Printer Struk Kasir atau Printer Tiket Dapur. 5) Tekan Tes Cetak untuk memastikan koneksi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Catatan: gunakan printer thermal Bluetooth yang mendukung ESC/POS (Bluetooth Classic / SPP). Aplikasi dapat menyimpan dua printer terpisah. Printer harus sudah dipasangkan dari pengaturan Android; tombol Pindai hanya menampilkan perangkat yang sudah dipasangkan. Kompatibilitas tetap perlu diuji pada printer fisik.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                                    if (viewModel.printerManager.hasBluetoothPermissions()) {
                                        viewModel.testPrintToThermal(role)
                                    } else {
                                        bluetoothPermissionLauncher.launch(viewModel.printerManager.requiredRuntimePermissions())
                                    }
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
                                        onClick = { viewModel.bindPrinterDevice(role, devName, mac, false) },
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


// ============================================================================
// SUPER ADMIN — OWNER MANAGEMENT (platform-level, never shown to Owner)
// ============================================================================
@Composable
fun ManajemenOwnerComposeScreen(viewModel: PosViewModel, uiState: PosUiState) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<Pair<OwnerAccount, String>?>(null) }
    LaunchedEffect(Unit) { viewModel.refreshOwnerAccounts() }

    pendingAction?.let { (owner, action) ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (action == "delete") "Hapus akun Owner?" else if (owner.isActive) "Nonaktifkan Owner?" else "Aktifkan Owner?") },
            text = { Text(if (action == "delete") "Akun ${owner.fullName} (${owner.email}) akan dihapus permanen dari autentikasi. Tindakan ini tidak bisa dibatalkan." else if (owner.isActive) "Owner ${owner.fullName} tidak akan bisa menggunakan aplikasi sampai diaktifkan kembali." else "Owner ${owner.fullName} akan diizinkan menggunakan aplikasi kembali.") },
            confirmButton = {
                Button(
                    enabled = !uiState.isLoading,
                    onClick = {
                        when (action) {
                            "delete" -> viewModel.deleteOwnerAccount(owner.userId)
                            "toggle" -> viewModel.setOwnerAccountActive(owner.userId, !owner.isActive)
                        }
                        pendingAction = null
                    }
                ) { Text(if (action == "delete") "Hapus permanen" else "Konfirmasi") }
            },
            dismissButton = { OutlinedButton(onClick = { pendingAction = null }) { Text("Batal") } }
        )
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Manajemen Owner", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Khusus Super Admin. Owner yang dibuat di sini mendapat undangan melalui email. Menu ini tidak tersedia untuk akun Owner biasa.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Nama Owner") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Owner") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        onClick = {
                            viewModel.createOwnerAccount(fullName, email)
                            fullName = ""
                            email = ""
                        },
                        enabled = !uiState.isLoading && fullName.isNotBlank() && email.contains("@"),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (uiState.isLoading) "Memproses..." else "＋ Tambah Owner") }
                    OutlinedButton(onClick = { viewModel.refreshOwnerAccounts() }, enabled = !uiState.isLoading, modifier = Modifier.fillMaxWidth()) { Text("Refresh daftar Owner") }
                }
            }
        }
        item {
            Text("Daftar Owner (${uiState.ownerAccounts.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (uiState.ownerAccounts.isEmpty()) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Text("Belum ada data Owner atau daftar belum berhasil dimuat.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(uiState.ownerAccounts, key = { it.userId }) { owner ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(owner.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(owner.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(onClick = {}, label = { Text(if (owner.isActive) "Aktif" else "Nonaktif") })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pendingAction = owner to "toggle" },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (owner.isActive) "Nonaktifkan" else "Aktifkan") }
                        OutlinedButton(
                            onClick = { pendingAction = owner to "delete" },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Hapus Owner") }
                    }
                }
            }
        }
    }
}
