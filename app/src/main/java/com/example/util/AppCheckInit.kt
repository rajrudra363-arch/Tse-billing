package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import java.util.UUID

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

@Volatile
private var appCheckInstalled = false

@Volatile
private var installedDebugTokenOverride: String? = null

fun configureAppCheck(context: Context, intent: Intent? = null) {
    try {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }

        val intentDebugToken = if (BuildConfig.DEBUG) {
            intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val buildConfigDebugToken = if (BuildConfig.DEBUG) {
            BuildConfig.APPCHECK_DEBUG_TOKEN.trim().takeIf { it.isNotBlank() }
        } else {
            null
        }
        val overrideToken = intentDebugToken ?: buildConfigDebugToken

        if (BuildConfig.DEBUG) {
            runCatching {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = appContext.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                if (!overrideToken.isNullOrBlank()) {
                    val existing = prefs.getString(DEBUG_SECRET_KEY, null)
                    if (existing != overrideToken) {
                        prefs.edit()
                            .putString(DEBUG_SECRET_KEY, overrideToken)
                            .commit()
                        Log.d(TAG, "App Check debug secret configured synchronously.")
                    }
                } else if (prefs.getString(DEBUG_SECRET_KEY, null).isNullOrBlank()) {
                    val generated = UUID.randomUUID().toString()
                    prefs.edit()
                        .putString(DEBUG_SECRET_KEY, generated)
                        .commit()
                    Log.i(TAG, "Generated persistent App Check debug token for debug build: $generated")
                }
            }
        }

        val hasNewDebugOverride = BuildConfig.DEBUG &&
            !intentDebugToken.isNullOrBlank() &&
            intentDebugToken != installedDebugTokenOverride

        if (!appCheckInstalled || hasNewDebugOverride) {
            synchronized(AppCheckProviderInstaller) {
                val stillNeedsInstall = !appCheckInstalled ||
                    (BuildConfig.DEBUG && !intentDebugToken.isNullOrBlank() && intentDebugToken != installedDebugTokenOverride)
                if (stillNeedsInstall) {
                    val appCheck = FirebaseAppCheck.getInstance()
                    AppCheckProviderInstaller.install(appCheck)
                    appCheckInstalled = true
                    if (BuildConfig.DEBUG && !intentDebugToken.isNullOrBlank()) {
                        installedDebugTokenOverride = intentDebugToken
                    }
                    Log.d(TAG, "Installed Firebase App Check provider: ${AppCheckProviderInstaller.providerName}")
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to initialize Firebase App Check (non-fatal): ${e.message}")
    }
}

fun getLocalAppCheckDebugToken(context: Context): String? {
    if (!BuildConfig.DEBUG) return null
    val appContext = context.applicationContext ?: context
    return runCatching {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = appContext.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        prefs.getString(DEBUG_SECRET_KEY, null)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

fun isAppCheckError(throwable: Throwable?): Boolean {
    var cur: Throwable? = throwable
    while (cur != null) {
        val msg = cur.message.orEmpty()
        if (msg.contains("App Check", ignoreCase = true) ||
            msg.contains("appcheck", ignoreCase = true) ||
            msg.contains("app_check", ignoreCase = true) ||
            msg.contains("AppCheck", ignoreCase = false)
        ) {
            return true
        }
        cur = cur.cause
    }
    return false
}

fun formatFirebaseErrorWithAppCheckHint(
    context: Context,
    throwable: Throwable?,
    fallback: String
): String {
    if (isAppCheckError(throwable)) {
        if (BuildConfig.DEBUG) {
            val token = getLocalAppCheckDebugToken(context)
            return if (!token.isNullOrBlank()) {
                "Firebase App Check rejected this debug build (${throwable?.message.orEmpty()}). Register this Debug Token in Firebase Console -> App Check -> Apps -> Manage debug tokens: $token"
            } else {
                "Firebase App Check rejected this debug build (${throwable?.message.orEmpty()}). Register your DebugAppCheckProvider token in Firebase Console -> App Check -> Apps -> Manage debug tokens."
            }
        }
        return "Firebase App Check verification failed (${throwable?.message.orEmpty()}). Verify Play Integrity API is enabled and your release SHA-256 fingerprint is registered in Firebase Console -> Project Settings & App Check."
    }
    return throwable?.message?.takeIf { it.isNotBlank() } ?: fallback
}
