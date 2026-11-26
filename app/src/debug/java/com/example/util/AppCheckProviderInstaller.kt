package com.example.util

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object AppCheckProviderInstaller {
    const val providerName: String = "DebugAppCheckProviderFactory"

    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
