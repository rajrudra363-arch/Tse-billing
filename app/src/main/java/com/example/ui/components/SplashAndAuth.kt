package com.example.ui.components

import android.widget.Toast
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AuthPolicy
import com.example.data.CloudAuthUser
import com.example.data.LoginSecurityState
import com.example.data.UserAccountEntity
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BlueAction
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldSoft
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.GreenPaidBg
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import com.example.util.InvoicePdfPrinter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    adminLoginSecurityState: LoginSecurityState = LoginSecurityState(
        maxAttempts = LoginSecurityState.ADMIN_MAX_ATTEMPTS,
        lockDurationMs = LoginSecurityState.ADMIN_LOCK_DURATION_MS
    ),
    cloudUser: CloudAuthUser? = null,
    cloudBusy: Boolean = false,
    hasAdminVerifyCode: Boolean = false,
    isStaffOnlyApk: Boolean = false,
    prefillInviteCode: String = "",
    onClearError: () -> Unit,
    onSetupAdmin: (username: String, password: String, confirm: String, adminVerifyCode: String) -> Unit,
    onLogin: (username: String, password: String, antiRobotPassed: Boolean, adminVerifyCode: String) -> Unit,
    onImportBackup: (String) -> Unit = {},
    onSignInGoogle: () -> Unit = {},
    onJoinInviteCode: (String) -> Unit = {},
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
                                cloudUser = cloudUser,
                                cloudBusy = cloudBusy,
                                isStaffOnlyApk = isStaffOnlyApk,
                                prefillInviteCode = prefillInviteCode,
                                onClearError = onClearError,
                                onSetupAdmin = onSetupAdmin,
                                onImportBackup = onImportBackup,
                                onSignInGoogle = onSignInGoogle,
                                onJoinInviteCode = onJoinInviteCode
                            )

                            true -> TechnicianAdminLoginForm(
                                accounts = accounts,
                                authError = authError,
                                loginSecurityState = loginSecurityState,
                                adminLoginSecurityState = adminLoginSecurityState,
                                cloudUser = cloudUser,
                                cloudBusy = cloudBusy,
                                hasAdminVerifyCode = hasAdminVerifyCode,
                                onClearError = onClearError,
                                onLogin = onLogin,
                                onSignInGoogle = onSignInGoogle,
                                onJoinInviteCode = onJoinInviteCode,
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
fun PasswordStrengthChecklist(
    password: String,
    modifier: Modifier = Modifier
) {
    val result = remember(password) { AuthPolicy.evaluatePassword(password) }
    val progress = (result.score / 5f).coerceIn(0f, 1f)
    val strengthLabel = when {
        password.isEmpty() -> "Enter strong Google-grade password"
        result.isStrong -> "Strong Password Ready"
        result.score >= 3 -> "Medium — complete all rules below"
        else -> "Weak — next step blocked until strong"
    }
    val barColor = when {
        result.isStrong -> GreenPaid
        result.score >= 3 -> AmberWarn
        else -> MaterialTheme.colorScheme.error
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, if (result.isStrong) GreenPaid else MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("password_strength_checklist")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strengthLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (password.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else barColor
                )
                Text(
                    text = "${result.score}/5",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
            StrengthRuleRow("At least 8 characters", result.hasMinLength)
            StrengthRuleRow("Uppercase letter (A–Z)", result.hasUppercase)
            StrengthRuleRow("Lowercase letter (a–z)", result.hasLowercase)
            StrengthRuleRow("Number (0–9)", result.hasDigit)
            StrengthRuleRow("Special symbol (@, #, $, %, !) & no spaces", result.hasSpecialChar && result.hasNoWhitespace)
        }
    }
}

@Composable
private fun StrengthRuleRow(label: String, met: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (met) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (met) GreenPaid else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (met) GreenPaid else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (met) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstTimeAdminSetupForm(
    authError: String,
    cloudUser: CloudAuthUser?,
    cloudBusy: Boolean,
    isStaffOnlyApk: Boolean = false,
    prefillInviteCode: String = "",
    onClearError: () -> Unit,
    onSetupAdmin: (String, String, String, String) -> Unit,
    onImportBackup: (String) -> Unit,
    onSignInGoogle: () -> Unit,
    onJoinInviteCode: (String) -> Unit
) {
    // Mode 0 = Create Admin Account, Mode 1 = Technician / Super Admin Direct Login (Invite Code), Mode 2 = Sign In (Restore Backup)
    var setupMode by remember(isStaffOnlyApk) { mutableIntStateOf(if (isStaffOnlyApk) 1 else 0) }
    val fixedAdminUsername = AuthPolicy.CANONICAL_ADMIN_USERNAME
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var adminVerifyCode by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf(prefillInviteCode) }
    LaunchedEffect(prefillInviteCode) {
        if (prefillInviteCode.isNotBlank() && inviteCode.isBlank()) {
            inviteCode = prefillInviteCode
        }
    }
    var showPass by remember { mutableStateOf(false) }
    var showPasteBackupDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    val passwordEvaluation = remember(password) { AuthPolicy.evaluatePassword(password) }
    val canCreateAdmin = passwordEvaluation.isStrong &&
        password == confirm &&
        (adminVerifyCode.isBlank() || adminVerifyCode.trim().length >= 4)

    fun handleBackupUri(uri: android.net.Uri?) {
        if (uri == null) return
        scope.launch {
            val res = readBackupTextFromUri(context, uri)
            res.onSuccess { text ->
                onImportBackup(text)
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    err.message ?: "Could not read selected backup file.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val importBackupOpenDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        handleBackupUri(uri)
    }

    val importBackupGetContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        handleBackupUri(uri)
    }

    fun launchSafeBackupPicker() {
        clearFocusAndHideToolbar(focusManager, textToolbar)
        val launchedOpenDoc = runCatching {
            importBackupOpenDocLauncher.launch(arrayOf("*/*"))
            true
        }.getOrDefault(false)
        if (launchedOpenDoc) return

        val launchedGetContent = runCatching {
            importBackupGetContentLauncher.launch("*/*")
            true
        }.getOrDefault(false)
        if (!launchedGetContent) {
            Toast.makeText(
                context,
                "No system file picker responded. You can paste your backup JSON below.",
                Toast.LENGTH_LONG
            ).show()
            showPasteBackupDialog = true
        }
    }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = TealAccent,
        selectedLabelColor = Color.White,
        selectedLeadingIconColor = Color.White,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurface,
        iconColor = MaterialTheme.colorScheme.onSurface
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = TealAccent)
            Text(
                text = if (isStaffOnlyApk) "Technician & Super Admin Sign In" else "Welcome — Sign In or Setup",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (!isStaffOnlyApk) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = setupMode == 0,
                    onClick = { setupMode = 0; onClearError() },
                    colors = chipColors,
                    label = { Text("Create Admin", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("setup_tab_create")
                )
                FilterChip(
                    selected = setupMode == 1,
                    onClick = { setupMode = 1; onClearError() },
                    colors = chipColors,
                    leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Direct Login (Invite)", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("setup_tab_cloud")
                )
                FilterChip(
                    selected = setupMode == 2,
                    onClick = { setupMode = 2; onClearError() },
                    colors = chipColors,
                    leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Sign In (Backup)", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("setup_tab_restore")
                )
            }
        }

        when (setupMode) {
            0 -> {
                Text(
                    text = "Only one Admin exists in this app ($fixedAdminUsername). Set a strong Gmail-style password and 2-Way Verification Code. Technicians & Super Admins use 'Direct Login (Invite)'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = fixedAdminUsername,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Admin Username (Fixed)") },
                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TealAccent) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("setup_admin_username")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; onClearError() },
                    label = { Text("Strong Admin Password (8+ chars, A-Z, a-z, 0-9, @#\$)") },
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

                PasswordStrengthChecklist(password = password)

                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; onClearError() },
                    label = { Text("Confirm Admin Password") },
                    isError = confirm.isNotEmpty() && confirm != password,
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("setup_admin_confirm")
                )

                OutlinedTextField(
                    value = adminVerifyCode,
                    onValueChange = { adminVerifyCode = it; onClearError() },
                    label = { Text("2-Way Verification Code (Optional, min 4 chars)") },
                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (canCreateAdmin) {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            onSetupAdmin(fixedAdminUsername, password, confirm, adminVerifyCode)
                        }
                    }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("setup_admin_verify_code")
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
                        onSetupAdmin(fixedAdminUsername, password, confirm, adminVerifyCode)
                    },
                    enabled = canCreateAdmin,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenPaid,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("create_admin_button")
                ) {
                    Text(
                        text = if (canCreateAdmin) "Next — Create Admin Account" else "Enter Strong Password to Continue",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            1 -> {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, TealAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Technician / Super Admin Direct Login",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sign in with Google, enter the Workspace Invite Code shared by Admin on WhatsApp, and open Login directly (no Admin setup needed).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (cloudUser != null) {
                    Surface(
                        color = GreenPaidBg,
                        border = BorderStroke(1.dp, GreenPaid),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenPaid)
                            Text(
                                text = "Google Connected: ${cloudUser.email}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = GreenPaid
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            onSignInGoogle()
                        },
                        enabled = !cloudBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cloud_google_signin_button")
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in with Google", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it; onClearError() },
                    label = { Text("Admin Invite Code (e.g. TECH-... or SUPER-...)") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        clearFocusAndHideToolbar(focusManager, textToolbar)
                        onJoinInviteCode(inviteCode)
                    }),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("cloud_invite_code_input")
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
                        onJoinInviteCode(inviteCode)
                    },
                    enabled = !cloudBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("cloud_join_button")
                ) {
                    if (cloudBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (cloudBusy) "Connecting Workspace..." else "Connect & Open Direct Login",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            else -> {
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select your previously exported TSE-Billing-Backup.json file. Your Admin account, Technicians, Customers, Products, Settings, and Bills will immediately load onto the Login screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = { launchSafeBackupPicker() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("setup_import_backup_button")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sign In & Restore Backup (.json)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = {
                        clearFocusAndHideToolbar(focusManager, textToolbar)
                        showPasteBackupDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_paste_backup_button")
                ) {
                    Text("Or Paste Backup JSON Manually", color = TealAccent, fontWeight = FontWeight.Bold)
                }

                if (authError.isNotEmpty()) {
                    Text(
                        text = authError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showPasteBackupDialog) {
        BackupPasteImportDialog(
            onDismiss = { showPasteBackupDialog = false },
            onRetryFilePicker = {
                showPasteBackupDialog = false
                launchSafeBackupPicker()
            },
            onImportJsonText = onImportBackup
        )
    }
}

private const val CAPTCHA_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"

private fun generateCaptchaCode(seed: Int): String {
    val rnd = Random(System.nanoTime() + seed.toLong() * 7919L)
    return buildString(6) {
        repeat(6) {
            append(CAPTCHA_CHARSET[rnd.nextInt(CAPTCHA_CHARSET.length)])
        }
    }
}

@Composable
private fun ProfessionalCaptchaBox(
    captchaCode: String,
    seed: Int,
    modifier: Modifier = Modifier
) {
    val glyphs = remember(captchaCode, seed) {
        val rnd = Random(captchaCode.hashCode().toLong() * 31L + seed)
        val palette = intArrayOf(
            0xFFF4D47E.toInt(), // GoldSoft
            0xFF39C5D0.toInt(), // CyanGlow
            0xFFFFFFFF.toInt(), // White
            0xFFA7F3D0.toInt(), // Mint
            0xFFFDE68A.toInt(), // Cream Yellow
            0xFFBAE6FD.toInt()  // Sky Blue
        )
        captchaCode.mapIndexed { idx, ch ->
            CaptchaGlyphSpec(
                char = ch.toString(),
                angleDeg = rnd.nextInt(-22, 23).toFloat(),
                yJitter = rnd.nextInt(-7, 8).toFloat(),
                skewX = (rnd.nextFloat() - 0.5f) * 0.42f,
                colorInt = palette[(idx + rnd.nextInt(palette.size)) % palette.size]
            )
        }
    }

    val noiseSeed = remember(captchaCode, seed) { captchaCode.hashCode() + seed * 17 }

    Box(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF091B2C), Color(0xFF10344D), Color(0xFF0A1F33))
                )
            )
            .border(1.2.dp, GoldAccent, RoundedCornerShape(10.dp))
            .semantics { contentDescription = "CAPTCHA code $captchaCode" }
            .testTag("anti_robot_challenge_text"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rnd = Random(noiseSeed)
            val w = size.width
            val h = size.height

            // Subtle diagonal security mesh lines
            for (i in 0..5) {
                val xStart = w * (i / 6f)
                drawLine(
                    color = Color(0xFF1E5F74).copy(alpha = 0.45f),
                    start = Offset(xStart, 0f),
                    end = Offset((xStart + w * 0.25f).coerceAtMost(w), h),
                    strokeWidth = 1.5f
                )
            }

            // Two wavy Bezier strike paths across the text
            val wave1 = Path().apply {
                moveTo(0f, h * (0.3f + rnd.nextFloat() * 0.2f))
                cubicTo(
                    w * 0.33f, h * (0.08f + rnd.nextFloat() * 0.35f),
                    w * 0.66f, h * (0.65f + rnd.nextFloat() * 0.25f),
                    w, h * (0.25f + rnd.nextFloat() * 0.45f)
                )
            }
            drawPath(
                path = wave1,
                color = GoldAccent.copy(alpha = 0.5f),
                style = Stroke(width = 2.2f)
            )

            val wave2 = Path().apply {
                moveTo(0f, h * (0.65f + rnd.nextFloat() * 0.2f))
                cubicTo(
                    w * 0.30f, h * (0.85f - rnd.nextFloat() * 0.3f),
                    w * 0.70f, h * (0.15f + rnd.nextFloat() * 0.35f),
                    w, h * (0.45f + rnd.nextFloat() * 0.35f)
                )
            }
            drawPath(
                path = wave2,
                color = CyanGlow.copy(alpha = 0.42f),
                style = Stroke(width = 1.8f)
            )

            // Random interference dots
            repeat(32) {
                drawCircle(
                    color = if (it % 2 == 0) GoldSoft.copy(alpha = 0.28f) else CyanGlow.copy(alpha = 0.25f),
                    radius = rnd.nextFloat() * 2.2f + 1.0f,
                    center = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
                )
            }

            // Distorted character glyphs rendered directly onto native Canvas
            drawIntoCanvas { canvas ->
                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.MONOSPACE,
                        android.graphics.Typeface.BOLD
                    )
                    textSize = (h * 0.48f).coerceIn(24f, 44f)
                }
                val slotW = w / (glyphs.size + 0.6f)
                glyphs.forEachIndexed { idx, g ->
                    val cx = slotW * (idx + 0.8f)
                    val cy = h * 0.65f + g.yJitter
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.translate(cx, cy)
                    canvas.nativeCanvas.rotate(g.angleDeg)
                    textPaint.textSkewX = g.skewX
                    textPaint.color = g.colorInt
                    canvas.nativeCanvas.drawText(g.char, 0f, 0f, textPaint)
                    canvas.nativeCanvas.restore()
                }
            }
        }
    }
}

private data class CaptchaGlyphSpec(
    val char: String,
    val angleDeg: Float,
    val yJitter: Float,
    val skewX: Float,
    val colorInt: Int
)

@Composable
private fun TechnicianAdminLoginForm(
    accounts: List<UserAccountEntity>,
    authError: String,
    loginSecurityState: LoginSecurityState,
    adminLoginSecurityState: LoginSecurityState,
    cloudUser: CloudAuthUser?,
    cloudBusy: Boolean,
    hasAdminVerifyCode: Boolean,
    onClearError: () -> Unit,
    onLogin: (String, String, Boolean, String) -> Unit,
    onSignInGoogle: () -> Unit,
    onJoinInviteCode: (String) -> Unit,
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

    // Pre-select the canonical Admin (@TotalSolutionEnterprises) so any third person immediately sees @TotalSolutionEnterprises + Password prompt
    var selectedUsername by remember(distinctAccounts) {
        val defaultAdmin = distinctAccounts.firstOrNull { it.role.equals("admin", ignoreCase = true) }
            ?: distinctAccounts.firstOrNull()
        mutableStateOf(defaultAdmin?.username ?: AuthPolicy.CANONICAL_ADMIN_USERNAME)
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var adminVerifyCode by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var recoverySuccessMsg by remember { mutableStateOf("") }
    var showSwitchInvite by remember { mutableStateOf(false) }
    var inviteCodeInput by remember { mutableStateOf("") }

    // Professional distorted Canvas CAPTCHA state
    var challengeSeed by remember { mutableIntStateOf(0) }
    val captchaCode = remember(challengeSeed) { generateCaptchaCode(challengeSeed) }
    var antiRobotInput by remember { mutableStateOf("") }
    val antiRobotPassed = antiRobotInput.trim() == captchaCode

    val selectedAccount = distinctAccounts.firstOrNull { it.username == selectedUsername }
    val isAdminSelected = selectedAccount?.role.equals("admin", ignoreCase = true) ||
        selectedUsername.equals(AuthPolicy.CANONICAL_ADMIN_USERNAME, ignoreCase = true)
    val activeSecurityState = if (isAdminSelected) adminLoginSecurityState else loginSecurityState
    val needsAdmin2Way = isAdminSelected && hasAdminVerifyCode

    // Live countdown ticker during security lock (15 min Admin on this phone OR 2 hr Staff)
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeSecurityState.lockUntilMillis, isAdminSelected) {
        while (activeSecurityState.lockUntilMillis > System.currentTimeMillis()) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
        nowMillis = System.currentTimeMillis()
    }
    val isLocked = activeSecurityState.isLocked(nowMillis)
    val remainingLockMs = activeSecurityState.remainingLockMillis(nowMillis)
    val effectiveAttempts = activeSecurityState.effectiveFailedAttempts(nowMillis)
    val remainingAttempts = activeSecurityState.remainingAttempts(nowMillis)

    // Rotate CAPTCHA after each failed attempt
    LaunchedEffect(activeSecurityState.failedAttempts, activeSecurityState.lockUntilMillis) {
        if (activeSecurityState.failedAttempts > 0) {
            challengeSeed++
            antiRobotInput = ""
        }
    }

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

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
        onLogin(selectedUsername, password, antiRobotPassed, adminVerifyCode)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Login, contentDescription = null, tint = TealAccent)
                Text(
                    text = "Account Login",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(onClick = { showSwitchInvite = !showSwitchInvite }) {
                Text(
                    text = if (cloudUser != null) "Cloud Synced" else "Google Sync",
                    color = if (cloudUser != null) GreenPaid else TealAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = "Welcome to Total Solution Bill system. Verify the distorted CAPTCHA code and enter your password.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (showSwitchInvite) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, TealAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cloudUser == null) {
                        Button(
                            onClick = onSignInGoogle,
                            enabled = !cloudBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().testTag("cloud_google_signin_button")
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Sign in with Google", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "Connected: ${cloudUser.email}",
                            style = MaterialTheme.typography.labelMedium,
                            color = GreenPaid,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inviteCodeInput,
                            onValueChange = { inviteCodeInput = it },
                            label = { Text("Invite Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("cloud_invite_code_input")
                        )
                        Button(
                            onClick = { onJoinInviteCode(inviteCodeInput) },
                            enabled = !cloudBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                            modifier = Modifier.height(50.dp).testTag("cloud_join_button")
                        ) {
                            Text("Sync", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (isLocked) {
            Surface(
                color = RedDueBg,
                border = BorderStroke(1.5.dp, RedDue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
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
                            text = if (isAdminSelected) {
                                "15-Minute Admin Lockout Active (3/3 Wrong Attempts)"
                            } else {
                                "2-Hour Security Lockout Active (5/5 Wrong Passwords)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = RedDueDark
                        )
                        Text(
                            text = if (isAdminSelected) {
                                "Password entry blocked on THIS phone for ${formatCountdown(remainingLockMs)}. Other phones retain 3 fresh attempts."
                            } else {
                                "Try again in ${formatCountdown(remainingLockMs)}. After 2 hours, you will receive 5 login attempts again."
                            },
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
                    text = if (isAdminSelected) {
                        "Admin Security Alert: $effectiveAttempts/${LoginSecurityState.ADMIN_MAX_ATTEMPTS} wrong tries — $remainingAttempts try(s) left before 15-min phone lock."
                    } else {
                        "Security Alert: $effectiveAttempts/${LoginSecurityState.MAX_ATTEMPTS} failed attempts — $remainingAttempts try(s) left before 2-hour lock."
                    },
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("login_username_dropdown"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            Icon(Icons.Default.Person, contentDescription = null, tint = TealAccent)
                        }
                        Text(
                            text = if (selectedAccount != null) {
                                "${AuthPolicy.formatHandle(selectedAccount.username)} (${roleTitle(selectedAccount.role)})"
                            } else {
                                "$selectedUsername (Admin)"
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Open account list", tint = MaterialTheme.colorScheme.onSurface)
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
                                Text("${AuthPolicy.formatHandle(acc.username)} ($roleLabel)", fontWeight = FontWeight.Bold)
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

        if (needsAdmin2Way) {
            OutlinedTextField(
                value = adminVerifyCode,
                onValueChange = { adminVerifyCode = it; onClearError() },
                label = { Text("Admin 2-Way Verification Code") },
                enabled = !isLocked,
                leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TealAccent) },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("login_admin_verify_code")
            )
        }

        // Professional Distorted Canvas CAPTCHA Verification
        Surface(
            color = if (antiRobotPassed) GreenPaidBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
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
                            tint = if (antiRobotPassed) GreenPaid else TealAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (antiRobotPassed) "CAPTCHA Verified" else "Security CAPTCHA (Case-Sensitive)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (antiRobotPassed) GreenPaid else MaterialTheme.colorScheme.onSurface,
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
                        modifier = Modifier.size(34.dp).testTag("anti_robot_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "New CAPTCHA Challenge", tint = TealAccent)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfessionalCaptchaBox(
                        captchaCode = captchaCode,
                        seed = challengeSeed,
                        modifier = Modifier.weight(1.25f)
                    )

                    OutlinedTextField(
                        value = antiRobotInput,
                        onValueChange = { raw ->
                            antiRobotInput = raw.filter { it.isLetterOrDigit() }.take(8)
                            onClearError()
                        },
                        label = { Text("Enter Code") },
                        enabled = !isLocked,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
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
            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("login_submit_button")
        ) {
            Text(
                text = if (isLocked) "Locked (${formatCountdown(remainingLockMs)})" else "Login",
                fontWeight = FontWeight.Bold,
                color = Color.White
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
            Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(18.dp), tint = TealAccent)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Forgot Admin Password?",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
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
                        adminVerifyCode = ""
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
    val targetUsername = adminAccount?.username.orEmpty().ifBlank { AuthPolicy.CANONICAL_ADMIN_USERNAME }
    var recoveryPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showRecoveryPass by remember { mutableStateOf(false) }
    var showNewPass by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    val newPasswordStrong = remember(newPassword) { AuthPolicy.evaluatePassword(newPassword).isStrong }
    val canReset = recoveryPassword.isNotBlank() && newPasswordStrong && newPassword == confirmPassword

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
                    Text(
                        text = "Admin Account: ${AuthPolicy.formatHandle(targetUsername)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                        label = { Text("Strong New Password") },
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
                    PasswordStrengthChecklist(password = newPassword)
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMsg = "" },
                        label = { Text("Confirm New Password") },
                        isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
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
                        targetUsername,
                        newPassword,
                        confirmPassword
                    ) { ok, msg ->
                        if (!ok) errorMsg = msg
                    }
                },
                enabled = canReset,
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                modifier = Modifier.testTag("reset_admin_password_button")
            ) {
                Text("Reset Admin Password", fontWeight = FontWeight.Bold, color = Color.White)
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
