package id.keretamalam.cafe.data.repository

import android.content.Context
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import id.keretamalam.cafe.data.config.SupabaseConfigManager
import id.keretamalam.cafe.data.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import java.util.UUID

class BackendNotVerifiedException(
    message: String = "Integrasi Supabase belum aktif atau konfigurasinya tidak valid. Periksa Project ID, URL HTTPS, dan publishable/anon key di Pengaturan Outlet."
) : IllegalStateException(message)

/**
 * Lapisan Repository resmi untuk Kereta Malam Cafe Android Native.
 *
 * Seluruh nama tabel, kolom, dan RPC mengikuti skema produksi `cafe-flow`:
 * - Tabel: `staff_profiles`, `categories`, `products`, `restaurant_tables`, `orders`,
 *   `order_items`, `payments`, `promotions`, `outlet_settings`, `staff_shifts`,
 *   `audit_logs`, `expenses`, `inventory_movements`, `printer_settings`, `website_settings`.
 * - RPC: `create_pos_order`, `create_qr_order`, `approve_qr_order_with_method`,
 *   `reject_qr_order`, `get_active_public_promotions`, `get_public_outlet_settings`.
 */
class CafeFlowRepository(private val context: Context) {

    private fun requireClient(): SupabaseClient {
        return SupabaseConfigManager.getOrCreateClient(context)
            ?: throw BackendNotVerifiedException()
    }

    // ========================================================================
    // 1. AUTENTIKASI & HAK AKSES (RBAC)
    // ========================================================================
    suspend fun signInWithEmailPassword(emailInput: String, passwordInput: String): StaffProfile {
        val client = try {
            requireClient()
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException(
                e.message ?: "Konfigurasi Supabase tidak valid. Periksa URL dan Project ID di Pengaturan Outlet.",
                e
            )
        }
        try {
            client.auth.signInWith(Email) {
                email = emailInput.trim()
                password = passwordInput
            }
            val profile = fetchMyStaffProfile()
                ?: throw IllegalStateException("Login diterima, tetapi profil staf tidak ditemukan. Periksa akses akun di database.")
            logActivity(
                action = "auth.login",
                entityType = "auth",
                entityId = profile.userId,
                details = buildJsonObject { put("method", "password") }
            )
            return profile
        } catch (e: UnknownHostException) {
            throw IllegalStateException("Tidak dapat menjangkau server Supabase. Periksa URL project dan koneksi internet.", e)
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("Koneksi ke Supabase timeout. Periksa internet lalu coba lagi.", e)
        } catch (e: IOException) {
            throw IllegalStateException("Terjadi gangguan jaringan saat menghubungi Supabase. Periksa koneksi lalu coba lagi.", e)
        }
    }

    suspend fun signOut() {
        val client = SupabaseConfigManager.getOrCreateClient(context) ?: return
        val currentUser = client.auth.currentUserOrNull()
        if (currentUser != null) {
            runCatching {
                logActivity(
                    action = "auth.logout",
                    entityType = "auth",
                    entityId = currentUser.id,
                    details = buildJsonObject { put("method", "password") }
                )
            }
        }
        client.auth.signOut()
    }

    suspend fun fetchMyStaffProfile(): StaffProfile? {
        val client = requireClient()
        val user = client.auth.currentUserOrNull() ?: return null
        return client.from("staff_profiles")
            .select(Columns.raw("user_id, owner_id, full_name, role, is_active, permissions")) {
                filter { eq("user_id", user.id) }
            }
            .decodeSingleOrNull<StaffProfile>()
    }

    // ========================================================================
    // 2. KATEGORI, PRODUK, DAN MEJA
    // ========================================================================
    suspend fun fetchCategories(): List<Category> {
        val client = requireClient()
        return client.from("categories")
            .select { order("sort_order", Order.ASCENDING) }
            .decodeList<Category>()
    }

    suspend fun fetchProducts(): List<Product> {
        val client = requireClient()
        return client.from("products")
            .select { order("name", Order.ASCENDING) }
            .decodeList<Product>()
    }

    suspend fun saveProduct(product: Product): Product {
        val client = requireClient()
        val payload = buildJsonObject {
            put("name", product.name.trim())
            put("description", product.description)
            put("price", product.price)
            put("cost_price", product.costPrice ?: 0.0)
            put("image_url", product.imageUrl)
            put("category_id", product.categoryId)
            put("stock", product.stock)
            put("low_stock_threshold", product.lowStockThreshold)
            put("show_on_menu", product.showOnMenu)
            put("is_active", product.isActive)
        }
        return if (product.id.isNotBlank()) {
            client.from("products")
                .update(payload) {
                    filter { eq("id", product.id) }
                    select()
                }
                .decodeSingle<Product>()
        } else {
            client.from("products")
                .insert(payload) { select() }
                .decodeSingle<Product>()
        }
    }

    suspend fun deleteProduct(productId: String) {
        val client = requireClient()
        client.from("products").delete {
            filter { eq("id", productId) }
        }
    }

    suspend fun fetchTables(): List<RestaurantTable> {
        val client = requireClient()
        return client.from("restaurant_tables")
            .select { order("code", Order.ASCENDING) }
            .decodeList<RestaurantTable>()
    }

    fun buildValidTableQrUrl(tableCode: String): String {
        val creds = SupabaseConfigManager.loadCredentials(context)
        val baseUrl = creds.publicWebMenuBaseUrl.removeSuffix("/")
        return "$baseUrl/menu?table=$tableCode"
    }

    // ========================================================================
    // 3. KASIR & TRANSAKSI (RPC `create_pos_order` DENGAN IDEMPOTENCY KEY)
    // ========================================================================
    /**
     * Menyimpan transaksi POS menggunakan RPC `create_pos_order` sesuai skema produksi.
     * - Tidak mengurangi stok dua kali dari sisi aplikasi karena trigger `trg_deduct_stock`
     *   pada tabel `order_items` sudah mengurangi stok secara atomik di database.
     * - Menggunakan `idempotency_key` UUID unik untuk mencegah transaksi ganda.
     * - Menunggu respons sukses dari backend sebelum mengembalikan `OrderWithItems`.
     */
    suspend fun createPosOrder(
        idempotencyKey: String = UUID.randomUUID().toString(),
        tableId: String?,
        customerName: String?,
        notes: String?,
        promotionId: String?,
        subtotal: Double,
        discount: Double,
        tax: Double,
        total: Double,
        paymentMethod: String,
        cashReceived: Double?,
        items: List<CartItem>
    ): OrderWithItems {
        val client = requireClient()
        val session = client.auth.currentSessionOrNull()
            ?: throw IllegalStateException("Sesi login tidak ditemukan. Silakan login ulang.")

        val rpcPayload = buildJsonObject {
            putJsonObject("p_order") {
                put("idempotency_key", idempotencyKey)
                put("source", "pos")
                put("status", "preparing")
                if (tableId != null) put("table_id", tableId) else put("table_id", JsonNull)
                if (promotionId != null) put("promotion_id", promotionId) else put("promotion_id", JsonNull)
                put("customer_name", customerName ?: "Walk-in Customer")
                put("notes", notes)
                put("subtotal", subtotal)
                put("discount", discount)
                put("tax", tax)
                put("total", total)
                put("is_paid", true)
                put("payment_method", paymentMethod)
                put("payment_status", "paid")
            }
            putJsonArray("p_items") {
                items.forEach { cartItem ->
                    addJsonObject {
                        put("product_id", cartItem.product.id)
                        put("quantity", cartItem.quantity)
                        put("note", cartItem.note.ifBlank { null })
                    }
                }
            }
            putJsonObject("p_payment") {
                put("method", paymentMethod)
                if (cashReceived != null) put("cash_received", cashReceived) else put("cash_received", JsonNull)
            }
        }

        val result = client.postgrest.rpc("create_pos_order", rpcPayload)
        val responseObj = result.decodeAs<JsonObject>()
        val createdOrderId = responseObj["order"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Transaksi dibuat tetapi respons ID order dari backend tidak valid.")

        return fetchOrderById(createdOrderId)
            ?: throw IllegalStateException("Transaksi tersimpan tetapi gagal memuat detail struk dari backend.")
    }

    suspend fun fetchOrders(fromIso: String? = null, toIso: String? = null, limitCount: Long = 500): List<OrderWithItems> {
        val client = requireClient()
        return client.from("orders")
            .select(Columns.raw("*, order_items(*), payments(method, amount, cash_received, change_amount), restaurant_tables(name)")) {
                filter {
                    if (!fromIso.isNullOrBlank()) gte("created_at", fromIso)
                    if (!toIso.isNullOrBlank()) lte("created_at", toIso)
                }
                order("created_at", Order.DESCENDING)
                limit(limitCount)
            }
            .decodeList<OrderWithItems>()
    }

    suspend fun fetchOrderById(orderId: String): OrderWithItems? {
        val client = requireClient()
        return client.from("orders")
            .select(Columns.raw("*, order_items(*), payments(method, amount, cash_received, change_amount), restaurant_tables(name)")) {
                filter { eq("id", orderId) }
            }
            .decodeSingleOrNull<OrderWithItems>()
    }

    suspend fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
        val client = requireClient()
        client.from("orders").update(
            buildJsonObject { put("status", newStatus.raw) }
        ) {
            filter { eq("id", orderId) }
        }
    }

    suspend fun approveQrOrder(orderId: String, paymentMethod: String = "qris", cashReceived: Double? = null) {
        val client = requireClient()
        client.postgrest.rpc(
            "approve_qr_order_with_method",
            buildJsonObject {
                put("p_order_id", orderId)
                put("p_payment_method", paymentMethod)
                if (cashReceived != null) put("p_cash_received", cashReceived) else put("p_cash_received", JsonNull)
            }
        )
    }

    suspend fun rejectQrOrder(orderId: String) {
        val client = requireClient()
        client.postgrest.rpc(
            "reject_qr_order",
            buildJsonObject { put("p_order_id", orderId) }
        )
    }

    // ========================================================================
    // 4. PROMOSI, PENGATURAN OUTLET, SHIFT, KEUANGAN, & LOG AKTIVITAS
    // ========================================================================
    suspend fun fetchPromotions(): List<Promotion> {
        val client = requireClient()
        return client.from("promotions")
            .select { order("created_at", Order.DESCENDING) }
            .decodeList<Promotion>()
    }

    suspend fun fetchOutletSettings(): OutletSettings {
        val client = requireClient()
        val user = client.auth.currentUserOrNull()
        if (user != null) {
            val row = client.from("outlet_settings")
                .select { filter { eq("owner_id", user.id) } }
                .decodeSingleOrNull<OutletSettings>()
            if (row != null) return row
        }
        return OutletSettings()
    }

    suspend fun fetchStaffShifts(): List<StaffShift> {
        val client = requireClient()
        return client.from("staff_shifts")
            .select { order("opened_at", Order.DESCENDING) }
            .decodeList<StaffShift>()
    }

    suspend fun fetchExpenses(): List<Expense> {
        val client = requireClient()
        return client.from("expenses")
            .select { order("expense_date", Order.DESCENDING) }
            .decodeList<Expense>()
    }

    suspend fun fetchAuditLogs(): List<AuditLog> {
        val client = requireClient()
        return client.from("audit_logs")
            .select {
                order("created_at", Order.DESCENDING)
                limit(200)
            }
            .decodeList<AuditLog>()
    }

    suspend fun logActivity(action: String, entityType: String, entityId: String?, details: JsonObject) {
        val client = SupabaseConfigManager.getOrCreateClient(context) ?: return
        val user = client.auth.currentUserOrNull() ?: return
        client.from("audit_logs").insert(
            buildJsonObject {
                put("owner_id", user.id)
                put("actor_user_id", user.id)
                put("action", action)
                put("entity_type", entityType)
                put("entity_id", entityId)
                put("details", details)
            }
        )
    }
}
