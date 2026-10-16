package com.wrxhud.obd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wrxhud.MainActivity
import com.wrxhud.R
import com.wrxhud.settings.SettingsRepository
import com.wrxhud.vehicle.VehicleSnapshot
import com.wrxhud.vehicle.WrxProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ObdService : Service() {
    companion object {
        private const val TAG = "ObdService"
        private const val CHANNEL_ID = "obd_link"
        private const val NOTIFICATION_ID = 42
        const val ACTION_START = "com.wrxhud.obd.START"
        const val ACTION_STOP = "com.wrxhud.obd.STOP"
        const val EXTRA_USE_SIMULATOR = "use_simulator"
    }

    inner class LocalBinder : Binder() {
        fun getService(): ObdService = this@ObdService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var settingsRepository: SettingsRepository
    private var connection: ObdConnection? = null
    private var poller: ObdPoller? = null
    private var simJob: kotlinx.coroutines.Job? = null

    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession()
                stopSelf()
            }
            ACTION_START, null -> {
                val useSim = intent?.getBooleanExtra(EXTRA_USE_SIMULATOR, false) == true
                startForegroundNotification()
                if (useSim) startSimulator() else startLive()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopSession()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.obd_notification_title))
            .setContentText(getString(R.string.obd_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
    }

    private fun startSimulator() {
        stopSession()
        _status.value = "Simulator"
        simJob = scope.launch {
            SimulatedObdSource().snapshots().collect { frame ->
                _snapshot.value = frame
            }
        }
    }

    private fun startLive() {
        stopSession()
        scope.launch {
            try {
                _status.value = "Connecting…"
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                    ?: run {
                        _status.value = "Bluetooth unavailable"
                        return@launch
                    }
                if (!adapter.isEnabled) {
                    _status.value = "Bluetooth disabled"
                    return@launch
                }
                val conn = ObdConnection(adapter)
                connection = conn
                val device = conn.findMxPlus()
                    ?: run {
                        _status.value = "Pair OBDLink MX+ in Android settings"
                        return@launch
                    }
                conn.connect(device)
                _status.value = "Connected: ${device.name}"

                val prefs = settingsRepository.preferencesFlow.first()
                val pollerInstance = ObdPoller(
                    connection = conn,
                    scope = scope,
                    gearConfigProvider = {
                        val p = prefs
                        ObdPoller.GearConfig(
                            transmission = if (p.useSptCvt) WrxProfile.sptCvt else WrxProfile.manual6,
                            tireCircumferenceM = WrxProfile.tireCircumferenceMeters(
                                p.tireWidthMm,
                                p.tireAspect,
                                p.wheelDiameterIn
                            )
                        )
                    },
                    gpsSpeedProvider = {
                        val s = _snapshot.value
                        s.gpsSpeedMps to s.gpsHasFix
                    },
                    thresholdsProvider = { prefs.toAlarmThresholds() }
                )
                poller = pollerInstance
                scope.launch {
                    pollerInstance.snapshot.collect { _snapshot.value = it }
                }
                pollerInstance.start()
            } catch (t: Throwable) {
                Log.e(TAG, "Live OBD failed", t)
                _status.value = "Error: ${t.message}"
                _snapshot.updateConnected(false)
            }
        }
    }

    private fun stopSession() {
        simJob?.cancel()
        simJob = null
        poller?.stop()
        poller = null
        connection?.close()
        connection = null
        _status.value = "Stopped"
        _snapshot.updateConnected(false)
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.obd_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun MutableStateFlow<VehicleSnapshot>.updateConnected(connected: Boolean) {
        value = value.copy(obdConnected = connected)
    }
}
