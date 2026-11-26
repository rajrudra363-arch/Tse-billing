package com.example.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupervisorAccount
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
import androidx.compose.material3.HorizontalDivider
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
import com.example.data.AppIdentity
import com.example.data.AuthPolicy
import com.example.data.CloudAuthUser
import com.example.data.LoginSecurityState
import com.example.data.TechActivationState
import com.example.data.UserAccountEntity
import com.example.ui.OtpUiState
import com.example.ui.PendingPasswordChangeState
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
import com.example.util.BitmapUtils
import com.example.util.InvoicePdfPrinter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

private enum class AuthPortalDestination {
    HUB,
    ADMIN_SIGN_UP,
    ADMIN_LOGIN,
    TECHNICIAN_LOGIN,
    SUPER_ADMIN_LOGIN
}

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
                text = AppIdentity.OFFICIAL_APP_NAME,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = AppIdentity.OFFICIAL_COMPANY_NAME,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = CyanGlow,
                letterSpacing = 1.8.sp
            )
            Text(
                text = "LOADING SECURE BILLING & SERVICE SYSTEM... $percentText%",
                style = MaterialTheme.typography.labelMedium,
                color = GoldSoft,
                letterSpacing = 1.3.sp,
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
    techActivationState: TechActivationState = TechActivationState(),
    activationBusy: Boolean = false,
    activationError: String = "",
    otpState: OtpUiState = OtpUiState(),
    passkeyBusy: Boolean = false,
    onClearError: () -> Unit,
    onSetupAdmin: (username: String, password: String, confirm: String, adminVerifyCode: String) -> Unit = { _, _, _, _ -> },
    onLogin: (username: String, password: String, antiRobotPassed: Boolean, adminVerifyCode: String) -> Unit,
    onImportBackup: (String) -> Unit = {},
    onSignInGoogle: () -> Unit = {},
    onSignInTechnicianEmail: (email: String, password: String) -> Unit = { _, _ -> },
    onJoinInviteCode: (String) -> Unit = {},
    onVerifyTechActivation: (email: String, passkey: String) -> Unit = { _, _ -> },
    onClearTechActivation: () -> Unit = {},
    onResetAdminPassword: (
        recoveryPassword: String,
        targetAdminUsername: String?,
        newPassword: String,
        confirmPassword: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit = { _, _, _, _, _ -> },
    onRequestOtp: (email: String, purpose: String) -> Unit = { _, _ -> },
    onVerifyOtp: (otp: String, onVerified: () -> Unit) -> Unit = { _, _ -> },
    onClearOtp: () -> Unit = {},
    onRegisterCompanyAdmin: (
        adminName: String,
        mobile: String,
        email: String,
        password: String,
        confirmPassword: String,
        twoFactorPassword: String,
        confirmTwoFactorPassword: String,
        onRegistered: () -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onResetPasswordWithOtp: (
        email: String,
        purpose: String,
        newPassword: String,
        confirmPassword: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit = { _, _, _, _, _ -> },
    onLoginAdminEmailOrUser: (
        identifier: String,
        password: String,
        twoFactorCode: String,
        antiRobotPassed: Boolean
    ) -> Unit = { id, pass, twoFa, robot -> onLogin(id, pass, robot, twoFa) },
    onLoginSuperAdmin: (
        identifier: String,
        password: String,
        antiRobotPassed: Boolean
    ) -> Unit = { id, pass, robot -> onLogin(id, pass, robot, "") },
    onLoginWithPasskey: (
        expectedRole: String,
        emailHint: String,
        antiRobotPassed: Boolean
    ) -> Unit = { _, _, _ -> }
) {
    var destination by remember(isStaffOnlyApk, techActivationState.verified, prefillInviteCode) {
        mutableStateOf(
            when {
                isStaffOnlyApk -> AuthPortalDestination.TECHNICIAN_LOGIN
                techActivationState.verified || prefillInviteCode.isNotBlank() -> AuthPortalDestination.TECHNICIAN_LOGIN
                else -> AuthPortalDestination.HUB
            }
        )
    }

    if (destination != AuthPortalDestination.HUB && !isStaffOnlyApk) {
        BackHandler {
            onClearError()
            onClearOtp()
            destination = AuthPortalDestination.HUB
        }
    }

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
                .widthIn(max = 480.dp)
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
                        contentDescription = "${AppIdentity.OFFICIAL_APP_NAME} Security Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(NavyDeep.copy(alpha = 0.35f), NavyDeep.copy(alpha = 0.90f))
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
                                text = AppIdentity.OFFICIAL_APP_NAME,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "${AppIdentity.OFFICIAL_COMPANY_NAME} · SECURE ACCESS",
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
                    if (isAdminConfigured == null && !isStaffOnlyApk) {
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
                    } else {
                        AnimatedContent(
                            targetState = destination,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                            label = "authPortalDest"
                        ) { currentDest ->
                            when (currentDest) {
                                AuthPortalDestination.HUB -> FourOptionAuthHub(
                                    isAdminConfigured = isAdminConfigured == true,
                                    onSelect = { chosen ->
                                        onClearError()
                                        onClearOtp()
                                        destination = chosen
                                    }
                                )

                                AuthPortalDestination.ADMIN_SIGN_UP -> AdminSignUpSection(
                                    isStaffOnlyApk = isStaffOnlyApk,
                                    authError = authError,
                                    cloudBusy = cloudBusy,
                                    otpState = otpState,
                                    onBack = {
                                        onClearError()
                                        onClearOtp()
                                        destination = AuthPortalDestination.HUB
                                    },
                                    onClearError = onClearError,
                                    onRequestOtp = onRequestOtp,
                                    onVerifyOtp = onVerifyOtp,
                                    onRegisterCompanyAdmin = { name, mob, email, pass, conf, twoFa, confTwoFa ->
                                        onRegisterCompanyAdmin(name, mob, email, pass, conf, twoFa, confTwoFa) {
                                            destination = AuthPortalDestination.ADMIN_LOGIN
                                        }
                                    },
                                    onSetupLocalAdmin = { user, pass, conf, code ->
                                        onSetupAdmin(user, pass, conf, code)
                                    },
                                    onImportBackup = onImportBackup
                                )

                                AuthPortalDestination.ADMIN_LOGIN -> AdminLoginSection(
                                    accounts = accounts,
                                    authError = authError,
                                    cloudUser = cloudUser,
                                    cloudBusy = cloudBusy,
                                    passkeyBusy = passkeyBusy,
                                    adminLoginSecurityState = adminLoginSecurityState,
                                    hasAdminVerifyCode = hasAdminVerifyCode,
                                    otpState = otpState,
                                    onBack = {
                                        onClearError()
                                        onClearOtp()
                                        destination = AuthPortalDestination.HUB
                                    },
                                    onClearError = onClearError,
                                    onLoginAdmin = onLoginAdminEmailOrUser,
                                    onLoginWithPasskey = { hint, robot ->
                                        onLoginWithPasskey("admin", hint, robot)
                                    },
                                    onSignInGoogle = onSignInGoogle,
                                    onRequestOtp = onRequestOtp,
                                    onVerifyOtp = onVerifyOtp,
                                    onClearOtp = onClearOtp,
                                    onResetPasswordWithOtp = onResetPasswordWithOtp,
                                    onResetAdminPassword = onResetAdminPassword,
                                    onImportBackup = onImportBackup
                                )

                                AuthPortalDestination.TECHNICIAN_LOGIN -> TechnicianLoginHubSection(
                                    accounts = accounts,
                                    authError = authError,
                                    cloudUser = cloudUser,
                                    cloudBusy = cloudBusy,
                                    passkeyBusy = passkeyBusy,
                                    loginSecurityState = loginSecurityState,
                                    isStaffOnlyApk = isStaffOnlyApk,
                                    prefillInviteCode = prefillInviteCode,
                                    techActivationState = techActivationState,
                                    activationBusy = activationBusy,
                                    activationError = activationError,
                                    otpState = otpState,
                                    onBack = if (!isStaffOnlyApk) {
                                        {
                                            onClearError()
                                            onClearOtp()
                                            destination = AuthPortalDestination.HUB
                                        }
                                    } else null,
                                    onClearError = onClearError,
                                    onLogin = onLogin,
                                    onSignInTechnicianEmail = onSignInTechnicianEmail,
                                    onLoginWithPasskey = { hint ->
                                        onLoginWithPasskey("technician", hint, true)
                                    },
                                    onSignInGoogle = onSignInGoogle,
                                    onJoinInviteCode = onJoinInviteCode,
                                    onVerifyTechActivation = onVerifyTechActivation,
                                    onClearTechActivation = onClearTechActivation,
                                    onRequestOtp = onRequestOtp,
                                    onVerifyOtp = onVerifyOtp,
                                    onClearOtp = onClearOtp,
                                    onResetPasswordWithOtp = onResetPasswordWithOtp
                                )

                                AuthPortalDestination.SUPER_ADMIN_LOGIN -> SuperAdminLoginSection(
                                    accounts = accounts,
                                    authError = authError,
                                    cloudBusy = cloudBusy,
                                    passkeyBusy = passkeyBusy,
                                    loginSecurityState = loginSecurityState,
                                    otpState = otpState,
                                    onBack = {
                                        onClearError()
                                        onClearOtp()
                                        destination = AuthPortalDestination.HUB
                                    },
                                    onClearError = onClearError,
                                    onLoginSuperAdmin = onLoginSuperAdmin,
                                    onLoginWithPasskey = { hint, robot ->
                                        onLoginWithPasskey("super_admin", hint, robot)
                                    },
                                    onRequestOtp = onRequestOtp,
                                    onVerifyOtp = onVerifyOtp,
                                    onClearOtp = onClearOtp,
                                    onResetPasswordWithOtp = onResetPasswordWithOtp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FourOptionAuthHub(
    isAdminConfigured: Boolean,
    onSelect: (AuthPortalDestination) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("auth_four_option_hub")
    ) {
        Text(
            text = "Welcome to ${AppIdentity.OFFICIAL_APP_NAME}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Select your role to continue:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PortalOptionButton(
            title = "ADMIN SIGN UP",
            subtitle = if (isAdminConfigured) "Register new ${AppIdentity.OFFICIAL_COMPANY_NAME} Admin with Email OTP" else "First-time ${AppIdentity.OFFICIAL_COMPANY_NAME} Admin setup & 2FA",
            icon = Icons.Default.PersonAdd,
            containerColor = TealAccent,
            testTag = "auth_option_admin_signup",
            onClick = { onSelect(AuthPortalDestination.ADMIN_SIGN_UP) }
        )

        PortalOptionButton(
            title = "ADMIN LOGIN",
            subtitle = "Password, 2FA, CAPTCHA & WebAuthn Passkey",
            icon = Icons.Default.AdminPanelSettings,
            containerColor = NavyDeep,
            testTag = "auth_option_admin_login",
            onClick = { onSelect(AuthPortalDestination.ADMIN_LOGIN) }
        )

        PortalOptionButton(
            title = "TECHNICIAN LOGIN",
            subtitle = "Email/Username, Password, Passkey or Invite Code",
            icon = Icons.Default.Build,
            containerColor = BlueAction,
            testTag = "auth_option_technician_login",
            onClick = { onSelect(AuthPortalDestination.TECHNICIAN_LOGIN) }
        )

        PortalOptionButton(
            title = "SUPER ADMIN LOGIN",
            subtitle = "Authorized executive access only (No public sign up)",
            icon = Icons.Default.SupervisorAccount,
            containerColor = Color(0xFF4C1D95),
            testTag = "auth_option_super_admin_login",
            onClick = { onSelect(AuthPortalDestination.SUPER_ADMIN_LOGIN) }
        )
    }
}

@Composable
private fun PortalOptionButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.18f),
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
private fun PortalHeaderRow(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).testTag("auth_back_to_hub_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to role options", tint = TealAccent)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminSignUpSection(
    isStaffOnlyApk: Boolean,
    authError: String,
    cloudBusy: Boolean,
    otpState: OtpUiState,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    onRequestOtp: (email: String, purpose: String) -> Unit,
    onVerifyOtp: (otp: String, onVerified: () -> Unit) -> Unit,
    onRegisterCompanyAdmin: (
        adminName: String,
        mobile: String,
        email: String,
        password: String,
        confirmPassword: String,
        twoFactorPassword: String,
        confirmTwoFactorPassword: String
    ) -> Unit,
    onSetupLocalAdmin: (username: String, password: String, confirm: String, twoFactor: String) -> Unit,
    onImportBackup: (String) -> Unit
) {
    var adminName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var twoFactorPassword by remember { mutableStateOf("") }
    var confirmTwoFactorPassword by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var showBackupRestore by remember { mutableStateOf(false) }
    var showPasteBackupDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    val passEval = remember(password) { AuthPolicy.evaluatePassword(password) }
    val otpVerifiedForEmail = otpState.otpVerified && otpState.email.equals(email.trim(), ignoreCase = true)
    val canSubmitCloud = !isStaffOnlyApk &&
        !cloudBusy &&
        adminName.trim().length >= 2 &&
        mobile.filter { it.isDigit() }.length >= 10 &&
        AuthPolicy.isValidEmail(email.trim()) &&
        otpVerifiedForEmail &&
        passEval.isStrong &&
        password == confirmPassword &&
        twoFactorPassword.trim().length >= 4 &&
        twoFactorPassword == confirmTwoFactorPassword

    fun handleBackupUri(uri: android.net.Uri?) {
        if (uri == null) return
        scope.launch {
            readBackupTextFromUri(context, uri).onSuccess(onImportBackup).onFailure {
                Toast.makeText(context, it.message ?: "Could not read backup.", Toast.LENGTH_LONG).show()
            }
        }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { handleBackupUri(it) }
    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { handleBackupUri(it) }

    fun launchBackupPicker() {
        clearFocusAndHideToolbar(focusManager, textToolbar)
        if (!runCatching { openDoc.launch(arrayOf("*/*")); true }.getOrDefault(false) &&
            !runCatching { getContent.launch("*/*"); true }.getOrDefault(false)
        ) {
            showPasteBackupDialog = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("admin_signup_screen")) {
        PortalHeaderRow(
            title = "Admin Sign Up",
            subtitle = "Create verified Admin account for ${AppIdentity.OFFICIAL_COMPANY_NAME}",
            onBack = onBack
        )

        if (isStaffOnlyApk) {
            Surface(color = RedDueBg, border = BorderStroke(1.dp, RedDue), shape = RoundedCornerShape(12.dp)) {
                Text(
                    text = "Admin Sign Up is disabled on Staff APK builds.",
                    color = RedDueDark,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp)
                )
            }
            return@Column
        }

        // Permanently locked Official Company Name
        OutlinedTextField(
            value = AppIdentity.OFFICIAL_COMPANY_NAME,
            onValueChange = {},
            readOnly = true,
            label = { Text("Company Name (Permanently Locked)") },
            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = TealAccent) },
            trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "Locked", tint = GoldAccent) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("signup_company_name_locked")
        )

        OutlinedTextField(
            value = adminName,
            onValueChange = { adminName = it; onClearError() },
            label = { Text("Admin Name") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("signup_admin_name")
        )

        OutlinedTextField(
            value = mobile,
            onValueChange = { mobile = it; onClearError() },
            label = { Text("Mobile Number") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("signup_admin_mobile")
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; onClearError() },
            label = { Text("Email Address") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("signup_admin_email")
        )

        // Email OTP Verification block
        Surface(
            color = if (otpVerifiedForEmail) GreenPaidBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, if (otpVerifiedForEmail) GreenPaid else TealAccent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (otpVerifiedForEmail) "Email OTP Verified" else "Email OTP Verification",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (otpVerifiedForEmail) GreenPaid else NavyDeep
                    )
                    OutlinedButton(
                        onClick = { onRequestOtp(email, "ADMIN_SIGNUP") },
                        enabled = !otpState.busy && AuthPolicy.isValidEmail(email.trim()),
                        modifier = Modifier.testTag("signup_send_otp_button")
                    ) {
                        Text(if (otpState.challengeId.isNotBlank()) "Resend OTP" else "Send OTP", fontWeight = FontWeight.Bold)
                    }
                }
                if (otpState.message.isNotBlank()) {
                    Text(
                        text = otpState.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (otpVerifiedForEmail) GreenPaid else TealAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!otpVerifiedForEmail && otpState.challengeId.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = { otpInput = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text("Enter Email OTP") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("signup_otp_input")
                        )
                        Button(
                            onClick = { onVerifyOtp(otpInput) {} },
                            enabled = !otpState.busy && otpInput.length >= 4,
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                            modifier = Modifier.height(50.dp).testTag("signup_verify_otp_button")
                        ) {
                            Text("Verify", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = { Text("Create Password") },
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
            value = confirmPassword,
            onValueChange = { confirmPassword = it; onClearError() },
            label = { Text("Confirm Password") },
            isError = confirmPassword.isNotEmpty() && confirmPassword != password,
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("setup_admin_confirm")
        )

        OutlinedTextField(
            value = twoFactorPassword,
            onValueChange = { twoFactorPassword = it; onClearError() },
            label = { Text("Create Two-Factor Authentication Password (min 4 chars)") },
            leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TealAccent) },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("setup_admin_verify_code")
        )

        OutlinedTextField(
            value = confirmTwoFactorPassword,
            onValueChange = { confirmTwoFactorPassword = it; onClearError() },
            label = { Text("Confirm Two-Factor Authentication Password") },
            isError = confirmTwoFactorPassword.isNotEmpty() && confirmTwoFactorPassword != twoFactorPassword,
            leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TealAccent) },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("setup_admin_verify_code_confirm")
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
                onRegisterCompanyAdmin(
                    adminName,
                    mobile,
                    email,
                    password,
                    confirmPassword,
                    twoFactorPassword,
                    confirmTwoFactorPassword
                )
            },
            enabled = canSubmitCloud,
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenPaid,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_admin_button")
        ) {
            if (cloudBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = when {
                    cloudBusy -> "Creating Admin Workspace..."
                    !otpVerifiedForEmail -> "Verify Email OTP to Create Admin"
                    else -> "Complete Admin Sign Up"
                },
                fontWeight = FontWeight.Bold
            )
        }

        // Offline local fallback / Backup Restore expander
        TextButton(
            onClick = { showBackupRestore = !showBackupRestore },
            modifier = Modifier.fillMaxWidth().testTag("setup_tab_restore")
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = TealAccent)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (showBackupRestore) "Hide Backup & Local Options" else "Restore Existing Backup (.json) / Offline Setup",
                color = TealAccent,
                fontWeight = FontWeight.Bold
            )
        }

        AnimatedVisibility(visible = showBackupRestore) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { launchBackupPicker() },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("setup_import_backup_button")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import TS-Backup.json", fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { showPasteBackupDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("setup_paste_backup_button")
                ) {
                    Text("Or Paste Backup JSON Manually", color = TealAccent)
                }
                if (passEval.isStrong && password == confirmPassword && twoFactorPassword.isNotBlank() && twoFactorPassword == confirmTwoFactorPassword) {
                    OutlinedButton(
                        onClick = {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            onSetupLocalAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, password, confirmPassword, twoFactorPassword)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("create_local_admin_fallback_button")
                    ) {
                        Text("Create Offline Local Admin Only", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showPasteBackupDialog) {
        BackupPasteImportDialog(
            onDismiss = { showPasteBackupDialog = false },
            onRetryFilePicker = {
                showPasteBackupDialog = false
                launchBackupPicker()
            },
            onImportJsonText = onImportBackup
        )
    }
}

@Composable
private fun AdminLoginSection(
    accounts: List<UserAccountEntity>,
    authError: String,
    cloudUser: CloudAuthUser?,
    cloudBusy: Boolean,
    passkeyBusy: Boolean,
    adminLoginSecurityState: LoginSecurityState,
    hasAdminVerifyCode: Boolean,
    otpState: OtpUiState,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    onLoginAdmin: (identifier: String, password: String, twoFactorCode: String, antiRobotPassed: Boolean) -> Unit,
    onLoginWithPasskey: (emailHint: String, antiRobotPassed: Boolean) -> Unit,
    onSignInGoogle: () -> Unit,
    onRequestOtp: (email: String, purpose: String) -> Unit,
    onVerifyOtp: (otp: String, onVerified: () -> Unit) -> Unit,
    onClearOtp: () -> Unit,
    onResetPasswordWithOtp: (email: String, purpose: String, newPass: String, confirmPass: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onResetAdminPassword: (String, String?, String, String, (Boolean, String) -> Unit) -> Unit,
    onImportBackup: (String) -> Unit
) {
    val savedAdmin = remember(accounts) {
        accounts.firstOrNull { it.role.equals("admin", ignoreCase = true) }
    }
    val initialIdentifier = remember(savedAdmin, cloudUser) {
        savedAdmin?.email?.ifBlank { savedAdmin.username }
            ?: cloudUser?.email.orEmpty().ifBlank { AuthPolicy.CANONICAL_ADMIN_USERNAME }
    }

    var identifier by remember(initialIdentifier) { mutableStateOf(initialIdentifier) }
    var password by remember { mutableStateOf("") }
    var twoFactorCode by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var recoverySuccessMsg by remember { mutableStateOf("") }

    var challengeSeed by remember { mutableIntStateOf(0) }
    val captchaCode = remember(challengeSeed) { generateCaptchaCode(challengeSeed) }
    var antiRobotInput by remember { mutableStateOf("") }
    val antiRobotPassed = antiRobotInput.trim() == captchaCode

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(adminLoginSecurityState.lockUntilMillis) {
        while (adminLoginSecurityState.lockUntilMillis > System.currentTimeMillis()) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
        nowMillis = System.currentTimeMillis()
    }
    val isLocked = adminLoginSecurityState.isLocked(nowMillis)
    val remainingLockMs = adminLoginSecurityState.remainingLockMillis(nowMillis)
    val effectiveAttempts = adminLoginSecurityState.effectiveFailedAttempts(nowMillis)
    val remainingAttempts = adminLoginSecurityState.remainingAttempts(nowMillis)

    LaunchedEffect(adminLoginSecurityState.failedAttempts, adminLoginSecurityState.lockUntilMillis) {
        if (adminLoginSecurityState.failedAttempts > 0) {
            challengeSeed++
            antiRobotInput = ""
        }
    }

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    Column(verticalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.testTag("admin_login_screen")) {
        PortalHeaderRow(
            title = "Admin Login",
            subtitle = "${AppIdentity.OFFICIAL_COMPANY_NAME} — Password, 2FA, CAPTCHA or Passkey",
            onBack = onBack
        )

        if (isLocked) {
            LockoutWarningBanner(
                isAdmin = true,
                remainingLockMs = remainingLockMs
            )
        } else if (effectiveAttempts > 0) {
            AttemptsWarningBanner(isAdmin = true, attempts = effectiveAttempts, remaining = remainingAttempts)
        }

        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it; onClearError() },
            label = { Text("Admin Email or Username") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TealAccent) },
            enabled = !isLocked,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("admin_login_identifier")
        )

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

        if (hasAdminVerifyCode || identifier.contains("@")) {
            OutlinedTextField(
                value = twoFactorCode,
                onValueChange = { twoFactorCode = it; onClearError() },
                label = { Text("Two-Factor Authentication Password") },
                enabled = !isLocked,
                leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TealAccent) },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("login_admin_verify_code")
            )
        }

        CaptchaVerificationCard(
            captchaCode = captchaCode,
            challengeSeed = challengeSeed,
            antiRobotInput = antiRobotInput,
            antiRobotPassed = antiRobotPassed,
            isLocked = isLocked,
            onRefresh = {
                challengeSeed++
                antiRobotInput = ""
                onClearError()
            },
            onInputChange = {
                antiRobotInput = it
                onClearError()
            },
            onDone = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onLoginAdmin(identifier, password, twoFactorCode, antiRobotPassed)
            }
        )

        if (recoverySuccessMsg.isNotEmpty()) {
            Text(recoverySuccessMsg, color = GreenPaid, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
            onClick = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onLoginAdmin(identifier, password, twoFactorCode, antiRobotPassed)
            },
            enabled = !isLocked && !cloudBusy,
            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login_submit_button")
        ) {
            if (cloudBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (isLocked) "Locked (${formatCountdown(remainingLockMs)})" else "Login with Password",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        OutlinedButton(
            onClick = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onLoginWithPasskey(identifier, antiRobotPassed)
            },
            enabled = !isLocked && !passkeyBusy && !cloudBusy,
            border = BorderStroke(1.2.dp, NavyDeep),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_passkey_login_button")
        ) {
            if (passkeyBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = NavyDeep)
            Spacer(Modifier.width(8.dp))
            Text("Login with Passkey (FIDO2)", fontWeight = FontWeight.Bold, color = NavyDeep)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    onClearError()
                    onClearOtp()
                    recoverySuccessMsg = ""
                    showForgotDialog = true
                },
                modifier = Modifier.testTag("forgot_password_button")
            ) {
                Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(18.dp), tint = TealAccent)
                Spacer(Modifier.width(4.dp))
                Text("Forgot Password?", fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = onSignInGoogle,
                enabled = !cloudBusy,
                modifier = Modifier.testTag("cloud_google_signin_button")
            ) {
                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp), tint = BlueAction)
                Spacer(Modifier.width(4.dp))
                Text(if (cloudUser != null) "Cloud Synced" else "Google Sync", fontWeight = FontWeight.SemiBold, color = BlueAction)
            }
        }
    }

    if (showForgotDialog) {
        OtpAndRecoveryForgotPasswordDialog(
            title = "Forgot Admin Password",
            purpose = "PASSWORD_RESET",
            prefillEmail = savedAdmin?.email.orEmpty().ifBlank { if (identifier.contains("@")) identifier else "" },
            targetUsername = savedAdmin?.username ?: AuthPolicy.CANONICAL_ADMIN_USERNAME,
            showLocalRecoveryTab = true,
            otpState = otpState,
            cloudBusy = cloudBusy,
            onDismiss = {
                onClearOtp()
                showForgotDialog = false
            },
            onRequestOtp = onRequestOtp,
            onVerifyOtp = onVerifyOtp,
            onResetPasswordWithOtp = { em, newP, confP, cb ->
                onResetPasswordWithOtp(em, "PASSWORD_RESET", newP, confP) { ok, msg ->
                    if (ok) {
                        password = ""
                        twoFactorCode = ""
                        recoverySuccessMsg = msg
                        showForgotDialog = false
                    }
                    cb(ok, msg)
                }
            },
            onResetLocalAdminPassword = { recPass, targetUser, newP, confP, cb ->
                onResetAdminPassword(recPass, targetUser, newP, confP) { ok, msg ->
                    if (ok) {
                        password = ""
                        twoFactorCode = ""
                        recoverySuccessMsg = msg
                        showForgotDialog = false
                    }
                    cb(ok, msg)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechnicianLoginHubSection(
    accounts: List<UserAccountEntity>,
    authError: String,
    cloudUser: CloudAuthUser?,
    cloudBusy: Boolean,
    passkeyBusy: Boolean,
    loginSecurityState: LoginSecurityState,
    isStaffOnlyApk: Boolean,
    prefillInviteCode: String,
    techActivationState: TechActivationState,
    activationBusy: Boolean,
    activationError: String,
    otpState: OtpUiState,
    onBack: (() -> Unit)?,
    onClearError: () -> Unit,
    onLogin: (String, String, Boolean, String) -> Unit,
    onSignInTechnicianEmail: (String, String) -> Unit,
    onLoginWithPasskey: (emailHint: String) -> Unit,
    onSignInGoogle: () -> Unit,
    onJoinInviteCode: (String) -> Unit,
    onVerifyTechActivation: (String, String) -> Unit,
    onClearTechActivation: () -> Unit,
    onRequestOtp: (String, String) -> Unit,
    onVerifyOtp: (String, () -> Unit) -> Unit,
    onClearOtp: () -> Unit,
    onResetPasswordWithOtp: (String, String, String, String, (Boolean, String) -> Unit) -> Unit
) {
    val techAccounts = remember(accounts, techActivationState) {
        val base = accounts.filter {
            it.role.equals("technician", ignoreCase = true) || it.role.equals("staff", ignoreCase = true)
        }
        if (techActivationState.verified && techActivationState.username.isNotBlank()) {
            base.filter { it.username.equals(techActivationState.username, ignoreCase = true) }
        } else base
    }
    var selectedMode by remember(prefillInviteCode) {
        mutableIntStateOf(if (prefillInviteCode.isNotBlank()) 2 else 0) // 0 = Email/User Login, 1 = Passkey Activate, 2 = Invite Code
    }
    var selectedTechUsername by remember(techAccounts, techActivationState) {
        mutableStateOf(
            techActivationState.username.ifBlank {
                techAccounts.firstOrNull()?.username.orEmpty()
            }
        )
    }
    var techEmailOrUsername by remember(techActivationState, techAccounts) {
        mutableStateOf(
            techActivationState.email.ifBlank {
                techAccounts.firstOrNull()?.email?.ifBlank { techAccounts.firstOrNull()?.username.orEmpty() }.orEmpty()
            }
        )
    }
    var techPassword by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var inviteCode by remember(prefillInviteCode) { mutableStateOf(prefillInviteCode) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var recoverySuccessMsg by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("technician_login_screen")) {
        PortalHeaderRow(
            title = "Technician Login",
            subtitle = "Authorized ${AppIdentity.OFFICIAL_COMPANY_NAME} field technicians",
            onBack = onBack
        )

        if (techActivationState.verified) {
            Surface(
                color = GreenPaidBg,
                border = BorderStroke(1.dp, GreenPaid),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("tech_activation_verified_badge")
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Authorized: ${techActivationState.email}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GreenPaid)
                        Text(AppIdentity.OFFICIAL_COMPANY_NAME, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onClearTechActivation, modifier = Modifier.testTag("clear_activation_button")) {
                        Text("Switch", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selectedMode == 0,
                onClick = { selectedMode = 0; onClearError() },
                label = { Text("Technician Login", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("setup_tab_tech_email")
            )
            FilterChip(
                selected = selectedMode == 1,
                onClick = { selectedMode = 1; onClearError() },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("First-Time Activate", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("setup_tab_activation")
            )
            FilterChip(
                selected = selectedMode == 2,
                onClick = { selectedMode = 2; onClearError() },
                leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Invite Code", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("setup_tab_cloud")
            )
        }

        when (selectedMode) {
            0 -> {
                if (techAccounts.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login_username_dropdown"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedTechUsername.ifBlank { "Pick Saved Technician" }.let { u ->
                                        if (u.startsWith("@")) u else "@$u"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Pick Technician")
                            }
                        }
                        DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                            techAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.displayName} (${AuthPolicy.formatHandle(acc.username)})") },
                                    onClick = {
                                        selectedTechUsername = acc.username
                                        techEmailOrUsername = acc.email.ifBlank { acc.username }
                                        dropdownExpanded = false
                                    },
                                    modifier = Modifier.testTag("login_user_option_${acc.username}")
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = techEmailOrUsername,
                    onValueChange = { techEmailOrUsername = it; onClearError() },
                    label = { Text("Technician Email or Username") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("tech_email_login_input")
                )

                OutlinedTextField(
                    value = techPassword,
                    onValueChange = { techPassword = it; onClearError() },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password")
                        }
                    },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("tech_password_login_input")
                )

                if (recoverySuccessMsg.isNotEmpty()) {
                    Text(recoverySuccessMsg, color = GreenPaid, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                if (authError.isNotEmpty()) {
                    Text(
                        text = authError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("tech_email_login_error")
                    )
                }

                Button(
                    onClick = {
                        clearFocusAndHideToolbar(focusManager, textToolbar)
                        val cleanId = techEmailOrUsername.trim()
                        val localMatch = accounts.firstOrNull {
                            (it.role.equals("technician", ignoreCase = true) || it.role.equals("staff", ignoreCase = true)) &&
                                (it.username.equals(cleanId, ignoreCase = true) || it.email.equals(cleanId, ignoreCase = true)) &&
                                it.passwordHash.isNotBlank()
                        }
                        if (cleanId.contains("@") && localMatch == null) {
                            onSignInTechnicianEmail(cleanId, techPassword)
                        } else if (cleanId.contains("@") && !localMatch?.email.isNullOrBlank()) {
                            onSignInTechnicianEmail(cleanId, techPassword)
                        } else {
                            onLogin(localMatch?.username ?: cleanId, techPassword, true, "")
                        }
                    },
                    enabled = !cloudBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("tech_email_login_submit_button")
                ) {
                    if (cloudBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (cloudBusy) "Signing In..." else "Sign In with Password", fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = {
                        clearFocusAndHideToolbar(focusManager, textToolbar)
                        onLoginWithPasskey(techEmailOrUsername)
                    },
                    enabled = !passkeyBusy && !cloudBusy,
                    border = BorderStroke(1.2.dp, NavyDeep),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("tech_passkey_login_button")
                ) {
                    if (passkeyBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = NavyDeep)
                    Spacer(Modifier.width(8.dp))
                    Text("Login with Passkey (FIDO2)", fontWeight = FontWeight.Bold, color = NavyDeep)
                }

                TextButton(
                    onClick = {
                        onClearOtp()
                        recoverySuccessMsg = ""
                        showForgotDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("tech_forgot_password_button")
                ) {
                    Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(18.dp), tint = TealAccent)
                    Spacer(Modifier.width(4.dp))
                    Text("Forgot Technician Password? (Email OTP)", fontWeight = FontWeight.SemiBold)
                }
            }

            1 -> TechnicianActivationPasskeySection(
                activationBusy = activationBusy,
                activationError = activationError,
                onVerifyTechActivation = onVerifyTechActivation
            )

            else -> {
                if (cloudUser != null) {
                    Surface(color = GreenPaidBg, border = BorderStroke(1.dp, GreenPaid), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Google Connected: ${cloudUser.email}", modifier = Modifier.padding(10.dp), color = GreenPaid, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onSignInGoogle,
                        enabled = !cloudBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("cloud_google_signin_button")
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in with Google", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it; onClearError() },
                    label = { Text("Admin Invite Code") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("cloud_invite_code_input")
                )
                if (authError.isNotEmpty()) {
                    Text(authError, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onJoinInviteCode(inviteCode) },
                    enabled = !cloudBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("cloud_join_button")
                ) {
                    Text(if (cloudBusy) "Connecting..." else "Connect with Invite Code", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    if (showForgotDialog) {
        OtpAndRecoveryForgotPasswordDialog(
            title = "Reset Technician Password",
            purpose = "PASSWORD_RESET",
            prefillEmail = if (techEmailOrUsername.contains("@")) techEmailOrUsername else "",
            targetUsername = selectedTechUsername,
            showLocalRecoveryTab = false,
            otpState = otpState,
            cloudBusy = cloudBusy,
            onDismiss = {
                onClearOtp()
                showForgotDialog = false
            },
            onRequestOtp = onRequestOtp,
            onVerifyOtp = onVerifyOtp,
            onResetPasswordWithOtp = { em, newP, confP, cb ->
                onResetPasswordWithOtp(em, "PASSWORD_RESET", newP, confP) { ok, msg ->
                    if (ok) {
                        techPassword = ""
                        recoverySuccessMsg = msg
                        showForgotDialog = false
                    }
                    cb(ok, msg)
                }
            }
        )
    }
}

@Composable
private fun SuperAdminLoginSection(
    accounts: List<UserAccountEntity>,
    authError: String,
    cloudBusy: Boolean,
    passkeyBusy: Boolean,
    loginSecurityState: LoginSecurityState,
    otpState: OtpUiState,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    onLoginSuperAdmin: (identifier: String, password: String, antiRobotPassed: Boolean) -> Unit,
    onLoginWithPasskey: (emailHint: String, antiRobotPassed: Boolean) -> Unit,
    onRequestOtp: (String, String) -> Unit,
    onVerifyOtp: (String, () -> Unit) -> Unit,
    onClearOtp: () -> Unit,
    onResetPasswordWithOtp: (String, String, String, String, (Boolean, String) -> Unit) -> Unit
) {
    val superAdminAccounts = remember(accounts) {
        accounts.filter { it.role.equals("super_admin", ignoreCase = true) }
    }
    var identifier by remember(superAdminAccounts) {
        mutableStateOf(superAdminAccounts.firstOrNull()?.let { it.email.ifBlank { it.username } }.orEmpty())
    }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    var challengeSeed by remember { mutableIntStateOf(0) }
    val captchaCode = remember(challengeSeed) { generateCaptchaCode(challengeSeed) }
    var antiRobotInput by remember { mutableStateOf("") }
    val antiRobotPassed = antiRobotInput.trim() == captchaCode

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("super_admin_login_screen")) {
        PortalHeaderRow(
            title = "Super Admin Login",
            subtitle = "System-level analytics & Employee Management only",
            onBack = onBack
        )

        Surface(
            color = Color(0xFFEDE9FE),
            border = BorderStroke(1.dp, Color(0xFF5B21B6)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Authorized Super Admin accounts only. Public Super Admin registration is permanently disabled.",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF4C1D95),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(10.dp)
            )
        }

        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it; onClearError() },
            label = { Text("Super Admin Email or Username") },
            leadingIcon = { Icon(Icons.Default.SupervisorAccount, contentDescription = null, tint = Color(0xFF5B21B6)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("super_admin_identifier_input")
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password")
                }
            },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("super_admin_password_input")
        )

        CaptchaVerificationCard(
            captchaCode = captchaCode,
            challengeSeed = challengeSeed,
            antiRobotInput = antiRobotInput,
            antiRobotPassed = antiRobotPassed,
            isLocked = false,
            onRefresh = {
                challengeSeed++
                antiRobotInput = ""
                onClearError()
            },
            onInputChange = {
                antiRobotInput = it
                onClearError()
            },
            onDone = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onLoginSuperAdmin(identifier, password, antiRobotPassed)
            }
        )

        if (authError.isNotEmpty()) {
            Text(authError, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onLoginSuperAdmin(identifier, password, antiRobotPassed)
            },
            enabled = !cloudBusy,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C1D95), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("super_admin_login_submit_button")
        ) {
            Text("Login as Super Admin", fontWeight = FontWeight.Bold, color = Color.White)
        }

        OutlinedButton(
            onClick = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onLoginWithPasskey(identifier, antiRobotPassed)
            },
            enabled = !passkeyBusy && !cloudBusy,
            border = BorderStroke(1.2.dp, Color(0xFF4C1D95)),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("super_admin_passkey_login_button")
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF4C1D95))
            Spacer(Modifier.width(8.dp))
            Text("Login with Passkey (FIDO2)", fontWeight = FontWeight.Bold, color = Color(0xFF4C1D95))
        }
    }
}

@Composable
private fun LockoutWarningBanner(isAdmin: Boolean, remainingLockMs: Long) {
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
            Icon(Icons.Default.LockClock, contentDescription = "Account locked", tint = RedDueDark, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isAdmin) "15-Minute Admin Lockout Active (3/3 Wrong Attempts)" else "2-Hour Security Lockout Active (5/5 Wrong Passwords)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = RedDueDark
                )
                Text(
                    text = "Try again in ${formatCountdown(remainingLockMs)}.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF450A0A)
                )
            }
        }
    }
}

@Composable
private fun AttemptsWarningBanner(isAdmin: Boolean, attempts: Int, remaining: Int) {
    Surface(
        color = Color(0xFFFFFBEB),
        border = BorderStroke(1.dp, AmberWarn),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("login_attempts_warning")
    ) {
        Text(
            text = if (isAdmin) {
                "Admin Security Alert: $attempts/${LoginSecurityState.ADMIN_MAX_ATTEMPTS} wrong tries — $remaining try(s) left before 15-min phone lock."
            } else {
                "Security Alert: $attempts/${LoginSecurityState.MAX_ATTEMPTS} failed attempts — $remaining try(s) left before 2-hour lock."
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF92400E),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun CaptchaVerificationCard(
    captchaCode: String,
    challengeSeed: Int,
    antiRobotInput: String,
    antiRobotPassed: Boolean,
    isLocked: Boolean,
    onRefresh: () -> Unit,
    onInputChange: (String) -> Unit,
    onDone: () -> Unit
) {
    Surface(
        color = if (antiRobotPassed) GreenPaidBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.2.dp, if (antiRobotPassed) GreenPaid else TealAccent),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("anti_robot_card")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    onClick = onRefresh,
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
                    onValueChange = { raw -> onInputChange(raw.filter { it.isLetterOrDigit() }.take(8)) },
                    label = { Text("Enter Code") },
                    enabled = !isLocked,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("anti_robot_answer_input")
                )
            }
        }
    }
}

private fun formatCountdown(ms: Long): String {
    val totalSec = ((ms + 999L) / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
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
        modifier = modifier.fillMaxWidth().testTag("password_strength_checklist")
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strengthLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (password.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else barColor
                )
                Text("${result.score}/5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

private const val CAPTCHA_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"

private fun generateCaptchaCode(seed: Int): String {
    val rnd = Random(System.nanoTime() + seed.toLong() * 7919L)
    return buildString {
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
    val glyphSpecs = remember(captchaCode, seed) {
        val rnd = Random(captchaCode.hashCode().toLong() + seed.toLong() * 997L)
        val palette = listOf(0xFF0B253A.toInt(), 0xFF0F766E.toInt(), 0xFF1D4ED8.toInt(), 0xFF7C2D12.toInt(), 0xFF4C1D95.toInt())
        captchaCode.mapIndexed { idx, ch ->
            CaptchaGlyphSpec(
                char = ch.toString(),
                angleDeg = (rnd.nextFloat() * 34f) - 17f,
                yJitter = (rnd.nextFloat() * 14f) - 7f,
                skewX = (rnd.nextFloat() * 0.36f) - 0.18f,
                colorInt = palette[(idx + rnd.nextInt(palette.size)) % palette.size]
            )
        }
    }

    Box(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF1F5F9))
            .border(1.2.dp, Color(0xFF94A3B8), RoundedCornerShape(10.dp))
            .semantics { contentDescription = "CAPTCHA challenge code" }
            .testTag("anti_robot_challenge_text")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rnd = Random(seed.toLong() * 31337L + captchaCode.hashCode())
            repeat(18) {
                drawCircle(
                    color = Color(0xFF64748B).copy(alpha = 0.28f),
                    radius = 1.5f + rnd.nextFloat() * 2.5f,
                    center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                )
            }
            repeat(4) {
                val p = Path().apply {
                    val yStart = rnd.nextFloat() * size.height
                    moveTo(0f, yStart)
                    cubicTo(
                        size.width * 0.33f,
                        rnd.nextFloat() * size.height,
                        size.width * 0.66f,
                        rnd.nextFloat() * size.height,
                        size.width,
                        rnd.nextFloat() * size.height
                    )
                }
                drawPath(
                    path = p,
                    color = if (it % 2 == 0) Color(0xFF0F766E).copy(alpha = 0.32f) else Color(0xFF1D4ED8).copy(alpha = 0.25f),
                    style = Stroke(width = 1.8f)
                )
            }

            drawIntoCanvas { canvas ->
                val count = glyphSpecs.size.coerceAtLeast(1)
                val paddingX = size.width * 0.10f
                val stepX = (size.width - paddingX * 2f) / count
                val baseY = size.height * 0.66f

                glyphSpecs.forEachIndexed { idx, g ->
                    val cx = paddingX + stepX * idx + stepX * 0.45f
                    val cy = baseY + g.yJitter
                    val textPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = g.colorInt
                        textSize = (size.height * 0.44f).coerceIn(28f, 44f)
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSkewX = g.skewX
                    }
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.translate(cx, cy)
                    canvas.nativeCanvas.rotate(g.angleDeg)
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
private fun OtpAndRecoveryForgotPasswordDialog(
    title: String,
    purpose: String,
    prefillEmail: String,
    targetUsername: String,
    showLocalRecoveryTab: Boolean,
    otpState: OtpUiState,
    cloudBusy: Boolean,
    onDismiss: () -> Unit,
    onRequestOtp: (email: String, purpose: String) -> Unit,
    onVerifyOtp: (otp: String, onVerified: () -> Unit) -> Unit,
    onResetPasswordWithOtp: (email: String, newPass: String, confirmPass: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onResetLocalAdminPassword: ((String, String?, String, String, (Boolean, String) -> Unit) -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    var useRecoveryKey by remember { mutableStateOf(false) }
    var email by remember(prefillEmail) { mutableStateOf(prefillEmail) }
    var otpCode by remember { mutableStateOf("") }
    var recoveryPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPass by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    val otpVerifiedForEmail = otpState.otpVerified && otpState.email.equals(email.trim(), ignoreCase = true)
    val newPasswordStrong = remember(newPassword) { AuthPolicy.evaluatePassword(newPassword).isStrong }
    val canSubmit = if (useRecoveryKey) {
        recoveryPassword.isNotBlank() && newPasswordStrong && newPassword == confirmPassword
    } else {
        otpVerifiedForEmail && newPasswordStrong && newPassword == confirmPassword && !cloudBusy
    }

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
                Text(title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            ProvideSafeTextToolbar {
                Column(
                    modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showLocalRecoveryTab) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !useRecoveryKey,
                                onClick = { useRecoveryKey = false; errorMsg = "" },
                                label = { Text("Email OTP") }
                            )
                            FilterChip(
                                selected = useRecoveryKey,
                                onClick = { useRecoveryKey = true; errorMsg = "" },
                                label = { Text("Recovery Key") }
                            )
                        }
                    }

                    if (!useRecoveryKey) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; errorMsg = "" },
                            label = { Text("Registered Email") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("forgot_otp_email_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onRequestOtp(email, purpose) },
                                enabled = !otpState.busy && AuthPolicy.isValidEmail(email.trim()),
                                modifier = Modifier.testTag("forgot_send_otp_button")
                            ) {
                                Text(if (otpState.challengeId.isNotBlank()) "Resend OTP" else "Send OTP")
                            }
                            if (otpVerifiedForEmail) {
                                Text("OTP Verified", color = GreenPaid, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!otpVerifiedForEmail && otpState.challengeId.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { otpCode = it.filter { ch -> ch.isDigit() }.take(6) },
                                    label = { Text("Enter OTP") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("forgot_otp_code_input")
                                )
                                Button(
                                    onClick = { onVerifyOtp(otpCode) {} },
                                    enabled = !otpState.busy && otpCode.length >= 4,
                                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                                    modifier = Modifier.height(50.dp).testTag("forgot_verify_otp_button")
                                ) {
                                    Text("Verify")
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = recoveryPassword,
                            onValueChange = { recoveryPassword = it; errorMsg = "" },
                            label = { Text("Default Recovery Password") },
                            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("forgot_master_password_input")
                        )
                    }

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; errorMsg = "" },
                        label = { Text("Strong New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showNewPass = !showNewPass }) {
                                Icon(if (showNewPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password")
                            }
                        },
                        visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("forgot_new_password_input")
                    )
                    PasswordStrengthChecklist(password = newPassword)
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMsg = "" },
                        label = { Text("Confirm New Password") },
                        isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("forgot_confirm_password_input")
                    )
                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("forgot_password_error"))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    if (useRecoveryKey && onResetLocalAdminPassword != null) {
                        onResetLocalAdminPassword(recoveryPassword, targetUsername, newPassword, confirmPassword) { ok, msg ->
                            if (!ok) errorMsg = msg
                        }
                    } else {
                        onResetPasswordWithOtp(email, newPassword, confirmPassword) { ok, msg ->
                            if (!ok) errorMsg = msg
                        }
                    }
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                modifier = Modifier.testTag("reset_admin_password_button")
            ) {
                Text("Reset Password", fontWeight = FontWeight.Bold, color = Color.White)
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

@Composable
private fun TechnicianActivationPasskeySection(
    activationBusy: Boolean,
    activationError: String,
    onVerifyTechActivation: (email: String, passkey: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var passkey by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, TealAccent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Technician Account Activation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Enter your authorized Email and Activation Passkey (TS-XXXX-XXXX) shared by Admin to unlock Login.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Authorized Email") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("tech_activation_email_input")
        )

        OutlinedTextField(
            value = passkey,
            onValueChange = { passkey = it },
            label = { Text("Activation Passkey (e.g. TS-XXXX-XXXX)") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onVerifyTechActivation(email, passkey)
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("tech_activation_passkey_input")
        )

        if (activationError.isNotEmpty()) {
            Text(activationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("tech_activation_error_text"))
        }

        Button(
            onClick = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onVerifyTechActivation(email, passkey)
            },
            enabled = !activationBusy,
            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("verify_activation_button")
        ) {
            if (activationBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (activationBusy) "Verifying Passkey..." else "Verify Email & Passkey", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun MandatoryPasswordChangeScreen(
    pendingState: PendingPasswordChangeState,
    authError: String,
    busy: Boolean,
    logoBase64: String = "",
    passkeyBusy: Boolean = false,
    onSubmitNewPassword: (newPassword: String, confirmPassword: String) -> Unit = { _, _ -> },
    onCompleteFirstLogin: (username: String, newPassword: String, confirmPassword: String, photoBitmap: Bitmap?) -> Unit = { _, newP, confP, _ ->
        onSubmitNewPassword(newP, confP)
    },
    onRegisterPasskey: () -> Unit = {},
    onSignOut: () -> Unit
) {
    BackHandler {
        // Prevent bypassing mandatory first-login setup
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember(pendingState.username) { mutableStateOf(pendingState.username) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var pickedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    val existingPhoto = remember(pendingState.photoBase64) {
        pendingState.photoBase64.takeIf { it.isNotBlank() }?.let { InvoicePdfPrinter.decodeBase64Bitmap(it) }
    }
    val previewBitmap = pickedPhoto ?: existingPhoto
    val hasPhoto = previewBitmap != null

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = BitmapUtils.decodeSampledBitmapFromUri(context, uri, 900, 900)
                if (bmp != null) pickedPhoto = bmp
                else Toast.makeText(context, "Could not load selected profile photo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    val evaluation = remember(newPassword) { AuthPolicy.evaluatePassword(newPassword) }
    val canSubmit = !busy && hasPhoto && username.trim().length >= 3 && evaluation.isStrong && newPassword == confirmPassword

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
                .fillMaxWidth()
                .testTag("mandatory_password_change_screen"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TseShieldLogo(logoBase64 = logoBase64, modifier = Modifier.size(48.dp))
                    Column {
                        Text("Technician First-Time Setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(AppIdentity.OFFICIAL_COMPANY_NAME, style = MaterialTheme.typography.labelMedium, color = TealAccent, fontWeight = FontWeight.Bold)
                    }
                }

                // Read-only identity info assigned by Admin
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, TealAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Verified Technician Identity (Admin Controlled)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NavyDeep)
                        Text("Name: ${pendingState.name}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Mobile: ${pendingState.phone.ifBlank { "Registered" }}", style = MaterialTheme.typography.bodySmall)
                        Text("Email: ${pendingState.email}", style = MaterialTheme.typography.bodySmall)
                        if (pendingState.techId.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                                Text("Tech ID: ${pendingState.techId}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = TealAccent)
                            }
                        }
                    }
                }

                // Mandatory Profile Photo Upload
                Surface(
                    color = if (hasPhoto) GreenPaidBg else RedDueBg,
                    border = BorderStroke(1.dp, if (hasPhoto) GreenPaid else RedDue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Technician profile photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = "Profile photo required", tint = RedDueDark)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (hasPhoto) "Profile Photo Ready" else "Profile Photo Mandatory",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (hasPhoto) GreenPaid else RedDueDark
                            )
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                },
                                modifier = Modifier.testTag("first_login_pick_photo_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (hasPhoto) "Change Photo" else "Upload Photo (Required)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Create Username (min 3 chars)") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("first_login_username_input")
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Create Strong Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password")
                        }
                    },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("first_login_new_password")
                )

                PasswordStrengthChecklist(password = newPassword)

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("first_login_confirm_password")
                )

                OutlinedButton(
                    onClick = onRegisterPasskey,
                    enabled = !passkeyBusy && !busy,
                    border = BorderStroke(1.dp, NavyDeep),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("first_login_register_passkey_button")
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = NavyDeep)
                    Spacer(Modifier.width(8.dp))
                    Text(if (passkeyBusy) "Registering Passkey..." else "Register FIDO2 Passkey (Optional)", fontWeight = FontWeight.Bold, color = NavyDeep)
                }

                if (authError.isNotEmpty()) {
                    Text(authError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("first_login_error_text"))
                }

                Button(
                    onClick = {
                        clearFocusAndHideToolbar(focusManager, textToolbar)
                        onCompleteFirstLogin(username.trim(), newPassword, confirmPassword, pickedPhoto)
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenPaid,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("first_login_update_password_button")
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = when {
                            busy -> "Saving Profile & Password..."
                            !hasPhoto -> "Upload Profile Photo to Continue"
                            else -> "Complete Setup & Open Dashboard"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        clearFocusAndHideToolbar(focusManager, textToolbar)
                        onSignOut()
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("first_login_signout_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sign Out", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
