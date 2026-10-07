package com.localstream.app

import android.app.Application
import com.localstream.app.data.Library
import com.localstream.app.data.Settings

class LocalStreamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        Settings.init(this)
        Library.init(this)
    }
}
