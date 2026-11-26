package com.example.util

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException

/**
 * Genuine FIDO2/WebAuthn Passkey helper powered by Jetpack Credential Manager.
 * Always pairs with Cloud Functions challenge & cryptographic public-key verification;
 * never stores private keys in Firestore and never treats local biometric alone as server login.
 */
object PasskeyAuthHelper {
    suspend fun createPasskey(
        context: Context,
        requestJson: String
    ): Result<String> = runCatching {
        val manager = CredentialManager.create(context)
        val request = CreatePublicKeyCredentialRequest(requestJson)
        val result = manager.createCredential(context, request)
        val pkRes = result as? CreatePublicKeyCredentialResponse
            ?: error("Unexpected credential response type.")
        pkRes.registrationResponseJson
    }.recoverCatching { err ->
        val friendly = when (err) {
            is CreateCredentialCancellationException -> "Passkey setup cancelled."
            is CreateCredentialException -> err.errorMessage?.toString()
                ?: "Passkey creation failed (${err.type}). Ensure screen lock/Google Password Manager is active."
            else -> err.message ?: "Passkey creation failed."
        }
        error(friendly)
    }

    suspend fun getPasskeyAssertion(
        context: Context,
        requestJson: String
    ): Result<String> = runCatching {
        val manager = CredentialManager.create(context)
        val option = GetPublicKeyCredentialOption(requestJson)
        val request = GetCredentialRequest(listOf(option))
        val result = manager.getCredential(context, request)
        val cred = result.credential as? PublicKeyCredential
            ?: error("Selected credential is not a WebAuthn Passkey.")
        cred.authenticationResponseJson
    }.recoverCatching { err ->
        val friendly = when (err) {
            is GetCredentialCancellationException -> "Passkey verification cancelled."
            is NoCredentialException -> "No registered Passkey found on this device for your account. Sign in with password or register a Passkey first."
            is GetCredentialException -> err.errorMessage?.toString()
                ?: "Passkey authentication failed (${err.type})."
            else -> err.message ?: "Passkey authentication failed."
        }
        error(friendly)
    }
}
