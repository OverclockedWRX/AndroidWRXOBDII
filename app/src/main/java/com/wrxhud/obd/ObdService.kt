package com.wrxhud.obd

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wrxhud.MainActivity
import com.wrxhud.R
import com.wrxhud.settings.SettingsRepository
import com.wrxhud.vehicle.AlarmEvaluator
import com.wrxhud.vehicle.VehicleInfo
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
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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
    private val scanExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "obd-scan").apply { isDaemon = true }
    }

    private lateinit var settingsRepository: SettingsRepository
    private var connection: ObdConnection? = null
    private var poller: ObdPoller? = null
    private var simJob: kotlinx.coroutines.Job? = null
    private var liveJob: kotlinx.coroutines.Job? = null

    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val _vehicleInfo = MutableStateFlow(VehicleInfo())
    val vehicleInfo: StateFlow<VehicleInfo> = _vehicleInfo.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _scanBusy = MutableStateFlow(false)
    val scanBusy: StateFlow<Boolean> = _scanBusy.asStateFlow()
    @Volatile private var scanning = false

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession(publishIdle = true)
                stopSelf()
            }
            ACTION_START, null -> {
                val useSim = intent?.getBooleanExtra(EXTRA_USE_SIMULATOR, false) == true
                startForegroundNotification(useSimulator = useSim)
                if (useSim) {
                    startSimulator()
                } else {
                    startLive()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopSession(publishIdle = false)
        scope.cancel()
        scanExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun startForegroundNotification(useSimulator: Boolean) {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.obd_notification_title))
            .setContentText(getString(R.string.obd_notification_text))
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
        // Simulator has no BT/USB device — connectedDevice can SecurityException on API 34+.
        val type = if (useSimulator) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground($type) failed; retrying specialUse", t)
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        }
    }

    private fun startSimulator() {
        stopSession(publishIdle = false)
        _status.value = "Simulator"
        simJob = scope.launch {
            // Re-check preference so a stale START cannot keep sim running after disable.
            val prefs = settingsRepository.preferencesFlow.first()
            if (!prefs.useSimulator) {
                _status.value = "Simulator disabled"
                publishPhoneOnlyIdle()
                return@launch
            }
            _vehicleInfo.value = VehicleInfoReader.simulatorPlaceholder()
            SimulatedObdSource(thresholds = prefs.toAlarmThresholds()).snapshots().collect { frame ->
                _snapshot.value = frame
            }
        }
    }

    private fun startLive() {
        stopSession(publishIdle = false)
        liveJob = scope.launch {
            // Never fall back to simulator when live mode is requested.
            val prefsAtStart = settingsRepository.preferencesFlow.first()
            if (prefsAtStart.useSimulator) {
                startSimulator()
                return@launch
            }
            publishPhoneOnlyIdle()
            try {
                if (ContextCompat.checkSelfPermission(
                        this@ObdService,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    _status.value = "Bluetooth permission required"
                    publishPhoneOnlyIdle()
                    return@launch
                }
                _status.value = "Connecting…"
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                    ?: run {
                        _status.value = "Bluetooth unavailable"
                        publishPhoneOnlyIdle()
                        return@launch
                    }
                if (!adapter.isEnabled) {
                    _status.value = "Bluetooth disabled"
                    publishPhoneOnlyIdle()
                    return@launch
                }
                val conn = ObdConnection(adapter)
                connection = conn
                val device = try {
                    conn.findMxPlus()
                } catch (se: SecurityException) {
                    _status.value = "Bluetooth permission required"
                    publishPhoneOnlyIdle()
                    return@launch
                } ?: run {
                    _status.value = "No OBDLink MX+ — GPS only"
                    publishPhoneOnlyIdle()
                    return@launch
                }
                conn.connect(device)
                _status.value = "Connected: ${device.name}"

                var latestPrefs = settingsRepository.preferencesFlow.first()
                val prefsWatcher = launch {
                    settingsRepository.preferencesFlow.collect { latestPrefs = it }
                }
                val pollerInstance = ObdPoller(
                    connection = conn,
                    scope = scope,
                    gearConfigProvider = {
                        val p = latestPrefs
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
                    thresholdsProvider = { latestPrefs.toAlarmThresholds() },
                    assignmentsProvider = { latestPrefs.pidAssignments }
                )
                poller = pollerInstance
                launch {
                    pollerInstance.vehicleInfo.collect { info ->
                        _vehicleInfo.value = info
                    }
                }
                launch {
                    try {
                        pollerInstance.snapshot.collect { frame ->
                            if (latestPrefs.useSimulator) return@collect
                            _snapshot.value = frame.copy(usingSimulator = false)
                        }
                    } finally {
                        prefsWatcher.cancel()
                    }
                }
                pollerInstance.start()
            } catch (t: Throwable) {
                Log.e(TAG, "Live OBD failed", t)
                _status.value = when (t) {
                    is SecurityException -> "Bluetooth permission required"
                    else -> "Error: ${t.message}"
                }
                publishPhoneOnlyIdle()
            }
        }
    }

    /**
     * Pause the live poller, scan supported PIDs / Mode 22 probes, then resume.
     * Returns empty list if not connected to a live adapter.
     */
    suspend fun scanPids(): List<PidScanRow> {
        val assignments = settingsRepository.preferencesFlow.first().pidAssignments
        return withPausedPoller(statusWhileBusy = "Scanning PIDs…") { conn ->
            PidScanner(conn, assignments).scan()
        }.orEmpty()
    }

    /** Read stored / pending / permanent DTCs (Modes 03 / 07 / 0A). */
    suspend fun readDtcs(): DtcReadResult {
        return withPausedPoller(statusWhileBusy = "Reading DTCs…") { conn ->
            DtcReader(conn).readAll()
        } ?: DtcReadResult(message = "Not connected — turn off simulator and link MX+")
    }

    /**
     * Clear emission DTCs (Mode 04). Caller must confirm with the user first.
     * @return true when the adapter accepted the clear
     */
    suspend fun clearDtcs(): Boolean {
        return withPausedPoller(statusWhileBusy = "Clearing DTCs…") { conn ->
            DtcReader(conn).clearCodes()
        } == true
    }

    /** Re-fetch VIN / CALID / adapter identity while connected. */
    suspend fun refreshVehicleInfo(): VehicleInfo {
        val p = poller
        if (p == null) {
            return _vehicleInfo.value
        }
        return withPausedPoller(statusWhileBusy = "Reading vehicle info…") {
            p.refreshVehicleInfo()
            p.vehicleInfo.value
        } ?: _vehicleInfo.value
    }

    private suspend fun <T> withPausedPoller(
        statusWhileBusy: String,
        block: (ObdConnection) -> T
    ): T? {
        val conn = connection
        val p = poller
        if (conn == null || !conn.isOpen || p == null) {
            return null
        }
        if (scanning) return null
        scanning = true
        _scanBusy.value = true
        val previousStatus = _status.value
        _status.value = statusWhileBusy
        return try {
            withContext(Dispatchers.IO) {
                suspendCoroutine { cont ->
                    scanExecutor.execute {
                        try {
                            p.pause()
                            Thread.sleep(80)
                            cont.resume(block(conn))
                        } catch (t: Throwable) {
                            cont.resumeWithException(t)
                        } finally {
                            p.resume()
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, statusWhileBusy, t)
            null
        } finally {
            scanning = false
            _scanBusy.value = false
            _status.value = previousStatus
        }
    }

    fun isLiveConnected(): Boolean = connection?.isOpen == true && poller != null

    private fun stopSession(publishIdle: Boolean) {
        simJob?.cancel()
        simJob = null
        liveJob?.cancel()
        liveJob = null
        poller?.stop()
        poller = null
        connection?.close()
        connection = null
        _vehicleInfo.value = VehicleInfo()
        if (publishIdle) {
            _status.value = "Stopped"
            publishPhoneOnlyIdle()
        }
    }

    private fun publishPhoneOnlyIdle() {
        val gpsSpeed = _snapshot.value.gpsSpeedMps
        val gpsFix = _snapshot.value.gpsHasFix
        _snapshot.value = VehicleSnapshot(
            timestampMs = System.currentTimeMillis(),
            gpsSpeedMps = gpsSpeed,
            gpsHasFix = gpsFix,
            obdConnected = false,
            usingSimulator = false,
            alarms = AlarmEvaluator.evaluate(
                VehicleSnapshot(gpsSpeedMps = gpsSpeed, gpsHasFix = gpsFix),
                WrxProfile.AlarmThresholds(),
                expectObd = true
            )
        )
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
}
