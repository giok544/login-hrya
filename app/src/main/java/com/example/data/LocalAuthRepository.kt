package com.example.data

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.localAuthDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aksesku_local_auth"
)

data class LocalUserAccount(
    val username: String,
    val displayName: String,
    val bio: String = "",
    val phoneLabel: String = "",
    val sessionAlertsEnabled: Boolean = true,
    val createdAtMillis: Long,
    val lastLoginAtMillis: Long,
)

data class LocalLoginSession(
    val id: String,
    val deviceName: String,
    val osVersion: String,
    val authMethod: String,
    val note: String,
    val status: SessionStatus,
    val createdAtMillis: Long,
)

data class LocalActiveState(
    val account: LocalUserAccount,
    val sessions: List<LocalLoginSession>,
    val activeSessionId: String?,
)

object LocalAuthCrypto {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private val usernameRegex = Regex("^[a-zA-Z0-9._-]{3,30}$")

    fun isValidUsername(username: String): Boolean = usernameRegex.matches(username.trim())

    fun generateSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verifyPassword(password: String, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val candidate = hashPassword(password, salt)
        return MessageDigest.isEqual(candidate, expectedHash)
    }
}

class LocalAuthRepository(private val context: Context) {

    private val dataStore = context.applicationContext.localAuthDataStore

    private object Keys {
        val ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        val CURRENT_USERNAME = stringPreferencesKey("current_username")
        val ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
    }

    val activeStateFlow: Flow<LocalActiveState?> = dataStore.data.map { prefs ->
        val currentUsername = prefs[Keys.CURRENT_USERNAME] ?: return@map null
        val root = parseAccountsObject(prefs[Keys.ACCOUNTS_JSON])
        val accountObj = root.optJSONObject(currentUsername.lowercase()) ?: return@map null
        val account = accountObj.toLocalUserAccount()
        val sessions = parseSessions(accountObj.optJSONArray("sessions"))
        LocalActiveState(
            account = account,
            sessions = sessions,
            activeSessionId = prefs[Keys.ACTIVE_SESSION_ID],
        )
    }

    suspend fun register(
        username: String,
        password: String,
        displayName: String,
    ): Result<LocalUserAccount> {
        val cleanUsername = username.trim()
        val normalizedKey = cleanUsername.lowercase()
        if (!LocalAuthCrypto.isValidUsername(cleanUsername)) {
            return Result.failure(
                IllegalArgumentException("Username harus 3–30 karakter (huruf, angka, titik, garis bawah, atau tanda hubung).")
            )
        }
        if (password.length < 6 || password.length > 100) {
            return Result.failure(
                IllegalArgumentException("Password harus terdiri dari 6 hingga 100 karakter.")
            )
        }
        val cleanDisplayName = displayName.trim().ifEmpty { cleanUsername }.take(60)

        return try {
            var createdAccount: LocalUserAccount? = null
            dataStore.edit { prefs ->
                val root = parseAccountsObject(prefs[Keys.ACCOUNTS_JSON])
                if (root.has(normalizedKey)) {
                    throw IllegalStateException("Username \"$cleanUsername\" sudah terdaftar. Silakan masuk.")
                }

                val now = System.currentTimeMillis()
                val salt = LocalAuthCrypto.generateSalt()
                val hash = LocalAuthCrypto.hashPassword(password, salt)
                val sessionId = UUID.randomUUID().toString()
                val newSession = createSessionJson(
                    sessionId = sessionId,
                    note = "Pendaftaran & masuk pertama",
                    status = SessionStatus.ACTIVE,
                    timestamp = now,
                )

                val accountJson = JSONObject().apply {
                    put("username", cleanUsername)
                    put("displayName", cleanDisplayName)
                    put("bio", "")
                    put("phoneLabel", "")
                    put("sessionAlertsEnabled", true)
                    put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    put("passwordHash", Base64.encodeToString(hash, Base64.NO_WRAP))
                    put("createdAtMillis", now)
                    put("lastLoginAtMillis", now)
                    put("sessions", JSONArray().put(newSession))
                }

                root.put(normalizedKey, accountJson)
                prefs[Keys.ACCOUNTS_JSON] = root.toString()
                prefs[Keys.CURRENT_USERNAME] = normalizedKey
                prefs[Keys.ACTIVE_SESSION_ID] = sessionId
                createdAccount = accountJson.toLocalUserAccount()
            }
            Result.success(requireNotNull(createdAccount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(username: String, password: String): Result<LocalUserAccount> {
        val cleanUsername = username.trim()
        val normalizedKey = cleanUsername.lowercase()
        if (cleanUsername.isEmpty() || password.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Username dan password wajib diisi.")
            )
        }

        return try {
            var loggedInAccount: LocalUserAccount? = null
            dataStore.edit { prefs ->
                val root = parseAccountsObject(prefs[Keys.ACCOUNTS_JSON])
                val accountJson = root.optJSONObject(normalizedKey)
                    ?: throw IllegalArgumentException("Username atau password tidak sesuai.")

                val salt = Base64.decode(accountJson.getString("salt"), Base64.NO_WRAP)
                val expectedHash = Base64.decode(accountJson.getString("passwordHash"), Base64.NO_WRAP)
                if (!LocalAuthCrypto.verifyPassword(password, salt, expectedHash)) {
                    throw IllegalArgumentException("Username atau password tidak sesuai.")
                }

                val now = System.currentTimeMillis()
                val sessionId = UUID.randomUUID().toString()
                val newSession = createSessionJson(
                    sessionId = sessionId,
                    note = "Masuk dengan username & password",
                    status = SessionStatus.ACTIVE,
                    timestamp = now,
                )

                val existingSessions = accountJson.optJSONArray("sessions") ?: JSONArray()
                val updatedSessions = JSONArray().put(newSession)
                for (i in 0 until minOf(existingSessions.length(), 24)) {
                    updatedSessions.put(existingSessions.getJSONObject(i))
                }

                accountJson.put("lastLoginAtMillis", now)
                accountJson.put("sessions", updatedSessions)
                root.put(normalizedKey, accountJson)

                prefs[Keys.ACCOUNTS_JSON] = root.toString()
                prefs[Keys.CURRENT_USERNAME] = normalizedKey
                prefs[Keys.ACTIVE_SESSION_ID] = sessionId
                loggedInAccount = accountJson.toLocalUserAccount()
            }
            Result.success(requireNotNull(loggedInAccount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        phoneLabel: String,
        sessionAlertsEnabled: Boolean,
    ): Result<Unit> {
        val cleanName = displayName.trim()
        if (cleanName.isEmpty() || cleanName.length > 60) {
            return Result.failure(IllegalArgumentException("Nama tampilan harus 1 hingga 60 karakter."))
        }
        val cleanBio = bio.trim()
        if (cleanBio.length > 200) {
            return Result.failure(IllegalArgumentException("Bio maksimal 200 karakter."))
        }
        val cleanPhone = phoneLabel.trim()
        if (cleanPhone.length > 30) {
            return Result.failure(IllegalArgumentException("Label kontak maksimal 30 karakter."))
        }

        return try {
            dataStore.edit { prefs ->
                val root = parseAccountsObject(prefs[Keys.ACCOUNTS_JSON])
                val key = username.lowercase()
                val accountJson = root.optJSONObject(key)
                    ?: throw IllegalStateException("Akun tidak ditemukan.")
                accountJson.put("displayName", cleanName)
                accountJson.put("bio", cleanBio)
                accountJson.put("phoneLabel", cleanPhone)
                accountJson.put("sessionAlertsEnabled", sessionAlertsEnabled)
                root.put(key, accountJson)
                prefs[Keys.ACCOUNTS_JSON] = root.toString()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordVerificationSession(username: String, note: String): Result<Unit> {
        val cleanNote = note.trim()
        if (cleanNote.length > 120) {
            return Result.failure(IllegalArgumentException("Catatan maksimal 120 karakter."))
        }
        return try {
            dataStore.edit { prefs ->
                val root = parseAccountsObject(prefs[Keys.ACCOUNTS_JSON])
                val key = username.lowercase()
                val accountJson = root.optJSONObject(key)
                    ?: throw IllegalStateException("Akun tidak ditemukan.")
                val now = System.currentTimeMillis()
                val newSession = createSessionJson(
                    sessionId = UUID.randomUUID().toString(),
                    note = cleanNote.ifEmpty { "Verifikasi keamanan manual" },
                    status = SessionStatus.VERIFIED,
                    timestamp = now,
                )
                val existingSessions = accountJson.optJSONArray("sessions") ?: JSONArray()
                val updated = JSONArray().put(newSession)
                for (i in 0 until minOf(existingSessions.length(), 24)) {
                    updated.put(existingSessions.getJSONObject(i))
                }
                accountJson.put("sessions", updated)
                root.put(key, accountJson)
                prefs[Keys.ACCOUNTS_JSON] = root.toString()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifySession(username: String, sessionId: String): Result<Unit> {
        return updateSessionStatus(username, sessionId, SessionStatus.VERIFIED, null)
    }

    suspend fun deleteSession(username: String, sessionId: String): Result<Unit> {
        return try {
            dataStore.edit { prefs ->
                val root = parseAccountsObject(prefs[Keys.ACCOUNTS_JSON])
                val key = username.lowercase()
                val accountJson = root.optJSONObject(key)
                    ?: throw IllegalStateException("Akun tidak ditemukan.")
                val existing = accountJson.optJSONArray("sessions") ?: JSONArray()
                val filtered = JSONArray()
                for (i in 0 until existing.length()) {
                    val obj = existing.getJSONObject(i)
                    if (obj.optString("id") != sessionId) {
                        filtered.put(obj)
                    }
                }
                accountJson.put("sessions", filtered)
                root.put(key, accountJson)
                prefs[Keys.ACCOUNTS_JSON] = root.toString()
                if (prefs[Keys.ACTIVE_SESSION_ID] == sessionId) {
                    prefs.remove(Keys.ACTIVE_SESSION_ID)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        dataStore.edit { prefs ->
            val current = prefs[Keys.CURRENT_USERNAME]
            val activeSessionId = prefs[Keys.ACTIVE_SESSION_ID]
            if (current != null && activeSessionId != null) {
                val root = parseAccountsObject(prefs[Keys.ACCOUNTS_JSON])
                val accountJson = root.optJSONObject(current)
                if (accountJson != null) {
                    val sessions = accountJson.optJSONArray("sessions") ?: JSONArray()
                    for (i in 0 until sessions.length()) {
                        val obj = sessions.getJSONObject(i)
                        if (obj.optString("id") == activeSessionId) {
                            obj.put("status", SessionStatus.SIGNED_OUT.rawValue)
                            obj.put("note", "Keluar dari perangkat")
                            break
                        }
                    }
                    accountJson.put("sessions", sessions)
                    root.put(current, accountJson)
                    prefs[Keys.ACCOUNTS_JSON] = root.toString()
                }
            }
            prefs.remove(Keys.CURRENT_USERNAME)
            prefs.remove(Keys.ACTIVE_SESSION_ID)
        }
    }

    private suspend fun updateSessionStatus(
        username: String,
        sessionId: String,
        newStatus: SessionStatus,
        note: String?,
    ): Result<Unit> {
        return try {
            dataStore.edit { prefs ->
                val root = parseAccountsObject(prefs[Keys.ACCOUNTS_JSON])
                val key = username.lowercase()
                val accountJson = root.optJSONObject(key)
                    ?: throw IllegalStateException("Akun tidak ditemukan.")
                val sessions = accountJson.optJSONArray("sessions") ?: JSONArray()
                for (i in 0 until sessions.length()) {
                    val obj = sessions.getJSONObject(i)
                    if (obj.optString("id") == sessionId) {
                        obj.put("status", newStatus.rawValue)
                        if (note != null) obj.put("note", note)
                        break
                    }
                }
                accountJson.put("sessions", sessions)
                root.put(key, accountJson)
                prefs[Keys.ACCOUNTS_JSON] = root.toString()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseAccountsObject(raw: String?): JSONObject {
        if (raw.isNullOrBlank()) return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun createSessionJson(
        sessionId: String,
        note: String,
        status: SessionStatus,
        timestamp: Long,
    ): JSONObject {
        val deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Perangkat Android" }
            .take(80)
        val osRelease = "Android ${Build.VERSION.RELEASE.ifBlank { "API ${Build.VERSION.SDK_INT}" }}".take(40)
        return JSONObject().apply {
            put("id", sessionId)
            put("deviceName", deviceModel)
            put("osVersion", osRelease)
            put("authMethod", "Username & Password")
            put("note", note)
            put("status", status.rawValue)
            put("createdAtMillis", timestamp)
        }
    }

    private fun JSONObject.toLocalUserAccount(): LocalUserAccount {
        return LocalUserAccount(
            username = optString("username"),
            displayName = optString("displayName").ifBlank { optString("username") },
            bio = optString("bio"),
            phoneLabel = optString("phoneLabel"),
            sessionAlertsEnabled = optBoolean("sessionAlertsEnabled", true),
            createdAtMillis = optLong("createdAtMillis"),
            lastLoginAtMillis = optLong("lastLoginAtMillis"),
        )
    }

    private fun parseSessions(array: JSONArray?): List<LocalLoginSession> {
        if (array == null) return emptyList()
        val list = ArrayList<LocalLoginSession>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(
                LocalLoginSession(
                    id = obj.optString("id"),
                    deviceName = obj.optString("deviceName", "Perangkat Android"),
                    osVersion = obj.optString("osVersion", "Android"),
                    authMethod = obj.optString("authMethod", "Username & Password"),
                    note = obj.optString("note"),
                    status = SessionStatus.fromRaw(obj.optString("status")),
                    createdAtMillis = obj.optLong("createdAtMillis"),
                )
            )
        }
        return list
    }
}
