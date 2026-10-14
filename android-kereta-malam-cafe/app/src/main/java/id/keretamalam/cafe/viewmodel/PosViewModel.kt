package id.keretamalam.cafe.viewmodel

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.keretamalam.cafe.data.config.BackendIntegrationStatus
import id.keretamalam.cafe.data.config.SupabaseConfigManager
import id.keretamalam.cafe.data.model.*
import id.keretamalam.cafe.data.repository.CafeFlowRepository
import id.keretamalam.cafe.hardware.EscPosBluetoothManager
import id.keretamalam.cafe.hardware.PrinterRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PosUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val isCheckoutProcessing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val darkTheme: Boolean = false,
    val uiScalePercent: Int = 100,
    val backendStatus: BackendIntegrationStatus = BackendIntegrationStatus.INACTIVE_PENDING_VERIFICATION,
    val currentProjectId: String = "",
    val currentSupabaseUrl: String = "",
    val staffList: List<StaffProfile> = SandboxSeedData.staffList,
    val staffProfile: StaffProfile = SandboxSeedData.staffList.first(),
    val categories: List<Category> = SandboxSeedData.categories,
    val products: List<Product> = SandboxSeedData.products,
    val productAddons: List<ProductAddon> = emptyList(),
    val customizationOptions: List<ProductCustomizationOption> = emptyList(),
    val tables: List<RestaurantTable> = SandboxSeedData.tables,
    val promotions: List<Promotion> = SandboxSeedData.promotions,
    val orders: List<OrderWithItems> = SandboxSeedData.initialOrders,
    val shifts: List<StaffShift> = SandboxSeedData.initialShifts,
    val expenses: List<Expense> = SandboxSeedData.initialExpenses,
    val inventoryMovements: List<InventoryMovement> = SandboxSeedData.initialInventoryMovements,
    val auditLogs: List<AuditLog> = SandboxSeedData.initialAuditLogs,
    val outletSettings: OutletSettings = OutletSettings(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val aiReplyText: String? = null,
    // Kasir & Keranjang State
    val selectedCategoryId: String = "ALL",
    val searchQuery: String = "",
    val cart: List<CartItem> = emptyList(),
    val selectedTableId: String? = SandboxSeedData.tables.first().id,
    val customerName: String = "",
    val orderNotes: String = "",
    val selectedPromotionId: String? = null,
    val paymentMethod: String = "cash",
    val cashReceivedInput: String = "",
    val receiptDialogOrder: OrderWithItems? = null,
    val thermalPreviewText: String = "",
    // Jangan tampilkan perangkat printer contoh/fiktif. Daftar diisi setelah scan Bluetooth nyata.
    val pairedBluetoothDevices: List<Pair<String, String>> = emptyList()
) {
    val subtotal: Double
        get() = cart.sumOf { it.lineTotal }

    val selectedPromotion: Promotion?
        get() = promotions.firstOrNull { it.id == selectedPromotionId }

    val activePromotion: Promotion?
        get() = selectedPromotion?.takeIf { it.active && it.allowCashier }

    val selectedPaymentOption: PaymentMethod?
        get() = paymentMethods.firstOrNull { it.code.equals(paymentMethod, true) }
            ?: paymentMethods.firstOrNull { it.type.equals(paymentMethod, true) }

    val paymentMethodType: String
        get() = selectedPaymentOption?.type?.lowercase() ?: paymentMethod.lowercase()

    val promotionEligibilityReason: String?
        get() {
            val promo = selectedPromotion ?: return if (selectedPromotionId == null) null else "Promo tidak ditemukan. Pilih promo lain."
            if (!promo.active) return "Promo ini sedang nonaktif."
            if (!promo.allowCashier) return "Promo ini tidak tersedia untuk kasir."
            if (subtotal < promo.minPurchase) return "Minimum belanja ${RupiahFormatter.format(promo.minPurchase)} belum tercapai."
            val matchingItems = if (promo.scope == "product") cart.filter { it.product.id == promo.productId } else cart
            val matchingQuantity = matchingItems.sumOf { it.quantity }
            if (matchingQuantity < promo.minQuantity.coerceAtLeast(1)) {
                return if (promo.scope == "product") "Minimal ${promo.minQuantity} item produk yang dipilih diperlukan untuk promo ini."
                else "Minimal ${promo.minQuantity} item diperlukan untuk promo ini."
            }
            if (promo.scope == "product" && matchingItems.isEmpty()) {
                return "Tambahkan produk yang memenuhi promo ke keranjang."
            }
            return null
        }

    val isPromotionEligible: Boolean
        get() = selectedPromotionId == null || (activePromotion != null && promotionEligibilityReason == null)

    val discountAmount: Double
        get() {
            val promo = activePromotion ?: return 0.0
            if (promotionEligibilityReason != null) return 0.0
            val eligibleSubtotal = if (promo.scope == "product") {
                cart.filter { it.product.id == promo.productId }.sumOf { it.lineTotal }
            } else subtotal
            if (eligibleSubtotal <= 0.0) return 0.0
            val raw = if (promo.type == "percentage") {
                eligibleSubtotal * (promo.value / 100.0)
            } else {
                promo.value
            }
            return raw.coerceIn(0.0, eligibleSubtotal)
        }

    val taxableBase: Double
        get() = (subtotal - discountAmount).coerceAtLeast(0.0)

    val taxAndServiceAmount: Double
        get() {
            val tax = if (outletSettings.taxEnabled) taxableBase * (outletSettings.taxRate / 100.0) else 0.0
            val svc = if (!outletSettings.serviceEnabled) 0.0 else if (outletSettings.serviceFeeMode == "fixed") {
                outletSettings.serviceFixedAmount.coerceAtLeast(0.0)
            } else {
                taxableBase * (outletSettings.serviceRate.coerceAtLeast(0.0) / 100.0)
            }
            return kotlin.math.round(tax + svc)
        }

    val grandTotal: Double
        get() = taxableBase + taxAndServiceAmount

    val cashReceivedValue: Double
        get() = cashReceivedInput.toDoubleOrNull() ?: grandTotal

    val changeAmount: Double
        get() = (cashReceivedValue - grandTotal).coerceAtLeast(0.0)

    val paidOrders: List<OrderWithItems>
        get() = orders.filter { it.isPaid && it.status != OrderStatus.CANCELLED }

    val todayRevenue: Double
        get() = paidOrders.sumOf { it.total }

    val activeOrdersCount: Int
        get() = orders.count {
            it.status == OrderStatus.NEW || it.status == OrderStatus.PREPARING || it.status == OrderStatus.READY
        }

    val lowStockProducts: List<Product>
        get() = products.filter { it.stock <= it.lowStockThreshold }
}

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CafeFlowRepository(application)
    private var orderBaselineInitialized = false
    private var lastPlayedOrderAlertAt = 0L
    val printerManager = EscPosBluetoothManager(application)
    private val checkoutMutex = Mutex()
    private val orderRefreshMutex = Mutex()

    private val _uiState = MutableStateFlow(
        PosUiState(
            thermalPreviewText = formatOrderForThermalPreview(SandboxSeedData.initialOrders.first(), false)
        )
    )
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    init {
        initializeBackendConnection()

        // QR orders are written by the web menu directly to Supabase. Keep the
        // native cashier's order lane fresh while signed in, without reloading
        // products, settings, reports, and the rest of the app every cycle.
        viewModelScope.launch {
            _uiState
                .map { it.isLoggedIn && it.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED }
                .distinctUntilChanged()
                .collectLatest { shouldPoll ->
                    if (shouldPoll) {
                        while (true) {
                            kotlinx.coroutines.delay(3_000)
                            refreshOrdersSilently()
                        }
                    }
                }
        }
    }

    private suspend fun refreshOrdersSilently() {
        if (
            !_uiState.value.isLoggedIn ||
            _uiState.value.backendStatus != BackendIntegrationStatus.ACTIVE_VERIFIED
        ) return

        if (!orderRefreshMutex.tryLock()) return
        try {
            val latestOrders = repository.fetchOrders(limitCount = 500)
            val currentOrders = _uiState.value.orders
            if (orderBaselineInitialized) {
                val knownIds = currentOrders.map { it.id }.toSet()
                val hasNewIncomingOrder = latestOrders.any { order ->
                    order.id !in knownIds &&
                        (order.status == OrderStatus.NEW)
                }
                if (hasNewIncomingOrder) playOrderAlertSound()
            } else {
                orderBaselineInitialized = true
            }
            _uiState.update { current ->
                if (
                    current.isLoggedIn &&
                    current.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED
                ) {
                    current.copy(orders = latestOrders)
                } else {
                    current
                }
            }
        } catch (_: Exception) {
            // A temporary network hiccup must not log out the cashier or erase
            // the last known orders. The next cycle retries automatically.
        } finally {
            orderRefreshMutex.unlock()
        }
    }

    /**
     * Production credentials are supplied by the build pipeline. Verify them quietly
     * on first launch so staff never need to configure developer infrastructure.
     */
    private fun initializeBackendConnection() {
        viewModelScope.launch {
            val creds = SupabaseConfigManager.loadCredentials(getApplication())
            if (creds.status == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                refreshBackendConfigStatus()
                return@launch
            }

            val expectedUrl = "https://${SupabaseConfigManager.CONFIRMED_PROJECT_ID}.supabase.co"
            if (
                creds.projectId != SupabaseConfigManager.CONFIRMED_PROJECT_ID ||
                creds.supabaseUrl != expectedUrl ||
                creds.publishableKey.isBlank()
            ) {
                refreshBackendConfigStatus()
                _uiState.update {
                    it.copy(errorMessage = "Aplikasi belum siap digunakan. Silakan hubungi penyedia aplikasi.")
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.verifyDatabaseConnection(
                    projectId = creds.projectId,
                    supabaseUrl = creds.supabaseUrl,
                    publishableKey = creds.publishableKey
                )
                SupabaseConfigManager.saveVerifiedCredentials(
                    context = getApplication(),
                    projectId = creds.projectId,
                    supabaseUrl = creds.supabaseUrl,
                    publishableKey = creds.publishableKey,
                    publicWebMenuBaseUrl = creds.publicWebMenuBaseUrl
                )
            }.onSuccess {
                refreshBackendConfigStatus()
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
            }.onFailure {
                SupabaseConfigManager.deactivateIntegration(getApplication())
                refreshBackendConfigStatus()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Layanan sementara tidak tersedia. Periksa koneksi internet lalu coba lagi."
                    )
                }
            }
        }
    }

    private fun nowString(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("id", "ID")).format(Date())
    }

    fun formatOrderForThermalPreview(order: OrderWithItems, isKitchen: Boolean): String {
        val divider = "--------------------------------"
        if (isKitchen) {
            return buildString {
                appendLine("KERETA MALAM CAFE")
                appendLine("TIKET PESANAN DAPUR / BAR")
                appendLine(divider)
                appendLine("ORDER #${order.orderNo} (${order.source.uppercase()})")
                appendLine("MEJA  : ${order.restaurantTable?.name ?: "Takeaway"}")
                appendLine("WAKTU : ${order.createdAt}")
                appendLine(divider)
                order.orderItems.forEach { item ->
                    appendLine("${item.quantity}x ${item.productName}")
                    if (!item.note.isNullOrBlank()) appendLine("   Catatan: ${item.note}")
                }
                if (!order.notes.isNullOrBlank()) appendLine("CATATAN ORDER: ${order.notes}")
                appendLine(divider)
                appendLine("Mohon segera disiapkan.")
            }
        }
        val payment = order.payments.firstOrNull()
        return buildString {
            appendLine("KERETA MALAM CAFE")
            appendLine("STRUK PEMBAYARAN RESMI")
            appendLine(divider)
            appendLine("Order #${order.orderNo} · ${order.source.uppercase()}")
            appendLine("Waktu     : ${order.createdAt}")
            appendLine("Meja      : ${order.restaurantTable?.name ?: "Takeaway"}")
            if (!order.customerName.isNullOrBlank()) appendLine("Pelanggan : ${order.customerName}")
            appendLine(divider)
            order.orderItems.forEach { item ->
                appendLine(item.productName)
                appendLine("  ${item.quantity} x ${RupiahFormatter.format(item.unitPrice)} = ${RupiahFormatter.format(item.lineTotal)}")
                if (!item.note.isNullOrBlank()) appendLine("  Catatan: ${item.note}")
            }
            appendLine(divider)
            appendLine("Subtotal  : ${RupiahFormatter.format(order.subtotal)}")
            if (order.discount > 0) appendLine("Diskon    : -${RupiahFormatter.format(order.discount)}")
            if (order.tax > 0) appendLine("Pajak/Svc : ${RupiahFormatter.format(order.tax)}")
            appendLine("TOTAL     : ${RupiahFormatter.format(order.total)}")
            appendLine("Metode    : ${(payment?.method ?: order.paymentMethod ?: "cash").uppercase()}")
            payment?.cashReceived?.let { appendLine("Tunai     : ${RupiahFormatter.format(it)}") }
            payment?.changeAmount?.let { appendLine("Kembalian : ${RupiahFormatter.format(it)}") }
            appendLine(divider)
            appendLine("Terima kasih telah berkunjung!")
        }
    }

    fun refreshBackendConfigStatus() {
        val creds = SupabaseConfigManager.loadCredentials(getApplication())
        _uiState.update {
            it.copy(
                backendStatus = creds.status,
                currentProjectId = creds.projectId,
                currentSupabaseUrl = creds.supabaseUrl,
                isLoggedIn = creds.status == BackendIntegrationStatus.ACTIVE_VERIFIED && creds.accessToken.isNotBlank()
            )
        }
        if (creds.status == BackendIntegrationStatus.ACTIVE_VERIFIED && creds.accessToken.isNotBlank()) {
            syncAllDataFromBackend()
        }
    }

    fun enableLocalSandboxMode(selectedProfile: StaffProfile = _uiState.value.staffProfile) {
        _uiState.update {
            it.copy(
                isLoggedIn = true,
                staffProfile = selectedProfile,
                backendStatus = BackendIntegrationStatus.LOCAL_SANDBOX_UI,
                successMessage = "Login Simulasi Lokal aktif sebagai ${selectedProfile.fullName} (${selectedProfile.role.labelId}). Data berlabel [SIMULASI LOKAL]."
            )
        }
        appendAuditLog("auth.sandbox_login", "staff_profiles", selectedProfile.userId, "{\"role\":\"${selectedProfile.role.name}\"}")
    }

    fun logoutSession() {
        viewModelScope.launch {
            runCatching { repository.signOut() }
            _uiState.update {
                it.copy(
                    isLoggedIn = false,
                    cart = emptyList(),
                    successMessage = "Sesi kasir berhasil keluar. Silakan login kembali."
                )
            }
        }
    }

    fun setThemeMode(dark: Boolean) {
        _uiState.update { it.copy(darkTheme = dark) }
    }

    fun setUiScale(percent: Int) {
        val valid = percent.coerceIn(40, 120)
        _uiState.update { it.copy(uiScalePercent = valid) }
    }

    fun clearBannerMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun showNotice(message: String, isError: Boolean = false) {
        _uiState.update {
            if (isError) it.copy(errorMessage = message, successMessage = null)
            else it.copy(successMessage = message, errorMessage = null)
        }
    }

    fun switchActiveStaffProfile(profile: StaffProfile) {
        if (_uiState.value.backendStatus != BackendIntegrationStatus.LOCAL_SANDBOX_UI) {
            showNotice(
                "Akun staf tidak bisa diganti lewat pemilihan profil. Keluar lalu masuk dengan email dan password staf yang sesuai.",
                isError = true
            )
            return
        }
        _uiState.update {
            it.copy(
                staffProfile = profile,
                isLoggedIn = true,
                successMessage = "Beralih ke profil simulasi lokal: ${profile.fullName} (${profile.role.labelId})"
            )
        }
    }

    fun addStaffProfile(fullName: String, email: String, role: StaffRole) {
        if (fullName.isBlank() || email.isBlank()) {
            showNotice("Nama lengkap dan email pegawai wajib diisi.", isError = true)
            return
        }
        val defaultPerms = when (role) {
            StaffRole.SUPER_ADMIN, StaffRole.OWNER -> PermissionKey.entries.associate { it.key to true }
            StaffRole.MANAGER -> PermissionKey.entries.associate { it.key to (it != PermissionKey.SETTINGS) }
            StaffRole.CASHIER -> mapOf("dashboard" to true, "orders" to true, "payments" to true, "tables" to true, "customers" to true, "products" to false, "inventory" to false, "reports" to false, "promotions" to false, "settings" to false)
        }
        if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
                runCatching { repository.inviteStaff(fullName, email, if (role == StaffRole.MANAGER) "manager" else "cashier", defaultPerms) }
                    .onSuccess {
                        _uiState.update { it.copy(isLoading = false, successMessage = "Undangan staf dikirim ke $email. Minta staf membuka email untuk mengaktifkan akun.") }
                        syncAllDataFromBackend()
                    }
                    .onFailure {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Undangan staf gagal dikirim. Pastikan akun Anda owner dan email belum terdaftar.") }
                    }
            }
            return
        }
        val created = StaffProfile(userId = "usr-${System.currentTimeMillis()}", ownerId = "usr-owner-01", fullName = fullName.trim(), email = email.trim(), role = role, isActive = true, permissions = defaultPerms)
        _uiState.update { it.copy(staffList = it.staffList + created, successMessage = "Pegawai ${created.fullName} berhasil ditambahkan dalam mode simulasi.") }
    }

    fun savePaymentMethod(method: PaymentMethod) {
        if (_uiState.value.backendStatus != BackendIntegrationStatus.ACTIVE_VERIFIED) {
            showNotice("Metode pembayaran hanya bisa disimpan saat layanan aktif.", isError = true)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.savePaymentMethod(method) }
                .onSuccess { saved -> _uiState.update { state -> state.copy(isLoading = false, paymentMethods = (state.paymentMethods.filterNot { it.id == saved.id } + saved).sortedBy { it.sortOrder }, successMessage = "Metode pembayaran berhasil disimpan.") } }
                .onFailure { _uiState.update { it.copy(isLoading = false, errorMessage = "Metode pembayaran gagal disimpan. Periksa koneksi dan akses akun.") } }
        }
    }

    fun deletePaymentMethod(id: String) {
        viewModelScope.launch {
            runCatching { repository.deletePaymentMethod(id) }
                .onSuccess { _uiState.update { s -> s.copy(paymentMethods = s.paymentMethods.filterNot { it.id == id }, successMessage = "Metode pembayaran dihapus.") } }
                .onFailure { _uiState.update { it.copy(errorMessage = "Metode pembayaran gagal dihapus.") } }
        }
    }

    fun uploadPaymentPhoto(bytes: ByteArray, mime: String, ext: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.uploadPaymentMethodImage(bytes, mime, ext) }
                .onSuccess { onDone(it) }
                .onFailure { _uiState.update { it.copy(errorMessage = "Gambar QR gagal diunggah. Gunakan JPG, PNG, atau WebP maksimal 8 MB.") }; onDone(null) }
        }
    }

    fun toggleStaffPermission(userId: String, permKey: String) {
        val state = _uiState.value
        val target = state.staffList.firstOrNull { it.userId == userId } ?: return
        if (!canManageStaff(state, target)) { showNotice("Hanya owner yang dapat mengubah hak akses staf lain.", isError = true); return }
        val next = target.permissions.toMutableMap().apply { put(permKey, target.permissions[permKey] != true) }
        if (state.backendStatus != BackendIntegrationStatus.ACTIVE_VERIFIED) {
            applyLocalStaffUpdate(target.copy(permissions = next), "Hak akses diperbarui (simulasi)."); return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            runCatching { repository.updateStaffProfile(userId, permissions = next) }
                .onSuccess { saved -> _uiState.update { current -> current.copy(isLoading = false, staffList = current.staffList.map { if (it.userId == saved.userId) saved else it }, successMessage = "Hak akses ${saved.fullName} tersimpan.") } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = "Hak akses gagal disimpan: ${err.message ?: "periksa izin/RLS Supabase"}") }; syncAllDataFromBackend() }
        }
    }

    fun setStaffActive(userId: String, active: Boolean) {
        val state = _uiState.value
        val target = state.staffList.firstOrNull { it.userId == userId } ?: return
        if (!canManageStaff(state, target)) { showNotice("Hanya owner yang dapat mengubah status staf lain.", isError = true); return }
        if (state.backendStatus != BackendIntegrationStatus.ACTIVE_VERIFIED) { applyLocalStaffUpdate(target.copy(isActive = active), if (active) "Akun staf diaktifkan (simulasi)." else "Akun staf dinonaktifkan (simulasi)."); return }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            runCatching { repository.updateStaffProfile(userId, isActive = active) }
                .onSuccess { saved -> _uiState.update { current -> current.copy(isLoading = false, staffList = current.staffList.map { if (it.userId == saved.userId) saved else it }, successMessage = if (active) "Akun diaktifkan." else "Akun dinonaktifkan.") } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = "Status akun gagal diubah: ${err.message ?: "periksa izin/RLS Supabase"}") }; syncAllDataFromBackend() }
        }
    }

    fun deleteStaffProfile(userId: String) {
        val state = _uiState.value
        val target = state.staffList.firstOrNull { it.userId == userId } ?: return
        if (!canManageStaff(state, target)) { showNotice("Hanya owner yang dapat menghapus profil staf lain.", isError = true); return }
        if (state.backendStatus != BackendIntegrationStatus.ACTIVE_VERIFIED) { _uiState.update { it.copy(staffList = it.staffList.filterNot { staff -> staff.userId == userId }, successMessage = "Profil dihapus dari simulasi.") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            runCatching { repository.deleteStaffProfile(userId) }
                .onSuccess { _uiState.update { current -> current.copy(isLoading = false, staffList = current.staffList.filterNot { it.userId == userId }, successMessage = "Profil staf dihapus. User Supabase Auth tidak ikut terhapus.") }; syncAllDataFromBackend() }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = "Profil gagal dihapus: ${err.message ?: "periksa izin/RLS Supabase"}") } }
        }
    }

    private fun canManageStaff(state: PosUiState, target: StaffProfile): Boolean =
        state.staffProfile.role == StaffRole.OWNER && state.staffProfile.isActive && target.userId != state.staffProfile.userId

    private fun applyLocalStaffUpdate(updated: StaffProfile, message: String) {
        _uiState.update { state -> state.copy(staffList = state.staffList.map { if (it.userId == updated.userId) updated else it }, successMessage = message) }
    }

    private fun appendAuditLog(action: String, entityType: String, entityId: String, detailsText: String) {
        val log = AuditLog(
            id = "log-${System.currentTimeMillis()}",
            actorUserId = _uiState.value.staffProfile.userId,
            actorName = _uiState.value.staffProfile.fullName,
            action = action,
            entityType = entityType,
            entityId = entityId,
            detailsText = detailsText,
            createdAt = nowString()
        )
        _uiState.update { it.copy(auditLogs = listOf(log) + it.auditLogs) }
    }

    fun verifyAndSaveSupabaseConfig(
        projectId: String,
        url: String,
        publishableKey: String,
        webMenuBaseUrl: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            runCatching {
                // Prevent a token from the previous project being reused with new credentials.
                SupabaseConfigManager.clearAuthSession(getApplication())
                repository.verifyDatabaseConnection(
                    projectId = projectId,
                    supabaseUrl = url,
                    publishableKey = publishableKey
                )
                SupabaseConfigManager.saveVerifiedCredentials(
                    context = getApplication(),
                    projectId = projectId,
                    supabaseUrl = url,
                    publishableKey = publishableKey,
                    publicWebMenuBaseUrl = webMenuBaseUrl
                )
            }.onSuccess {
                refreshBackendConfigStatus()
                _uiState.update { it.copy(isLoading = false, isLoggedIn = false) }
                showNotice("Verifikasi berhasil: koneksi Supabase dan endpoint staff_profiles dapat diakses.")
            }.onFailure { err ->
                runCatching {
                    SupabaseConfigManager.savePendingCredentials(
                        context = getApplication(),
                        projectId = projectId,
                        supabaseUrl = url,
                        publishableKey = publishableKey,
                        publicWebMenuBaseUrl = webMenuBaseUrl
                    )
                }
                SupabaseConfigManager.clearAuthSession(getApplication())
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        backendStatus = BackendIntegrationStatus.INACTIVE_PENDING_VERIFICATION,
                        isLoggedIn = false,
                        currentProjectId = projectId.trim(),
                        currentSupabaseUrl = url.trim().removeSuffix("/"),
                        errorMessage = "Verifikasi Supabase gagal. Konfigurasi disimpan sebagai belum terverifikasi. ${err.message ?: "Periksa URL, Project ID, key, skema, dan izin akses."}",
                        successMessage = null
                    )
                }
            }
        }
    }

    fun deactivateSupabaseConfig() {
        SupabaseConfigManager.deactivateIntegration(getApplication())
        _uiState.update {
            it.copy(
                backendStatus = BackendIntegrationStatus.INACTIVE_PENDING_VERIFICATION,
                isLoggedIn = false,
                successMessage = "Pengaturan koneksi diperbarui."
            )
        }
    }

    private fun playOrderAlertSound() {
        val now = System.currentTimeMillis()
        if (now - lastPlayedOrderAlertAt < 2_000L) return
        val soundUrl = getApplication<Application>()
            .getSharedPreferences("order_alert_settings", android.content.Context.MODE_PRIVATE)
            .getString("sound_url", null)
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: return
        lastPlayedOrderAlertAt = now
        runCatching {
            MediaPlayer().apply {
                setDataSource(soundUrl)
                setOnPreparedListener { player -> runCatching { player.start() }.onFailure { player.release() } }
                setOnCompletionListener { player -> player.release() }
                setOnErrorListener { player, _, _ -> player.release(); true }
                prepareAsync()
            }
        }
    }

    fun changePassword(newPassword: String) {
        if (newPassword.length < 8) {
            showNotice("Password baru minimal 8 karakter.", isError = true)
            return
        }
        viewModelScope.launch {
            runCatching { repository.changePassword(newPassword) }
                .onSuccess { showNotice("Password berhasil diganti.") }
                .onFailure { showNotice(it.message ?: "Gagal mengganti password. Coba login ulang lalu ulangi.", isError = true) }
        }
    }

    fun uploadOrderAlertSound(bytes: ByteArray, mimeType: String, extension: String, onUploaded: (String) -> Unit) {
        if (_uiState.value.backendStatus != BackendIntegrationStatus.ACTIVE_VERIFIED) {
            showNotice("Upload suara memerlukan koneksi Supabase aktif.", isError = true)
            return
        }
        viewModelScope.launch {
            runCatching { repository.uploadOrderAlertSound(bytes, mimeType, extension) }
                .onSuccess { url ->
                    onUploaded(url)
                    showNotice("Suara pesanan berhasil diunggah. Alarm aktif di perangkat ini.")
                }
                .onFailure { showNotice(it.message ?: "Gagal mengunggah suara pesanan.", isError = true) }
        }
    }

    fun loginWithSupabaseAuth(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            showNotice("Email dan password wajib diisi.", isError = true)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.signInWithEmailPassword(email, password)
            }.onSuccess { profile ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        staffProfile = profile,
                        successMessage = "Login Supabase berhasil sebagai ${profile.fullName}"
                    )
                }
                syncAllDataFromBackend()
            }.onFailure { error ->
                val safeDiagnostic = error.message
                    ?.takeIf {
                        it.startsWith("Permintaan Supabase gagal (HTTP ") ||
                            it.startsWith("Akun berhasil login, tetapi belum terdaftar") ||
                            it.startsWith("Akun staff ini sedang nonaktif")
                    }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = safeDiagnostic
                            ?: "Login gagal. Periksa email/password dan koneksi internet, lalu coba lagi."
                    )
                }
            }
        }
    }

    fun syncAllDataFromBackend() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val profile = repository.fetchMyStaffProfile()
                val staffProfiles = repository.fetchStaffProfiles()
                val categories = repository.fetchCategories()
                val products = repository.fetchProducts()
                val productAddons = repository.fetchProductAddons()
                val customizationOptions = repository.fetchCustomizationOptions()
                val tables = repository.fetchTables()
                val promotions = repository.fetchPromotions()
                val orders = repository.fetchOrders()
                val settings = repository.fetchOutletSettings()
                val paymentMethods = repository.fetchPaymentMethods()
                val shifts = repository.fetchStaffShifts()
                val expenses = repository.fetchExpenses()
                val logs = repository.fetchAuditLogs()

                orderBaselineInitialized = true
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        staffProfile = profile ?: it.staffProfile,
                        staffList = staffProfiles,
                        categories = categories,
                        products = products,
                        productAddons = productAddons,
                        customizationOptions = customizationOptions,
                        tables = tables,
                        promotions = promotions,
                        orders = orders,
                        outletSettings = settings,
                        paymentMethods = paymentMethods,
                        shifts = shifts,
                        expenses = expenses,
                        auditLogs = logs,
                        // Routine syncs are silent; only actionable errors are surfaced.
                        successMessage = null
                    )
                }
            }.onFailure { err ->
                // A table/RLS/network error is not proof that the login session is invalid.
                // Keep the real session unless Supabase explicitly rejects the token (401).
                val authRejected = err.message?.contains("HTTP 401") == true
                if (authRejected) {
                    SupabaseConfigManager.clearAuthSession(getApplication())
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = !authRejected,
                        cart = emptyList(),
                        selectedTableId = null,
                        categories = emptyList(),
                        products = emptyList(),
                        productAddons = emptyList(),
                        customizationOptions = emptyList(),
                        tables = emptyList(),
                        promotions = emptyList(),
                        orders = emptyList(),
                        shifts = emptyList(),
                        expenses = emptyList(),
                        inventoryMovements = emptyList(),
                        auditLogs = emptyList(),
                        errorMessage = if (authRejected) {
                            "Sesi login ditolak server. Silakan login kembali."
                        } else {
                            "Sinkronisasi gagal. Sesi tetap aktif; data produksi belum dimuat. Detail: ${err.message?.take(180) ?: "koneksi atau akses database bermasalah"}"
                        }
                    )
                }
            }
        }
    }

    fun askAiAssistant(prompt: String) {
        if (prompt.isBlank()) {
            showNotice("Masukkan pertanyaan analisis terlebih dahulu.", isError = true)
            return
        }
        viewModelScope.launch {
            if (_uiState.value.backendStatus != BackendIntegrationStatus.ACTIVE_VERIFIED) {
                _uiState.update {
                    it.copy(
                        aiReplyText = null,
                        errorMessage = "Fitur asisten belum tersedia saat ini. Silakan coba lagi nanti."
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.invokeAiAssistant(prompt)
            }.onSuccess { reply ->
                _uiState.update {
                    it.copy(isLoading = false, aiReplyText = reply)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        aiReplyText = null,
                        errorMessage = "Edge Function 'ai-assistant' belum dapat diakses: ${err.message}"
                    )
                }
            }
        }
    }

    // ========================================================================
    // KASIR & KERANJANG
    // ========================================================================
    fun setKasirFilter(categoryId: String, search: String) {
        _uiState.update { it.copy(selectedCategoryId = categoryId, searchQuery = search) }
    }

    fun setKasirOrderMeta(
        tableId: String? = _uiState.value.selectedTableId,
        customer: String = _uiState.value.customerName,
        notes: String = _uiState.value.orderNotes,
        promoId: String? = _uiState.value.selectedPromotionId,
        payMethod: String = _uiState.value.paymentMethod,
        cashInput: String = _uiState.value.cashReceivedInput
    ) {
        _uiState.update {
            it.copy(
                selectedTableId = tableId,
                customerName = customer,
                orderNotes = notes,
                selectedPromotionId = promoId,
                paymentMethod = payMethod,
                cashReceivedInput = cashInput
            )
        }
    }

    fun addToCart(
        product: Product,
        sugar: String = "Normal Sugar",
        ice: String = "Normal Ice",
        cupSize: String = "Regular",
        addons: List<ProductAddon> = emptyList(),
        customNote: String = ""
    ) {
        if (!product.isActive || product.isOutOfStock) {
            showNotice("Produk ${product.name} sedang habis atau tidak aktif.", isError = true)
            return
        }
        _uiState.update { state ->
            val currentTotalQty = state.cart.filter { it.product.id == product.id }.sumOf { it.quantity }
            if (currentTotalQty + 1 > product.stock) {
                return@update state.copy(errorMessage = "Stok ${product.name} hanya tersisa ${product.stock}.")
            }
            val addonSummary = if (addons.isNotEmpty()) "+ ${addons.joinToString(", ") { it.name }}" else null
            val combinedNote = listOfNotNull(
                sugar.takeIf { it != "Normal Sugar" },
                ice.takeIf { it != "Normal Ice" },
                if (cupSize != "Regular") "Cup $cupSize" else null,
                addonSummary,
                customNote.trim().takeIf { it.isNotEmpty() }
            ).joinToString(" · ")

            val existingIdx = state.cart.indexOfFirst {
                it.product.id == product.id && it.note == combinedNote && it.cupSize == cupSize
            }
            val nextCart = if (existingIdx >= 0) {
                state.cart.toMutableList().apply {
                    this[existingIdx] = this[existingIdx].copy(quantity = this[existingIdx].quantity + 1)
                }
            } else {
                state.cart + CartItem(
                    cartItemId = "${product.id}-${System.currentTimeMillis()}",
                    product = product,
                    quantity = 1,
                    note = combinedNote,
                    sugarLevel = sugar,
                    iceLevel = ice,
                    cupSize = cupSize,
                    selectedAddons = addons
                )
            }
            state.copy(cart = nextCart, errorMessage = null)
        }
    }

    fun updateCartItemQuantity(cartItemId: String, delta: Int) {
        _uiState.update { state ->
            val nextCart = state.cart.mapNotNull { item ->
                if (item.cartItemId != cartItemId) return@mapNotNull item
                val nextQty = item.quantity + delta
                when {
                    nextQty <= 0 -> null
                    nextQty > item.product.stock -> item
                    else -> item.copy(quantity = nextQty)
                }
            }
            state.copy(cart = nextCart)
        }
    }

    fun updateCartItemNote(cartItemId: String, note: String) {
        _uiState.update { state ->
            state.copy(
                cart = state.cart.map {
                    if (it.cartItemId == cartItemId) it.copy(note = note) else it
                }
            )
        }
    }

    fun clearCart() {
        _uiState.update { it.copy(cart = emptyList()) }
    }

    fun openReceiptDialog(order: OrderWithItems?) {
        _uiState.update { it.copy(receiptDialogOrder = order) }
    }

    fun submitPosCheckout() {
        val currentState = _uiState.value
        if (currentState.cart.isEmpty() || currentState.isCheckoutProcessing) return
        if (!currentState.isPromotionEligible) {
            showNotice(currentState.promotionEligibilityReason ?: "Syarat promo belum terpenuhi.", isError = true)
            return
        }
        if (!checkoutMutex.tryLock()) return

        val idempotencyKey = UUID.randomUUID().toString()
        val paymentRecordMethod = currentState.selectedPaymentOption?.let { method ->
            listOf(method.name, method.provider, method.accountName, method.accountNumber)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
        }?.ifBlank { currentState.paymentMethodType } ?: currentState.paymentMethodType

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isCheckoutProcessing = true, errorMessage = null) }

                if (currentState.paymentMethodType == "cash" && currentState.cashReceivedValue < currentState.grandTotal) {
                    throw IllegalArgumentException("Nominal uang tunai kurang dari total tagihan.")
                }

                if (currentState.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                    val confirmedOrder = repository.createPosOrder(
                        idempotencyKey = idempotencyKey,
                        tableId = currentState.selectedTableId,
                        customerName = currentState.customerName.ifBlank { "Walk-in Customer" },
                        notes = currentState.orderNotes.ifBlank { null },
                        promotionId = currentState.selectedPromotionId,
                        subtotal = currentState.subtotal,
                        discount = currentState.discountAmount,
                        tax = currentState.taxAndServiceAmount,
                        total = currentState.grandTotal,
                        paymentMethod = currentState.paymentMethodType,
                        paymentRecordMethod = paymentRecordMethod,
                        cashReceived = if (currentState.paymentMethodType == "cash") currentState.cashReceivedValue else null,
                        items = currentState.cart
                    )
                    printerManager.getSavedPrinter(PrinterRole.CASHIER)?.let {
                        if (it.autoPrint) printerManager.printCashierReceipt(confirmedOrder)
                    }
                    printerManager.getSavedPrinter(PrinterRole.KITCHEN)?.let {
                        if (it.autoPrint) printerManager.printKitchenTicket(confirmedOrder)
                    }
                    _uiState.update {
                        it.copy(
                            isCheckoutProcessing = false,
                            cart = emptyList(),
                            customerName = "",
                            orderNotes = "",
                            cashReceivedInput = "",
                            receiptDialogOrder = confirmedOrder,
                            thermalPreviewText = formatOrderForThermalPreview(confirmedOrder, false),
                            successMessage = "Transaksi #${confirmedOrder.orderNo} terkonfirmasi oleh Supabase."
                        )
                    }
                    syncAllDataFromBackend()
                } else {
                    // Mode Simulasi UI Lokal Terlabel
                    val nextNo = (currentState.orders.maxOfOrNull { it.orderNo } ?: 1000) + 1
                    val orderId = "ord-$nextNo"
                    val tableObj = currentState.tables.find { it.id == currentState.selectedTableId }
                    val newOrder = OrderWithItems(
                        id = orderId,
                        orderNo = nextNo,
                        source = "pos",
                        status = OrderStatus.PREPARING,
                        tableId = tableObj?.id,
                        customerName = currentState.customerName.ifBlank { "Walk-in Customer" },
                        notes = currentState.orderNotes.ifBlank { null },
                        subtotal = currentState.subtotal,
                        discount = currentState.discountAmount,
                        tax = currentState.taxAndServiceAmount,
                        total = currentState.grandTotal,
                        isPaid = true,
                        paymentMethod = currentState.paymentMethodType,
                        promotionId = currentState.selectedPromotionId,
                        paymentStatus = PaymentStatus.PAID,
                        createdAt = nowString(),
                        updatedAt = nowString(),
                        restaurantTable = TableNameRef(tableObj?.name ?: "Takeaway"),
                        orderItems = currentState.cart.mapIndexed { idx, c ->
                            OrderItem(
                                id = "oi-${System.currentTimeMillis()}-$idx",
                                orderId = orderId,
                                productId = c.product.id,
                                productName = c.product.name,
                                unitPrice = c.unitPriceWithAddons,
                                quantity = c.quantity,
                                note = c.note.ifBlank { null },
                                lineTotal = c.lineTotal,
                                costPriceSnapshot = c.product.costPrice
                            )
                        },
                        payments = listOf(
                            PaymentRecord(
                                id = "pay-${System.currentTimeMillis()}",
                                orderId = orderId,
                                method = paymentRecordMethod,
                                amount = currentState.grandTotal,
                                cashReceived = if (currentState.paymentMethodType == "cash") currentState.cashReceivedValue else null,
                                changeAmount = if (currentState.paymentMethodType == "cash") currentState.changeAmount else null
                            )
                        )
                    )

                    // Pemotongan stok atomik 1 kali (sesuai trg_deduct_stock)
                    val updatedProducts = currentState.products.map { prod ->
                        val soldQty = currentState.cart.filter { it.product.id == prod.id }.sumOf { it.quantity }
                        if (soldQty > 0) prod.copy(stock = (prod.stock - soldQty).coerceAtLeast(0)) else prod
                    }

                    _uiState.update {
                        it.copy(
                            isCheckoutProcessing = false,
                            products = updatedProducts,
                            orders = listOf(newOrder) + it.orders,
                            cart = emptyList(),
                            customerName = "",
                            orderNotes = "",
                            cashReceivedInput = "",
                            receiptDialogOrder = newOrder,
                            thermalPreviewText = formatOrderForThermalPreview(newOrder, false),
                            successMessage = "Order #${newOrder.orderNo} berhasil disimpan (${RupiahFormatter.format(newOrder.total)})."
                        )
                    }
                    appendAuditLog(
                        action = "order.create_pos_order",
                        entityType = "orders",
                        entityId = newOrder.id,
                        detailsText = "{\"order_no\":${newOrder.orderNo},\"total\":${newOrder.total.toLong()},\"method\":\"${currentState.paymentMethod}\"}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckoutProcessing = false,
                        errorMessage = e.message ?: "Transaksi gagal diproses."
                    )
                }
            } finally {
                checkoutMutex.unlock()
            }
        }
    }

    // ========================================================================
    // PESANAN & QR ORDERING
    // ========================================================================
    fun deleteOrders(orderIds: List<String>) {
        if (orderIds.isEmpty()) return
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching { repository.deleteOrders(orderIds) }
                    .onSuccess {
                        syncAllDataFromBackend()
                        showNotice("${orderIds.size} transaksi berhasil dihapus.")
                    }
                    .onFailure { showNotice(it.message ?: "Gagal menghapus transaksi.", isError = true) }
                return@launch
            }
            _uiState.update { state ->
                state.copy(orders = state.orders.filterNot { it.id in orderIds })
            }
            appendAuditLog("order.delete", "orders", "bulk", "{\"count\":${orderIds.size}}")
            showNotice("${orderIds.size} transaksi dihapus dari data lokal.")
        }
    }

    fun deleteAllOrders() {
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching { repository.deleteAllOrders() }
                    .onSuccess {
                        syncAllDataFromBackend()
                        showNotice("Semua transaksi berhasil dihapus.")
                    }
                    .onFailure { showNotice(it.message ?: "Gagal menghapus semua transaksi.", isError = true) }
                return@launch
            }
            val count = _uiState.value.orders.size
            _uiState.update { it.copy(orders = emptyList()) }
            appendAuditLog("order.delete_all", "orders", "all", "{\"count\":$count}")
            showNotice("Semua transaksi lokal dihapus.")
        }
    }

    fun updateOrderStatus(order: OrderWithItems, nextStatus: OrderStatus, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching { repository.updateOrderStatus(order.id, nextStatus) }
                    .onSuccess {
                        refreshOrdersSilently()
                        onSuccess?.invoke()
                    }
                    .onFailure { showNotice(it.message ?: "Gagal memperbarui status pesanan.", isError = true) }
                return@launch
            }

            _uiState.update { state ->
                val updatedProducts = if (nextStatus == OrderStatus.CANCELLED && order.status != OrderStatus.CANCELLED) {
                    state.products.map { prod ->
                        val item = order.orderItems.find { it.productId == prod.id }
                        if (item != null) prod.copy(stock = prod.stock + item.quantity) else prod
                    }
                } else state.products

                val updatedOrders = state.orders.map {
                    if (it.id == order.id) it.copy(status = nextStatus, updatedAt = nowString()) else it
                }
                state.copy(
                    products = updatedProducts,
                    orders = updatedOrders,
                    successMessage = "Status Order #${order.orderNo} diubah ke ${nextStatus.labelId}."
                )
            }
            appendAuditLog("order.update_status", "orders", order.id, "{\"to\":\"${nextStatus.raw}\"}")
            onSuccess?.invoke()
        }
    }

    fun approveQrOrder(order: OrderWithItems, method: String, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching {
                    repository.approveQrOrder(order.id, method, if (method == "cash") order.total else null)
                }.onSuccess {
                    refreshOrdersSilently()
                    onSuccess?.invoke()
                }.onFailure {
                    showNotice(it.message ?: "Gagal menyetujui pembayaran QR.", isError = true)
                }
                return@launch
            }

            _uiState.update { state ->
                val updated = state.orders.map {
                    if (it.id == order.id) {
                        it.copy(
                            isPaid = true,
                            paymentMethod = method,
                            paymentStatus = PaymentStatus.PAID,
                            status = if (it.status == OrderStatus.NEW) OrderStatus.PREPARING else it.status,
                            payments = listOf(
                                PaymentRecord(
                                    id = "pay-${System.currentTimeMillis()}",
                                    orderId = order.id,
                                    method = method,
                                    amount = order.total,
                                    cashReceived = if (method == "cash") order.total else null,
                                    changeAmount = if (method == "cash") 0.0 else null
                                )
                            )
                        )
                    } else it
                }
                state.copy(
                    orders = updated,
                    successMessage = "Pembayaran QR Order #${order.orderNo} (${method.uppercase()}) dikonfirmasi."
                )
            }
            appendAuditLog("order.approve_qr", "orders", order.id, "{\"method\":\"$method\"}")
            onSuccess?.invoke()
        }
    }

    fun confirmOrderPayment(
        order: OrderWithItems,
        paymentMethod: String,
        cashReceived: Double?,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching {
                    repository.confirmOrderPayment(order.id, paymentMethod, cashReceived)
                }.onSuccess {
                    refreshOrdersSilently()
                    showNotice("Pembayaran Order #${order.orderNo} dikonfirmasi.")
                    onSuccess?.invoke()
                }.onFailure {
                    showNotice(it.message ?: "Gagal mengonfirmasi pembayaran.", isError = true)
                }
                return@launch
            }

            val method = paymentMethod.lowercase()
            val configuredMethod = _uiState.value.paymentMethods.firstOrNull { it.code.equals(method, true) }
            val isCash = method == "cash" || configuredMethod?.type.equals("cash", true)
            if (isCash && (cashReceived == null || cashReceived < order.total)) {
                showNotice("Uang diterima belum mencukupi.", isError = true)
                return@launch
            }
            val received = if (isCash) cashReceived else null
            val change = if (received != null) received - order.total else null
            _uiState.update { state ->
                state.copy(
                    orders = state.orders.map { current ->
                        if (current.id != order.id) current else current.copy(
                            isPaid = true,
                            paymentMethod = method,
                            paymentStatus = PaymentStatus.PAID,
                            status = if (current.status == OrderStatus.NEW) OrderStatus.PREPARING else current.status,
                            payments = current.payments + PaymentRecord(
                                id = "pay-${System.currentTimeMillis()}",
                                orderId = order.id,
                                method = method,
                                amount = order.total,
                                cashReceived = received,
                                changeAmount = change
                            )
                        )
                    }
                )
            }
            showNotice("Pembayaran Order #${order.orderNo} dikonfirmasi.")
            appendAuditLog("order.confirm_payment", "orders", order.id, "{\"method\":\"$method\"}")
            onSuccess?.invoke()
        }
    }

    fun rejectQrOrder(order: OrderWithItems) {
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching { repository.rejectQrOrder(order.id) }
                    .onSuccess {
                        syncAllDataFromBackend()
                        showNotice("Pesanan QR #${order.orderNo} ditolak.")
                    }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    orders = state.orders.map {
                        if (it.id == order.id) {
                            it.copy(status = OrderStatus.CANCELLED, paymentStatus = PaymentStatus.REJECTED, isPaid = false)
                        } else it
                    },
                    successMessage = "Pesanan QR #${order.orderNo} telah ditolak."
                )
            }
            appendAuditLog("order.reject_qr", "orders", order.id, "{}")
        }
    }

    fun simulateIncomingQrOrder(table: RestaurantTable) {
        val state = _uiState.value
        val sampleProduct = state.products.firstOrNull { !it.isOutOfStock } ?: return
        val nextNo = (state.orders.maxOfOrNull { it.orderNo } ?: 1000) + 1
        val newOrder = OrderWithItems(
            id = "ord-$nextNo",
            orderNo = nextNo,
            source = "qr",
            status = OrderStatus.NEW,
            tableId = table.id,
            customerName = "Pelanggan QR (${table.code})",
            notes = "Pesanan dari QR Menu Meja",
            subtotal = sampleProduct.price,
            discount = 0.0,
            tax = 0.0,
            total = sampleProduct.price,
            isPaid = false,
            paymentMethod = "qris",
            paymentStatus = PaymentStatus.AWAITING_CONFIRMATION,
            createdAt = nowString(),
            updatedAt = nowString(),
            restaurantTable = TableNameRef(table.name),
            orderItems = listOf(
                OrderItem(
                    id = "oi-${System.currentTimeMillis()}",
                    orderId = "ord-$nextNo",
                    productId = sampleProduct.id,
                    productName = sampleProduct.name,
                    unitPrice = sampleProduct.price,
                    quantity = 1,
                    note = "Normal Sugar · Normal Ice",
                    lineTotal = sampleProduct.price,
                    costPriceSnapshot = sampleProduct.costPrice
                )
            ),
            payments = emptyList()
        )
        _uiState.update {
            it.copy(
                orders = listOf(newOrder) + it.orders,
                successMessage = "Pesanan QR #${newOrder.orderNo} dari ${table.name} masuk dan menunggu konfirmasi kasir."
            )
        }
    }

    // ========================================================================
    // SHIFT KASIR & AUDIT SHIFT
    // ========================================================================
    fun openNewShift(openingCash: Double) {
        if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
            showNotice("Pencatatan shift live belum tersambung ke penyimpanan Supabase. Shift tidak dibuat agar data tidak hanya tersimpan lokal.", isError = true)
            return
        }
        val shift = StaffShift(
            id = "shf-${System.currentTimeMillis()}",
            staffUserId = _uiState.value.staffProfile.userId,
            staffName = _uiState.value.staffProfile.fullName,
            openingCash = openingCash,
            closingCash = null,
            expectedCash = openingCash,
            status = "open",
            notes = "Shift dibuka dari aplikasi Android",
            openedAt = nowString()
        )
        _uiState.update {
            it.copy(
                shifts = listOf(shift) + it.shifts,
                successMessage = "Shift baru dibuka dengan modal awal ${RupiahFormatter.format(openingCash)}."
            )
        }
        appendAuditLog("shift.open", "staff_shifts", shift.id, "{\"opening_cash\":${openingCash.toLong()}}")
    }

    fun closeActiveShift(shiftId: String, closingCash: Double, expectedCash: Double, notes: String) {
        if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
            showNotice("Penutupan shift live belum tersambung ke Supabase. Data kas tidak diubah.", isError = true)
            return
        }
        _uiState.update { state ->
            state.copy(
                shifts = state.shifts.map {
                    if (it.id == shiftId) {
                        it.copy(
                            status = "closed",
                            closingCash = closingCash,
                            expectedCash = expectedCash,
                            notes = notes.ifBlank { it.notes },
                            closedAt = nowString()
                        )
                    } else it
                },
                successMessage = "Shift kasir berhasil ditutup dan dikirim ke Audit Shift."
            )
        }
        appendAuditLog("shift.close", "staff_shifts", shiftId, "{\"closing_cash\":${closingCash.toLong()}}")
    }

    fun verifyShiftAudit(shiftId: String) {
        val state = _uiState.value
        val shift = state.shifts.firstOrNull { it.id == shiftId }
        if (shift == null) {
            showNotice("Shift tidak ditemukan. Muat ulang data audit.", isError = true)
            return
        }
        if (shift.status.equals("open", true)) {
            showNotice("Shift masih aktif. Tutup shift terlebih dahulu sebelum verifikasi audit.", isError = true)
            return
        }
        if (state.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
                runCatching { repository.verifyStaffShiftAudit(shiftId) }
                    .onSuccess { updated ->
                        _uiState.update { current ->
                            current.copy(isLoading = false, shifts = current.shifts.map { if (it.id == updated.id) updated else it },
                                successMessage = "Audit shift berhasil diverifikasi dan tersimpan di Supabase.")
                        }
                        appendAuditLog("shift.audit_verify", "staff_shifts", shiftId, "{\"verified\":true}")
                    }
                    .onFailure { err ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Verifikasi audit gagal disimpan: ${err.message ?: "Periksa izin UPDATE, RLS, dan kolom audit_verified."}") }
                    }
            }
            return
        }
        _uiState.update { current ->
            current.copy(shifts = current.shifts.map { if (it.id == shiftId) it.copy(auditVerified = true) else it },
                successMessage = "Audit shift lokal berhasil diverifikasi (mode sandbox).")
        }
        appendAuditLog("shift.audit_verify", "staff_shifts", shiftId, "{\"verified\":true}")
    }

    // ========================================================================
    // PRODUK, KATEGORI, MEJA, INVENTORY, PROMOSI, KEUANGAN & PENGATURAN
    // ========================================================================
    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (_uiState.value.categories.any { it.name.equals(trimmed, ignoreCase = true) }) {
            showNotice("Kategori sudah ada.", isError = true)
            return
        }
        val newCat = Category(name = trimmed, sortOrder = _uiState.value.categories.size + 1)
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching { repository.saveCategory(newCat) }
                    .onSuccess { saved ->
                        _uiState.update { it.copy(categories = (it.categories + saved).distinctBy(Category::id)) }
                        showNotice("Kategori \"$trimmed\" disimpan.")
                    }
                    .onFailure { showNotice(it.message ?: "Kategori gagal disimpan.", isError = true) }
            } else {
                val local = newCat.copy(id = "cat-${System.currentTimeMillis()}")
                _uiState.update { it.copy(categories = it.categories + local, successMessage = "Kategori \"$trimmed\" ditambahkan (mode lokal).") }
            }
        }
    }

    fun uploadProductPhoto(bytes: ByteArray, mimeType: String, extension: String, onUploaded: (String) -> Unit) {
        if (_uiState.value.backendStatus != BackendIntegrationStatus.ACTIVE_VERIFIED) {
            showNotice("Foto produk perlu koneksi Supabase aktif.", isError = true)
            return
        }
        viewModelScope.launch {
            runCatching { repository.uploadProductImage(bytes, mimeType, extension) }
                .onSuccess { url ->
                    onUploaded(url)
                    showNotice("Foto produk berhasil diunggah.")
                }
                .onFailure { showNotice(it.message ?: "Gagal mengunggah foto produk.", isError = true) }
        }
    }

    fun saveProductItem(product: Product) {
        if (product.name.isBlank() || product.price <= 0) {
            showNotice("Nama produk dan harga jual wajib valid.", isError = true)
            return
        }
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching { repository.saveProduct(product) }
                    .onSuccess {
                        syncAllDataFromBackend()
                        showNotice("Produk \"${product.name}\" tersimpan di Supabase.")
                    }
                    .onFailure { showNotice(it.message ?: "Gagal menyimpan produk.", isError = true) }
                return@launch
            }
            _uiState.update { state ->
                val updated = if (product.id.isNotBlank()) {
                    state.products.map { if (it.id == product.id) product else it }
                } else {
                    state.products + product.copy(id = "prod-${System.currentTimeMillis()}")
                }
                state.copy(
                    products = updated,
                    successMessage = "Produk \"${product.name}\" berhasil disimpan."
                )
            }
        }
    }

    fun deleteProductItem(productId: String) {
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching { repository.deleteProduct(productId) }
                    .onSuccess {
                        syncAllDataFromBackend()
                        showNotice("Produk berhasil dihapus dari Supabase.")
                    }
                    .onFailure { showNotice(it.message ?: "Gagal menghapus produk.", isError = true) }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    products = state.products.filterNot { it.id == productId },
                    successMessage = "Produk berhasil dihapus."
                )
            }
        }
    }

    fun toggleProductQrMenu(productId: String, show: Boolean) {
        val product = _uiState.value.products.firstOrNull { it.id == productId } ?: return
        saveProductItem(product.copy(showOnMenu = show))
    }

    fun toggleProductActive(productId: String, active: Boolean) {
        val product = _uiState.value.products.firstOrNull { it.id == productId } ?: return
        saveProductItem(product.copy(isActive = active))
    }

    fun saveProductAddon(addon: ProductAddon) {
        if (addon.name.isBlank() || addon.productId.isBlank() || addon.price < 0) {
            showNotice("Pilih produk, isi nama topping, dan pastikan harga tidak negatif.", isError = true)
            return
        }
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching { repository.saveProductAddon(addon) }
                    .onSuccess { syncProductOptions(); showNotice("Topping tersimpan.") }
                    .onFailure { showNotice(it.message ?: "Topping gagal disimpan.", isError = true) }
            } else {
                val saved = addon.copy(id = addon.id.ifBlank { "addon-${System.currentTimeMillis()}" })
                _uiState.update { state -> state.copy(productAddons = if (addon.id.isBlank()) state.productAddons + saved else state.productAddons.map { if (it.id == addon.id) saved else it }, successMessage = "Topping disimpan (mode lokal).") }
            }
        }
    }

    fun deleteProductAddon(id: String) {
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching { repository.deleteProductAddon(id) }
                    .onSuccess { syncProductOptions(); showNotice("Topping dihapus.") }
                    .onFailure { showNotice(it.message ?: "Topping gagal dihapus.", isError = true) }
            } else {
                _uiState.update { it.copy(productAddons = it.productAddons.filterNot { item -> item.id == id }) }
            }
        }
    }

    fun saveCustomizationOption(option: ProductCustomizationOption) {
        if (option.name.isBlank() || option.productId.isBlank() || option.priceDelta < 0 || option.groupKey !in listOf("sugar", "ice", "size")) {
            showNotice("Lengkapi produk, nama opsi, grup, dan harga yang valid.", isError = true)
            return
        }
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching { repository.saveCustomizationOption(option) }
                    .onSuccess { syncProductOptions(); showNotice("Opsi kustomisasi tersimpan.") }
                    .onFailure { showNotice(it.message ?: "Opsi kustomisasi gagal disimpan.", isError = true) }
            } else {
                val saved = option.copy(id = option.id.ifBlank { "custom-${System.currentTimeMillis()}" })
                _uiState.update { state -> state.copy(customizationOptions = if (option.id.isBlank()) state.customizationOptions + saved else state.customizationOptions.map { if (it.id == option.id) saved else it }, successMessage = "Opsi disimpan (mode lokal).") }
            }
        }
    }

    fun deleteCustomizationOption(id: String) {
        viewModelScope.launch {
            if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
                runCatching { repository.deleteCustomizationOption(id) }
                    .onSuccess { syncProductOptions(); showNotice("Opsi kustomisasi dihapus.") }
                    .onFailure { showNotice(it.message ?: "Opsi gagal dihapus.", isError = true) }
            } else {
                _uiState.update { it.copy(customizationOptions = it.customizationOptions.filterNot { item -> item.id == id }) }
            }
        }
    }

    private fun syncProductOptions() {
        viewModelScope.launch {
            runCatching {
                repository.fetchProductAddons() to repository.fetchCustomizationOptions()
            }.onSuccess { (addons, options) ->
                _uiState.update { it.copy(productAddons = addons, customizationOptions = options) }
            }.onFailure { showNotice(it.message ?: "Gagal memuat opsi produk.", isError = true) }
        }
    }

    fun addRestaurantTable(name: String, code: String) {
        val cleanName = name.trim()
        val cleanCode = code.trim().uppercase()
        if (cleanName.isBlank() || cleanCode.isBlank()) {
            showNotice("Nama dan kode meja wajib diisi.", isError = true)
            return
        }
        if (_uiState.value.tables.any { it.code.equals(cleanCode, ignoreCase = true) }) {
            showNotice("Kode meja $cleanCode sudah digunakan. Pakai kode lain.", isError = true)
            return
        }

        if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
                runCatching { repository.createRestaurantTable(cleanName, cleanCode) }
                    .onSuccess { created ->
                        runCatching { repository.fetchTables() }
                            .onSuccess { tables ->
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        tables = tables,
                                        selectedTableId = created.id,
                                        successMessage = "Meja ${created.name} (${created.code}) berhasil disimpan ke database."
                                    )
                                }
                            }
                            .onFailure { refreshError ->
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        tables = it.tables + created,
                                        selectedTableId = created.id,
                                        successMessage = "Meja tersimpan. Daftar meja belum bisa diperbarui: ${refreshError.message ?: "coba muat ulang"}"
                                    )
                                }
                            }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = err.message?.takeIf(String::isNotBlank)
                                    ?: "Meja gagal disimpan. Periksa koneksi dan izin database."
                            )
                        }
                    }
            }
            return
        }

        val created = RestaurantTable(
            id = "tbl-${System.currentTimeMillis()}",
            name = cleanName,
            code = cleanCode
        )
        _uiState.update {
            it.copy(
                tables = it.tables + created,
                successMessage = "Meja ${created.name} (${created.code}) ditambahkan dalam mode simulasi."
            )
        }
    }

    fun deleteRestaurantTable(tableId: String) {
        if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
                runCatching { repository.deleteRestaurantTable(tableId) }
                    .onSuccess {
                        runCatching { repository.fetchTables() }
                            .onSuccess { tables ->
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        tables = tables,
                                        selectedTableId = tables.firstOrNull()?.id,
                                        successMessage = "Meja berhasil dihapus dari database."
                                    )
                                }
                            }
                            .onFailure { err ->
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        tables = it.tables.filterNot { table -> table.id == tableId },
                                        selectedTableId = it.tables.firstOrNull { table -> table.id != tableId }?.id,
                                        successMessage = "Meja terhapus. Daftar belum bisa diperbarui: ${err.message ?: "coba muat ulang"}"
                                    )
                                }
                            }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = err.message?.takeIf(String::isNotBlank)
                                    ?: "Meja gagal dihapus. Periksa koneksi atau transaksi yang memakai meja ini."
                            )
                        }
                    }
            }
            return
        }
        _uiState.update {
            it.copy(
                tables = it.tables.filterNot { t -> t.id == tableId },
                successMessage = "Meja berhasil dihapus dalam mode simulasi."
            )
        }
    }

    fun recordInventoryMovement(productId: String, type: String, qty: Int, reason: String) {
        if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
            showNotice("Mutasi stok live belum tersambung ke Supabase. Stok tidak diubah.", isError = true)
            return
        }
        val prod = _uiState.value.products.find { it.id == productId } ?: return
        if (qty == 0 || reason.isBlank()) {
            showNotice("Jumlah dan alasan mutasi stok wajib diisi.", isError = true)
            return
        }
        val signedDelta = if (type == "out") -kotlin.math.abs(qty) else qty
        val movement = InventoryMovement(
            id = "inv-${System.currentTimeMillis()}",
            productId = prod.id,
            productName = prod.name,
            type = type,
            quantity = signedDelta,
            reason = reason.trim(),
            actorName = _uiState.value.staffProfile.fullName,
            createdAt = nowString()
        )
        _uiState.update { state ->
            state.copy(
                products = state.products.map {
                    if (it.id == prod.id) it.copy(stock = (it.stock + signedDelta).coerceAtLeast(0)) else it
                },
                inventoryMovements = listOf(movement) + state.inventoryMovements,
                successMessage = "Mutasi stok ${prod.name} dicatat."
            )
        }
        appendAuditLog("inventory.movement", "inventory_movements", movement.id, "{\"product\":\"${prod.name}\",\"delta\":$signedDelta}")
    }

    fun addPromotion(name: String, type: String, value: Double, minPurchase: Double) {
        if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
            showNotice("Pembuatan promosi live belum tersambung ke Supabase. Promosi tidak dibuat.", isError = true)
            return
        }
        if (name.isBlank()) return
        val promo = Promotion(
            id = "promo-${System.currentTimeMillis()}",
            name = name.trim(),
            type = type,
            value = value,
            minPurchase = minPurchase,
            active = true
        )
        _uiState.update {
            it.copy(
                promotions = listOf(promo) + it.promotions,
                successMessage = "Promosi \"${promo.name}\" disimpan."
            )
        }
    }

    fun togglePromotionActive(promoId: String) {
        _uiState.update { state ->
            state.copy(
                promotions = state.promotions.map {
                    if (it.id == promoId) it.copy(active = !it.active) else it
                }
            )
        }
    }

    fun deletePromotion(promoId: String) {
        _uiState.update { state ->
            state.copy(
                promotions = state.promotions.filterNot { it.id == promoId },
                successMessage = "Promosi berhasil dihapus."
            )
        }
    }

    fun addExpense(amount: Double, category: String, date: String, method: String, description: String) {
        if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
            showNotice("Pencatatan pengeluaran live belum tersambung ke Supabase. Pengeluaran tidak disimpan.", isError = true)
            return
        }
        if (amount <= 0 || description.isBlank()) {
            showNotice("Nominal harus lebih dari Rp0 dan keterangan wajib diisi.", isError = true)
            return
        }
        val exp = Expense(
            id = "exp-${System.currentTimeMillis()}",
            amount = amount,
            category = category,
            expenseDate = date,
            paymentMethod = method,
            description = description.trim(),
            createdAt = nowString()
        )
        _uiState.update {
            it.copy(
                expenses = listOf(exp) + it.expenses,
                successMessage = "Pengeluaran berhasil disimpan."
            )
        }
    }

    fun updateOutletSettings(settings: OutletSettings) {
        if (_uiState.value.backendStatus == BackendIntegrationStatus.ACTIVE_VERIFIED) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
                val current = _uiState.value.outletSettings
                val languageOnly = settings.copy(appLanguage = current.appLanguage) == current &&
                    settings.appLanguage != current.appLanguage
                runCatching {
                    if (languageOnly) repository.updateOutletLanguage(settings.appLanguage)
                    else repository.updateOutletSettings(settings)
                }
                    .onSuccess {
                        _uiState.update { it.copy(outletSettings = settings, isLoading = false, successMessage = "Pengaturan outlet berhasil disimpan.") }
                    }
                    .onFailure { error ->
                        val detail = error.message?.replace("\n", " ")?.replace("\r", " ")?.take(220)?.takeIf { it.isNotBlank() }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = if (detail == null) "Pengaturan outlet gagal disimpan. Periksa koneksi."
                                else "Pengaturan outlet gagal disimpan: $detail"
                            )
                        }
                    }
            }
            return
        }
        _uiState.update {
            it.copy(outletSettings = settings, successMessage = "Pengaturan outlet diperbarui.")
        }
    }

    fun scanBluetoothPrinters() {
        val result = printerManager.listPairedPrinters()
        result.onSuccess { devices ->
            if (devices.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        pairedBluetoothDevices = devices.map { (it.name ?: "Printer Bluetooth") to it.address },
                        successMessage = "Ditemukan ${devices.size} perangkat Bluetooth terpasang."
                    )
                }
            } else {
                showNotice("Tidak ada printer Bluetooth terpasang yang terdeteksi. Pair printer lewat pengaturan Bluetooth Android, lalu coba pindai lagi.", isError = true)
            }
        }.onFailure { err ->
            showNotice("Info Bluetooth: ${err.message}")
        }
    }

    fun bindPrinterDevice(role: PrinterRole, name: String, address: String, autoPrint: Boolean) {
        printerManager.savePrinter(role, name, address, autoPrint)
        showNotice("${role.labelId} dihubungkan ke $name ($address).")
    }

    fun printOrderToThermal(order: OrderWithItems, role: PrinterRole, forceReprint: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    thermalPreviewText = formatOrderForThermalPreview(order, role == PrinterRole.KITCHEN)
                )
            }
            val result = if (role == PrinterRole.KITCHEN) {
                printerManager.printKitchenTicket(order, forceReprint)
            } else {
                printerManager.printCashierReceipt(order, forceReprint)
            }
            result.onSuccess {
                showNotice("Berhasil mengirim struk ESC/POS ke ${role.labelId}.")
            }.onFailure { err ->
                showNotice("Preview ESC/POS diperbarui (${err.message}).")
            }
        }
    }
}
