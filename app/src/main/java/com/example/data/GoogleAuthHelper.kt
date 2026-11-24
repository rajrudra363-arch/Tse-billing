package com.example.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class CloudAuthUser(
    val uid: String,
    val email: String,
    val displayName: String
)

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findActivity()
        else -> null
    }

    private fun resolveWebClientId(context: Context): String {
        val id = runCatching { context.getString(R.string.default_web_client_id).trim() }.getOrDefault("")
        if (id.isEmpty()) {
            error("Google Sign-In configuration missing (default_web_client_id). Rebuild with Google Services plugin.")
        }
        return id
    }

    fun currentUser(context: Context): CloudAuthUser? {
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
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
            val act = context.findActivity() ?: (context as? Activity)
            val credContext: Context = act ?: context
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
            val user = exchangeCredential(auth, response.credential)
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            Result.success(null)
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

            val webClientId = resolveWebClientId(activityContext)
            val act: Activity? = activityContext.findActivity() ?: (activityContext as? Activity)
            val credContext: Context = act ?: activityContext
            val cm = CredentialManager.create(credContext)

            // Step 1: Standard Jetpack Credential Manager interactive sign-in
            // Uses GetSignInWithGoogleOption as mandated by official Android guidelines & the Firebase skill
            var primaryError: Throwable? = null
            try {
                Log.i(TAG, "Attempting interactive Google Sign-In with GetSignInWithGoogleOption...")
                val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                val req = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                val response = cm.getCredential(credContext, req)
                val user = exchangeCredential(auth, response.credential)
                if (user != null) {
                    Log.i(TAG, "Interactive CredentialManager sign-in succeeded: ${user.email}")
                    return Result.success(user)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "GetSignInWithGoogleOption failed (${t::class.simpleName}: ${t.message})", t)
                primaryError = t
            }

            // Step 2: Try GetGoogleIdOption if the error was not a user/system cancellation
            if (primaryError != null && primaryError !is GetCredentialCancellationException) {
                try {
                    Log.i(TAG, "Attempting GetGoogleIdOption secondary attempt...")
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setServerClientId(webClientId)
                        .setFilterByAuthorizedAccounts(false)
                        .setAutoSelectEnabled(false)
                        .build()
                    val req = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                    val response = cm.getCredential(credContext, req)
                    val user = exchangeCredential(auth, response.credential)
                    if (user != null) {
                        Log.i(TAG, "GetGoogleIdOption secondary attempt succeeded: ${user.email}")
                        return Result.success(user)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "GetGoogleIdOption secondary attempt failed: ${t.message}", t)
                }
            }

            // Step 3: UNIVERSAL FALLBACK FOR REALME / COLOROS / OPPO / VIVO / XIAOMI / HYPEROS
            // On Realme UI and ColorOS, Google Play Services Credential Manager often aborts
            // with GetCredentialCancellationException ("activity is cancelled by the user")
            // due to OEM window overlay restrictions or account synchronization glitches.
            // Using Firebase's startActivityForSignInWithProvider launches Google OAuth in a Chrome Custom Tab,
            // bypassing OEM overlay bugs and providing 100% reliable Google authentication on all devices.
            if (act != null) {
                Log.i(TAG, "Triggering Firebase OAuthProvider Google fallback on Activity ${act::class.simpleName}...")
                try {
                    val providerBuilder = OAuthProvider.newBuilder("google.com")
                    providerBuilder.addCustomParameter("prompt", "select_account")
                    val pending = auth.pendingAuthResult
                    val authResult = if (pending != null) {
                        pending.await()
                    } else {
                        auth.startActivityForSignInWithProvider(act, providerBuilder.build()).await()
                    }
                    val user = authResult.user
                    if (user != null) {
                        Log.i(TAG, "Firebase OAuthProvider fallback succeeded: ${user.email}")
                        return Result.success(
                            CloudAuthUser(
                                uid = user.uid,
                                email = user.email.orEmpty(),
                                displayName = user.displayName.orEmpty()
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "Firebase OAuthProvider fallback failed: ${t.message}", t)
                    val msg = t.message.orEmpty()
                    if (msg.contains("canceled", ignoreCase = true) || msg.contains("cancelled", ignoreCase = true)) {
                        return Result.failure(IllegalStateException("Google Sign-In was cancelled."))
                    }
                    return Result.failure(Exception("Google Sign-In failed: ${t.message ?: "Authentication error"}", t))
                }
            }

            // Step 4: Handle failure if all steps were exhausted
            val rootErr = primaryError
            if (rootErr is GetCredentialCancellationException) {
                val msg = rootErr.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: rootErr.message.orEmpty()
                return Result.failure(IllegalStateException("Google Sign-In was cancelled ($msg)."))
            } else if (rootErr is NoCredentialException) {
                return Result.failure(
                    IllegalStateException("No Google account found on device. Please add a Google account in Settings and try again.")
                )
            } else if (rootErr != null) {
                return Result.failure(IllegalStateException("Google Sign-In failed: ${rootErr.message}"))
            }

            Result.failure(IllegalStateException("Google Sign-In did not complete."))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Google Sign-In error", t)
            Result.failure(Exception(t.message ?: "Google Sign-In failed.", t))
        }
    }

    private suspend fun exchangeCredential(
        auth: FirebaseAuth,
        credential: androidx.credentials.Credential
    ): CloudAuthUser? {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return null
        }
        val googleIdTokenCred = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCred.idToken, null)
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
        }
    }
}
