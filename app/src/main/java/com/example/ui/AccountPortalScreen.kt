package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AddModerator
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.example.R
import com.example.auth.performSignOut
import com.example.data.AccountRepository
import com.example.data.model.LoginSession
import com.example.data.model.SessionStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.theme.MonoFontFamily
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private enum class PortalTab {
    ACCOUNT,
    SESSIONS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPortalScreen(
    firebaseUser: FirebaseUser,
    currentUserId: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainViewModel(AccountRepository(db), currentUserId)
            }
        },
    )

    LaunchedEffect(currentUserId) {
        viewModel.syncSessionOnEntry(firebaseUser)
    }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.clearFeedback()
        }
    }

    var selectedTab by rememberSaveable { mutableStateOf(PortalTab.ACCOUNT) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showVerifySessionDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Spacer(Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = selectedTab == PortalTab.ACCOUNT,
                        onClick = { selectedTab = PortalTab.ACCOUNT },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == PortalTab.ACCOUNT) Icons.Rounded.Person else Icons.Outlined.Person,
                                contentDescription = stringResource(R.string.nav_account),
                            )
                        },
                        label = { Text(stringResource(R.string.nav_account)) },
                        modifier = Modifier.testTag("nav_account_tab"),
                    )
                    NavigationRailItem(
                        selected = selectedTab == PortalTab.SESSIONS,
                        onClick = { selectedTab = PortalTab.SESSIONS },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == PortalTab.SESSIONS) Icons.Rounded.History else Icons.Outlined.History,
                                contentDescription = stringResource(R.string.nav_sessions),
                            )
                        },
                        label = { Text(stringResource(R.string.nav_sessions)) },
                        modifier = Modifier.testTag("nav_sessions_tab"),
                    )
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        text = if (selectedTab == PortalTab.ACCOUNT) "Profil & Keamanan" else "Aktivitas Masuk",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = { showSignOutDialog = true },
                                modifier = Modifier.testTag("sign_out_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = stringResource(R.string.sign_out),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.sign_out))
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == PortalTab.ACCOUNT,
                                onClick = { selectedTab = PortalTab.ACCOUNT },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == PortalTab.ACCOUNT) Icons.Rounded.Person else Icons.Outlined.Person,
                                        contentDescription = stringResource(R.string.nav_account),
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_account)) },
                                modifier = Modifier.testTag("nav_account_tab"),
                            )
                            NavigationBarItem(
                                selected = selectedTab == PortalTab.SESSIONS,
                                onClick = { selectedTab = PortalTab.SESSIONS },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == PortalTab.SESSIONS) Icons.Rounded.History else Icons.Outlined.History,
                                        contentDescription = stringResource(R.string.nav_sessions),
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_sessions)) },
                                modifier = Modifier.testTag("nav_sessions_tab"),
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (selectedTab == PortalTab.SESSIONS) {
                        ExtendedFloatingActionButton(
                            onClick = { showVerifySessionDialog = true },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.AddModerator,
                                    contentDescription = null,
                                )
                            },
                            text = { Text(stringResource(R.string.record_verification_session)) },
                            modifier = Modifier.testTag("add_verification_button"),
                        )
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    when (selectedTab) {
                        PortalTab.ACCOUNT -> AccountOverviewTab(
                            firebaseUser = firebaseUser,
                            profileState = profileState,
                            isBusy = isBusy,
                            onSaveProfile = { name, bio, phone, alerts, done ->
                                viewModel.updateProfile(name, bio, phone, alerts, done)
                            },
                            onCopyUid = { uid ->
                                copyTextToClipboard(context, uid)
                                scope.launch {
                                    snackbarHostState.showSnackbar("UID akun disalin ke papan klip")
                                }
                            },
                            onOpenSignOut = { showSignOutDialog = true },
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 680.dp),
                        )

                        PortalTab.SESSIONS -> SessionsHistoryTab(
                            sessionsState = sessionsState,
                            activeSessionId = activeSessionId,
                            isBusy = isBusy,
                            onVerifySession = { viewModel.markSessionVerified(it) },
                            onDeleteSession = { viewModel.deleteSession(it) },
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 680.dp),
                        )
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.sign_out_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        performSignOut(
                            credentialManager = credentialManager,
                            scope = scope,
                            beforeFirebaseSignOut = {
                                viewModel.markActiveSessionSignedOut()
                            },
                        )
                    },
                    modifier = Modifier.testTag("confirm_sign_out_button"),
                ) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showVerifySessionDialog) {
        RecordVerificationDialog(
            isBusy = isBusy,
            onDismiss = { showVerifySessionDialog = false },
            onConfirm = { note ->
                viewModel.recordVerificationSession(note) {
                    showVerifySessionDialog = false
                }
            },
        )
    }
}

@Composable
private fun AccountOverviewTab(
    firebaseUser: FirebaseUser,
    profileState: UiState<UserProfile?>,
    isBusy: Boolean,
    onSaveProfile: (String, String, String, Boolean, () -> Unit) -> Unit,
    onCopyUid: (String) -> Unit,
    onOpenSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isEditing by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (profileState) {
            UiState.Loading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(
                            text = "Memuat profil akun Anda…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            is UiState.Error -> {
                ErrorBannerCard(message = profileState.message)
            }

            is UiState.Success -> {
                val profile = profileState.data
                val displayName = profile?.displayName?.ifBlank { null }
                    ?: firebaseUser.displayName
                    ?: "Pengguna AksesKu"
                val email = profile?.email?.ifBlank { null }
                    ?: firebaseUser.email
                    ?: "Tidak ada email"
                val photoUrl = profile?.photoUrl ?: firebaseUser.photoUrl?.toString()

                IdentityHeaderCard(
                    displayName = displayName,
                    email = email,
                    uid = firebaseUser.uid,
                    photoUrl = photoUrl,
                    lastLoginAt = profile?.lastLoginAt,
                    onCopyUid = {
                        onCopyUid(firebaseUser.uid)
                    },
                )

                var editName by remember(profile?.displayName, isEditing) {
                    mutableStateOf(displayName)
                }
                var editBio by remember(profile?.bio, isEditing) {
                    mutableStateOf(profile?.bio.orEmpty())
                }
                var editPhone by remember(profile?.phoneLabel, isEditing) {
                    mutableStateOf(profile?.phoneLabel.orEmpty())
                }
                var editAlerts by remember(profile?.sessionAlertsEnabled, isEditing) {
                    mutableStateOf(profile?.sessionAlertsEnabled ?: true)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Detail Profil & Preferensi",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (!isEditing) {
                                TextButton(
                                    onClick = { isEditing = true },
                                    modifier = Modifier.testTag("edit_profile_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.edit_profile))
                                }
                            }
                        }

                        if (isEditing) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { if (it.length <= 60) editName = it },
                                label = { Text("Nama Tampilan") },
                                singleLine = true,
                                supportingText = { Text("${editName.length}/60") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_name_input"),
                            )
                            OutlinedTextField(
                                value = editBio,
                                onValueChange = { if (it.length <= 200) editBio = it },
                                label = { Text("Jabatan / Bio Singkat") },
                                maxLines = 3,
                                supportingText = { Text("${editBio.length}/200") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_bio_input"),
                            )
                            OutlinedTextField(
                                value = editPhone,
                                onValueChange = { if (it.length <= 30) editPhone = it },
                                label = { Text("Label Kontak / Nomor") },
                                singleLine = true,
                                supportingText = { Text("${editPhone.length}/30") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_phone_input"),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Peringatan Sesi Masuk",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "Pantau login perangkat baru di riwayat",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = editAlerts,
                                    onCheckedChange = { editAlerts = it },
                                    modifier = Modifier.testTag("profile_alerts_switch"),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                            ) {
                                OutlinedButton(
                                    onClick = { isEditing = false },
                                    enabled = !isBusy,
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                                Button(
                                    onClick = {
                                        onSaveProfile(editName, editBio, editPhone, editAlerts) {
                                            isEditing = false
                                        }
                                    },
                                    enabled = !isBusy && editName.trim().isNotEmpty(),
                                    modifier = Modifier.testTag("save_profile_button"),
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(stringResource(R.string.save_profile))
                                }
                            }
                        } else {
                            ProfileFieldRow(label = "Nama Tampilan", value = displayName)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ProfileFieldRow(
                                label = "Jabatan / Bio",
                                value = profile?.bio?.ifBlank { null } ?: "Belum diisi",
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ProfileFieldRow(
                                label = "Label Kontak",
                                value = profile?.phoneLabel?.ifBlank { null } ?: "Belum diisi",
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ProfileFieldRow(
                                label = "Peringatan Sesi",
                                value = if (profile?.sessionAlertsEnabled != false) "Aktif" else "Nonaktif",
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenSignOut,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Keluar dari Akun Ini")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun IdentityHeaderCard(
    displayName: String,
    email: String,
    uid: String,
    photoUrl: String?,
    lastLoginAt: Timestamp?,
    onCopyUid: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Foto profil $displayName",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = displayName.firstOrNull()?.uppercase() ?: "A",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = "Akun Google terverifikasi",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Masuk terakhir: ${formatTimestamp(lastLoginAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "UID AKUN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = uid,
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = onCopyUid,
                        modifier = Modifier.testTag("copy_uid_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Salin UID akun",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
private fun SessionsHistoryTab(
    sessionsState: UiState<List<LoginSession>>,
    activeSessionId: String?,
    isBusy: Boolean,
    onVerifySession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (sessionsState) {
        UiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                ErrorBannerCard(message = sessionsState.message)
            }
        }

        is UiState.Success -> {
            val sessions = sessionsState.data
            if (sessions.isEmpty()) {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Belum ada catatan sesi",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Gunakan tombol Catat Verifikasi Sesi di bawah untuk mencatat pemeriksaan perangkat baru.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            isCurrentDeviceSession = session.id == activeSessionId,
                            isBusy = isBusy,
                            onVerify = { onVerifySession(session.id) },
                            onDelete = { onDeleteSession(session.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: LoginSession,
    isCurrentDeviceSession: Boolean,
    isBusy: Boolean,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = session.parsedStatus
    val (badgeText, badgeBg, badgeFg) = when (status) {
        SessionStatus.ACTIVE -> Triple(
            if (isCurrentDeviceSession) "Sesi Ini • Aktif" else "Aktif",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        SessionStatus.VERIFIED -> Triple(
            "Terverifikasi",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        SessionStatus.SIGNED_OUT -> Triple(
            "Keluar",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Column {
                        Text(
                            text = session.deviceName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${session.osVersion} • ${session.authProvider.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeBg,
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            AnimatedVisibility(visible = !session.sessionNote.isNullOrBlank()) {
                Text(
                    text = session.sessionNote.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimestamp(session.createdAt),
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (status == SessionStatus.ACTIVE) {
                        TextButton(
                            onClick = onVerify,
                            enabled = !isBusy,
                            modifier = Modifier.testTag("verify_session_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Verifikasi")
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("delete_session_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Hapus riwayat sesi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordVerificationDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catat Verifikasi Sesi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tambahkan catatan singkat untuk menandai bahwa sesi perangkat ini telah Anda periksa.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 120) note = it },
                    label = { Text("Catatan (opsional)") },
                    placeholder = { Text("Misal: Perangkat pribadi aman") },
                    supportingText = { Text("${note.length}/120") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verification_note_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(note) },
                enabled = !isBusy,
                modifier = Modifier.testTag("confirm_verification_button"),
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ErrorBannerCard(message: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun formatTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "Baru saja"
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
    return formatter.format(timestamp.toDate())
}

private fun copyTextToClipboard(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("AksesKu UID", value))
}
