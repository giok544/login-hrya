package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class SessionStatus(val rawValue: String) {
    ACTIVE("active"),
    VERIFIED("verified"),
    SIGNED_OUT("signed_out");

    companion object {
        fun fromRaw(raw: String?): SessionStatus =
            entries.firstOrNull { it.rawValue == raw } ?: ACTIVE
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val bio: String? = null,
    val phoneLabel: String? = null,
    val sessionAlertsEnabled: Boolean = true,
    @ServerTimestamp val lastLoginAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class LoginSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val osVersion: String = "",
    val authProvider: String = "google",
    val sessionNote: String? = null,
    val status: String = SessionStatus.ACTIVE.rawValue,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val parsedStatus: SessionStatus
        get() = SessionStatus.fromRaw(status)
}
