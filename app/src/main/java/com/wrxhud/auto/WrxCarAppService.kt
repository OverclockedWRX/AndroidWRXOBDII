package com.wrxhud.auto

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
/**
 * Android Auto entry point. Hosts the race instrument / HUD display.
 * (Google Maps on Android Auto handles routing separately).
 *
 * Real vehicles only list this when the APK came from a trusted source
 * (Play Internal Testing / Internal App Sharing) — not from adb alone.
 */
class WrxCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return if (debuggable) {
            // DHU + debug hosts; also needed for Internal App Sharing of debug builds.
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session = WrxCarSession()
}
