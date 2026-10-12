package id.keretamalam.cafe.viewmodel

import android.app.Application
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
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

data class PosUiState(
    val isLoading: Boolean = false,
    val isCheckoutProcessing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val darkTheme: Boolean = false,
    val uiScalePercent: Int = 100,
    val backendStatus: BackendIntegrationStatus = BackendIntegrationStatus.INACTIVE_PENDING_VERIFICATION,
    val currentProjectId: String = "",
    val currentSupabaseUrl: String = "",
    val staffProfile: StaffProfile? = null,
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val tables: List<RestaurantTable> = emptyList(),
    val promotions: List<Promotion> = emptyList(),
    val orders: List<OrderWithItems> = emptyList(),
    val shifts: List<StaffShift> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val outletSettings: OutletSettings = OutletSettings(),
    // Kasir & Keranjang State
    val selectedCategoryId: String = "ALL",
    val searchQuery: String = "",
    val cart: List<CartItem> = emptyList(),
    val selectedTableId: String? = null,
    val customerName: String = "",
    val orderNotes: String = "",
    val selectedPromotionId: String? = null,
    val paymentMethod: String = "cash",
    val cashReceivedInput: String = "",
    val lastCompletedOrder: OrderWithItems? = null
) {
    val subtotal: Double
        get() = cart.sumOf { it.lineTotal }

    val activePromotion: Promotion?
        get() = promotions.firstOrNull { it.id == selectedPromotionId && it.active }

    val discountAmount: Double
        get() {
            val promo = activePromotion ?: return 0.0
            if (subtotal < promo.minPurchase) return 0.0
            val raw = if (promo.type == "percentage") {
                subtotal * (promo.value / 100.0)
            } else {
                promo.value
            }
            return raw.coerceAtMost(subtotal)
        }

    val taxableBase: Double
        get() = (subtotal - discountAmount).coerceAtLeast(0.0)

    val taxAndServiceAmount: Double
        get() {
            val tax = if (outletSettings.taxEnabled) taxableBase * (outletSettings.taxRate / 100.0) else 0.0
            val svc = if (outletSettings.serviceEnabled) taxableBase * (outletSettings.serviceRate / 100.0) else 0.0
            return tax + svc
        }

    val grandTotal: Double
        get() = taxableBase + taxAndServiceAmount

    val cashReceivedValue: Double
        get() = cashReceivedInput.toDoubleOrNull() ?: grandTotal

    val changeAmount: Double
        get() = (cashReceivedValue - grandTotal).coerceAtLeast(0.0)
}

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CafeFlowRepository(application)
    val printerManager = EscPosBluetoothManager(application)

    // Mutex mencegah transaksi ganda saat tombol bayar ditekan berulang kali
    private val checkoutMutex = Mutex()

    private val _uiState = MutableStateFlow(PosUiState())
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    init {
        refreshBackendConfigStatus()
    }

    fun refreshBackendConfigStatus() {
        val creds = SupabaseConfigManager.loadCredentials(getApplication())
        _uiState.update {
            it.copy(
                backendStatus = creds.status,
                currentProjectId = creds.projectId,
                currentSupabaseUrl = creds.supabaseUrl
            )
        }
        if (creds.status == BackendIntegrationStatus.ACTIVE_VERIFIED) {
            syncAllDataFromBackend()
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

    fun verifyAndSaveSupabaseConfig(
        projectId: String,
        url: String,
        publishableKey: String,
        webMenuBaseUrl: String
    ) {
        runCatching {
            SupabaseConfigManager.saveVerifiedCredentials(
                context = getApplication(),
                projectId = projectId,
                supabaseUrl = url,
                publishableKey = publishableKey,
                publicWebMenuBaseUrl = webMenuBaseUrl
            )
        }.onSuccess {
            refreshBackendConfigStatus()
            _uiState.update { it.copy(successMessage = "Konfigurasi Supabase ($projectId) disimpan.") }
        }.onFailure { err ->
            _uiState.update { it.copy(errorMessage = err.message ?: "Gagal menyimpan konfigurasi Supabase.") }
        }
    }

    fun syncAllDataFromBackend() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val profile = repository.fetchMyStaffProfile()
                val categories = repository.fetchCategories()
                val products = repository.fetchProducts()
                val tables = repository.fetchTables()
                val promotions = repository.fetchPromotions()
                val orders = repository.fetchOrders()
                val settings = repository.fetchOutletSettings()
                val shifts = repository.fetchStaffShifts()
                val expenses = repository.fetchExpenses()
                val logs = repository.fetchAuditLogs()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        staffProfile = profile,
                        categories = categories,
                        products = products,
                        tables = tables,
                        promotions = promotions,
                        orders = orders,
                        outletSettings = settings,
                        shifts = shifts,
                        expenses = expenses,
                        auditLogs = logs
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Gagal memuat data dari Supabase."
                    )
                }
            }
        }
    }

    // ========================================================================
    // KERANJANG KASIR & PENCEGAHAN TRANSAKSI GANDA
    // ========================================================================
    fun addToCart(product: Product) {
        if (!product.isActive || product.isOutOfStock) {
            _uiState.update { it.copy(errorMessage = "Produk ${product.name} sedang habis atau tidak aktif.") }
            return
        }
        _uiState.update { state ->
            val existingIndex = state.cart.indexOfFirst { it.product.id == product.id }
            val currentQty = if (existingIndex >= 0) state.cart[existingIndex].quantity else 0
            if (currentQty + 1 > product.stock) {
                return@update state.copy(errorMessage = "Stok ${product.name} tersisa ${product.stock}.")
            }
            val nextCart = if (existingIndex >= 0) {
                state.cart.toMutableList().apply {
                    val item = this[existingIndex]
                    this[existingIndex] = item.copy(quantity = item.quantity + 1)
                }
            } else {
                state.cart + CartItem(product = product, quantity = 1)
            }
            state.copy(cart = nextCart, errorMessage = null)
        }
    }

    fun updateCartItemQuantity(productId: String, delta: Int) {
        _uiState.update { state ->
            val nextCart = state.cart.mapNotNull { item ->
                if (item.product.id != productId) return@mapNotNull item
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

    fun updateCartItemNote(productId: String, note: String) {
        _uiState.update { state ->
            state.copy(
                cart = state.cart.map {
                    if (it.product.id == productId) it.copy(note = note) else it
                }
            )
        }
    }

    /**
     * Eksekusi Checkout Kasir yang menunggu konfirmasi backend sebelum menyatakan transaksi berhasil.
     * Mencegah double-submit dengan `checkoutMutex.tryLock()` dan `idempotency_key` UUID.
     */
    fun submitPosCheckout() {
        val currentState = _uiState.value
        if (currentState.cart.isEmpty() || currentState.isCheckoutProcessing) return
        if (!checkoutMutex.tryLock()) return

        val idempotencyKey = UUID.randomUUID().toString()

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isCheckoutProcessing = true, errorMessage = null) }

                if (currentState.paymentMethod == "cash" && currentState.cashReceivedValue < currentState.grandTotal) {
                    throw IllegalArgumentException("Nominal uang tunai kurang dari total tagihan.")
                }

                val confirmedOrder = repository.createPosOrder(
                    idempotencyKey = idempotencyKey,
                    tableId = currentState.selectedTableId,
                    customerName = currentState.customerName.ifBlank { "Walk-in" },
                    notes = currentState.orderNotes.ifBlank { null },
                    promotionId = currentState.selectedPromotionId,
                    subtotal = currentState.subtotal,
                    discount = currentState.discountAmount,
                    tax = currentState.taxAndServiceAmount,
                    total = currentState.grandTotal,
                    paymentMethod = currentState.paymentMethod,
                    cashReceived = if (currentState.paymentMethod == "cash") currentState.cashReceivedValue else null,
                    items = currentState.cart
                )

                // Cetak otomatis jika dikonfigurasi pada printer kasir / dapur
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
                        lastCompletedOrder = confirmedOrder,
                        successMessage = "Transaksi #${confirmedOrder.orderNo} terkonfirmasi oleh backend."
                    )
                }
                syncAllDataFromBackend()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckoutProcessing = false,
                        errorMessage = e.message ?: "Transaksi gagal dikonfirmasi oleh backend."
                    )
                }
            } finally {
                checkoutMutex.unlock()
            }
        }
    }
}
