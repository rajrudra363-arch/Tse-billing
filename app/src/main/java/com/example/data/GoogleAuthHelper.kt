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
        is ContextWrapper -> baseContext.findActivity()
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
            val credContext: Context = context.findActivity() ?: context
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
            val webClientId = resolveWebClientId(activityContext)
            val credContext: Context = activityContext.findActivity() ?: activityContext
            val cm = CredentialManager.create(credContext)

            val response = try {
                // Primary: Try GetGoogleIdOption (filterByAuthorizedAccounts = false)
                // This uses the native Credential Manager bottom sheet which works reliably across all OEMs (Xiaomi/MIUI/HyperOS, Samsung, OnePlus, etc.)
                // without launching external activities that trigger cancellation on MIUI/HyperOS task switching.
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()
                val req = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                cm.getCredential(credContext, req)
            } catch (firstErr: Exception) {
                if (firstErr is CancellationException) throw firstErr
                Log.w(TAG, "GetGoogleIdOption failed (${firstErr::class.simpleName}: ${firstErr.message}), falling back to GetSignInWithGoogleOption", firstErr)
                try {
                    val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
                        .build()
                    val fallbackReq = GetCredentialRequest.Builder()
                        .addCredentialOption(signInOption)
                        .build()
                    cm.getCredential(credContext, fallbackReq)
                } catch (secondErr: Exception) {
                    if (secondErr is CancellationException) throw secondErr
                    Log.e(TAG, "Both Google sign-in options failed. 1: ${firstErr.message}, 2: ${secondErr.message}", secondErr)
                    throw secondErr
                }
            }

            val user = exchangeCredential(auth, response.credential)
                ?: return Result.failure(IllegalStateException("Google Sign-In did not return a valid ID token."))
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            val msg = e.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: e.message.orEmpty()
            Log.w(TAG, "GetCredentialCancellationException: type=${e.type}, message=$msg", e)
            val detailedMsg = when {
                msg.contains("16", ignoreCase = true) || msg.contains("reauth", ignoreCase = true) ->
                    "Google account re-authentication required ([16]). Please re-verify your Google account in phone Settings."
                msg.contains("10", ignoreCase = true) || msg.contains("developer", ignoreCase = true) ->
                    "Google configuration mismatch (Code 10). Verify app SHA-1 fingerprint in Firebase Console."
                msg.contains("cancelled by the user", ignoreCase = true) ->
                    "Google Sign-In was cancelled by system/device ($msg). Please ensure Google Play Services has background permissions, or re-select account."
                msg.isNotBlank() ->
                    "Google Sign-In cancelled: $msg"
                else ->
                    "Google Sign-In cancelled."
            }
            Result.failure(IllegalStateException(detailedMsg))
        } catch (e: NoCredentialException) {
            Result.failure(
                IllegalStateException(
                    "No Google account credential found on this device. Please add a Google account in device Settings and try again."
                )
            )
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager error (${e.type}): ${e.message}", e)
            val errText = e.errorMessage?.toString()?.takeIf { it.isNotBlank() }
                ?: e.message?.takeIf { it.isNotBlank() }
                ?: "Google Sign-In failed (${e.type}). Check internet connection & Google Play Services."
            Result.failure(IllegalStateException(errText))
        } catch (t: Throwable) {
            Log.e(TAG, "Google Sign-In failed", t)
            Result.failure(Exception(t.message ?: "Google Sign-In failed. Ensure Google Play Services is available.", t))
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
