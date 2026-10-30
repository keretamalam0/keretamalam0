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
 * Seluruh nama tabel, kolom, dan RPC mengikuti skema backend produksi yang sudah terhubung:
 * - Tabel: `staff_profiles`, `categories`, `products`, `restaurant_tables`, `orders`,
 *   `order_items`, `payments`, `promotions`, `outlet_settings`, `staff_shifts`,
 *   `audit_logs`, `expenses`, `inventory_movements`, `printer_settings`, `website_settings`.
 * - RPC: `create_pos_order`, `create_qr_order`, `approve_qr_order_with_method`,
 *   `reject_qr_order`, `get_active_public_promotions`, `get_public_outlet_settings`.
 * - Edge Function: `ai-assistant`.
 */
data class PrinterSettingRecord(
    val printerRole: String,
    val printerName: String,
    val bluetoothAddress: String,
    val isEnabled: Boolean,
    val paperWidth: Int
)

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
            "PUT" -> reqBuilder.put(requestBody ?: "".toRequestBody(jsonMediaType))
            "PATCH" -> reqBuilder.patch(requestBody ?: "".toRequestBody(jsonMediaType))
            "DELETE" -> if (requestBody != null) reqBuilder.delete(requestBody) else reqBuilder.delete()
            else -> reqBuilder.get()
        }
        httpClient.newCall(reqBuilder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsedError = runCatching {
                    val element = json.parseToJsonElement(raw)
                    val obj = element as? JsonObject
                    listOf("error_description", "msg", "message", "error", "error_code", "code")
                        .firstNotNullOfOrNull { key ->
                            when (val value = obj?.get(key)) {
                                is JsonPrimitive -> value.contentOrNull
                                is JsonObject -> (value["message"] as? JsonPrimitive)?.contentOrNull
                                else -> null
                            }?.takeIf { it.isNotBlank() }
                        }
                }.getOrNull()
                // Gateways may return plain text or HTML instead of Supabase JSON.
                // Keep a short readable diagnostic rather than hiding it behind a parser failure.
                val plainError = raw
                    .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(160)
                    .takeIf { it.isNotBlank() && !it.startsWith("{") && !it.startsWith("[") }
                val errMsg = (parsedError ?: plainError ?: when (response.code) {
                    401 -> "Sesi login tidak valid atau sudah kedaluwarsa. Login ulang lalu coba lagi."
                    403 -> "Akun tidak diizinkan melakukan tindakan ini."
                    else -> "Server tidak mengirim detail error."
                })
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
        val refreshToken = authObj["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val userObj = authObj["user"]?.jsonObject
        val userId = userObj?.get("id")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("User ID tidak ditemukan pada respons Supabase Auth.")
        val userEmail = userObj["email"]?.jsonPrimitive?.contentOrNull ?: emailInput.trim()

        SupabaseConfigManager.saveAuthSession(context, accessToken, userId, userEmail, refreshToken)

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

    private suspend fun refreshAuthSession(creds: SupabaseRuntimeCredentials): SupabaseRuntimeCredentials {
        if (creds.refreshToken.isBlank()) {
            throw IllegalStateException(
                "Sesi login sudah kedaluwarsa. Keluar lalu login kembali, kemudian coba ganti password."
            )
        }
        val payload = buildJsonObject { put("refresh_token", creds.refreshToken) }
        val raw = try {
            executeJsonRequest(
                method = "POST",
                url = "${creds.supabaseUrl}/auth/v1/token?grant_type=refresh_token",
                headers = mapOf(
                    "apikey" to creds.publishableKey,
                    "Content-Type" to "application/json"
                ),
                bodyJson = json.encodeToString(JsonObject.serializer(), payload)
            )
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            if (message.contains("HTTP 400") || message.contains("HTTP 401")) {
                throw IllegalStateException(
                    "Sesi login sudah kedaluwarsa. Keluar lalu login kembali, kemudian coba ganti password."
                )
            }
            throw error
        }
        val session = json.parseToJsonElement(raw).jsonObject
        val accessToken = session["access_token"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Supabase tidak mengirim access token baru.")
        val refreshToken = session["refresh_token"]?.jsonPrimitive?.contentOrNull
            ?: creds.refreshToken
        val user = session["user"]?.jsonObject
        val userId = user?.get("id")?.jsonPrimitive?.contentOrNull ?: creds.authenticatedUserId
        val email = user?.get("email")?.jsonPrimitive?.contentOrNull ?: creds.authenticatedEmail
        SupabaseConfigManager.saveAuthSession(context, accessToken, userId, email, refreshToken)
        return creds.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
            authenticatedUserId = userId,
            authenticatedEmail = email
        )
    }

    suspend fun changePassword(newPassword: String) {
        require(newPassword.length >= 8) { "Password baru minimal 8 karakter." }
        val creds = requireVerifiedCredentials()
        check(creds.accessToken.isNotBlank()) { "Sesi login tidak valid. Silakan login ulang." }
        val payload = buildJsonObject { put("password", newPassword) }
        try {
            executeJsonRequest(
                method = "PUT",
                url = "${creds.supabaseUrl}/auth/v1/user",
                headers = buildHeaders(creds),
                bodyJson = json.encodeToString(JsonObject.serializer(), payload)
            )
        } catch (error: IllegalStateException) {
            // Supabase access tokens expire. Refresh once and retry only for an Auth 401.
            if (!error.message.orEmpty().contains("HTTP 401, /auth/v1/user")) throw error
            val refreshed = refreshAuthSession(creds)
            executeJsonRequest(
                method = "PUT",
                url = "${refreshed.supabaseUrl}/auth/v1/user",
                headers = buildHeaders(refreshed),
                bodyJson = json.encodeToString(JsonObject.serializer(), payload)
            )
        }
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

    suspend fun fetchStaffProfiles(): List<StaffProfile> {
        val creds = requireVerifiedCredentials()
        // Single-app setup: an active Owner can see every registered staff profile,
        // regardless of the profile's owner_id. RLS enforces the Owner-only boundary.
        val raw = executeJsonRequest(
            "GET",
            "${creds.supabaseUrl}/rest/v1/staff_profiles?select=user_id,owner_id,full_name,email,role,is_active,permissions&order=created_at.asc",
            buildHeaders(creds)
        )
        return json.decodeFromString(raw)
    }

    suspend fun fetchMyStaffProfile(): StaffProfile? {
        val creds = requireVerifiedCredentials()
        if (creds.authenticatedUserId.isBlank()) return null
        val encodedId = URLEncoder.encode(creds.authenticatedUserId, "UTF-8")
        val url = "${creds.supabaseUrl}/rest/v1/staff_profiles?select=user_id,owner_id,full_name,email,role,is_active,permissions&user_id=eq.$encodedId&limit=1"
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

    suspend fun fetchOrderAlertSoundUrl(): String? {
        val creds = requireVerifiedCredentials()
        val ownerId = creds.authenticatedUserId
        require(ownerId.isNotBlank()) { "Silakan masuk sebagai pemilik outlet terlebih dahulu." }
        val encodedOwner = URLEncoder.encode(ownerId, "UTF-8")
        val raw = executeJsonRequest(
            "GET",
            "${creds.supabaseUrl}/rest/v1/outlet_settings?select=order_alert_sound_url&owner_id=eq.$encodedOwner&limit=1",
            buildHeaders(creds)
        )
        val row = json.parseToJsonElement(raw).jsonArray.firstOrNull()?.jsonObject
        return row?.get("order_alert_sound_url")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    suspend fun saveOrderAlertSoundUrl(soundUrl: String) {
        val creds = requireVerifiedCredentials()
        val ownerId = creds.authenticatedUserId
        require(ownerId.isNotBlank()) { "Silakan masuk sebagai pemilik outlet terlebih dahulu." }
        require(soundUrl.startsWith("https://") || soundUrl.startsWith("http://")) { "URL suara tidak valid." }
        val encodedOwner = URLEncoder.encode(ownerId, "UTF-8")
        val existingRaw = executeJsonRequest(
            "GET",
            "${creds.supabaseUrl}/rest/v1/outlet_settings?select=id&owner_id=eq.$encodedOwner&limit=1",
            buildHeaders(creds)
        )
        val row = json.parseToJsonElement(existingRaw).jsonArray.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("Pengaturan outlet belum ditemukan.")
        val id = row["id"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Data outlet tidak memiliki ID.")
        val encodedId = URLEncoder.encode(id, "UTF-8")
        val payload = buildJsonObject { put("order_alert_sound_url", soundUrl) }
        val updated = executeJsonRequest(
            "PATCH",
            "${creds.supabaseUrl}/rest/v1/outlet_settings?id=eq.$encodedId&owner_id=eq.$encodedOwner",
            buildHeaders(creds, "return=representation"),
            json.encodeToString(JsonObject.serializer(), payload)
        )
        require(json.parseToJsonElement(updated).jsonArray.isNotEmpty()) {
            "URL suara tidak tersimpan ke pengaturan outlet."
        }
    }

    suspend fun uploadOrderAlertSound(bytes: ByteArray, mimeType: String, fileExtension: String): String {
        require(bytes.isNotEmpty()) { "File suara kosong." }
        require(bytes.size <= 10 * 1024 * 1024) { "Ukuran file suara maksimal 10 MB." }
        val creds = requireVerifiedCredentials()
        val safeExt = fileExtension.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "mp3" }
        val objectPath = "order-alert-sounds/${UUID.randomUUID()}.$safeExt"
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
                    throw IllegalStateException("Upload suara gagal (HTTP ${response.code}). Periksa izin Storage bucket product-images. $detail")
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

    suspend fun recordInventoryMovement(product: Product, type: String, delta: Int, reason: String, actorName: String) {
        require(delta != 0) { "Jumlah mutasi stok tidak boleh nol." }
        require(reason.isNotBlank()) { "Catatan mutasi stok wajib diisi." }
        val creds = requireVerifiedCredentials()
        val encodedId = URLEncoder.encode(product.id, "UTF-8")
        val newStock = product.stock + delta
        require(newStock >= 0) { "Stok tidak boleh kurang dari nol." }
        val stockPayload = buildJsonObject { put("stock", newStock) }
        val updated = executeJsonRequest(
            "PATCH",
            "${creds.supabaseUrl}/rest/v1/products?id=eq.$encodedId",
            buildHeaders(creds, "return=representation"),
            json.encodeToString(JsonObject.serializer(), stockPayload)
        )
        require(json.parseToJsonElement(updated).jsonArray.isNotEmpty()) { "Stok tidak berubah. Periksa izin inventory dan akses produk." }
        try {
            val movementPayload = buildJsonObject {
                put("product_id", product.id)
                put("product_name", product.name)
                put("type", type)
                put("quantity", delta)
                put("reason", reason.trim())
                put("actor_name", actorName)
            }
            executeJsonRequest(
                "POST",
                "${creds.supabaseUrl}/rest/v1/inventory_movements",
                buildHeaders(creds, "return=minimal"),
                json.encodeToString(JsonObject.serializer(), movementPayload)
            )
        } catch (error: Throwable) {
            runCatching {
                val rollback = buildJsonObject { put("stock", product.stock) }
                executeJsonRequest("PATCH", "${creds.supabaseUrl}/rest/v1/products?id=eq.$encodedId", buildHeaders(creds, "return=minimal"), json.encodeToString(JsonObject.serializer(), rollback))
            }
            throw error
        }
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

    suspend fun saveCategory(category: Category): Category {
        val creds = requireVerifiedCredentials()
        val payload = buildJsonObject {
            put("name", category.name.trim())
            put("sort_order", category.sortOrder)
            put("is_active", category.isActive)
        }
        val raw = executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/rest/v1/categories",
            headers = buildHeaders(creds, "return=representation"),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        return json.decodeFromString<List<Category>>(raw).first()
    }

    suspend fun setCategoryActive(categoryId: String, active: Boolean) {
        val creds = requireVerifiedCredentials()
        val payload = buildJsonObject { put("is_active", active) }
        executeJsonRequest(
            "PATCH",
            "${creds.supabaseUrl}/rest/v1/categories?id=eq.${URLEncoder.encode(categoryId, "UTF-8")}",
            buildHeaders(creds, "return=minimal"),
            json.encodeToString(JsonObject.serializer(), payload)
        )
    }

    suspend fun deleteCategory(categoryId: String) {
        val creds = requireVerifiedCredentials()
        executeJsonRequest(
            "DELETE",
            "${creds.supabaseUrl}/rest/v1/categories?id=eq.${URLEncoder.encode(categoryId, "UTF-8")}",
            buildHeaders(creds)
        )
    }

    suspend fun fetchProductAddons(): List<ProductAddon> {
        val creds = requireVerifiedCredentials()
        val raw = executeJsonRequest(
            "GET",
            "${creds.supabaseUrl}/rest/v1/product_addons?select=*&order=name.asc",
            buildHeaders(creds)
        )
        return json.decodeFromString(raw)
    }

    suspend fun saveProductAddon(addon: ProductAddon): ProductAddon {
        val creds = requireVerifiedCredentials()
        require(addon.productId.isNotBlank()) { "Pilih produk untuk topping." }
        val payload = buildJsonObject {
            put("product_id", addon.productId)
            put("name", addon.name.trim())
            put("price", addon.price)
            put("is_active", addon.isActive)
        }
        val body = json.encodeToString(JsonObject.serializer(), payload)
        val url = if (addon.id.isBlank()) "${creds.supabaseUrl}/rest/v1/product_addons"
            else "${creds.supabaseUrl}/rest/v1/product_addons?id=eq.${URLEncoder.encode(addon.id, "UTF-8")}"
        val raw = executeJsonRequest(
            if (addon.id.isBlank()) "POST" else "PATCH",
            url, buildHeaders(creds, "return=representation"), body
        )
        return json.decodeFromString<List<ProductAddon>>(raw).first()
    }

    suspend fun deleteProductAddon(id: String) {
        val creds = requireVerifiedCredentials()
        executeJsonRequest(
            "DELETE",
            "${creds.supabaseUrl}/rest/v1/product_addons?id=eq.${URLEncoder.encode(id, "UTF-8")}",
            buildHeaders(creds)
        )
    }

    suspend fun fetchCustomizationOptions(): List<ProductCustomizationOption> {
        val creds = requireVerifiedCredentials()
        val raw = executeJsonRequest(
            "GET",
            "${creds.supabaseUrl}/rest/v1/product_customization_options?select=*&order=sort_order.asc,name.asc",
            buildHeaders(creds)
        )
        return json.decodeFromString(raw)
    }

    suspend fun saveCustomizationOption(option: ProductCustomizationOption): ProductCustomizationOption {
        val creds = requireVerifiedCredentials()
        require(option.productId.isNotBlank()) { "Pilih produk untuk opsi kustomisasi." }
        val payload = buildJsonObject {
            put("product_id", option.productId)
            put("group_key", option.groupKey)
            put("name", option.name.trim())
            put("price_delta", option.priceDelta)
            put("is_active", option.isActive)
            put("sort_order", option.sortOrder)
        }
        val body = json.encodeToString(JsonObject.serializer(), payload)
        val url = if (option.id.isBlank()) "${creds.supabaseUrl}/rest/v1/product_customization_options"
            else "${creds.supabaseUrl}/rest/v1/product_customization_options?id=eq.${URLEncoder.encode(option.id, "UTF-8")}"
        val raw = executeJsonRequest(
            if (option.id.isBlank()) "POST" else "PATCH",
            url, buildHeaders(creds, "return=representation"), body
        )
        return json.decodeFromString<List<ProductCustomizationOption>>(raw).first()
    }

    suspend fun deleteCustomizationOption(id: String) {
        val creds = requireVerifiedCredentials()
        executeJsonRequest(
            "DELETE",
            "${creds.supabaseUrl}/rest/v1/product_customization_options?id=eq.${URLEncoder.encode(id, "UTF-8")}",
            buildHeaders(creds)
        )
    }

    suspend fun fetchTables(): List<RestaurantTable> {
        val creds = requireVerifiedCredentials()
        val url = "${creds.supabaseUrl}/rest/v1/restaurant_tables?select=*&order=code.asc"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    suspend fun createRestaurantTable(name: String, code: String): RestaurantTable {
        val creds = requireVerifiedCredentials()
        val payload = buildJsonObject {
            put("name", name.trim())
            put("code", code.trim().uppercase())
        }
        val raw = executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/rest/v1/restaurant_tables",
            headers = buildHeaders(creds, "return=representation"),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        return json.decodeFromString<List<RestaurantTable>>(raw).firstOrNull()
            ?: throw IllegalStateException("Meja tidak dikembalikan oleh database.")
    }

    suspend fun deleteRestaurantTable(tableId: String) {
        val creds = requireVerifiedCredentials()
        val encodedId = URLEncoder.encode(tableId, "UTF-8")
        val raw = executeJsonRequest(
            method = "DELETE",
            url = "${creds.supabaseUrl}/rest/v1/restaurant_tables?id=eq.$encodedId",
            headers = buildHeaders(creds, "return=representation")
        )
        val deleted = runCatching { json.decodeFromString<List<RestaurantTable>>(raw) }.getOrDefault(emptyList())
        if (deleted.isEmpty()) {
            throw IllegalStateException("Meja tidak terhapus. Periksa izin database atau pastikan meja belum dipakai transaksi.")
        }
    }

    fun buildValidTableQrUrl(tableCode: String): String {
        val creds = SupabaseConfigManager.loadCredentials(context)
        val baseUrl = "https://kemalcafe.netlify.app"
        return "$baseUrl/menu?table=${URLEncoder.encode(tableCode, "UTF-8")}"
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
        paymentRecordMethod: String = paymentMethod,
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
                // Never send UI labels or sandbox keys (for example "row 01") to UUID columns.
                val validPromotionId = promotionId?.takeIf {
                    it.matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))
                }
                if (validPromotionId != null) put("promotion_id", validPromotionId) else put("promotion_id", JsonNull)
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
                        putJsonArray("selected_option_ids") {
                            cartItem.selectedCustomizationOptions
                                .map { it.id }
                                .filter { it.matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")) }
                                .distinct()
                                .forEach { add(it) }
                        }
                        putJsonArray("selected_addon_ids") {
                            cartItem.selectedAddons
                                .map { it.id }
                                .filter { it.matches(Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")) }
                                .distinct()
                                .forEach { add(it) }
                        }
                    }
                }
            }
            putJsonObject("p_payment") {
                put("method", paymentRecordMethod)
                if (cashReceived != null) put("cash_received", cashReceived) else put("cash_received", JsonNull)
            }
        }

        val requestBody = json.encodeToString(JsonObject.serializer(), rpcPayload)
        // A stale access token must not block checkout. Reuse the same idempotency key
        // and identical payload after refreshing, so retry cannot intentionally create a second order.
        val raw = try {
            executeJsonRequest(
                method = "POST",
                url = "${creds.supabaseUrl}/rest/v1/rpc/create_pos_order",
                headers = buildHeaders(creds),
                bodyJson = requestBody
            )
        } catch (error: IllegalStateException) {
            val message = error.message.orEmpty()
            val isExpiredJwt = message.contains("HTTP 401") &&
                (message.contains("JWT expired", ignoreCase = true) ||
                 message.contains("token expired", ignoreCase = true) ||
                 message.contains("invalid JWT", ignoreCase = true))
            if (!isExpiredJwt) throw error
            val refreshed = refreshAuthSession(creds)
            executeJsonRequest(
                method = "POST",
                url = "${refreshed.supabaseUrl}/rest/v1/rpc/create_pos_order",
                headers = buildHeaders(refreshed),
                bodyJson = requestBody
            )
        }
        val responseObj = json.parseToJsonElement(raw).jsonObject
        val createdOrderId = responseObj["order"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Transaksi dibuat tetapi respons ID order dari backend tidak valid.")

        return fetchOrderById(createdOrderId)
            ?: throw IllegalStateException("Transaksi tersimpan tetapi gagal memuat detail struk dari backend.")
    }

    suspend fun fetchCashierChatThreads(): List<CashierChatThread> {
        val creds = requireVerifiedCredentials()
        val url = "${creds.supabaseUrl}/rest/v1/cashier_chat_threads?select=*&order=last_message_at.desc&limit=100"
        return json.decodeFromString(executeJsonRequest("GET", url, buildHeaders(creds)))
    }

    suspend fun fetchCashierChatMessages(threadId: String): List<CashierChatMessage> {
        val creds = requireVerifiedCredentials()
        val encodedId = URLEncoder.encode(threadId, "UTF-8")
        val url = "${creds.supabaseUrl}/rest/v1/cashier_chat_messages?select=id,thread_id,sender_role,body,created_at&thread_id=eq.$encodedId&order=created_at.asc&limit=300"
        return json.decodeFromString(executeJsonRequest("GET", url, buildHeaders(creds)))
    }

    suspend fun deleteCashierChatThread(threadId: String) {
        val creds = requireVerifiedCredentials()
        require(threadId.isNotBlank()) { "Percakapan tidak valid." }
        val encodedId = URLEncoder.encode(threadId, "UTF-8")
        // Match Cafe Flow web: deleting the thread cascades to its messages.
        executeJsonRequest(
            method = "DELETE",
            url = "${creds.supabaseUrl}/rest/v1/cashier_chat_threads?id=eq.$encodedId",
            headers = buildHeaders(creds)
        )
    }
    suspend fun sendCashierChatMessage(threadId: String, body: String) {
        val creds = requireVerifiedCredentials()
        val payload = buildJsonObject {
            put("p_thread_id", threadId)
            put("p_body", body.trim())
        }
        executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/rest/v1/rpc/send_cashier_chat",
            headers = buildHeaders(creds),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
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

    /**
     * Deletes in bounded batches instead of making three HTTP requests per order.
     * Child rows are removed first to remain compatible with schemas without ON DELETE CASCADE.
     */
    suspend fun deleteOrders(orderIds: List<String>) {
        val creds = requireVerifiedCredentials()
        val ids = orderIds.asSequence().filter { it.isNotBlank() }.distinct().toList()
        ids.chunked(50).forEach { batch ->
            val inFilter = URLEncoder.encode("(${batch.joinToString(",")})", "UTF-8")
            executeJsonRequest(
                "DELETE",
                "${creds.supabaseUrl}/rest/v1/order_items?order_id=in.$inFilter",
                buildHeaders(creds)
            )
            executeJsonRequest(
                "DELETE",
                "${creds.supabaseUrl}/rest/v1/payments?order_id=in.$inFilter",
                buildHeaders(creds)
            )
            executeJsonRequest(
                "DELETE",
                "${creds.supabaseUrl}/rest/v1/orders?id=in.$inFilter",
                buildHeaders(creds)
            )
        }
    }

    /**
     * Fetches only order IDs page-by-page, then deletes them in batches. Avoids loading up
     * all nested order items/payments and avoids the previous 3N-request deletion loop.
     */
    suspend fun deleteAllOrders() {
        val creds = requireVerifiedCredentials()
        val ids = mutableListOf<String>()
        var offset = 0
        val pageSize = 1000
        while (true) {
            val raw = executeJsonRequest(
                "GET",
                "${creds.supabaseUrl}/rest/v1/orders?select=id&order=created_at.desc&limit=$pageSize&offset=$offset",
                buildHeaders(creds)
            )
            val page = json.parseToJsonElement(raw).jsonArray.mapNotNull { row ->
                row.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            }
            ids += page
            if (page.size < pageSize) break
            offset += pageSize
        }
        deleteOrders(ids)
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
        if (newStatus == OrderStatus.CANCELLED) {
            // The database function locks the order and restores stock atomically,
            // only once even if the cashier taps cancel repeatedly.
            val payload = buildJsonObject { put("p_order_id", orderId) }
            executeJsonRequest(
                method = "POST",
                url = "${creds.supabaseUrl}/rest/v1/rpc/cancel_order_and_restore_stock",
                headers = buildHeaders(creds),
                bodyJson = json.encodeToString(JsonObject.serializer(), payload)
            )
            return
        }
        val encodedId = URLEncoder.encode(orderId, "UTF-8")
        val payload = buildJsonObject { put("status", newStatus.raw) }
        executeJsonRequest(
            method = "PATCH",
            url = "${creds.supabaseUrl}/rest/v1/orders?id=eq.$encodedId",
            headers = buildHeaders(creds),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
    }

    suspend fun returnPaidOrder(orderId: String, reason: String, returnedBy: String) {
        val creds = requireVerifiedCredentials()
        require(reason.isNotBlank()) { "Alasan retur wajib diisi." }
        val encodedId = URLEncoder.encode(orderId, "UTF-8")
        val payload = buildJsonObject {
            put("returned_at", java.time.OffsetDateTime.now().toString())
            put("return_reason", reason.trim())
            put("returned_by", returnedBy.trim())
        }
        val response = executeJsonRequest(
            method = "PATCH",
            url = "${creds.supabaseUrl}/rest/v1/orders?id=eq.$encodedId&is_paid=eq.true&returned_at=is.null",
            headers = buildHeaders(creds, "return=representation"),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        val updatedRows = runCatching { json.parseToJsonElement(response) as? JsonArray }.getOrNull()
        check(!updatedRows.isNullOrEmpty()) {
            "Retur tidak tersimpan. Transaksi mungkin sudah diretur atau status pembayarannya bukan lunas."
        }
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

    suspend fun confirmOrderPayment(orderId: String, paymentMethod: String, cashReceived: Double? = null) {
        val creds = requireVerifiedCredentials()
        val payload = buildJsonObject {
            put("p_order_id", orderId)
            put("p_payment_method", paymentMethod.lowercase())
            if (cashReceived != null) put("p_cash_received", cashReceived) else put("p_cash_received", JsonNull)
        }
        executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/rest/v1/rpc/confirm_order_payment",
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

    suspend fun createPromotion(promotion: Promotion): Promotion {
        val creds = requireVerifiedCredentials()
        require(creds.authenticatedUserId.isNotBlank()) { "Akun pemilik outlet tidak terdeteksi." }
        val payload = buildJsonObject {
            put("owner_id", creds.authenticatedUserId)
            put("name", promotion.name.trim())
            put("type", promotion.type)
            put("value", promotion.value)
            put("min_purchase", promotion.minPurchase)
            put("max_purchase", promotion.maxPurchase)
            put("min_quantity", promotion.minQuantity.coerceAtLeast(1))
            put("scope", promotion.scope)
            if (promotion.productId.isNullOrBlank()) put("product_id", JsonNull) else put("product_id", promotion.productId)
            put("active", promotion.active)
            put("campaign_type", promotion.campaignType)
            put("allow_customer", promotion.allowCustomer)
            put("allow_cashier", promotion.allowCashier)
        }
        val raw = executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/rest/v1/promotions",
            headers = buildHeaders(creds, "return=representation"),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        return json.decodeFromString<List<Promotion>>(raw).firstOrNull()
            ?: throw IllegalStateException("Promo tidak tersimpan atau server tidak mengembalikan data promo.")
    }

    suspend fun updatePromotionActive(promotionId: String, active: Boolean) {
        val creds = requireVerifiedCredentials()
        val encodedId = URLEncoder.encode(promotionId, "UTF-8")
        val encodedOwner = URLEncoder.encode(creds.authenticatedUserId, "UTF-8")
        val payload = buildJsonObject { put("active", active) }
        val raw = executeJsonRequest(
            method = "PATCH",
            url = "${creds.supabaseUrl}/rest/v1/promotions?id=eq.$encodedId&owner_id=eq.$encodedOwner",
            headers = buildHeaders(creds, "return=representation"),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        require(json.parseToJsonElement(raw).jsonArray.isNotEmpty()) {
            "Status promo tidak berubah. Periksa hak akses atau pastikan promo milik outlet ini."
        }
    }

    suspend fun deletePromotion(promotionId: String) {
        val creds = requireVerifiedCredentials()
        val encodedId = URLEncoder.encode(promotionId, "UTF-8")
        val encodedOwner = URLEncoder.encode(creds.authenticatedUserId, "UTF-8")
        executeJsonRequest(
            method = "DELETE",
            url = "${creds.supabaseUrl}/rest/v1/promotions?id=eq.$encodedId&owner_id=eq.$encodedOwner",
            headers = buildHeaders(creds, "return=minimal")
        )
    }


    suspend fun fetchPrinterSettings(): List<PrinterSettingRecord> {
        val creds = requireVerifiedCredentials()
        require(creds.authenticatedUserId.isNotBlank()) { "Silakan masuk terlebih dahulu." }
        val owner = URLEncoder.encode(creds.authenticatedUserId, "UTF-8")
        val url = "${creds.supabaseUrl}/rest/v1/printer_settings?select=printer_role,printer_name,bluetooth_address,is_enabled,paper_width&owner_id=eq.$owner"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.parseToJsonElement(raw).jsonArray.mapNotNull { item ->
            val obj = item.jsonObject
            val role = obj["printer_role"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["printer_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val address = obj["bluetooth_address"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            PrinterSettingRecord(
                printerRole = role,
                printerName = name,
                bluetoothAddress = address,
                isEnabled = obj["is_enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                paperWidth = obj["paper_width"]?.jsonPrimitive?.intOrNull ?: 58
            )
        }
    }

    suspend fun savePrinterSetting(
        role: String,
        printerName: String,
        bluetoothAddress: String,
        enabled: Boolean = true,
        paperWidth: Int = 58
    ) {
        require(role in setOf("cashier", "kitchen")) { "Jenis printer tidak valid." }
        require(printerName.isNotBlank() && bluetoothAddress.isNotBlank()) { "Pilih perangkat printer terlebih dahulu." }
        require(paperWidth in setOf(58, 80)) { "Lebar kertas harus 58 mm atau 80 mm." }
        val creds = requireVerifiedCredentials()
        require(creds.authenticatedUserId.isNotBlank()) { "Silakan masuk terlebih dahulu." }
        val payload = buildJsonObject {
            put("owner_id", creds.authenticatedUserId)
            put("printer_role", role)
            put("printer_name", printerName)
            put("bluetooth_address", bluetoothAddress)
            put("is_enabled", enabled)
            put("paper_width", paperWidth)
            put("updated_at", java.time.Instant.now().toString())
        }
        val url = "${creds.supabaseUrl}/rest/v1/printer_settings?on_conflict=owner_id,printer_role"
        val raw = executeJsonRequest(
            method = "POST",
            url = url,
            headers = buildHeaders(creds, "resolution=merge-duplicates,return=representation"),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        require(json.parseToJsonElement(raw).jsonArray.isNotEmpty()) {
            "Konfigurasi printer tidak tersimpan di Supabase."
        }
    }

    suspend fun deletePrinterSetting(role: String) {
        require(role in setOf("cashier", "kitchen")) { "Jenis printer tidak valid." }
        val creds = requireVerifiedCredentials()
        val owner = URLEncoder.encode(creds.authenticatedUserId, "UTF-8")
        val encodedRole = URLEncoder.encode(role, "UTF-8")
        executeJsonRequest(
            method = "DELETE",
            url = "${creds.supabaseUrl}/rest/v1/printer_settings?owner_id=eq.$owner&printer_role=eq.$encodedRole",
            headers = buildHeaders(creds, "return=minimal")
        )
    }

    suspend fun fetchOutletSettings(): OutletSettings {
        val creds = requireVerifiedCredentials()
        val ownerId = creds.authenticatedUserId
        require(ownerId.isNotBlank()) { "Silakan masuk sebagai pemilik outlet terlebih dahulu." }
        val encodedOwner = URLEncoder.encode(ownerId, "UTF-8")
        val url = "${creds.supabaseUrl}/rest/v1/outlet_settings?select=*&owner_id=eq.$encodedOwner&limit=1"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString<List<OutletSettings>>(raw).firstOrNull() ?: OutletSettings()
    }

    suspend fun updateOutletLanguage(language: String) {
        require(language in setOf("id", "en", "zh", "th", "vi")) { "Bahasa aplikasi tidak didukung." }
        val creds = requireVerifiedCredentials()
        val ownerId = creds.authenticatedUserId
        require(ownerId.isNotBlank()) { "Silakan masuk sebagai pemilik outlet terlebih dahulu." }
        val encodedOwner = URLEncoder.encode(ownerId, "UTF-8")
        val existingRaw = executeJsonRequest(
            method = "GET",
            url = "${creds.supabaseUrl}/rest/v1/outlet_settings?select=id&owner_id=eq.$encodedOwner&limit=1",
            headers = buildHeaders(creds)
        )
        val existing = json.parseToJsonElement(existingRaw).jsonArray.firstOrNull()
            ?: throw IllegalStateException("Pengaturan outlet untuk akun ini belum ditemukan.")
        val id = existing.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Data outlet tidak memiliki ID.")
        val encodedId = URLEncoder.encode(id, "UTF-8")
        val payload = buildJsonObject { put("app_language", language) }
        val updatedRaw = executeJsonRequest(
            method = "PATCH",
            url = "${creds.supabaseUrl}/rest/v1/outlet_settings?id=eq.$encodedId&owner_id=eq.$encodedOwner",
            headers = buildHeaders(creds, "return=representation"),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        require(json.parseToJsonElement(updatedRaw).jsonArray.isNotEmpty()) {
            "Bahasa tidak tersimpan. Periksa kebijakan akses tabel outlet_settings."
        }
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
            put("opening_hours", settings.openingHours)
            put("instagram", settings.instagram)
            put("receipt_note", settings.receiptNote)
            put("receipt_footer", settings.receiptFooter)
            put("tax_rate", settings.taxRate)
            put("service_rate", settings.serviceRate)
            put("service_fee_mode", settings.serviceFeeMode)
            put("service_fixed_amount", settings.serviceFixedAmount)
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
        val encodedId = existing?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?.let { URLEncoder.encode(it, "UTF-8") }
        if (existing != null && encodedId == null) {
            throw IllegalStateException("Data outlet tidak memiliki ID.")
        }

        suspend fun persist(payloadToSave: JsonObject) {
            if (existing != null) {
                executeJsonRequest(
                    method = "PATCH",
                    url = "${creds.supabaseUrl}/rest/v1/outlet_settings?id=eq.$encodedId&owner_id=eq.$encodedOwner",
                    headers = buildHeaders(creds, "return=representation"),
                    bodyJson = json.encodeToString(JsonObject.serializer(), payloadToSave)
                )
            } else {
                executeJsonRequest(
                    method = "POST",
                    url = "${creds.supabaseUrl}/rest/v1/outlet_settings",
                    headers = buildHeaders(creds, "return=representation"),
                    bodyJson = json.encodeToString(JsonObject.serializer(), payloadToSave)
                )
            }
        }

        try {
            persist(payload)
        } catch (error: IllegalStateException) {
            val detail = error.message.orEmpty()
            val stalePublicUrlColumn = detail.contains("public_web_base_url", ignoreCase = true) &&
                (detail.contains("schema cache", ignoreCase = true) ||
                    detail.contains("PGRST204", ignoreCase = true))
            if (!stalePublicUrlColumn) throw error

            // A stale PostgREST schema cache must not block tax/service-charge settings.
            // Retry only this known compatibility failure; all other errors remain visible.
            val compatiblePayload = buildJsonObject {
                payload.forEach { (key, value) ->
                    if (key != "public_web_base_url") put(key, value)
                }
            }
            persist(compatiblePayload)
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

    suspend fun updateStaffProfile(userId: String, isActive: Boolean? = null, permissions: Map<String, Boolean>? = null): StaffProfile {
        val creds = requireVerifiedCredentials()
        require(userId.isNotBlank() && userId != creds.authenticatedUserId) { "ID staf tidak valid atau merupakan akun owner." }
        val payload = buildJsonObject {
            isActive?.let { put("is_active", it) }
            permissions?.let { map -> putJsonObject("permissions") { map.forEach { (key, value) -> put(key, value) } } }
        }
        require(payload.isNotEmpty()) { "Tidak ada perubahan untuk disimpan." }
        val user = URLEncoder.encode(userId, "UTF-8")
        // In the single-app model, Owners manage staff profiles across the app.
        // The global Owner-only RLS policy is the authorization boundary.
        val raw = executeJsonRequest("PATCH",
            "${creds.supabaseUrl}/rest/v1/staff_profiles?user_id=eq.$user&select=user_id,owner_id,full_name,role,is_active,permissions",
            buildHeaders(creds, "return=representation"),
            json.encodeToString(JsonObject.serializer(), payload))
        return json.decodeFromString<List<StaffProfile>>(raw).firstOrNull()
            ?: throw IllegalStateException("Profil staf tidak berubah. Periksa izin UPDATE dan RLS Supabase.")
    }

    suspend fun deleteStaffProfile(userId: String) {
        val creds = requireVerifiedCredentials()
        require(userId.isNotBlank() && userId != creds.authenticatedUserId) { "ID staf tidak valid atau merupakan akun owner." }
        val user = URLEncoder.encode(userId, "UTF-8")
        // Authorization is enforced by the active-Owner RLS policy.
        executeJsonRequest("DELETE",
            "${creds.supabaseUrl}/rest/v1/staff_profiles?user_id=eq.$user",
            buildHeaders(creds, "return=minimal"))
    }

    private suspend fun callOwnerManagement(payload: JsonObject): JsonObject {
        val creds = requireVerifiedCredentials()
        val raw = executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/functions/v1/create-owner",
            headers = buildHeaders(creds),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        val response = json.parseToJsonElement(raw).jsonObject
        if (response["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            throw IllegalStateException(response["error"]?.jsonPrimitive?.contentOrNull ?: "Permintaan Manajemen Owner gagal.")
        }
        return response
    }

    suspend fun fetchOwners(): List<OwnerAccount> {
        val response = callOwnerManagement(buildJsonObject { put("action", "list") })
        val owners = response["owners"] ?: return emptyList()
        return json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(OwnerAccount.serializer()), owners)
    }

    suspend fun createOwner(fullName: String, email: String) {
        callOwnerManagement(buildJsonObject {
            put("full_name", fullName.trim())
            put("email", email.trim())
        })
    }

    suspend fun setOwnerActive(userId: String, active: Boolean) {
        callOwnerManagement(buildJsonObject {
            put("action", "set_active")
            put("user_id", userId)
            put("is_active", active)
        })
    }

    suspend fun deleteOwner(userId: String) {
        callOwnerManagement(buildJsonObject {
            put("action", "delete")
            put("user_id", userId)
        })
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
        // RLS applies my_owner_id() so staff accounts can read their outlet shifts too.
        val url = "${creds.supabaseUrl}/rest/v1/staff_shifts?select=*&order=opened_at.desc"
        val raw = executeJsonRequest("GET", url, buildHeaders(creds))
        return json.decodeFromString(raw)
    }

    suspend fun createStaffShift(shift: StaffShift): StaffShift {
        val creds = requireVerifiedCredentials()
        require(shift.openingCash >= 0.0) { "Modal awal tidak boleh negatif." }
        require(shift.staffUserId == creds.authenticatedUserId) { "Akun kasir aktif tidak cocok dengan sesi login." }
        require(shift.ownerId.isNotBlank() && !shift.ownerId.startsWith("usr-")) { "ID owner outlet tidak valid. Login ulang dengan akun staf yang terhubung ke outlet." }
        val payload = buildJsonObject {
            put("owner_id", shift.ownerId)
            put("staff_user_id", shift.staffUserId)
            put("opened_at", shift.openedAt)
            put("opening_cash", shift.openingCash)
            put("status", "open")
            put("note", shift.notes ?: "")
        }
        val raw = executeJsonRequest("POST", "${creds.supabaseUrl}/rest/v1/staff_shifts", buildHeaders(creds, "return=representation"), json.encodeToString(JsonObject.serializer(), payload))
        return json.decodeFromString<List<StaffShift>>(raw).firstOrNull()
            ?: throw IllegalStateException("Supabase tidak mengembalikan shift yang dibuat.")
    }

    suspend fun closeStaffShift(shift: StaffShift, closingCash: Double, expectedCash: Double, notes: String): StaffShift {
        val creds = requireVerifiedCredentials()
        require(shift.id.isNotBlank()) { "ID shift tidak valid." }
        require(shift.ownerId.isNotBlank() && !shift.ownerId.startsWith("usr-")) { "ID owner outlet tidak valid. Muat ulang sesi outlet." }
        require(closingCash >= 0.0) { "Kas akhir tidak boleh negatif." }
        val encodedId = URLEncoder.encode(shift.id, "UTF-8")
        val owner = URLEncoder.encode(shift.ownerId, "UTF-8")
        val payload = buildJsonObject {
            put("closing_cash", closingCash)
            put("expected_cash", expectedCash)
            put("cash_difference", closingCash - expectedCash)
            put("discrepancy_note", notes)
            put("closed_at", java.time.Instant.now().toString())
            put("status", "closed")
            put("closed_by_user_id", creds.authenticatedUserId)
            put("audit_note", notes.ifBlank { shift.notes ?: "" })
        }
        val raw = executeJsonRequest("PATCH", "${creds.supabaseUrl}/rest/v1/staff_shifts?id=eq.$encodedId&owner_id=eq.$owner&status=eq.open&select=*", buildHeaders(creds, "return=representation"), json.encodeToString(JsonObject.serializer(), payload))
        return json.decodeFromString<List<StaffShift>>(raw).firstOrNull()
            ?: throw IllegalStateException("Shift tidak berubah. Mungkin sudah ditutup atau izin UPDATE/RLS shift.manage belum diberikan.")
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

    suspend fun createExpense(expense: Expense): Expense {
        val creds = requireVerifiedCredentials()
        require(expense.amount > 0.0) { "Nominal pengeluaran harus lebih dari Rp0." }
        require(expense.category.isNotBlank() && expense.description.isNotBlank()) {
            "Kategori dan keterangan pengeluaran wajib diisi."
        }
        val ownerId = expense.ownerId.takeIf { it.isNotBlank() && !it.startsWith("usr-") }
            ?: creds.authenticatedUserId
        require(ownerId.isNotBlank()) { "Akun pemilik outlet tidak ditemukan." }
        val payload = buildJsonObject {
            put("p_owner_id", ownerId)
            put("p_amount", expense.amount)
            put("p_category", expense.category.trim())
            put("p_description", expense.description.trim())
            put("p_expense_date", expense.expenseDate)
            put("p_payment_method", expense.paymentMethod.ifBlank { "cash" })
            put("p_cash_source", expense.cashSource.ifBlank { "cash_fund" })
            put("p_idempotency_key", expense.idempotencyKey ?: UUID.randomUUID().toString())
            put("p_shift_id", expense.shiftId)
        }
        val raw = executeJsonRequest(
            "POST",
            "${creds.supabaseUrl}/rest/v1/rpc/km_record_operational_expense",
            buildHeaders(creds),
            json.encodeToString(JsonObject.serializer(), payload)
        )
        return json.decodeFromString<List<Expense>>(raw).firstOrNull()
            ?: throw IllegalStateException("Server tidak mengembalikan transaksi pengeluaran yang tersimpan.")
    }

    suspend fun createCashFundEntry(type: String, amount: Double, replenishmentSource: String, description: String, ownerId: String): CashFundEntry {
        val creds = requireVerifiedCredentials()
        require(type in listOf("set_target", "replenishment", "close_session")) { "Jenis transaksi modal kas tidak valid." }
        require((type == "close_session" || amount > 0.0) && description.isNotBlank()) { "Nominal dan keterangan wajib diisi." }
        require(replenishmentSource in listOf("setting", "sales_cash", "sales_qris", "sales_transfer", "owner_funds", "other")) { "Sumber modal kas tidak valid." }
        val resolvedOwnerId = ownerId.takeIf { it.isNotBlank() && !it.startsWith("usr-") } ?: creds.authenticatedUserId
        require(resolvedOwnerId.isNotBlank()) { "Akun pengguna tidak ditemukan." }
        val payload = buildJsonObject {
            put("p_owner_id", resolvedOwnerId)
            put("p_entry_type", type)
            put("p_amount", amount)
            put("p_source", replenishmentSource)
            put("p_description", description.trim())
            put("p_idempotency_key", UUID.randomUUID().toString())
        }
        val raw = executeJsonRequest(
            "POST",
            "${creds.supabaseUrl}/rest/v1/rpc/km_record_cash_fund_entry",
            buildHeaders(creds),
            json.encodeToString(JsonObject.serializer(), payload)
        )
        return json.decodeFromString<List<CashFundEntry>>(raw).firstOrNull()
            ?: throw IllegalStateException("Server tidak mengembalikan catatan modal kas.")
    }

    suspend fun fetchCashFundEntries(): List<CashFundEntry> {
        val creds = requireVerifiedCredentials()
        val raw = executeJsonRequest(
            "GET",
            "${creds.supabaseUrl}/rest/v1/cash_fund_entries?select=*&order=created_at.desc",
            buildHeaders(creds)
        )
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
        val url = "${creds.supabaseUrl}/rest/v1/audit_logs?select=*&order=created_at.desc"
        val allLogs = mutableListOf<AuditLog>()
        val pageSize = 1000
        var offset = 0
        while (true) {
            val headers = buildHeaders(creds).toMutableMap().apply {
                put("Range-Unit", "items")
                put("Range", "$offset-${offset + pageSize - 1}")
            }
            val raw = executeJsonRequest("GET", url, headers)
            val page = json.decodeFromString<List<AuditLog>>(raw)
            allLogs += page
            if (page.size < pageSize) break
            offset += pageSize
        }
        return allLogs
    }

    suspend fun invokeAiAssistant(
        prompt: String,
        imageBase64: String? = null,
        imageMimeType: String = "image/jpeg"
    ): String {
        val creds = requireVerifiedCredentials()
        val payload = buildJsonObject {
            put("app_name", "Kereta Malam Cafe")
            put("chat_id", "kereta-malam-cafe-android")
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt.trim().ifBlank { "Tolong analisis screenshot ini dan jelaskan masalahnya dalam konteks aplikasi Kereta Malam Cafe." })
                })
            })
            if (!imageBase64.isNullOrBlank()) {
                put("image_base64", imageBase64)
                put("image_mime_type", imageMimeType)
            }
        }
        val raw = executeJsonRequest(
            method = "POST",
            url = "${creds.supabaseUrl}/functions/v1/ai-assistant",
            headers = buildHeaders(creds),
            bodyJson = json.encodeToString(JsonObject.serializer(), payload)
        )
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            throw IllegalStateException("Respons AI tidak valid. Coba lagi.")
        }
        val errorMessage = obj["error"]?.jsonPrimitive?.contentOrNull
        if (!errorMessage.isNullOrBlank()) {
            throw IllegalStateException(errorMessage)
        }
        return obj["message"]?.jsonPrimitive?.contentOrNull
            ?: obj["reply"]?.jsonPrimitive?.contentOrNull
            ?: obj["answer"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("AI belum memberikan jawaban. Coba kirim ulang.")
    }

    suspend fun logActivity(action: String, entityType: String, entityId: String?, details: JsonObject) {
        val creds = SupabaseConfigManager.loadCredentials(context)
        if (creds.status != BackendIntegrationStatus.ACTIVE_VERIFIED || creds.authenticatedUserId.isBlank()) return
        val ownerId = runCatching { fetchMyStaffProfile()?.ownerId }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: creds.authenticatedUserId
        val payload = buildJsonObject {
            put("owner_id", ownerId)
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
