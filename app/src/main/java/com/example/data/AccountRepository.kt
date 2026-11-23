package com.example.data

import android.content.Context
import android.os.Build
import com.example.R
import com.example.data.model.LoginSession
import com.example.data.model.SessionStatus
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AccountRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google sebelum mengakses Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users")
                .document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeLoginSessions(userId: String): Flow<List<LoginSession>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/login_sessions"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("login_sessions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LoginSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncOnSignIn(user: FirebaseUser, note: String): Result<String> {
        val userDocPath = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "User ID berubah saat sinkronisasi sesi." }

            val safeDisplayName = (user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "Pengguna AksesKu").take(60)
            val safeEmail = (user.email?.takeIf { it.isNotBlank() }
                ?: "pengguna@aksesku.app").take(120)
            val safePhotoUrl = user.photoUrl?.toString()?.take(500)?.takeIf { it.isNotBlank() }

            val userDocRef = db.collection("users").document(uid)
            val existing = userDocRef.get().await()

            if (!existing.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to safeDisplayName,
                    "email" to safeEmail,
                    "photoUrl" to safePhotoUrl,
                    "sessionAlertsEnabled" to true,
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                userDocRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "displayName" to safeDisplayName,
                    "email" to safeEmail,
                    "photoUrl" to safePhotoUrl,
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                userDocRef.update(updatePayload).await()
            }

            val sessionRef = userDocRef.collection("login_sessions").document()
            val deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Perangkat Android" }
                .take(80)
            val osRelease = "Android ${Build.VERSION.RELEASE.ifBlank { "API ${Build.VERSION.SDK_INT}" }}".take(40)

            val sessionPayload = mapOf(
                "userId" to uid,
                "deviceName" to deviceModel,
                "osVersion" to osRelease,
                "authProvider" to "google",
                "sessionNote" to note.trim().take(120).takeIf { it.isNotEmpty() },
                "status" to SessionStatus.ACTIVE.rawValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
            sessionRef.set(sessionPayload).await()

            Result.success(sessionRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDocPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        userId: String,
        displayName: String,
        bio: String,
        phoneLabel: String,
        sessionAlertsEnabled: Boolean,
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Akses lintas pengguna ditolak." }

            val trimmedName = displayName.trim()
            require(trimmedName.length in 1..60) { "Nama tampilan harus 1 sampai 60 karakter." }
            val trimmedBio = bio.trim()
            require(trimmedBio.length <= 200) { "Bio maksimal 200 karakter." }
            val trimmedPhone = phoneLabel.trim()
            require(trimmedPhone.length <= 30) { "Label kontak maksimal 30 karakter." }

            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to trimmedName,
                    "bio" to trimmedBio,
                    "phoneLabel" to trimmedPhone,
                    "sessionAlertsEnabled" to sessionAlertsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordManualVerificationSession(
        userId: String,
        note: String,
    ): Result<Unit> {
        val collectionPath = "users/$userId/login_sessions"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Akses lintas pengguna ditolak." }
            val trimmedNote = note.trim()
            require(trimmedNote.length <= 120) { "Catatan maksimal 120 karakter." }

            val docRef = db.collection("users").document(uid)
                .collection("login_sessions").document()
            val deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Perangkat Android" }
                .take(80)
            val osRelease = "Android ${Build.VERSION.RELEASE.ifBlank { "API ${Build.VERSION.SDK_INT}" }}".take(40)

            val payload = mapOf(
                "userId" to uid,
                "deviceName" to deviceModel,
                "osVersion" to osRelease,
                "authProvider" to "google",
                "sessionNote" to trimmedNote.ifEmpty { "Verifikasi keamanan manual" },
                "status" to SessionStatus.VERIFIED.rawValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, collectionPath)
            Result.failure(e)
        }
    }

    suspend fun updateSessionStatus(
        userId: String,
        sessionId: String,
        newStatus: SessionStatus,
        note: String? = null,
    ): Result<Unit> {
        val docPath = "users/$userId/login_sessions/$sessionId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Akses lintas pengguna ditolak." }
            require(sessionId.isNotBlank()) { "ID sesi tidak boleh kosong." }

            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.rawValue,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (note != null) {
                val trimmed = note.trim()
                require(trimmed.length <= 120) { "Catatan maksimal 120 karakter." }
                updates["sessionNote"] = trimmed
            }

            db.collection("users").document(uid)
                .collection("login_sessions").document(sessionId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> {
        val docPath = "users/$userId/login_sessions/$sessionId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Akses lintas pengguna ditolak." }
            require(sessionId.isNotBlank()) { "ID sesi tidak boleh kosong." }

            db.collection("users").document(uid)
                .collection("login_sessions").document(sessionId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
