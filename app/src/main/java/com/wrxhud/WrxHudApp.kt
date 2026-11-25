package com.wrxhud

import android.app.Application
import com.wrxhud.telemetry.TelemetryRepository

class WrxHudApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TelemetryRepository.init(this)
    }
}
