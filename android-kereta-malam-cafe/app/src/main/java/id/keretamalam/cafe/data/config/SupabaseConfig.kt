package id.keretamalam.cafe.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import id.keretamalam.cafe.BuildConfig

enum class BackendIntegrationStatus {
    INACTIVE_PENDING_VERIFICATION,
    ACTIVE_VERIFIED,
    LOCAL_SANDBOX_UI
}

data class SupabaseRuntimeCredentials(
    val projectId: String,
    val supabaseUrl: String,
    val publishableKey: String,
    val publicWebMenuBaseUrl: String,
    val isVerifiedByUser: Boolean,
    val accessToken: String = "",
    val authenticatedUserId: String = "",
    val authenticatedEmail: String = ""
) {
    val status: BackendIntegrationStatus
        get() = if (isVerifiedByUser && supabaseUrl.startsWith("https://") && publishableKey.isNotBlank()) {
            BackendIntegrationStatus.ACTIVE_VERIFIED
        } else {
            BackendIntegrationStatus.INACTIVE_PENDING_VERIFICATION
        }
}

/**
 * Mengelola konfigurasi Supabase dan sesi Auth secara terpisah dari UI menggunakan EncryptedSharedPreferences.
 *
 * PENTING:
 * - Target project yang dikonfirmasi pemilik untuk Kereta Malam Cafe: `dbazoxwhhljpgtbnletc`.
 * - Integrasi tetap BELUM AKTIF sampai URL, project ID, dan Publishable/Anon Key berhasil diuji ke database secara live.
 * - DILARANG memasukkan `service_role` key atau secret backend.
 */
object SupabaseConfigManager {
    private const val PREF_FILE = "kereta_malam_secure_supabase_config"
    private const val KEY_PROJECT_ID = "supabase_project_id"
    private const val KEY_URL = "supabase_url"
    private const val KEY_ANON = "supabase_publishable_key"
    private const val KEY_WEB_BASE_URL = "public_web_menu_base_url"
    private const val KEY_VERIFIED = "supabase_verified_by_user"
    private const val KEY_ACCESS_TOKEN = "supabase_access_token"
    private const val KEY_AUTH_USER_ID = "supabase_auth_user_id"
    private const val KEY_AUTH_EMAIL = "supabase_auth_email"

    const val CONFIRMED_PROJECT_ID = "dbazoxwhhljpgtbnletc"

    private fun encryptedPrefs(context: Context): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                PREF_FILE,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        }
    }

    fun loadCredentials(context: Context): SupabaseRuntimeCredentials {
        val prefs = encryptedPrefs(context)
        val projectId = prefs.getString(KEY_PROJECT_ID, BuildConfig.SUPABASE_PROJECT_ID).orEmpty().trim()
        val url = prefs.getString(KEY_URL, BuildConfig.SUPABASE_URL).orEmpty().trim()
        val key = prefs.getString(KEY_ANON, BuildConfig.SUPABASE_PUBLISHABLE_KEY).orEmpty().trim()
        val webUrl = prefs.getString(KEY_WEB_BASE_URL, BuildConfig.PUBLIC_WEB_MENU_BASE_URL)
            .orEmpty()
            .ifBlank { "https://pos.yuripratama246.workers.dev" }
        // Never infer verification from populated fields. A successful live request
        // must explicitly set KEY_VERIFIED to true.
        val verified = prefs.getBoolean(KEY_VERIFIED, false)
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty()
        val userId = prefs.getString(KEY_AUTH_USER_ID, "").orEmpty()
        val email = prefs.getString(KEY_AUTH_EMAIL, "").orEmpty()

        return SupabaseRuntimeCredentials(
            projectId = projectId,
            supabaseUrl = url,
            publishableKey = key,
            publicWebMenuBaseUrl = webUrl,
            isVerifiedByUser = verified,
            accessToken = accessToken,
            authenticatedUserId = userId,
            authenticatedEmail = email
        )
    }

    fun savePendingCredentials(
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
            .putBoolean(KEY_VERIFIED, false)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_AUTH_USER_ID)
            .remove(KEY_AUTH_EMAIL)
            .apply()
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
    }

    fun saveAuthSession(context: Context, accessToken: String, userId: String, email: String) {
        val prefs = encryptedPrefs(context)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_AUTH_USER_ID, userId)
            .putString(KEY_AUTH_EMAIL, email)
            .apply()
    }

    fun clearAuthSession(context: Context) {
        val prefs = encryptedPrefs(context)
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_AUTH_USER_ID)
            .remove(KEY_AUTH_EMAIL)
            .apply()
    }

    fun deactivateIntegration(context: Context) {
        val prefs = encryptedPrefs(context)
        prefs.edit().putBoolean(KEY_VERIFIED, false).apply()
        clearAuthSession(context)
    }
}
