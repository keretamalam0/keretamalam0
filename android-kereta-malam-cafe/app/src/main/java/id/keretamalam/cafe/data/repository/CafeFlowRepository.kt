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
                    obj["error_description"]?.jsonPrimitive?.contentOrNull
                        ?: obj["msg"]?.jsonPrimitive?.contentOrNull
                        ?: obj["message"]?.jsonPrimitive?.contentOrNull
                        ?: obj["error"]?.jsonPrimitive?.contentOrNull
                        ?: obj["code"]?.jsonPrimitive?.contentOrNull
                        ?: "Respons error tidak menyertakan detail."
                }.getOrDefault("Respons error tidak dapat dibaca.")
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .replace("\t", " ")
                    .take(160)
                // Show only short server diagnostics; never expose request headers, keys, or password.
                throw IllegalStateException(
                    "Permintaan Supabase gagal (HTTP ${response.code}, ${response.request.url.encodedPath}): $errMsg"
                )
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

    suspend fun uploadProductImage(bytes: ByteArray, mimeType: String, fileExtension: String): String {
        require(bytes.isNotEmpty()) { "File foto kosong." }
        require(bytes.size <= 8 * 1024 * 1024) { "Ukuran foto maksimal 8 MB." }
        val creds = requireVerifiedCredentials()
        val safeExt = fileExtension.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "jpg" }
        val objectPath = "products/${UUID.randomUUID()}.$safeExt"
        val encodedPath = objectPath.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        val request = Request.Builder()
            .url("${creds.supabaseUrl}/storage/v1/object/product-images/$encodedPath")
            .header("apikey", creds.publishableKey)
            .header("Authorization", "Bearer ${creds.accessToken.ifBlank { creds.publishableKey }}")
            .header("Content-Type", mimeType)
            .header("x-upsert", "false")
            .post(bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string().orEmpty().take(180)
                    throw IllegalStateException("Upload foto gagal (HTTP ${response.code}). Periksa bucket product-images dan Storage policy. $detail")
                }
            }
        }
        return "${creds.supabaseUrl}/storage/v1/object/public/product-images/$encodedPath"
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
                // Sandbox IDs (for example "tbl-1") are not UUIDs accepted by public.orders.table_id.
                val validTableId = tableId?.takeIf { it.matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")) }
                if (validTableId != null) put("table_id", validTableId) else put("table_id", JsonNull)
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

    suspend fun deleteOrders(orderIds: List<String>) {
        val creds = requireVerifiedCredentials()
        orderIds.distinct().forEach { orderId ->
            val encodedId = URLEncoder.encode(orderId, "UTF-8")
            // Remove child records first so deletion also works if foreign keys are not configured with CASCADE.
            executeJsonRequest("DELETE", "${creds.supabaseUrl}/rest/v1/order_items?order_id=eq.$encodedId", buildHeaders(creds))
            executeJsonRequest("DELETE", "${creds.supabaseUrl}/rest/v1/payments?order_id=eq.$encodedId", buildHeaders(creds))
            executeJsonRequest("DELETE", "${creds.supabaseUrl}/rest/v1/orders?id=eq.$encodedId", buildHeaders(creds))
        }
    }

    suspend fun deleteAllOrders() {
        val orders = fetchOrders(limitCount = 10000)
        deleteOrders(orders.map { it.id })
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

    suspend fun updateOutletSettings(settings: OutletSettings) {
        val creds = requireVerifiedCredentials()
        val ownerId = creds.authenticatedUserId
        require(ownerId.isNotBlank()) { "Silakan masuk sebagai pemilik outlet terlebih dahulu." }
        val payload = buildJsonObject {
            put("owner_id", ownerId)
            put("name", settings.name)
            put("address", settings.address)
            put("phone", settings.phone)
            put("tax_rate", settings.taxRate)
            put("service_rate", settings.serviceRate)
            put("tax_enabled", settings.taxEnabled)
            put("service_enabled", settings.serviceEnabled)
            put("cash_enabled", settings.cashEnabled)
            put("card_enabled", settings.cardEnabled)
            put("qris_enabled", settings.qrisEnabled)
            put("app_language", settings.appLanguage)
            put("global_theme", settings.globalTheme)
            put("public_web_base_url", settings.publicWebBaseUrl)
        }
        val encodedOwner = URLEncoder.encode(ownerId, "UTF-8")
        val existingRaw = executeJsonRequest(
            method = "GET",
            url = "${creds.supabaseUrl}/rest/v1/outlet_settings?select=id&owner_id=eq.$encodedOwner&limit=1",
            headers = buildHeaders(creds)
        )
        val existing = json.parseToJsonElement(existingRaw).jsonArray.firstOrNull()
        if (existing != null) {
            val id = existing.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("Data outlet tidak memiliki ID.")
            val encodedId = URLEncoder.encode(id, "UTF-8")
            executeJsonRequest(
                method = "PATCH",
                url = "${creds.supabaseUrl}/rest/v1/outlet_settings?id=eq.$encodedId",
                headers = buildHeaders(creds, "return=representation"),
                bodyJson = json.encodeToString(JsonObject.serializer(), payload)
            )
        } else {
            executeJsonRequest(
                method = "POST",
                url = "${creds.supabaseUrl}/rest/v1/outlet_settings",
                headers = buildHeaders(creds, "return=representation"),
                bodyJson = json.encodeToString(JsonObject.serializer(), payload)
            )
        }
    }

    suspend fun inviteStaff(fullName: String, email: String, role: String, permissions: Map<String, Boolean>) {
        val creds = requireVerifiedCredentials()
        val payload = buildJsonObject {
            put("full_name", fullName.trim())
            put("email", email.trim())
            put("role", if (role == "manager") "manager" else "cashier")
            putJsonObject("permissions") { permissions.forEach { (key, value) -> put(key, value) } }
        }
        val raw = executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/functions/v1/invite-staff",
            headers = buildHeaders(creds),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        val response = json.parseToJsonElement(raw).jsonObject
        if (response["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            throw IllegalStateException("Undangan gagal dikirim. Periksa email dan hak akses akun.")
        }
    }

    suspend fun fetchPaymentMethods(): List<PaymentMethod> {
        val creds = requireVerifiedCredentials()
        val raw = executeJsonRequest("GET",
            "${creds.supabaseUrl}/rest/v1/payment_methods?select=*&order=sort_order.asc,name.asc",
            buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    suspend fun savePaymentMethod(method: PaymentMethod): PaymentMethod {
        val creds = requireVerifiedCredentials()
        val payload = buildJsonObject {
            put("owner_id", creds.authenticatedUserId)
            put("code", method.code.ifBlank { method.type.lowercase() + "-" + UUID.randomUUID().toString().take(8) })
            put("name", method.name.trim())
            put("type", method.type)
            put("provider", method.provider.trim())
            put("account_name", method.accountName.trim())
            put("account_number", method.accountNumber.trim())
            put("instructions", method.instructions.trim())
            put("image_url", method.imageUrl)
            put("is_active", method.isActive)
            put("sort_order", method.sortOrder)
        }
        val url = if (method.id.isBlank()) "${creds.supabaseUrl}/rest/v1/payment_methods"
            else "${creds.supabaseUrl}/rest/v1/payment_methods?id=eq.${URLEncoder.encode(method.id, "UTF-8")}"
        val raw = executeJsonRequest(if (method.id.isBlank()) "POST" else "PATCH", url,
            buildHeaders(creds, "return=representation"), json.encodeToString(JsonObject.serializer(), payload))
        return json.decodeFromString<List<PaymentMethod>>(raw).firstOrNull()
            ?: throw IllegalStateException("Metode pembayaran gagal disimpan.")
    }

    suspend fun deletePaymentMethod(id: String) {
        val creds = requireVerifiedCredentials()
        executeJsonRequest("DELETE",
            "${creds.supabaseUrl}/rest/v1/payment_methods?id=eq.${URLEncoder.encode(id, "UTF-8")}",
            buildHeaders(creds))
    }

    suspend fun uploadPaymentMethodImage(bytes: ByteArray, mimeType: String, fileExtension: String): String {
        require(bytes.isNotEmpty()) { "File gambar kosong." }
        require(bytes.size <= 8 * 1024 * 1024) { "Ukuran gambar maksimal 8 MB." }
        val creds = requireVerifiedCredentials()
        val ext = fileExtension.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "jpg" }
        val objectPath = "${creds.authenticatedUserId}/${UUID.randomUUID()}.$ext"
        val encodedPath = objectPath.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        val request = Request.Builder()
            .url("${creds.supabaseUrl}/storage/v1/object/payment-method-images/$encodedPath")
            .header("apikey", creds.publishableKey)
            .header("Authorization", "Bearer ${creds.accessToken.ifBlank { creds.publishableKey }}")
            .header("Content-Type", mimeType)
            .header("x-upsert", "false")
            .post(bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Gagal mengunggah gambar. Coba lagi dengan file JPG/PNG/WebP.")
            }
        }
        return "${creds.supabaseUrl}/storage/v1/object/public/payment-method-images/$encodedPath"
    }

    suspend fun fetchStaffShifts(): List<StaffShift> {
        val creds = requireVerifiedCredentials()
        val url = "${creds.supabaseUrl}/rest/v1/staff_shifts?select=*&order=opened_at.desc"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    suspend fun verifyStaffShiftAudit(shiftId: String): StaffShift {
        val creds = requireVerifiedCredentials()
        require(shiftId.isNotBlank()) { "ID shift tidak valid." }
        val encodedId = java.net.URLEncoder.encode(shiftId, "UTF-8")
        val url = "${creds.supabaseUrl}/rest/v1/staff_shifts?id=eq.$encodedId&select=*"
        val raw = executeJsonRequest("PATCH", url, buildHeaders(creds, "return=representation"), """{"audit_verified":true}""")
        return json.decodeFromString<List<StaffShift>>(raw).firstOrNull()
            ?: throw IllegalStateException("Supabase tidak mengembalikan shift yang diperbarui. Periksa izin UPDATE, RLS, dan kolom audit_verified.")
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
