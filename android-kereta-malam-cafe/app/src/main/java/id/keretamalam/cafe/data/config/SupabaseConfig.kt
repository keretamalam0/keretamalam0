package id.keretamalam.cafe.data.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import id.keretamalam.cafe.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import java.net.URI

enum class BackendIntegrationStatus {
    INACTIVE_PENDING_VERIFICATION,
    ACTIVE_VERIFIED
}

data class SupabaseRuntimeCredentials(
    val projectId: String,
    val supabaseUrl: String,
    val publishableKey: String,
    val publicWebMenuBaseUrl: String,
    val isVerifiedByUser: Boolean
) {
    val status: BackendIntegrationStatus
        get() = if (isVerifiedByUser && runCatching {
            SupabaseConfigManager.validateCredentials(projectId, supabaseUrl, publishableKey)
        }.isSuccess) BackendIntegrationStatus.ACTIVE_VERIFIED
        else BackendIntegrationStatus.INACTIVE_PENDING_VERIFICATION
}

/**
 * Supabase credentials are validated before the client is constructed.
 * Never put a service_role or sb_secret key in an Android application.
 */
object SupabaseConfigManager {
    private const val PREF_FILE = "kereta_malam_secure_supabase_config"
    private const val KEY_PROJECT_ID = "supabase_project_id"
    private const val KEY_URL = "supabase_url"
    private const val KEY_ANON = "supabase_publishable_key"
    private const val KEY_WEB_BASE_URL = "public_web_menu_base_url"
    private const val KEY_VERIFIED = "supabase_verified_by_user"

    const val CANDIDATE_REPO_PROJECT_ID = "rvaemgwtdvncfvaerhgr"
    const val CANDIDATE_WORKER_PROJECT_ID = "dbazoxwhhljpgtbnletc"

    @Volatile
    private var cachedClient: SupabaseClient? = null

    private fun encryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREF_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Accept only a plain HTTPS Supabase project origin. In particular, reject
     * trailing-dot hosts, whitespace/control characters, credentials, ports,
     * paths, queries, fragments, and non-Supabase domains.
     */
    fun normalizeAndValidateSupabaseUrl(rawUrl: String, projectId: String): String {
        require(rawUrl.isNotEmpty() && rawUrl == rawUrl.trim()) {
            "SUPABASE_URL tidak valid: hapus spasi di awal atau akhir."
        }
        require(rawUrl.none { it.isWhitespace() || it.isISOControl() }) {
            "SUPABASE_URL tidak valid: URL tidak boleh berisi spasi atau karakter tersembunyi."
        }
        require(projectId.matches(Regex("^[a-z0-9]{20}$"))) {
            "Project ID Supabase tidak valid. Periksa Project ID pada dashboard Supabase."
        }

        val uri = try {
            URI(rawUrl)
        } catch (_: Exception) {
            throw IllegalArgumentException("SUPABASE_URL tidak valid. Gunakan https://PROJECT_ID.supabase.co.")
        }
        val host = uri.host
        require(uri.scheme == "https" && uri.rawAuthority != null && host != null) {
            "SUPABASE_URL tidak valid. URL wajib menggunakan HTTPS."
        }
        require(uri.rawUserInfo == null && uri.port == -1 &&
            (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") &&
            uri.rawQuery == null && uri.rawFragment == null) {
            "SUPABASE_URL harus berupa alamat project saja, tanpa port, path, query, atau fragment."
        }
        require(!host.endsWith(".")) {
            "SUPABASE_URL tidak valid: hostname tidak boleh diakhiri titik."
        }
        require(host == "$projectId.supabase.co") {
            "Hostname SUPABASE_URL tidak cocok dengan Project ID. Periksa URL dan Project ID pada dashboard Supabase."
        }
        return "https://$host"
    }

    fun validateCredentials(projectId: String, rawUrl: String, rawKey: String): String {
        val normalizedUrl = normalizeAndValidateSupabaseUrl(rawUrl, projectId)
        require(rawKey.isNotEmpty() && rawKey == rawKey.trim() &&
            rawKey.none { it.isWhitespace() || it.isISOControl() }) {
            "Publishable/anon key tidak valid: periksa spasi atau karakter tersembunyi."
        }
        require(!rawKey.startsWith("sb_secret_") && !rawKey.contains("service_role", ignoreCase = true)) {
            "Keamanan: gunakan publishable/anon key, bukan secret/service_role key."
        }
        require(rawKey.startsWith("sb_publishable_") || rawKey.startsWith("eyJ")) {
            "Key Supabase tidak dikenali. Gunakan publishable key atau anon key dari project yang benar."
        }
        return normalizedUrl
    }

    fun loadCredentials(context: Context): SupabaseRuntimeCredentials {
        val prefs = encryptedPrefs(context)
        val projectId = prefs.getString(KEY_PROJECT_ID, BuildConfig.SUPABASE_PROJECT_ID).orEmpty()
        val url = prefs.getString(KEY_URL, BuildConfig.SUPABASE_URL).orEmpty()
        val key = prefs.getString(KEY_ANON, BuildConfig.SUPABASE_PUBLISHABLE_KEY).orEmpty()
        val webUrl = prefs.getString(KEY_WEB_BASE_URL, BuildConfig.PUBLIC_WEB_MENU_BASE_URL)
            .orEmpty().ifBlank { "https://pos.yuripratama246.workers.dev" }
        val verified = prefs.getBoolean(KEY_VERIFIED, false)
        return SupabaseRuntimeCredentials(
            projectId = projectId,
            supabaseUrl = url,
            publishableKey = key,
            publicWebMenuBaseUrl = webUrl,
            isVerifiedByUser = verified
        )
    }

    fun saveVerifiedCredentials(
        context: Context,
        projectId: String,
        supabaseUrl: String,
        publishableKey: String,
        publicWebMenuBaseUrl: String
    ) {
        val normalizedProjectId = projectId
        val normalizedUrl = validateCredentials(normalizedProjectId, supabaseUrl, publishableKey)
        require(publicWebMenuBaseUrl == publicWebMenuBaseUrl.trim() &&
            publicWebMenuBaseUrl.none { it.isWhitespace() || it.isISOControl() }) {
            "URL menu publik tidak valid: periksa spasi atau karakter tersembunyi."
        }
        val prefs = encryptedPrefs(context)
        prefs.edit()
            .putString(KEY_PROJECT_ID, normalizedProjectId)
            .putString(KEY_URL, normalizedUrl)
            .putString(KEY_ANON, publishableKey)
            .putString(KEY_WEB_BASE_URL, publicWebMenuBaseUrl.removeSuffix("/"))
            .putBoolean(KEY_VERIFIED, true)
            .apply()
        cachedClient = null
    }

    fun deactivateIntegration(context: Context) {
        encryptedPrefs(context).edit().putBoolean(KEY_VERIFIED, false).apply()
        cachedClient = null
    }

    fun getOrCreateClient(context: Context): SupabaseClient? {
        val creds = loadCredentials(context)
        if (!creds.isVerifiedByUser) return null
        // Validate again at the trust boundary before constructing any HTTP/Auth client.
        val normalizedUrl = validateCredentials(creds.projectId, creds.supabaseUrl, creds.publishableKey)
        cachedClient?.let { return it }
        synchronized(this) {
            cachedClient?.let { return it }
            val client = createSupabaseClient(
                supabaseUrl = normalizedUrl,
                supabaseKey = creds.publishableKey
            ) {
                install(Auth) {
                    alwaysAutoRefresh = true
                    autoLoadFromStorage = true
                }
                install(Postgrest)
                install(Realtime)
                install(Functions)
            }
            cachedClient = client
            return client
        }
    }
}
