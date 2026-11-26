@file:Suppress("DEPRECATION")

package com.example.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.util.configureAppCheck
import com.example.util.formatFirebaseErrorWithAppCheckHint
import com.example.util.isAppCheckError
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.resume

data class CloudAuthUser(
    val uid: String,
    val email: String,
    val displayName: String
)

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext?.findComponentActivity()
        else -> null
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findActivity()
        else -> null
    }

    private fun resolveWebClientId(context: Context): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val id = if (resId != 0) runCatching { context.getString(resId).trim() }.getOrDefault("") else ""
        if (id.isNotEmpty()) {
            return id
        }
        // Fallback to Web OAuth client ID (client_type: 3) configured in google-services.json
        return "760882645284-ha4304oiq1ql4fobu04lmben27c32lgk.apps.googleusercontent.com"
    }

    private fun getAppSha1Fingerprint(context: Context): String {
        return runCatching {
            val pkgName = context.packageName
            val pm = context.packageManager
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                val sig = signingInfo?.apkContentsSigners?.firstOrNull()
                    ?: signingInfo?.signingCertificateHistory?.firstOrNull()
                sig?.toByteArray()
            } else {
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
                info.signatures?.firstOrNull()?.toByteArray()
            }
            if (certBytes == null) return@runCatching "unknown"
            val md = MessageDigest.getInstance("SHA-1")
            md.digest(certBytes).joinToString(":") { b -> "%02X".format(b) }
        }.getOrDefault("unknown")
    }

    fun currentUser(context: Context): CloudAuthUser? {
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            configureAppCheck(context)
            val u = FirebaseAuth.getInstance().currentUser ?: return null
            CloudAuthUser(
                uid = u.uid,
                email = u.email.orEmpty(),
                displayName = u.displayName.orEmpty()
            )
        }.getOrNull()
    }

    suspend fun trySilentGoogleSignIn(context: Context): Result<CloudAuthUser?> {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            configureAppCheck(context)
            val auth = FirebaseAuth.getInstance()
            val existing = auth.currentUser
            if (existing != null) {
                return Result.success(
                    CloudAuthUser(
                        uid = existing.uid,
                        email = existing.email.orEmpty(),
                        displayName = existing.displayName.orEmpty()
                    )
                )
            }
            val webClientId = resolveWebClientId(context)
            val credContext: Context = context.findActivity() ?: context
            try {
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(true)
                    .setAutoSelectEnabled(true)
                    .build()
                val req = GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build()
                val cm = CredentialManager.create(credContext)
                val response = cm.getCredential(credContext, req)
                val idToken = extractGoogleIdToken(response.credential)
                if (!idToken.isNullOrBlank()) {
                    val user = exchangeIdTokenWithFirebase(auth, idToken)
                    return Result.success(user)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: NoCredentialException) {
                // Fall through to legacy silent check
            } catch (t: Throwable) {
                Log.d(TAG, "CredentialManager silent sign-in skipped: ${t.message}")
            }

            // Secondary silent attempt via Play Services GoogleSignIn
            val legacyUser = runCatching {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build()
                val account = GoogleSignIn.getClient(credContext, gso).silentSignIn().await()
                val token = account?.idToken
                if (!token.isNullOrBlank()) exchangeIdTokenWithFirebase(auth, token) else null
            }.getOrNull()
            Result.success(legacyUser)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.d(TAG, "Silent Google Sign-In skipped: ${t.message}")
            Result.success(null)
        }
    }

    suspend fun signInWithGoogle(activityContext: Context): Result<CloudAuthUser> {
        return try {
            if (FirebaseApp.getApps(activityContext).isEmpty()) {
                FirebaseApp.initializeApp(activityContext)
            }
            configureAppCheck(activityContext)
            val auth = FirebaseAuth.getInstance()
            val existing = auth.currentUser
            if (existing != null) {
                Log.i(TAG, "User already signed in: ${existing.email}")
                return Result.success(
                    CloudAuthUser(
                        uid = existing.uid,
                        email = existing.email.orEmpty(),
                        displayName = existing.displayName.orEmpty()
                    )
                )
            }

            val playStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activityContext)
            if (playStatus != ConnectionResult.SUCCESS &&
                !GoogleApiAvailability.getInstance().isUserResolvableError(playStatus)
            ) {
                return Result.failure(
                    IllegalStateException("Google Play Services unavailable on this device (Code $playStatus).")
                )
            }

            val webClientId = resolveWebClientId(activityContext)
            val act = activityContext.findActivity()
            val componentAct = activityContext.findComponentActivity()
            val credContext: Context = act ?: activityContext
            val cm = CredentialManager.create(credContext)

            // Step 1: Primary Jetpack Credential Manager flow (GetSignInWithGoogleOption)
            var credError: Throwable? = null
            var idToken: String? = null
            try {
                Log.i(TAG, "Attempting CredentialManager sign-in with GetSignInWithGoogleOption...")
                val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                val req = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                val response = cm.getCredential(credContext, req)
                idToken = extractGoogleIdToken(response.credential)
                if (!idToken.isNullOrBlank()) {
                    Log.i(TAG, "GetSignInWithGoogleOption returned ID token.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "GetSignInWithGoogleOption failed (${t::class.simpleName}: ${t.message})", t)
                credError = t
            }

            // Step 2: Try GetGoogleIdOption if Step 1 failed with a non-cancellation error
            if (idToken.isNullOrBlank() && credError != null && credError !is GetCredentialCancellationException) {
                try {
                    Log.i(TAG, "Attempting CredentialManager GetGoogleIdOption...")
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setServerClientId(webClientId)
                        .setFilterByAuthorizedAccounts(false)
                        .setAutoSelectEnabled(false)
                        .build()
                    val req = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                    val response = cm.getCredential(credContext, req)
                    idToken = extractGoogleIdToken(response.credential)
                    if (!idToken.isNullOrBlank()) {
                        Log.i(TAG, "GetGoogleIdOption returned ID token.")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "GetGoogleIdOption failed (${t::class.simpleName}: ${t.message})", t)
                    credError = t
                }
            }

            // Step 3: Native Google Play Services Sign-In Intent fallback (works on Realme/ColorOS/Oppo/Vivo/Xiaomi/all OEMs)
            // Unlike Firebase OAuthProvider (web widget), GoogleSignInClient uses Play Services native SignInHubActivity
            // directly so it never hits the Firebase Auth web widget App Check 401 error, and returns exact ApiException codes.
            if (idToken.isNullOrBlank() && componentAct != null) {
                Log.i(TAG, "Falling back to native Play Services GoogleSignInClient on ${componentAct::class.simpleName}...")
                val legacyResult = signInWithPlayServicesIntent(componentAct, webClientId)
                if (legacyResult.isSuccess) {
                    idToken = legacyResult.getOrNull()
                } else {
                    val legacyErr = legacyResult.exceptionOrNull()
                    Log.e(TAG, "Play Services GoogleSignInClient failed: ${legacyErr?.message}", legacyErr)
                    return Result.failure(
                        legacyErr ?: IllegalStateException("Google Sign-In failed.")
                    )
                }
            }

            if (idToken.isNullOrBlank()) {
                return when (credError) {
                    is GetCredentialCancellationException -> {
                        val msg = credError.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: credError.message.orEmpty()
                        Result.failure(IllegalStateException("Google Sign-In cancelled ($msg)."))
                    }
                    is NoCredentialException -> {
                        Result.failure(
                            IllegalStateException("No Google account found on device. Add a Google account in Settings and retry.")
                        )
                    }
                    is GetCredentialException -> {
                        val detail = credError.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: credError.message.orEmpty()
                        Result.failure(IllegalStateException("Google Sign-In failed (${credError.type}): $detail"))
                    }
                    else -> {
                        Result.failure(
                            IllegalStateException(credError?.message ?: "Google Sign-In did not return a valid ID token.")
                        )
                    }
                }
            }

            // Step 4: Authenticate with Firebase Auth using the Google ID token
            val user = exchangeIdTokenWithFirebase(auth, idToken)
                ?: return Result.failure(IllegalStateException("Firebase sign-in returned null user."))
            Log.i(TAG, "Firebase Google Sign-In complete: ${user.email}")
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthException) {
            Log.e(TAG, "FirebaseAuthException (${e.errorCode}): ${e.message}", e)
            Result.failure(IllegalStateException(formatFirebaseAuthError(activityContext, e), e))
        } catch (t: Throwable) {
            Log.e(TAG, "Google Sign-In failed", t)
            Result.failure(Exception(formatFirebaseThrowableError(activityContext, t), t))
        }
    }

    private suspend fun signInWithPlayServicesIntent(
        activity: ComponentActivity,
        webClientId: String
    ): Result<String> {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(activity, gso)
        // Ensure user gets a clean account picker rather than a stale cached error state
        runCatching { client.signOut().await() }

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val key = "tse_google_signin_${System.nanoTime()}"
                var launcherRef: ActivityResultLauncher<Intent>? = null
                val localLauncher = activity.activityResultRegistry.register(
                    key,
                    ActivityResultContracts.StartActivityForResult()
                ) { activityResult ->
                    runCatching { launcherRef?.unregister() }
                    if (!cont.isActive) return@register
                    try {
                        val task = GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
                        val account = task.getResult(ApiException::class.java)
                        val token = account?.idToken
                        if (!token.isNullOrBlank()) {
                            cont.resume(Result.success(token))
                        } else {
                            cont.resume(
                                Result.failure(
                                    IllegalStateException(
                                        "Google account selected (${account?.email.orEmpty()}), but ID token is missing. Check Web Client ID in google-services.json."
                                    )
                                )
                            )
                        }
                    } catch (e: ApiException) {
                        val detail = formatGoogleSignInApiException(activity, e)
                        cont.resume(Result.failure(IllegalStateException(detail, e)))
                    } catch (t: Throwable) {
                        cont.resume(
                            Result.failure(
                                Exception("Google Sign-In failed: ${t.message ?: t::class.simpleName}", t)
                            )
                        )
                    }
                }
                launcherRef = localLauncher
                cont.invokeOnCancellation {
                    runCatching { localLauncher.unregister() }
                }
                try {
                    localLauncher.launch(client.signInIntent)
                } catch (t: Throwable) {
                    runCatching { localLauncher.unregister() }
                    if (cont.isActive) {
                        cont.resume(
                            Result.failure(
                                Exception("Unable to open Google Sign-In screen: ${t.message}", t)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun formatGoogleSignInApiException(context: Context, e: ApiException): String {
        val code = e.statusCode
        val codeName = GoogleSignInStatusCodes.getStatusCodeString(code)
        Log.e(TAG, "GoogleSignIn ApiException code=$code ($codeName), msg=${e.message}", e)
        return when (code) {
            CommonStatusCodes.DEVELOPER_ERROR -> {
                val sha1 = getAppSha1Fingerprint(context)
                "Google OAuth SHA-1 mismatch (Code 10: DEVELOPER_ERROR). Installed APK SHA-1: $sha1. Verify this SHA-1 is registered in Firebase Project Settings."
            }
            GoogleSignInStatusCodes.SIGN_IN_FAILED -> {
                "Google Sign-In configuration error (Code 12500: SIGN_IN_FAILED). Check Firebase Google Auth provider & OAuth consent screen."
            }
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED,
            CommonStatusCodes.CANCELED -> {
                "Google Sign-In cancelled by user (Code $code)."
            }
            GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> {
                "Google Sign-In is already in progress (Code 12502). Please wait and try again."
            }
            CommonStatusCodes.NETWORK_ERROR -> {
                "Network error during Google Sign-In (Code 7). Check your internet connection."
            }
            CommonStatusCodes.INTERNAL_ERROR -> {
                "Google Play Services internal error (Code 8). Please try again or update Google Play Services."
            }
            CommonStatusCodes.SIGN_IN_REQUIRED -> {
                "Google account re-authentication required (Code 4). Verify your Google account in Phone Settings."
            }
            else -> {
                "Google Sign-In failed (Code $code: $codeName). ${e.message.orEmpty()}".trim()
            }
        }
    }

    private fun extractGoogleIdToken(credential: androidx.credentials.Credential): String? {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return null
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    private fun formatFirebaseAuthError(context: Context, e: FirebaseAuthException): String {
        if (isAppCheckError(e)) {
            return formatFirebaseErrorWithAppCheckHint(context, e, "Firebase App Check rejected sign-in.")
        }
        val raw = e.localizedMessage ?: e.message.orEmpty()
        return "Firebase Auth failed (${e.errorCode}): $raw"
    }

    private fun formatFirebaseThrowableError(context: Context, t: Throwable): String {
        if (isAppCheckError(t)) {
            return formatFirebaseErrorWithAppCheckHint(context, t, "Firebase App Check rejected sign-in.")
        }
        return t.message.orEmpty().ifBlank { "Google Sign-In failed." }
    }

    private suspend fun exchangeIdTokenWithFirebase(
        auth: FirebaseAuth,
        idToken: String
    ): CloudAuthUser? {
        val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(firebaseCred).await()
        val user: FirebaseUser = authResult.user ?: return null
        return CloudAuthUser(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName.orEmpty()
        )
    }

    suspend fun signOutGoogle(context: Context) {
        withContext(Dispatchers.IO + NonCancellable) {
            runCatching {
                FirebaseAuth.getInstance().signOut()
            }
            runCatching {
                val credContext: Context = context.findActivity() ?: context
                val cm = CredentialManager.create(credContext)
                cm.clearCredentialState(ClearCredentialStateRequest())
            }
            runCatching {
                val webClientId = resolveWebClientId(context)
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build()
                GoogleSignIn.getClient(context, gso).signOut().await()
            }
        }
    }
}
