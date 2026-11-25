package com.wrxhud.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.wrxhud.telemetry.TelemetryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Android Auto instrument display: key gauges only. Reads the process-wide
 * [TelemetryRepository] (fed by the service), so it keeps updating with the phone UI closed.
 */
class HudCarScreen(carContext: CarContext) : Screen(carContext) {
    private var collectJob: Job? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                collectJob = lifecycleScope.launch {
                    combine(TelemetryRepository.snapshot, TelemetryRepository.preferences) { snap, prefs ->
                        CarRows.build(snap, prefs)
                    }.carRefresh().collect { invalidate() }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                collectJob?.cancel()
                collectJob = null
            }
        })
    }

    override fun onGetTemplate(): Template {
        val rows = CarRows.build(TelemetryRepository.snapshot.value, TelemetryRepository.preferences.value)
        val paneBuilder = Pane.Builder()
        rows.forEach { paneBuilder.addRow(Row.Builder().setTitle(it.title).addText(it.text).build()) }
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("WRX Race HUD")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
