package id.keretamalam.cafe.data.repository

import android.content.Context
import id.keretamalam.cafe.data.config.BackendIntegrationStatus
import id.keretamalam.cafe.data.config.SupabaseConfigManager
import id.keretamalam.cafe.data.config.SupabaseRuntimeCredentials
import id.keretamalam.cafe.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class BackendNotVerifiedException(
    message: String = "Layanan belum dapat terhubung. Periksa koneksi internet lalu coba lagi."
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
 * - Edge Function: `ai-assistant`.
 */
class CafeFlowRepository(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun requireVerifiedCredentials(): SupabaseRuntimeCredentials {
        val creds = SupabaseConfigManager.loadCredentials(context)
        if (creds.status != BackendIntegrationStatus.ACTIVE_VERIFIED) {
            throw BackendNotVerifiedException()
        }
        return creds
    }

    /**
     * Tests a real PostgREST request before configuration is marked verified.
     * The project ID and URL must identify the same standard Supabase project.
     */
    suspend fun verifyDatabaseConnection(
        projectId: String,
        supabaseUrl: String,
        publishableKey: String
    ) {
        val normalizedUrl = supabaseUrl.trim().trimEnd('/')
        val normalizedProjectId = projectId.trim()
        require(normalizedProjectId.isNotBlank()) { "Project ID wajib diisi." }
        require(publishableKey.isNotBlank()) { "Publishable/anon key wajib diisi." }
        require(!publishableKey.startsWith("sb_secret_") && !publishableKey.contains("service_role")) {
            "Jangan gunakan secret atau service_role key di aplikasi Android."
        }
        require(normalizedUrl == "https://$normalizedProjectId.supabase.co") {
            "Project ID dan URL Supabase tidak cocok. Periksa kembali project yang dipilih."
        }

        executeJsonRequest(
            method = "GET",
            url = "$normalizedUrl/rest/v1/staff_profiles?select=user_id&limit=1",
            headers = mapOf(
                "apikey" to publishableKey.trim(),
                "Authorization" to "Bearer ${publishableKey.trim()}",
                "Accept" to "application/json"
            )
        )
    }

    private fun buildHeaders(creds: SupabaseRuntimeCredentials, preferReturn: String? = null): Map<String, String> {
        val bearer = creds.accessToken.ifBlank { creds.publishableKey }
        val map = mutableMapOf(
            "apikey" to creds.publishableKey,
            "Authorization" to "Bearer $bearer",
            "Content-Type" to "application/json"
        )
        if (!preferReturn.isNullOrBlank()) {
            map["Prefer"] = preferReturn
        }
        return map
    }

    private suspend fun executeJsonRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        bodyJson: String? = null
    ): String = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        val requestBody = bodyJson?.toRequestBody(jsonMediaType)
        when (method.uppercase()) {
            "GET" -> reqBuilder.get()
            "POST" -> reqBuilder.post(requestBody ?: "".toRequestBody(jsonMediaType))
            "PATCH" -> reqBuilder.patch(requestBody ?: "".toRequestBody(jsonMediaType))
            "DELETE" -> if (requestBody != null) reqBuilder.delete(requestBody) else reqBuilder.delete()
            else -> reqBuilder.get()
        }
        httpClient.newCall(reqBuilder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errMsg = runCatching {
                    val obj = json.parseToJsonElement(raw).jsonObject
                    obj["msg"]?.jsonPrimitive?.contentOrNull
                        ?: obj["message"]?.jsonPrimitive?.contentOrNull
                        ?: obj["error_description"]?.jsonPrimitive?.contentOrNull
                        ?: raw
                }.getOrDefault(raw.ifBlank { "HTTP ${response.code}" })
                // Keep credentials and response bodies out of the UI; retain safe diagnostics.
                throw IllegalStateException("Permintaan Supabase gagal (HTTP " + response.code + ", " + request.url.encodedPath + ").")
            }
            raw
        }
    }

    // ========================================================================
    // 1. AUTENTIKASI & HAK AKSES (RBAC)
    // ========================================================================
    suspend fun signInWithEmailPassword(emailInput: String, passwordInput: String): StaffProfile {
        val creds = requireVerifiedCredentials()
        val authUrl = "${creds.supabaseUrl}/auth/v1/token?grant_type=password"
        val payload = buildJsonObject {
            put("email", emailInput.trim())
            put("password", passwordInput)
        }
        val raw = executeJsonRequest(
            method = "POST",
            url = authUrl,
            headers = buildHeaders(creds),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        val authObj = json.parseToJsonElement(raw).jsonObject
        val accessToken = authObj["access_token"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Token sesi tidak ditemukan pada respons Supabase Auth.")
        val userObj = authObj["user"]?.jsonObject
        val userId = userObj?.get("id")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("User ID tidak ditemukan pada respons Supabase Auth.")
        val userEmail = userObj["email"]?.jsonPrimitive?.contentOrNull ?: emailInput.trim()

        SupabaseConfigManager.saveAuthSession(context, accessToken, userId, userEmail)

        val profile = try {
            fetchMyStaffProfile()
        } catch (error: Exception) {
            SupabaseConfigManager.clearAuthSession(context)
            throw error
        } ?: run {
            SupabaseConfigManager.clearAuthSession(context)
            throw IllegalStateException(
                "Akun berhasil login, tetapi belum terdaftar di staff_profiles. Minta Owner menambahkan akun ini sebelum digunakan."
            )
        }

        if (!profile.isActive) {
            SupabaseConfigManager.clearAuthSession(context)
            throw IllegalStateException(
                "Akun staff ini sedang nonaktif. Hubungi Owner atau Manager."
            )
        }

        runCatching {
            logActivity(
                action = "auth.login",
                entityType = "auth",
                entityId = profile.userId,
                details = buildJsonObject { put("method", "password") }
            )
        }
        return profile
    }

    suspend fun signOut() {
        val creds = SupabaseConfigManager.loadCredentials(context)
        if (creds.status == BackendIntegrationStatus.ACTIVE_VERIFIED && creds.accessToken.isNotBlank()) {
            runCatching {
                logActivity(
                    action = "auth.logout",
                    entityType = "auth",
                    entityId = creds.authenticatedUserId,
                    details = buildJsonObject { put("method", "password") }
                )
                executeJsonRequest(
                    method = "POST",
                    url = "${creds.supabaseUrl}/auth/v1/logout",
                    headers = buildHeaders(creds)
                )
            }
        }
        SupabaseConfigManager.clearAuthSession(context)
    }

    suspend fun fetchMyStaffProfile(): StaffProfile? {
        val creds = requireVerifiedCredentials()
        if (creds.authenticatedUserId.isBlank()) return null
        val encodedId = URLEncoder.encode(creds.authenticatedUserId, "UTF-8")
        val url = "${creds.supabaseUrl}/rest/v1/staff_profiles?select=user_id,owner_id,full_name,role,is_active,permissions&user_id=eq.$encodedId&limit=1"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        val list = json.decodeFromString<List<StaffProfile>>(raw)
        return list.firstOrNull()?.copy(email = creds.authenticatedEmail)
    }

    // ========================================================================
    // 2. KATEGORI, PRODUK, DAN MEJA
    // ========================================================================
    suspend fun fetchCategories(): List<Category> {
        val creds = requireVerifiedCredentials()
        val url = "${creds.supabaseUrl}/rest/v1/categories?select=*&order=sort_order.asc"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    suspend fun fetchProducts(): List<Product> {
        val creds = requireVerifiedCredentials()
        val url = "${creds.supabaseUrl}/rest/v1/products?select=*&order=name.asc"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    suspend fun saveProduct(product: Product): Product {
        val creds = requireVerifiedCredentials()
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
        val bodyStr = json.encodeToString(JsonObject.serializer(), payload)
        val raw = if (product.id.isNotBlank()) {
            val encodedId = URLEncoder.encode(product.id, "UTF-8")
            executeJsonRequest(
                method = "PATCH",
                url = "${creds.supabaseUrl}/rest/v1/products?id=eq.$encodedId",
                headers = buildHeaders(creds, "return=representation"),
                bodyJson = bodyStr
            )
        } else {
            executeJsonRequest(
                method = "POST",
                url = "${creds.supabaseUrl}/rest/v1/products",
                headers = buildHeaders(creds, "return=representation"),
                bodyJson = bodyStr
            )
        }
        return json.decodeFromString<List<Product>>(raw).first()
    }

    suspend fun deleteProduct(productId: String) {
        val creds = requireVerifiedCredentials()
        val encodedId = URLEncoder.encode(productId, "UTF-8")
        executeJsonRequest(
            method = "DELETE",
            url = "${creds.supabaseUrl}/rest/v1/products?id=eq.$encodedId",
            headers = buildHeaders(creds)
        )
    }

    suspend fun fetchTables(): List<RestaurantTable> {
        val creds = requireVerifiedCredentials()
        val url = "${creds.supabaseUrl}/rest/v1/restaurant_tables?select=*&order=code.asc"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    fun buildValidTableQrUrl(tableCode: String): String {
        val creds = SupabaseConfigManager.loadCredentials(context)
        val baseUrl = creds.publicWebMenuBaseUrl.removeSuffix("/")
        return "$baseUrl/menu?table=$tableCode"
    }

    // ========================================================================
    // 3. KASIR & TRANSAKSI (RPC `create_pos_order` DENGAN IDEMPOTENCY KEY)
    // ========================================================================
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
        val creds = requireVerifiedCredentials()
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

        val raw = executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/rest/v1/rpc/create_pos_order",
            headers = buildHeaders(creds),
            bodyJson = json.encodeToString(JsonObject.serializer(), rpcPayload)
        )
        val responseObj = json.parseToJsonElement(raw).jsonObject
        val createdOrderId = responseObj["order"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Transaksi dibuat tetapi respons ID order dari backend tidak valid.")

        return fetchOrderById(createdOrderId)
            ?: throw IllegalStateException("Transaksi tersimpan tetapi gagal memuat detail struk dari backend.")
    }

    suspend fun fetchOrders(fromIso: String? = null, toIso: String? = null, limitCount: Int = 500): List<OrderWithItems> {
        val creds = requireVerifiedCredentials()
        val selectCols = URLEncoder.encode("*,order_items(*),payments(method,amount,cash_received,change_amount),restaurant_tables(name)", "UTF-8")
        val query = buildString {
            append("${creds.supabaseUrl}/rest/v1/orders?select=$selectCols&order=created_at.desc&limit=$limitCount")
            if (!fromIso.isNullOrBlank()) append("&created_at=gte.${URLEncoder.encode(fromIso, "UTF-8")}")
            if (!toIso.isNullOrBlank()) append("&created_at=lte.${URLEncoder.encode(toIso, "UTF-8")}")
        }
        val raw = executeJsonRequest("GET", query, buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    suspend fun fetchOrderById(orderId: String): OrderWithItems? {
        val creds = requireVerifiedCredentials()
        val selectCols = URLEncoder.encode("*,order_items(*),payments(method,amount,cash_received,change_amount),restaurant_tables(name)", "UTF-8")
        val encodedId = URLEncoder.encode(orderId, "UTF-8")
        val url = "${creds.supabaseUrl}/rest/v1/orders?select=$selectCols&id=eq.$encodedId&limit=1"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString<List<OrderWithItems>>(raw).firstOrNull()
    }

    suspend fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
        val creds = requireVerifiedCredentials()
        val encodedId = URLEncoder.encode(orderId, "UTF-8")
        val payload = buildJsonObject { put("status", newStatus.raw) }
        executeJsonRequest(
            method = "PATCH",
            url = "${creds.supabaseUrl}/rest/v1/orders?id=eq.$encodedId",
            headers = buildHeaders(creds),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
    }

    suspend fun approveQrOrder(orderId: String, paymentMethod: String = "qris", cashReceived: Double? = null) {
        val creds = requireVerifiedCredentials()
        val payload = buildJsonObject {
            put("p_order_id", orderId)
            put("p_payment_method", paymentMethod)
            if (cashReceived != null) put("p_cash_received", cashReceived) else put("p_cash_received", JsonNull)
        }
        executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/rest/v1/rpc/approve_qr_order_with_method",
            headers = buildHeaders(creds),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
    }

    suspend fun rejectQrOrder(orderId: String) {
        val creds = requireVerifiedCredentials()
        val payload = buildJsonObject { put("p_order_id", orderId) }
        executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/rest/v1/rpc/reject_qr_order",
            headers = buildHeaders(creds),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
    }

    // ========================================================================
    // 4. PROMOSI, PENGATURAN OUTLET, SHIFT, KEUANGAN, AI, & LOG AKTIVITAS
    // ========================================================================
    suspend fun fetchPromotions(): List<Promotion> {
        val creds = requireVerifiedCredentials()
        val url = "${creds.supabaseUrl}/rest/v1/promotions?select=*&order=created_at.desc"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    suspend fun fetchOutletSettings(): OutletSettings {
        val creds = requireVerifiedCredentials()
        val url = "${creds.supabaseUrl}/rest/v1/outlet_settings?select=*&limit=1"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString<List<OutletSettings>>(raw).firstOrNull() ?: OutletSettings()
    }

    suspend fun fetchStaffShifts(): List<StaffShift> {
        val creds = requireVerifiedCredentials()
        val url = "${creds.supabaseUrl}/rest/v1/staff_shifts?select=*&order=opened_at.desc"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    suspend fun fetchExpenses(): List<Expense> {
        val creds = requireVerifiedCredentials()
        val url = "${creds.supabaseUrl}/rest/v1/expenses?select=*&order=expense_date.desc"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    suspend fun fetchAuditLogs(): List<AuditLog> {
        val creds = requireVerifiedCredentials()
        val url = "${creds.supabaseUrl}/rest/v1/audit_logs?select=*&order=created_at.desc&limit=200"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    suspend fun invokeAiAssistant(prompt: String): String {
        val creds = requireVerifiedCredentials()
        val payload = buildJsonObject { put("prompt", prompt) }
        val raw = executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/functions/v1/ai-assistant",
            headers = buildHeaders(creds),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        val obj = json.parseToJsonElement(raw).jsonObject
        return obj["reply"]?.jsonPrimitive?.contentOrNull
            ?: obj["answer"]?.jsonPrimitive?.contentOrNull
            ?: raw
    }

    suspend fun logActivity(action: String, entityType: String, entityId: String?, details: JsonObject) {
        val creds = SupabaseConfigManager.loadCredentials(context)
        if (creds.status != BackendIntegrationStatus.ACTIVE_VERIFIED || creds.authenticatedUserId.isBlank()) return
        val payload = buildJsonObject {
            put("owner_id", creds.authenticatedUserId)
            put("actor_user_id", creds.authenticatedUserId)
            put("action", action)
            put("entity_type", entityType)
            put("entity_id", entityId)
            put("details", details)
        }
        runCatching {
            executeJsonRequest(
                method = "POST",
                url = "${creds.supabaseUrl}/rest/v1/audit_logs",
                headers = buildHeaders(creds),
                bodyJson = json.encodeToString(JsonObject.serializer(), payload)
            )
        }
    }
}
