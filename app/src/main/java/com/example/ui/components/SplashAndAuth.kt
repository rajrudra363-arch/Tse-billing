package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.LoginSecurityState
import com.example.data.UserAccountEntity
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldSoft
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.GreenPaidBg
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import com.example.util.InvoicePdfPrinter
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.random.Random

@Composable
fun SplashOverlay(
    logoBase64: String = "",
    onSkip: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "splashFloat")
    val floatY by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    var targetProgress by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label = "splashProgress"
    )
    val percentText = ((progress * 100f).toInt()).coerceIn(0, 100)
    var showSkip by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        targetProgress = 1f
        delay(950)
        showSkip = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF144763), NavyDeep),
                    radius = 1200f
                )
            )
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TseShieldLogo(
                logoBase64 = logoBase64,
                modifier = Modifier
                    .size(108.dp)
                    .offset(y = floatY.dp)
            )
            Text(
                text = "TOTAL SOLUTION ENTERPRISES",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "LOADING SECURE CCTV BILLING... $percentText%",
                style = MaterialTheme.typography.labelMedium,
                color = GoldSoft,
                letterSpacing = 1.6.sp,
                modifier = Modifier.testTag("splash_progress_text")
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = GoldAccent,
                trackColor = Color.White.copy(alpha = 0.16f)
            )
            if (showSkip) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.testTag("splash_skip_button")
                ) {
                    Text("Continue", color = CyanGlow)
                }
            }
        }
    }
}

@Composable
fun AuthScreen(
    isAdminConfigured: Boolean?,
    accounts: List<UserAccountEntity>,
    authError: String,
    logoBase64: String = "",
    loginSecurityState: LoginSecurityState = LoginSecurityState(),
    onClearError: () -> Unit,
    onSetupAdmin: (username: String, password: String, confirm: String) -> Unit,
    onLogin: (username: String, password: String, antiRobotPassed: Boolean) -> Unit,
    onImportBackup: (String) -> Unit = {},
    onResetAdminPassword: (
        recoveryPassword: String,
        targetAdminUsername: String?,
        newPassword: String,
        confirmPassword: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit = { _, _, _, _, _ -> }
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(NavyDeep, Color(0xFF0B5968))))
            .safeDrawingPadding()
            .imePadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(126.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cctv_banner),
                        contentDescription = "Total Solution Enterprises Security Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(NavyDeep.copy(alpha = 0.35f), NavyDeep.copy(alpha = 0.88f))
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TseShieldLogo(logoBase64 = logoBase64, modifier = Modifier.size(50.dp))
                        Column {
                            Text(
                                text = "TOTAL SOLUTION ENTERPRISES",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "SECURE CCTV BILLING PORTAL",
                                style = MaterialTheme.typography.labelMedium,
                                color = GoldSoft
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedContent(
                        targetState = isAdminConfigured,
                        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                        label = "authMode"
                    ) { configured ->
                        when (configured) {
                            null -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text("Checking secure accounts...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            false -> FirstTimeAdminSetupForm(
                                authError = authError,
                                onClearError = onClearError,
                                onSetupAdmin = onSetupAdmin,
                                onImportBackup = onImportBackup
                            )

                            true -> TechnicianAdminLoginForm(
                                accounts = accounts,
                                authError = authError,
                                loginSecurityState = loginSecurityState,
                                onClearError = onClearError,
                                onLogin = onLogin,
                                onResetAdminPassword = onResetAdminPassword
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstTimeAdminSetupForm(
    authError: String,
    onClearError: () -> Unit,
    onSetupAdmin: (String, String, String) -> Unit,
    onImportBackup: (String) -> Unit
) {
    // Mode 0 = Create Admin Account, Mode 1 = Sign In (Restore Backup)
    var setupMode by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { text ->
                onImportBackup(text)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = TealAccent)
            Text("Welcome — Account Setup", style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = setupMode == 0,
                onClick = { setupMode = 0; onClearError() },
                label = { Text("Create Admin", fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f).testTag("setup_tab_create")
            )
            FilterChip(
                selected = setupMode == 1,
                onClick = { setupMode = 1; onClearError() },
                label = { Text("Sign In (Backup)", fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f).testTag("setup_tab_restore")
            )
        }

        if (setupMode == 0) {
            Text(
                text = "Create your single Admin account. Device authentication (Fingerprint / Face / Screen Lock) will be configured in Settings after login.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; onClearError() },
                label = { Text("Admin Username (min 3 chars)") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("setup_admin_username")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; onClearError() },
                label = { Text("Admin Password (min 6 chars)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password")
                    }
                },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("setup_admin_password")
            )

            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it; onClearError() },
                label = { Text("Confirm Admin Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    onSetupAdmin(username, password, confirm)
                }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("setup_admin_confirm")
            )

            if (authError.isNotEmpty()) {
                Text(
                    text = authError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    onSetupAdmin(username, password, confirm)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("create_admin_button")
            ) {
                Text("Create Admin Account", fontWeight = FontWeight.Bold)
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sign In with Existing Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Text(
                        text = "Select your previously exported TSE-Billing-Backup.json file. Your Admin account, Technicians, Customers, Products, Settings, and Bills will immediately load onto the Login screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("setup_import_backup_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign In & Restore Backup (.json)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TechnicianAdminLoginForm(
    accounts: List<UserAccountEntity>,
    authError: String,
    loginSecurityState: LoginSecurityState,
    onClearError: () -> Unit,
    onLogin: (String, String, Boolean) -> Unit,
    onResetAdminPassword: (
        recoveryPassword: String,
        targetAdminUsername: String?,
        newPassword: String,
        confirmPassword: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit
) {
    // Enforce single Admin + unique usernames in login dropdown
    val distinctAccounts = remember(accounts) {
        val admins = accounts.filter { it.role.equals("admin", ignoreCase = true) }
        val nonAdmins = accounts.filter { !it.role.equals("admin", ignoreCase = true) }
        (listOfNotNull(admins.firstOrNull()) + nonAdmins)
            .distinctBy { it.username.lowercase() }
    }

    var selectedUsername by remember(distinctAccounts) {
        mutableStateOf("")
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var recoverySuccessMsg by remember { mutableStateOf("") }

    // Anti-Robot human challenge state
    var challengeSeed by remember { mutableIntStateOf(0) }
    val numA = remember(challengeSeed) { Random(System.nanoTime() + challengeSeed * 31).nextInt(2, 10) }
    val numB = remember(challengeSeed) { Random(System.nanoTime() + challengeSeed * 97 + 7).nextInt(1, 10) }
    var antiRobotInput by remember { mutableStateOf("") }
    val expectedSum = numA + numB
    val antiRobotPassed = antiRobotInput.trim().toIntOrNull() == expectedSum

    // Live countdown ticker during 2-hour security lock
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(loginSecurityState.lockUntilMillis) {
        while (loginSecurityState.lockUntilMillis > System.currentTimeMillis()) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
        nowMillis = System.currentTimeMillis()
    }
    val isLocked = loginSecurityState.isLocked(nowMillis)
    val remainingLockMs = loginSecurityState.remainingLockMillis(nowMillis)
    val effectiveAttempts = loginSecurityState.effectiveFailedAttempts(nowMillis)
    val remainingAttempts = loginSecurityState.remainingAttempts(nowMillis)

    // Rotate Anti-Robot challenge after each wrong attempt
    LaunchedEffect(loginSecurityState.failedAttempts, loginSecurityState.lockUntilMillis) {
        if (loginSecurityState.failedAttempts > 0) {
            challengeSeed++
            antiRobotInput = ""
        }
    }

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    val selectedAccount = distinctAccounts.firstOrNull { it.username == selectedUsername }

    fun roleTitle(role: String): String = when {
        role.equals("admin", ignoreCase = true) -> "Admin"
        role.equals("super_admin", ignoreCase = true) -> "Super Admin"
        else -> "Technician"
    }

    fun formatCountdown(ms: Long): String {
        val totalSec = ((ms + 999L) / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    fun submitLogin() {
        clearFocusAndHideToolbar(focusManager, textToolbar)
        onLogin(selectedUsername, password, antiRobotPassed)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Login, contentDescription = null, tint = TealAccent)
            Text("Account Login", style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
        }
        Text(
            text = "Welcome to Total Solution Bill system. Select username, solve Anti-Robot check, and enter password.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isLocked) {
            Surface(
                color = RedDueBg,
                border = BorderStroke(1.5.dp, RedDue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_lockout_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.LockClock,
                        contentDescription = "Account locked",
                        tint = RedDueDark,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "2-Hour Security Lockout Active (5/5 Wrong Passwords)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = RedDueDark
                        )
                        Text(
                            text = "Try again in ${formatCountdown(remainingLockMs)}. After 2 hours, you will receive 5 login attempts again.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF450A0A)
                        )
                    }
                }
            }
        } else if (effectiveAttempts > 0) {
            Surface(
                color = Color(0xFFFFFBEB),
                border = BorderStroke(1.dp, AmberWarn),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_attempts_warning")
            ) {
                Text(
                    text = "Security Alert: $effectiveAttempts/${LoginSecurityState.MAX_ATTEMPTS} failed attempts — $remainingAttempts try(s) left before 2-hour lock.",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    dropdownExpanded = true
                },
                enabled = !isLocked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("login_username_dropdown"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val selBmp = remember(selectedAccount?.photoBase64) {
                            selectedAccount?.photoBase64?.takeIf { it.isNotBlank() }?.let { InvoicePdfPrinter.decodeBase64Bitmap(it) }
                        }
                        if (selBmp != null) {
                            Image(
                                bitmap = selBmp.asImageBitmap(),
                                contentDescription = "Selected user photo",
                                modifier = Modifier.size(28.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null)
                        }
                        Text(
                            text = if (selectedAccount != null) {
                                "Username: ${selectedAccount.username} (${roleTitle(selectedAccount.role)})"
                            } else {
                                "Select Username"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Open account list")
                }
            }

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                distinctAccounts.forEach { acc ->
                    val roleLabel = roleTitle(acc.role)
                    val avatarBmp = remember(acc.photoBase64) {
                        acc.photoBase64.takeIf { it.isNotBlank() }?.let { InvoicePdfPrinter.decodeBase64Bitmap(it) }
                    }
                    DropdownMenuItem(
                        leadingIcon = {
                            if (avatarBmp != null) {
                                Image(
                                    bitmap = avatarBmp.asImageBitmap(),
                                    contentDescription = "${acc.displayName} photo",
                                    modifier = Modifier.size(32.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, tint = TealAccent)
                            }
                        },
                        text = {
                            Column {
                                Text("${acc.username} ($roleLabel)", fontWeight = FontWeight.Bold)
                                if (acc.displayName.isNotBlank() && acc.displayName != acc.username) {
                                    Text(acc.displayName, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        onClick = {
                            selectedUsername = acc.username
                            dropdownExpanded = false
                            onClearError()
                        },
                        modifier = Modifier.testTag("login_user_option_${acc.username}")
                    )
                }
            }
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = { Text("Password") },
            enabled = !isLocked,
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password")
                }
            },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("login_password_input")
        )

        // Anti-Robot Human Security Check
        Surface(
            color = if (antiRobotPassed) GreenPaidBg else Color(0xFFF1F5F9),
            border = BorderStroke(1.2.dp, if (antiRobotPassed) GreenPaid else TealAccent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("anti_robot_card")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = if (antiRobotPassed) Icons.Default.CheckCircle else Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (antiRobotPassed) GreenPaid else NavyPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (antiRobotPassed) "Anti-Robot Verified" else "Anti-Robot Verification",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (antiRobotPassed) GreenPaid else NavyPrimary,
                            modifier = Modifier.testTag("anti_robot_status_label")
                        )
                    }
                    IconButton(
                        onClick = {
                            challengeSeed++
                            antiRobotInput = ""
                            onClearError()
                        },
                        enabled = !isLocked,
                        modifier = Modifier.size(32.dp).testTag("anti_robot_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "New Anti-Robot Challenge", tint = TealAccent)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0B2238))
                            .border(1.dp, GoldAccent, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                color = Color(0xFF1E5F74),
                                start = Offset(0f, size.height * 0.25f),
                                end = Offset(size.width, size.height * 0.78f),
                                strokeWidth = 2f
                            )
                            drawLine(
                                color = Color(0xFFE4B44E).copy(alpha = 0.35f),
                                start = Offset(size.width * 0.1f, size.height * 0.8f),
                                end = Offset(size.width * 0.9f, size.height * 0.2f),
                                strokeWidth = 2f
                            )
                        }
                        Text(
                            text = "$numA + $numB = ?",
                            color = GoldSoft,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            modifier = Modifier.testTag("anti_robot_challenge_text")
                        )
                    }

                    OutlinedTextField(
                        value = antiRobotInput,
                        onValueChange = { raw ->
                            antiRobotInput = raw.filter { it.isDigit() }.take(3)
                            onClearError()
                        },
                        label = { Text("Answer") },
                        enabled = !isLocked,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewOnFocus()
                            .testTag("anti_robot_answer_input")
                    )
                }
            }
        }

        if (recoverySuccessMsg.isNotEmpty()) {
            Text(
                text = recoverySuccessMsg,
                color = GreenPaid,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (authError.isNotEmpty()) {
            Text(
                text = authError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("login_error_text")
            )
        }

        Button(
            onClick = { submitLogin() },
            enabled = !isLocked,
            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("login_submit_button")
        ) {
            Text(
                text = if (isLocked) "Locked (${formatCountdown(remainingLockMs)})" else "Login",
                fontWeight = FontWeight.Bold
            )
        }

        TextButton(
            onClick = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onClearError()
                recoverySuccessMsg = ""
                showForgotDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("forgot_password_button")
        ) {
            Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Forgot Admin Password?", fontWeight = FontWeight.SemiBold)
        }
    }

    if (showForgotDialog) {
        ForgotAdminPasswordDialog(
            adminAccount = distinctAccounts.firstOrNull { it.role.equals("admin", ignoreCase = true) },
            onDismiss = { showForgotDialog = false },
            onResetAdminPassword = { recoveryPass, targetUsername, newPass, confirmPass, onResult ->
                onResetAdminPassword(recoveryPass, targetUsername, newPass, confirmPass) { ok, msg ->
                    if (ok) {
                        password = ""
                        antiRobotInput = ""
                        challengeSeed++
                        recoverySuccessMsg = msg
                        showForgotDialog = false
                    }
                    onResult(ok, msg)
                }
            }
        )
    }
}

@Composable
private fun ForgotAdminPasswordDialog(
    adminAccount: UserAccountEntity?,
    onDismiss: () -> Unit,
    onResetAdminPassword: (
        recoveryPassword: String,
        targetAdminUsername: String?,
        newPassword: String,
        confirmPassword: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val targetUsername = adminAccount?.username.orEmpty()
    var recoveryPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showRecoveryPass by remember { mutableStateOf(false) }
    var showNewPass by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { clearFocusAndHideToolbar(focusManager, textToolbar) }
    }

    AlertDialog(
        onDismissRequest = {
            clearFocusAndHideToolbar(focusManager, textToolbar)
            onDismiss()
        },
        modifier = Modifier.testTag("forgot_password_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LockReset, contentDescription = null, tint = TealAccent)
                Text("Forgot Admin Password", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            ProvideSafeTextToolbar {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Enter the Default Recovery Password to reset your Admin account password safely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (targetUsername.isNotBlank()) {
                        Text(
                            text = "Admin Account: @$targetUsername",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }
                    OutlinedTextField(
                        value = recoveryPassword,
                        onValueChange = { recoveryPassword = it; errorMsg = "" },
                        label = { Text("Default Recovery Password") },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showRecoveryPass = !showRecoveryPass }) {
                                Icon(
                                    if (showRecoveryPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle recovery password"
                                )
                            }
                        },
                        visualTransformation = if (showRecoveryPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .testTag("forgot_master_password_input")
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; errorMsg = "" },
                        label = { Text("New Admin Password (min 6 chars)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showNewPass = !showNewPass }) {
                                Icon(
                                    if (showNewPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle new password"
                                )
                            }
                        },
                        visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .testTag("forgot_new_password_input")
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMsg = "" },
                        label = { Text("Confirm New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .testTag("forgot_confirm_password_input")
                    )
                    if (errorMsg.isNotEmpty()) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("forgot_password_error")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    onResetAdminPassword(
                        recoveryPassword,
                        targetUsername.ifBlank { null },
                        newPassword,
                        confirmPassword
                    ) { ok, msg ->
                        if (!ok) errorMsg = msg
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                modifier = Modifier.testTag("reset_admin_password_button")
            ) {
                Text("Reset Admin Password", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}
