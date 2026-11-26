package com.example

import android.app.Application
import com.example.util.configureAppCheck

class TseBillingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        configureAppCheck(this)
    }
}
