package id.keretamalam.cafe.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.text.NumberFormat
import java.util.Locale

@Serializable
enum class StaffRole {
    @SerialName("super_admin") SUPER_ADMIN,
    @SerialName("owner") OWNER,
    @SerialName("manager") MANAGER,
    @SerialName("cashier") CASHIER
}

enum class PermissionKey(val key: String) {
    DASHBOARD("dashboard"),
    ORDERS("orders"),
    PAYMENTS("payments"),
    PRODUCTS("products"),
    INVENTORY("inventory"),
    TABLES("tables"),
    REPORTS("reports"),
    CUSTOMERS("customers"),
    PROMOTIONS("promotions"),
    SETTINGS("settings")
}

@Serializable
data class StaffProfile(
    @SerialName("user_id") val userId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("full_name") val fullName: String,
    val role: StaffRole = StaffRole.CASHIER,
    @SerialName("is_active") val isActive: Boolean = true,
    val permissions: Map<String, Boolean> = emptyMap()
) {
    fun canAccess(permission: PermissionKey): Boolean {
        if (!isActive) return false
        return role == StaffRole.SUPER_ADMIN ||
            role == StaffRole.OWNER ||
            permissions[permission.key] == true
    }
}

@Serializable
data class Category(
    val id: String = "",
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 1,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Product(
    val id: String = "",
    val name: String,
    val description: String? = null,
    val price: Double = 0.0,
    @SerialName("cost_price") val costPrice: Double? = 0.0,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val stock: Int = 0,
    @SerialName("low_stock_threshold") val lowStockThreshold: Int = 5,
    @SerialName("show_on_menu") val showOnMenu: Boolean = true,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
) {
    val isLowStock: Boolean get() = stock in 1..lowStockThreshold
    val isOutOfStock: Boolean get() = stock <= 0
}

@Serializable
data class ProductAddon(
    val id: String = "",
    @SerialName("product_id") val productId: String,
    val name: String,
    val price: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class ProductCustomizationOption(
    val id: String = "",
    @SerialName("product_id") val productId: String,
    @SerialName("group_key") val groupKey: String,
    val name: String,
    @SerialName("price_delta") val priceDelta: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0
)

@Serializable
data class RestaurantTable(
    val id: String = "",
    val name: String,
    val code: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
enum class OrderStatus(val raw: String, val labelId: String) {
    @SerialName("new") NEW("new", "Baru"),
    @SerialName("preparing") PREPARING("preparing", "Diproses"),
    @SerialName("ready") READY("ready", "Siap"),
    @SerialName("completed") COMPLETED("completed", "Selesai"),
    @SerialName("cancelled") CANCELLED("cancelled", "Dibatalkan")
}

@Serializable
enum class PaymentStatus(val raw: String, val labelId: String) {
    @SerialName("unpaid") UNPAID("unpaid", "Belum dibayar"),
    @SerialName("awaiting_confirmation") AWAITING_CONFIRMATION("awaiting_confirmation", "Menunggu konfirmasi"),
    @SerialName("paid") PAID("paid", "Sudah dibayar"),
    @SerialName("rejected") REJECTED("rejected", "Ditolak")
}

@Serializable
data class OrderItem(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String,
    @SerialName("unit_price") val unitPrice: Double,
    val quantity: Int,
    val note: String? = null,
    @SerialName("line_total") val lineTotal: Double,
    @SerialName("cost_price_snapshot") val costPriceSnapshot: Double? = null
)

@Serializable
data class PaymentRecord(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    val method: String,
    val amount: Double,
    @SerialName("cash_received") val cashReceived: Double? = null,
    @SerialName("change_amount") val changeAmount: Double? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TableNameRef(
    val name: String
)

@Serializable
data class OrderWithItems(
    val id: String,
    @SerialName("order_no") val orderNo: Int,
    val source: String = "pos",
    val status: OrderStatus = OrderStatus.NEW,
    @SerialName("table_id") val tableId: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    val notes: String? = null,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    @SerialName("is_paid") val isPaid: Boolean = false,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_status") val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("order_items") val orderItems: List<OrderItem> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    @SerialName("restaurant_tables") val restaurantTable: TableNameRef? = null
)

@Serializable
data class Promotion(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String? = null,
    val name: String,
    val type: String = "percentage", // "percentage" | "fixed"
    val value: Double = 0.0,
    @SerialName("min_purchase") val minPurchase: Double = 0.0,
    @SerialName("min_quantity") val minQuantity: Int = 1,
    val scope: String = "order", // "order" | "product"
    @SerialName("product_id") val productId: String? = null,
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class OutletSettings(
    @SerialName("owner_id") val ownerId: String? = null,
    val name: String = "Kereta Malam Cafe",
    @SerialName("tax_rate") val taxRate: Double = 11.0,
    @SerialName("service_rate") val serviceRate: Double = 5.0,
    @SerialName("tax_enabled") val taxEnabled: Boolean = false,
    @SerialName("service_enabled") val serviceEnabled: Boolean = false,
    @SerialName("cash_enabled") val cashEnabled: Boolean = true,
    @SerialName("card_enabled") val cardEnabled: Boolean = true,
    @SerialName("qris_enabled") val qrisEnabled: Boolean = true,
    val rounding: Double = 0.0,
    @SerialName("app_language") val appLanguage: String = "id",
    @SerialName("global_theme") val globalTheme: String = "matte-carbon"
)

@Serializable
data class StaffShift(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String,
    @SerialName("staff_user_id") val staffUserId: String,
    @SerialName("opening_cash") val openingCash: Double = 0.0,
    @SerialName("closing_cash") val closingCash: Double? = null,
    @SerialName("expected_cash") val expectedCash: Double? = null,
    val status: String = "open", // "open" | "closed"
    val notes: String? = null,
    @SerialName("opened_at") val openedAt: String = "",
    @SerialName("closed_at") val closedAt: String? = null
)

@Serializable
data class AuditLog(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("actor_user_id") val actorUserId: String? = null,
    val action: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String? = null,
    val details: JsonObject? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class Expense(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String,
    val amount: Double,
    val category: String,
    val description: String,
    @SerialName("expense_date") val expenseDate: String,
    @SerialName("payment_method") val paymentMethod: String = "cash",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class InventoryMovement(
    val id: String = "",
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String = "",
    val type: String, // "in" | "out" | "adjustment"
    val quantity: Int,
    val reason: String,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class WebsiteSettingsModel(
    val id: String = "",
    @SerialName("site_name") val siteName: String = "Kereta Malam Cafe",
    val tagline: String? = "POS & QR Ordering",
    @SerialName("updated_at") val updatedAt: String? = null
)

data class CartItem(
    val product: Product,
    val quantity: Int = 1,
    val note: String = "",
    val selectedAddons: List<ProductAddon> = emptyList()
) {
    val unitPriceWithAddons: Double
        get() = product.price + selectedAddons.sumOf { it.price }
    val lineTotal: Double
        get() = unitPriceWithAddons * quantity
}

object RupiahFormatter {
    private val localeId = Locale("id", "ID")
    fun format(amount: Double): String {
        val numberFormat = NumberFormat.getNumberInstance(localeId).apply {
            maximumFractionDigits = 0
        }
        return "Rp" + numberFormat.format(kotlin.math.round(amount))
    }
}
