package com.example.data

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
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
            val webClientId = context.getString(R.string.default_web_client_id)
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()
            val req = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val cm = CredentialManager.create(context)
            val response = cm.getCredential(context, req)
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
            val webClientId = activityContext.getString(R.string.default_web_client_id)
            val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()
            val req = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val cm = CredentialManager.create(activityContext)
            val response = cm.getCredential(activityContext, req)
            val user = exchangeCredential(auth, response.credential)
                ?: return Result.failure(IllegalStateException("Google Sign-In did not return a valid ID token."))
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (_: GetCredentialCancellationException) {
            Result.failure(IllegalStateException("Google Sign-In cancelled."))
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
                val cm = CredentialManager.create(context)
                cm.clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }
}
