package com.example.ui.components

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.example.data.UserAccountEntity
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldSoft
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.TealAccent
import kotlinx.coroutines.delay

@Composable
fun SplashOverlay(
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
    var targetProgress by remember { mutableStateOf(0.15f) }
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(820, easing = FastOutSlowInEasing),
        label = "splashProgress"
    )
    var showSkip by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        targetProgress = 1f
        delay(650)
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
                modifier = Modifier
                    .size(104.dp)
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
                text = "LOADING SECURE CCTV BILLING...",
                style = MaterialTheme.typography.labelMedium,
                color = GoldSoft,
                letterSpacing = 1.6.sp
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
                    Text("Continue →", color = CyanGlow)
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
    onClearError: () -> Unit,
    onSetupAdmin: (username: String, password: String, confirm: String, finalCode: String) -> Unit,
    onLogin: (username: String, password: String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(NavyDeep, Color(0xFF0B5968))))
            .safeDrawingPadding()
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
                // Hero Banner Artwork
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
                        TseShieldLogo(modifier = Modifier.size(48.dp))
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
                                onSetupAdmin = onSetupAdmin
                            )

                            true -> StaffAdminLoginForm(
                                accounts = accounts,
                                authError = authError,
                                onClearError = onClearError,
                                onLogin = onLogin
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
    onSetupAdmin: (String, String, String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var finalCode by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = TealAccent)
            Text("First-Time Admin Setup", style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
        }
        Text(
            text = "Sabse pehle Admin username, password aur Final Bill Code set karein.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; onClearError() },
            label = { Text("Admin Username (min 3 chars)") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_admin_username")
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_admin_password")
        )

        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it; onClearError() },
            label = { Text("Confirm Admin Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_admin_confirm")
        )

        OutlinedTextField(
            value = finalCode,
            onValueChange = { finalCode = it; onClearError() },
            label = { Text("Initial Final Bill Code (min 4 chars)") },
            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_final_code")
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
                onSetupAdmin(username, password, confirm, finalCode)
            },
            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("create_admin_button")
        ) {
            Text("Create Admin Account", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StaffAdminLoginForm(
    accounts: List<UserAccountEntity>,
    authError: String,
    onClearError: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var selectedUsername by remember(accounts) {
        mutableStateOf(accounts.firstOrNull()?.username ?: "")
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    val selectedAccount = accounts.firstOrNull { it.username == selectedUsername }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Login, contentDescription = null, tint = TealAccent)
            Text("Staff / Admin Login", style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    dropdownExpanded = true
                },
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
                        Icon(Icons.Default.Person, contentDescription = null)
                        Text(
                            text = if (selectedAccount != null) {
                                "${selectedAccount.username} (${if (selectedAccount.role == "admin") "Admin" else "Staff"})"
                            } else {
                                "Select Username / ID"
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
                accounts.forEach { acc ->
                    val roleLabel = if (acc.role.equals("admin", ignoreCase = true)) "Admin" else "Staff"
                    DropdownMenuItem(
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
                        }
                    )
                }
            }
        }

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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onLogin(selectedUsername, password)
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("login_password_input")
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
                onLogin(selectedUsername, password)
            },
            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("login_submit_button")
        ) {
            Text("Login", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}
