package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountRepository
import com.example.data.model.LoginSession
import com.example.data.model.SessionStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActionFeedback(
    val message: String,
    val isError: Boolean = false,
)

class MainViewModel(
    private val repository: AccountRepository,
    private val currentUserId: String,
) : ViewModel() {

    val profileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing user profile", rootError)
                emit(UiState.Error("Gagal memuat profil akun. Periksa koneksi Anda dan coba lagi."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val sessionsState: StateFlow<UiState<List<LoginSession>>> =
        repository.observeLoginSessions(currentUserId)
            .map<List<LoginSession>, UiState<List<LoginSession>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing login sessions", rootError)
                emit(UiState.Error("Gagal memuat riwayat sesi masuk."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedback = MutableStateFlow<ActionFeedback?>(null)
    val feedback: StateFlow<ActionFeedback?> = _feedback.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private var hasSyncedSession = false

    fun syncSessionOnEntry(user: FirebaseUser) {
        if (hasSyncedSession || user.uid != currentUserId) return
        hasSyncedSession = true
        viewModelScope.launch {
            val result = repository.syncOnSignIn(user, note = "Masuk via Google Sign-In")
            result.onSuccess { sessionId ->
                _activeSessionId.value = sessionId
            }.onFailure {
                _feedback.value = ActionFeedback(
                    message = "Profil disinkronkan sebagian. Beberapa data mungkin tertunda.",
                    isError = true,
                )
            }
        }
    }

    fun updateProfile(
        displayName: String,
        bio: String,
        phoneLabel: String,
        sessionAlertsEnabled: Boolean,
        onComplete: () -> Unit = {},
    ) {
        if (_isBusy.value) return
        val trimmedName = displayName.trim()
        if (trimmedName.isEmpty() || trimmedName.length > 60) {
            _feedback.value = ActionFeedback(
                message = "Nama tampilan harus diisi antara 1 hingga 60 karakter.",
                isError = true,
            )
            return
        }
        if (bio.trim().length > 200) {
            _feedback.value = ActionFeedback(
                message = "Bio atau catatan peran maksimal 200 karakter.",
                isError = true,
            )
            return
        }
        if (phoneLabel.trim().length > 30) {
            _feedback.value = ActionFeedback(
                message = "Label kontak maksimal 30 karakter.",
                isError = true,
            )
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateProfile(
                userId = currentUserId,
                displayName = trimmedName,
                bio = bio.trim(),
                phoneLabel = phoneLabel.trim(),
                sessionAlertsEnabled = sessionAlertsEnabled,
            )
            _isBusy.value = false
            result.onSuccess {
                _feedback.value = ActionFeedback("Profil akun berhasil diperbarui.")
                onComplete()
            }.onFailure {
                _feedback.value = ActionFeedback(
                    message = "Gagal menyimpan perubahan profil. Silakan coba lagi.",
                    isError = true,
                )
            }
        }
    }

    fun recordVerificationSession(note: String, onComplete: () -> Unit = {}) {
        if (_isBusy.value) return
        if (note.trim().length > 120) {
            _feedback.value = ActionFeedback(
                message = "Catatan sesi maksimal 120 karakter.",
                isError = true,
            )
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.recordManualVerificationSession(currentUserId, note)
            _isBusy.value = false
            result.onSuccess {
                _feedback.value = ActionFeedback("Verifikasi sesi baru berhasil dicatat.")
                onComplete()
            }.onFailure {
                _feedback.value = ActionFeedback(
                    message = "Gagal mencatat verifikasi sesi.",
                    isError = true,
                )
            }
        }
    }

    fun markSessionVerified(sessionId: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateSessionStatus(
                userId = currentUserId,
                sessionId = sessionId,
                newStatus = SessionStatus.VERIFIED,
            )
            _isBusy.value = false
            result.onSuccess {
                _feedback.value = ActionFeedback("Sesi ditandai sebagai terverifikasi.")
            }.onFailure {
                _feedback.value = ActionFeedback(
                    message = "Gagal memperbarui status sesi.",
                    isError = true,
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteSession(currentUserId, sessionId)
            _isBusy.value = false
            result.onSuccess {
                if (_activeSessionId.value == sessionId) {
                    _activeSessionId.value = null
                }
                _feedback.value = ActionFeedback("Riwayat sesi dihapus.")
            }.onFailure {
                _feedback.value = ActionFeedback(
                    message = "Gagal menghapus riwayat sesi.",
                    isError = true,
                )
            }
        }
    }

    suspend fun markActiveSessionSignedOut() {
        val sessionId = _activeSessionId.value ?: return
        repository.updateSessionStatus(
            userId = currentUserId,
            sessionId = sessionId,
            newStatus = SessionStatus.SIGNED_OUT,
            note = "Keluar dari perangkat",
        )
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
