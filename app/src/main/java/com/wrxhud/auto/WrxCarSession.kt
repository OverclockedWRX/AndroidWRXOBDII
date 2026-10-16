package com.wrxhud.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class WrxCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return HudCarScreen(carContext)
    }
}
