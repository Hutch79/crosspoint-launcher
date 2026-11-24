package com.crosspoint.launcher

import android.app.Application
import com.crosspoint.launcher.util.LegacyTls

class CrossPointApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LegacyTls.install(this)
    }
}
