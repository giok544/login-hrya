package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LocalActiveState
import com.example.data.LocalAuthRepository
import com.example.data.LocalLoginSession
import com.example.data.model.SessionStatus
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LocalPortalTab {
    ACCOUNT,
    SESSIONS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAccountPortalScreen(
    state: LocalActiveState,
    repository: LocalAuthRepository,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable { mutableStateOf(LocalPortalTab.ACCOUNT) }
    var isBusy by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showVerifySessionDialog by remember { mutableStateOf(false) }

    val showMessage: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = selectedTab == LocalPortalTab.ACCOUNT,
                        onClick = { selectedTab = LocalPortalTab.ACCOUNT },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == LocalPortalTab.ACCOUNT) Icons.Rounded.Person else Icons.Outlined.Person,
                                contentDescription = stringResource(R.string.nav_account),
                            )
                        },
                        label = { Text(stringResource(R.string.nav_account)) },
                        modifier = Modifier.testTag("nav_account_tab"),
                    )
                    NavigationRailItem(
                        selected = selectedTab == LocalPortalTab.SESSIONS,
                        onClick = { selectedTab = LocalPortalTab.SESSIONS },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == LocalPortalTab.SESSIONS) Icons.Rounded.History else Icons.Outlined.History,
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
                                        text = if (selectedTab == LocalPortalTab.ACCOUNT) "Akun Username" else "Aktivitas Masuk",
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
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sign_out))
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(windowInsets = WindowInsets.navigationBars) {
                            NavigationBarItem(
                                selected = selectedTab == LocalPortalTab.ACCOUNT,
                                onClick = { selectedTab = LocalPortalTab.ACCOUNT },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == LocalPortalTab.ACCOUNT) Icons.Rounded.Person else Icons.Outlined.Person,
                                        contentDescription = stringResource(R.string.nav_account),
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_account)) },
                                modifier = Modifier.testTag("nav_account_tab"),
                            )
                            NavigationBarItem(
                                selected = selectedTab == LocalPortalTab.SESSIONS,
                                onClick = { selectedTab = LocalPortalTab.SESSIONS },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == LocalPortalTab.SESSIONS) Icons.Rounded.History else Icons.Outlined.History,
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
                    if (selectedTab == LocalPortalTab.SESSIONS) {
                        ExtendedFloatingActionButton(
                            onClick = { showVerifySessionDialog = true },
                            icon = {
                                Icon(imageVector = Icons.Rounded.AddModerator, contentDescription = null)
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
                        LocalPortalTab.ACCOUNT -> LocalAccountOverviewTab(
                            state = state,
                            isBusy = isBusy,
                            onSaveProfile = { name, bio, phone, alerts, done ->
                                scope.launch {
                                    isBusy = true
                                    val result = repository.updateProfile(
                                        username = state.account.username,
                                        displayName = name,
                                        bio = bio,
                                        phoneLabel = phone,
                                        sessionAlertsEnabled = alerts,
                                    )
                                    isBusy = false
                                    result.onSuccess {
                                        showMessage("Profil akun berhasil diperbarui.")
                                        done()
                                    }.onFailure { err ->
                                        showMessage(err.message ?: "Gagal memperbarui profil.")
                                    }
                                }
                            },
                            onOpenSignOut = { showSignOutDialog = true },
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 680.dp),
                        )

                        LocalPortalTab.SESSIONS -> LocalSessionsTab(
                            sessions = state.sessions,
                            activeSessionId = state.activeSessionId,
                            isBusy = isBusy,
                            onVerifySession = { sessionId ->
                                scope.launch {
                                    isBusy = true
                                    repository.verifySession(state.account.username, sessionId)
                                        .onSuccess { showMessage("Sesi ditandai terverifikasi.") }
                                    isBusy = false
                                }
                            },
                            onDeleteSession = { sessionId ->
                                scope.launch {
                                    isBusy = true
                                    repository.deleteSession(state.account.username, sessionId)
                                        .onSuccess { showMessage("Riwayat sesi dihapus.") }
                                    isBusy = false
                                }
                            },
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
                        scope.launch { repository.logout() }
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
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showVerifySessionDialog = false },
            title = { Text("Catat Verifikasi Sesi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tambahkan catatan singkat untuk pemeriksaan sesi perangkat ini.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (it.length <= 120) note = it },
                        label = { Text("Catatan (opsional)") },
                        singleLine = true,
                        supportingText = { Text("${note.length}/120") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            repository.recordVerificationSession(state.account.username, note)
                                .onSuccess {
                                    showVerifySessionDialog = false
                                    showMessage("Verifikasi sesi baru berhasil dicatat.")
                                }
                            isBusy = false
                        }
                    },
                    enabled = !isBusy,
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVerifySessionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LocalAccountOverviewTab(
    state: LocalActiveState,
    isBusy: Boolean,
    onSaveProfile: (String, String, String, Boolean, () -> Unit) -> Unit,
    onOpenSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = state.account
    var isEditing by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(62.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = account.displayName.firstOrNull()?.uppercase() ?: "A",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = account.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "@${account.username}",
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Masuk terakhir: ${formatMillis(account.lastLoginAtMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Mode Autentikasi: Username & Password (Terproteksi PBKDF2 di perangkat)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }

        var editName by remember(account.displayName, isEditing) { mutableStateOf(account.displayName) }
        var editBio by remember(account.bio, isEditing) { mutableStateOf(account.bio) }
        var editPhone by remember(account.phoneLabel, isEditing) { mutableStateOf(account.phoneLabel) }
        var editAlerts by remember(account.sessionAlertsEnabled, isEditing) { mutableStateOf(account.sessionAlertsEnabled) }

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
                            Icon(imageVector = Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { if (it.length <= 200) editBio = it },
                        label = { Text("Jabatan / Bio Singkat") },
                        maxLines = 3,
                        supportingText = { Text("${editBio.length}/200") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { if (it.length <= 30) editPhone = it },
                        label = { Text("Label Kontak / Nomor") },
                        singleLine = true,
                        supportingText = { Text("${editPhone.length}/30") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Peringatan Sesi Masuk", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Pantau aktivitas masuk akun Anda",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = editAlerts,
                            onCheckedChange = { editAlerts = it },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    ) {
                        OutlinedButton(onClick = { isEditing = false }, enabled = !isBusy) {
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
                    LocalProfileRow("Nama Tampilan", account.displayName)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LocalProfileRow("Username", "@${account.username}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LocalProfileRow("Jabatan / Bio", account.bio.ifBlank { "Belum diisi" })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LocalProfileRow("Label Kontak", account.phoneLabel.ifBlank { "Belum diisi" })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LocalProfileRow("Peringatan Sesi", if (account.sessionAlertsEnabled) "Aktif" else "Nonaktif")
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
            Icon(imageVector = Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Keluar dari Akun Ini")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LocalProfileRow(label: String, value: String) {
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
private fun LocalSessionsTab(
    sessions: List<LocalLoginSession>,
    activeSessionId: String?,
    isBusy: Boolean,
    onVerifySession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Belum ada catatan sesi", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Gunakan tombol Catat Verifikasi Sesi di bawah untuk menambahkan catatan baru.",
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
                val (badgeText, badgeBg, badgeFg) = when (session.status) {
                    SessionStatus.ACTIVE -> Triple(
                        if (session.id == activeSessionId) "Sesi Ini • Aktif" else "Aktif",
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
                                        text = "${session.osVersion} • ${session.authMethod}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Surface(shape = RoundedCornerShape(50), color = badgeBg) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }

                        AnimatedVisibility(visible = session.note.isNotBlank()) {
                            Text(
                                text = session.note,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatMillis(session.createdAtMillis),
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (session.status == SessionStatus.ACTIVE) {
                                    TextButton(
                                        onClick = { onVerifySession(session.id) },
                                        enabled = !isBusy,
                                    ) {
                                        Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Verifikasi")
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteSession(session.id) },
                                    enabled = !isBusy,
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
        }
    }
}

private fun formatMillis(millis: Long): String {
    if (millis <= 0L) return "Baru saja"
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
    return formatter.format(Date(millis))
}
