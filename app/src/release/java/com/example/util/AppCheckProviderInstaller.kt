package com.example.util

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal object AppCheckProviderInstaller {
    const val providerName: String = "PlayIntegrityAppCheckProviderFactory"

    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
