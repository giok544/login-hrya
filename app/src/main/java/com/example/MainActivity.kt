package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LocalAuthRepository
import com.example.ui.AccountPortalScreen
import com.example.ui.AuthScreen
import com.example.ui.LocalAccountPortalScreen
import com.example.ui.theme.AksesKuTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AksesKuTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val auth = remember { Firebase.auth }
    val localAuthRepository = remember(context) { LocalAuthRepository(context) }

    var firebaseUser by remember { mutableStateOf(auth.currentUser) }
    val localActiveState by localAuthRepository.activeStateFlow.collectAsStateWithLifecycle(
        initialValue = null
    )

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            firebaseUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val activeFirebaseUser = firebaseUser
    val activeLocal = localActiveState

    when {
        activeFirebaseUser != null -> {
            key(activeFirebaseUser.uid) {
                AccountPortalScreen(
                    firebaseUser = activeFirebaseUser,
                    currentUserId = activeFirebaseUser.uid,
                )
            }
        }
        activeLocal != null -> {
            key("local_${activeLocal.account.username}") {
                LocalAccountPortalScreen(
                    state = activeLocal,
                    repository = localAuthRepository,
                )
            }
        }
        else -> {
            AuthScreen(
                localAuthRepository = localAuthRepository,
                onGoogleAuthSuccess = {
                    firebaseUser = auth.currentUser
                },
            )
        }
    }
}
