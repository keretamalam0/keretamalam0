package id.keretamalam.cafe.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.text.NumberFormat
import java.util.Locale

@Serializable
enum class StaffRole(val labelId: String) {
    @SerialName("super_admin") SUPER_ADMIN("Super Admin"),
    @SerialName("owner") OWNER("Owner"),
    @SerialName("manager") MANAGER("Manager"),
    @SerialName("cashier") CASHIER("Kasir")
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

data class StaffPermissionItem(val key: String, val label: String, val group: String)

object StaffPermissionCatalog {
    val items = listOf(
        StaffPermissionItem("dashboard.total_sales", "Lihat total penjualan", "Dashboard"),
        StaffPermissionItem("dashboard.paid_orders", "Lihat jumlah pesanan lunas", "Dashboard"),
        StaffPermissionItem("dashboard.average_order", "Lihat rata-rata transaksi", "Dashboard"),
        StaffPermissionItem("dashboard.busy_hours", "Lihat jam tersibuk", "Dashboard"),
        StaffPermissionItem("dashboard.quick_links", "Lihat akses cepat", "Dashboard"),
        StaffPermissionItem("dashboard.sales_trend", "Lihat grafik tren omzet", "Dashboard"),
        StaffPermissionItem("dashboard.hourly_activity", "Lihat aktivitas per jam", "Dashboard"),
        StaffPermissionItem("dashboard.best_sellers", "Lihat menu terlaris dan omzetnya", "Dashboard"),
        StaffPermissionItem("dashboard.payment_methods", "Lihat ringkasan metode pembayaran", "Dashboard"),
        StaffPermissionItem("dashboard.outlet_status", "Lihat status outlet", "Dashboard"),
        StaffPermissionItem("history.export_csv", "Download / ekspor CSV transaksi", "Riwayat & Data"),
        StaffPermissionItem("history.delete", "Hapus transaksi", "Riwayat & Data"),
        StaffPermissionItem("reports.export", "Download / ekspor laporan", "Riwayat & Data"),
        StaffPermissionItem("products.edit", "Tambah dan edit produk", "Operasional"),
        StaffPermissionItem("products.delete", "Hapus produk", "Operasional"),
        StaffPermissionItem("inventory.edit", "Ubah stok dan mutasi inventori", "Operasional"),
        StaffPermissionItem("orders.cancel", "Batalkan pesanan", "Operasional"),
        StaffPermissionItem("payments.discount", "Gunakan diskon saat pembayaran", "Operasional"),
        StaffPermissionItem("shift.manage", "Buka dan tutup shift kasir", "Operasional"),
        StaffPermissionItem("staff.manage", "Kelola pegawai dan hak akses", "Administrasi"),
        StaffPermissionItem("settings.manage", "Ubah pengaturan outlet", "Administrasi"),
        StaffPermissionItem("audit.view", "Lihat log aktivitas dan audit", "Administrasi")
    )
    fun defaultsFor(role: StaffRole): Map<String, Boolean> = items.associate { item ->
        item.key to when (role) {
            StaffRole.OWNER, StaffRole.SUPER_ADMIN -> true
            StaffRole.MANAGER -> item.key !in setOf("staff.manage", "settings.manage")
            StaffRole.CASHIER -> item.key in setOf("dashboard.paid_orders", "dashboard.quick_links", "orders.cancel", "shift.manage")
        }
    }
}

@Serializable
data class StaffProfile(
    @SerialName("user_id") val userId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("full_name") val fullName: String,
    val email: String = "",
    val role: StaffRole = StaffRole.CASHIER,
    @SerialName("is_active") val isActive: Boolean = true,
    val permissions: Map<String, Boolean> = emptyMap()
) {
    fun canAccess(permission: PermissionKey): Boolean {
        // Global Super Admin is intentionally not an operational POS role.
        // Its separate control plane must authorize its own actions server-side.
        if (!isActive || role == StaffRole.SUPER_ADMIN) return false
        return role == StaffRole.OWNER || permissions[permission.key] == true
    }

    fun canPerform(permissionKey: String): Boolean {
        if (!isActive || role == StaffRole.SUPER_ADMIN) return false
        return role == StaffRole.OWNER || permissions[permissionKey] == true
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
    @SerialName("product_id") val productId: String = "",
    val name: String,
    val price: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class ProductCustomizationOption(
    val id: String = "",
    @SerialName("product_id") val productId: String = "",
    @SerialName("group_key") val groupKey: String = "sugar",
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
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("campaign_type") val campaignType: String = "promotion",
    @SerialName("allow_customer") val allowCustomer: Boolean = true,
    @SerialName("allow_cashier") val allowCashier: Boolean = true
)

@Serializable
data class PaymentMethod(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String? = null,
    val code: String = "",
    val name: String = "",
    val type: String = "qris",
    val provider: String = "",
    @SerialName("account_name") val accountName: String = "",
    @SerialName("account_number") val accountNumber: String = "",
    val instructions: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class OutletSettings(
    @SerialName("owner_id") val ownerId: String? = null,
    val name: String = "Kereta Malam Cafe",
    val address: String = "Jl. Stasiun Malam No. 24, Yogyakarta",
    val phone: String = "0812-3456-7890",
    @SerialName("tax_rate") val taxRate: Double = 11.0,
    @SerialName("service_rate") val serviceRate: Double = 5.0,
    @SerialName("service_fee_mode") val serviceFeeMode: String = "percentage",
    @SerialName("service_fixed_amount") val serviceFixedAmount: Double = 0.0,
    @SerialName("tax_enabled") val taxEnabled: Boolean = false,
    @SerialName("service_enabled") val serviceEnabled: Boolean = false,
    @SerialName("cash_enabled") val cashEnabled: Boolean = true,
    @SerialName("card_enabled") val cardEnabled: Boolean = true,
    @SerialName("qris_enabled") val qrisEnabled: Boolean = true,
    val rounding: Double = 0.0,
    @SerialName("app_language") val appLanguage: String = "id",
    @SerialName("global_theme") val globalTheme: String = "matte-carbon",
    val publicWebBaseUrl: String = "https://pos.yuripratama246.workers.dev"
)

@Serializable
data class StaffShift(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String = "usr-owner-01",
    @SerialName("staff_user_id") val staffUserId: String,
    val staffName: String = "Kasir",
    @SerialName("opening_cash") val openingCash: Double = 0.0,
    @SerialName("closing_cash") val closingCash: Double? = null,
    @SerialName("expected_cash") val expectedCash: Double? = null,
    val status: String = "open", // "open" | "closed"
    val notes: String? = null,
    @SerialName("audit_verified") val auditVerified: Boolean = false,
    @SerialName("opened_at") val openedAt: String = "",
    @SerialName("closed_at") val closedAt: String? = null
)

@Serializable
data class AuditLog(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("actor_user_id") val actorUserId: String? = null,
    val actorName: String = "Staf",
    val action: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String? = null,
    val details: JsonObject? = null,
    val detailsText: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class Expense(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String = "usr-owner-01",
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
    val warehouseName: String = "Gudang Utama Bar",
    val type: String, // "in" | "out" | "adjustment"
    val quantity: Int,
    val reason: String,
    val actorName: String = "Staf",
    @SerialName("created_at") val createdAt: String = ""
)

data class CartItem(
    val cartItemId: String,
    val product: Product,
    val quantity: Int = 1,
    val note: String = "",
    val sugarLevel: String = "Normal Sugar",
    val iceLevel: String = "Normal Ice",
    val cupSize: String = "Regular",
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

/**
 * Data Simulasi Lokal Terlabel yang menyalin langsung migrasi SQL `0000_create_pos_schema.sql`
 * dari repository `cafe-flow`. HANYA diaktifkan bila pengguna menyalakan Mode Simulasi UI Lokal.
 */
object SandboxSeedData {
    val categories = listOf(
        Category("11111111-1111-1111-1111-111111111101", "Kopi", 1),
        Category("11111111-1111-1111-1111-111111111102", "Non-Kopi", 2),
        Category("11111111-1111-1111-1111-111111111103", "Makanan", 3),
        Category("11111111-1111-1111-1111-111111111104", "Snack", 4),
        Category("11111111-1111-1111-1111-111111111105", "Dessert", 5)
    )

    val products = listOf(
        Product("prod-01", "Espresso", "Single shot arabika", 18000.0, 6500.0, null, "11111111-1111-1111-1111-111111111101", 40, 8),
        Product("prod-02", "Americano", "Espresso + air", 22000.0, 7500.0, null, "11111111-1111-1111-1111-111111111101", 35, 8),
        Product("prod-03", "Cappuccino", "Espresso + susu steam", 28000.0, 10500.0, null, "11111111-1111-1111-1111-111111111101", 30, 8),
        Product("prod-04", "Caffe Latte", "Lembut dan creamy", 30000.0, 11000.0, null, "11111111-1111-1111-1111-111111111101", 28, 8),
        Product("prod-05", "Kopi Susu Gula Aren", "Favorit pelanggan", 25000.0, 9000.0, null, "11111111-1111-1111-1111-111111111101", 4, 8),
        Product("prod-06", "Matcha Latte", "Matcha premium Uji", 32000.0, 12500.0, null, "11111111-1111-1111-1111-111111111102", 20, 5),
        Product("prod-07", "Chocolate Signature", "Cokelat panas/dingin", 27000.0, 10000.0, null, "11111111-1111-1111-1111-111111111102", 22, 5),
        Product("prod-08", "Lemon Tea", "Teh segar lemon", 20000.0, 6000.0, null, "11111111-1111-1111-1111-111111111102", 25, 5),
        Product("prod-09", "Nasi Goreng Spesial", "Dengan telur mata sapi", 38000.0, 16000.0, null, "11111111-1111-1111-1111-111111111103", 15, 4),
        Product("prod-10", "Mie Goreng Ayam", "Pedas manis", 35000.0, 14500.0, null, "11111111-1111-1111-1111-111111111103", 14, 4),
        Product("prod-11", "Chicken Katsu Rice", "Saus teriyaki", 45000.0, 19000.0, null, "11111111-1111-1111-1111-111111111103", 3, 4),
        Product("prod-12", "French Fries", "Saus keju", 25000.0, 9500.0, null, "11111111-1111-1111-1111-111111111104", 30, 6),
        Product("prod-13", "Pisang Goreng Keju", "Topping keju susu", 22000.0, 8000.0, null, "11111111-1111-1111-1111-111111111104", 18, 6),
        Product("prod-14", "Croissant Butter", "Renyah dan gurih", 24000.0, 9500.0, null, "11111111-1111-1111-1111-111111111104", 12, 6),
        Product("prod-15", "Cheesecake Slice", "New York style", 35000.0, 15000.0, null, "11111111-1111-1111-1111-111111111105", 10, 3),
        Product("prod-16", "Brownies Ice Cream", "Hangat dengan vanilla", 38000.0, 16000.0, null, "11111111-1111-1111-1111-111111111105", 9, 3)
    )

    val tables = listOf(
        RestaurantTable("tbl-1", "Meja 1", "M1"),
        RestaurantTable("tbl-2", "Meja 2", "M2"),
        RestaurantTable("tbl-3", "Meja 3", "M3"),
        RestaurantTable("tbl-4", "Meja 4", "M4"),
        RestaurantTable("tbl-5", "Meja 5", "M5"),
        RestaurantTable("tbl-ta", "Takeaway", "TA")
    )

    val promotions = listOf(
        Promotion("promo-1", "usr-owner-01", "Diskon Member Malam 10%", "percentage", 10.0, 50000.0, 1, "order", null, true),
        Promotion("promo-2", "usr-owner-01", "Potongan Bundling Kopi Rp10.000", "fixed", 10000.0, 60000.0, 2, "order", null, true)
    )

    val staffList = listOf(
        StaffProfile(
            userId = "usr-owner-01",
            ownerId = "usr-owner-01",
            fullName = "Yuri Pratama (Owner)",
            email = "owner@keretamalam.id",
            role = StaffRole.OWNER,
            isActive = true,
            permissions = PermissionKey.entries.associate { it.key to true }
        ),
        StaffProfile(
            userId = "usr-mgr-02",
            ownerId = "usr-owner-01",
            fullName = "Raka Mahendra (Manager)",
            email = "manager@keretamalam.id",
            role = StaffRole.MANAGER,
            isActive = true,
            permissions = PermissionKey.entries.associate { it.key to (it != PermissionKey.SETTINGS) }
        ),
        StaffProfile(
            userId = "usr-csh-03",
            ownerId = "usr-owner-01",
            fullName = "Nadia Putri (Kasir Shift Malam)",
            email = "kasir@keretamalam.id",
            role = StaffRole.CASHIER,
            isActive = true,
            permissions = mapOf(
                "dashboard" to true,
                "orders" to true,
                "payments" to true,
                "tables" to true,
                "customers" to true,
                "products" to false,
                "inventory" to false,
                "reports" to false,
                "promotions" to false,
                "settings" to false
            )
        )
    )

    val initialOrders = listOf(
        OrderWithItems(
            id = "ord-1001",
            orderNo = 1001,
            source = "pos",
            status = OrderStatus.COMPLETED,
            tableId = "tbl-1",
            customerName = "Budi Santoso",
            notes = null,
            subtotal = 75000.0,
            discount = 0.0,
            tax = 0.0,
            total = 75000.0,
            isPaid = true,
            paymentMethod = "cash",
            paymentStatus = PaymentStatus.PAID,
            createdAt = "2026-10-11 19:20",
            updatedAt = "2026-10-11 19:35",
            restaurantTable = TableNameRef("Meja 1"),
            orderItems = listOf(
                OrderItem("oi-1", "ord-1001", "prod-05", "Kopi Susu Gula Aren", 25000.0, 2, "Less Sugar · Normal Ice", 50000.0, 9000.0),
                OrderItem("oi-2", "ord-1001", "prod-12", "French Fries", 25000.0, 1, null, 25000.0, 9500.0)
            ),
            payments = listOf(PaymentRecord("pay-1", "ord-1001", "cash", 75000.0, 100000.0, 25000.0))
        ),
        OrderWithItems(
            id = "ord-1002",
            orderNo = 1002,
            source = "qr",
            status = OrderStatus.PREPARING,
            tableId = "tbl-3",
            customerName = "Alya Nabila",
            notes = "Minta tisu tambahan",
            subtotal = 77000.0,
            discount = 0.0,
            tax = 0.0,
            total = 77000.0,
            isPaid = true,
            paymentMethod = "qris",
            paymentStatus = PaymentStatus.PAID,
            createdAt = "2026-10-11 20:10",
            updatedAt = "2026-10-11 20:12",
            restaurantTable = TableNameRef("Meja 3"),
            orderItems = listOf(
                OrderItem("oi-3", "ord-1002", "prod-06", "Matcha Latte", 32000.0, 1, "Normal Sugar · Less Ice", 32000.0, 12500.0),
                OrderItem("oi-4", "ord-1002", "prod-11", "Chicken Katsu Rice", 45000.0, 1, "Saus pisah", 45000.0, 19000.0)
            ),
            payments = listOf(PaymentRecord("pay-2", "ord-1002", "qris", 77000.0, null, null))
        ),
        OrderWithItems(
            id = "ord-1003",
            orderNo = 1003,
            source = "qr",
            status = OrderStatus.NEW,
            tableId = "tbl-2",
            customerName = "Dimas Prakoso",
            notes = "Bayar di kasir / QRIS",
            subtotal = 58000.0,
            discount = 0.0,
            tax = 0.0,
            total = 58000.0,
            isPaid = false,
            paymentMethod = "qris",
            paymentStatus = PaymentStatus.AWAITING_CONFIRMATION,
            createdAt = "2026-10-11 20:40",
            updatedAt = "2026-10-11 20:40",
            restaurantTable = TableNameRef("Meja 2"),
            orderItems = listOf(
                OrderItem("oi-5", "ord-1003", "prod-03", "Cappuccino", 28000.0, 1, "Hot", 28000.0, 10500.0),
                OrderItem("oi-6", "ord-1003", "prod-04", "Caffe Latte", 30000.0, 1, "Iced", 30000.0, 11000.0)
            ),
            payments = emptyList()
        )
    )

    val initialShifts = listOf(
        StaffShift(
            id = "shf-01",
            staffUserId = "usr-csh-03",
            staffName = "Nadia Putri (Kasir Shift Malam)",
            openingCash = 300000.0,
            closingCash = null,
            expectedCash = 375000.0,
            status = "open",
            notes = "Shift sore-malam operasional normal",
            auditVerified = false,
            openedAt = "2026-10-11 15:00"
        ),
        StaffShift(
            id = "shf-00",
            staffUserId = "usr-mgr-02",
            staffName = "Raka Mahendra (Manager)",
            openingCash = 250000.0,
            closingCash = 890000.0,
            expectedCash = 890000.0,
            status = "closed",
            notes = "Kas sesuai fisik laci",
            auditVerified = true,
            openedAt = "2026-10-10 08:00",
            closedAt = "2026-10-10 22:00"
        )
    )

    val initialExpenses = listOf(
        Expense("exp-01", "usr-owner-01", 45000.0, "Bahan baku", "Pembelian susu segar full cream 2 karton & es batu kristal", "2026-10-11", "cash"),
        Expense("exp-02", "usr-owner-01", 30000.0, "Operasional", "Kertas struk thermal 58mm (6 roll)", "2026-10-11", "qris")
    )

    val initialInventoryMovements = listOf(
        InventoryMovement("inv-01", "prod-01", "Espresso", "Gudang Utama Bar", "in", 20, "Restock kopi biji roasting mingguan", "Raka Mahendra", "2026-10-11 08:30"),
        InventoryMovement("inv-02", "prod-05", "Kopi Susu Gula Aren", "Gudang Utama Bar", "adjustment", -1, "Kalibrasi grinder & QC shot pagi", "Nadia Putri", "2026-10-11 09:05")
    )

    val initialAuditLogs = listOf(
        AuditLog("log-01", "usr-owner-01", "usr-csh-03", "Nadia Putri", "shift.open", "staff_shifts", "shf-01", null, "{\"opening_cash\":300000}", "2026-10-11 15:00"),
        AuditLog("log-02", "usr-owner-01", "usr-csh-03", "Nadia Putri", "order.create_pos_order", "orders", "ord-1001", null, "{\"order_no\":1001,\"total\":75000,\"method\":\"cash\"}", "2026-10-11 19:20")
    )
}
