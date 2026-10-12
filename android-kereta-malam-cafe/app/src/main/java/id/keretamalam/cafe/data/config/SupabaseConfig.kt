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
        get() = if (isVerifiedByUser && supabaseUrl.startsWith("https://") && publishableKey.isNotBlank()) {
            BackendIntegrationStatus.ACTIVE_VERIFIED
        } else {
            BackendIntegrationStatus.INACTIVE_PENDING_VERIFICATION
        }
}

/**
 * Mengelola konfigurasi Supabase secara terpisah dari UI.
 *
 * PENTING:
 * - Project ID di repo `supabase/config.toml` adalah `rvaemgwtdvncfvaerhgr`.
 * - Project ID di bundle `pos.yuripratama246.workers.dev` adalah `dbazoxwhhljpgtbnletc`.
 * - Aplikasi TIDAK memilih salah satu secara asumsi. Integrasi ditandai BELUM AKTIF
 *   sampai admin/owner memverifikasi Project ID, URL, dan Publishable/Anon Key.
 * - DILARANG memasukkan `service_role` key atau secret backend.
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

    fun loadCredentials(context: Context): SupabaseRuntimeCredentials {
        val prefs = encryptedPrefs(context)
        val projectId = prefs.getString(KEY_PROJECT_ID, BuildConfig.SUPABASE_PROJECT_ID).orEmpty().trim()
        val url = prefs.getString(KEY_URL, BuildConfig.SUPABASE_URL).orEmpty().trim()
        val key = prefs.getString(KEY_ANON, BuildConfig.SUPABASE_PUBLISHABLE_KEY).orEmpty().trim()
        val webUrl = prefs.getString(KEY_WEB_BASE_URL, BuildConfig.PUBLIC_WEB_MENU_BASE_URL)
            .orEmpty()
            .ifBlank { "https://pos.yuripratama246.workers.dev" }
        val verified = prefs.getBoolean(
            KEY_VERIFIED,
            projectId.isNotBlank() && url.isNotBlank() && key.isNotBlank()
        )
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
        require(!publishableKey.startsWith("sb_secret_") && !publishableKey.contains("service_role")) {
            "Keamanan: Dilarang memasukkan service_role atau secret key ke aplikasi Android!"
        }
        val prefs = encryptedPrefs(context)
        prefs.edit()
            .putString(KEY_PROJECT_ID, projectId.trim())
            .putString(KEY_URL, supabaseUrl.trim().removeSuffix("/"))
            .putString(KEY_ANON, publishableKey.trim())
            .putString(KEY_WEB_BASE_URL, publicWebMenuBaseUrl.trim().removeSuffix("/"))
            .putBoolean(KEY_VERIFIED, projectId.isNotBlank() && supabaseUrl.isNotBlank() && publishableKey.isNotBlank())
            .apply()
        cachedClient = null
    }

    fun deactivateIntegration(context: Context) {
        val prefs = encryptedPrefs(context)
        prefs.edit().putBoolean(KEY_VERIFIED, false).apply()
        cachedClient = null
    }

    fun getOrCreateClient(context: Context): SupabaseClient? {
        val creds = loadCredentials(context)
        if (creds.status != BackendIntegrationStatus.ACTIVE_VERIFIED) {
            return null
        }
        cachedClient?.let { return it }
        synchronized(this) {
            cachedClient?.let { return it }
            val client = createSupabaseClient(
                supabaseUrl = creds.supabaseUrl,
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
