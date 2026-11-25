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
import com.wrxhud.telemetry.TelemetryRepository
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    /** Serialises all use of the adapter: poller (per cycle), PID scan, DTC, VIN. */
    private val connectionLock = Mutex()

    private lateinit var settingsRepository: SettingsRepository
    private var connection: ObdConnection? = null
    private var poller: ObdPoller? = null
    private var simJob: kotlinx.coroutines.Job? = null
    private var liveJob: kotlinx.coroutines.Job? = null

    private val repo = TelemetryRepository

    private val _scanBusy = MutableStateFlow(false)
    val scanBusy: StateFlow<Boolean> = _scanBusy.asStateFlow()

    private fun setStatus(text: String) = repo.publishObdStatus(text)

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
        repo.dataLog.stop()
        scope.cancel()
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
        val types = if (useSimulator) {
            listOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // location keeps GPS updates flowing for Android Auto with the phone UI closed.
            listOf(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        }
        var lastError: Throwable? = null
        for (type in types) {
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
                return
            } catch (t: Throwable) {
                lastError = t
                Log.e(TAG, "startForeground($type) failed; trying fallback type", t)
            }
        }
        throw lastError ?: IllegalStateException("startForeground failed")
    }

    private fun startSimulator() {
        stopSession(publishIdle = false)
        setStatus("Simulator")
        simJob = scope.launch {
            // Re-check preference so a stale START cannot keep sim running after disable.
            val prefs = settingsRepository.preferencesFlow.first()
            if (!prefs.useSimulator) {
                setStatus("Simulator disabled")
                publishPhoneOnlyIdle()
                return@launch
            }
            repo.publishVehicleInfo(VehicleInfoReader.simulatorPlaceholder())
            SimulatedObdSource(thresholds = prefs.toAlarmThresholds()).snapshots().collect { frame ->
                repo.publishObd(frame)
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
            setStatus("Connecting…")
            ReconnectLoop(onStatus = { text ->
                setStatus(text)
                publishPhoneOnlyIdle()
            }).run { markConnected ->
                try {
                    liveSession(markConnected)
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.e(TAG, "Live OBD failed", t)
                    if (t is SecurityException) {
                        setStatus("Bluetooth permission required")
                        publishPhoneOnlyIdle()
                        SessionEnd.GIVE_UP
                    } else {
                        SessionEnd.RETRY
                    }
                }
            }
        }
    }

    /** One connect-and-poll session using the existing connect/close code. */
    private suspend fun liveSession(markConnected: () -> Unit): SessionEnd {
        if (settingsRepository.preferencesFlow.first().useSimulator) {
            // Simulator switched on: stop reconnecting and hand over.
            startSimulator()
            return SessionEnd.GIVE_UP
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            setStatus("Bluetooth permission required")
            publishPhoneOnlyIdle()
            return SessionEnd.GIVE_UP
        }
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null) {
            setStatus("Bluetooth unavailable")
            publishPhoneOnlyIdle()
            return SessionEnd.GIVE_UP
        }
        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth disabled; will retry")
            return SessionEnd.RETRY
        }
        val conn = ObdConnection(adapter)
        connection = conn
        val device = try {
            conn.findMxPlus()
        } catch (se: SecurityException) {
            setStatus("Bluetooth permission required")
            publishPhoneOnlyIdle()
            return SessionEnd.GIVE_UP
        }
        if (device == null) {
            setStatus("No OBDLink MX+ — GPS only")
            publishPhoneOnlyIdle()
            return SessionEnd.GIVE_UP
        }
        try {
            withContext(Dispatchers.IO) { conn.connect(device) }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            conn.close()
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "Connect failed", t)
            conn.close()
            if (connection === conn) connection = null
            return SessionEnd.RETRY
        }
        setStatus("Connected: ${device.name}")
        markConnected()

        return coroutineScope {
            var latestPrefs = settingsRepository.preferencesFlow.first()
            val prefsWatcher = launch {
                settingsRepository.preferencesFlow.collect { latestPrefs = it }
            }
            val pollerInstance = ObdPoller(
                connection = ObdConnectionLink(conn),
                scope = scope,
                connectionLock = connectionLock,
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
                    val g = repo.gps.value
                    g.speedMps to g.hasFix
                },
                thresholdsProvider = { latestPrefs.toAlarmThresholds() },
                assignmentsProvider = { latestPrefs.pidAssignments },
                pollingModeProvider = { latestPrefs.pollingMode }
            )
            poller = pollerInstance
            val infoJob = launch {
                pollerInstance.vehicleInfo.collect { info -> repo.publishVehicleInfo(info) }
            }
            val statsJob = launch {
                pollerInstance.pollStats.collect { repo.publishPollStats(it) }
            }
            val frameJob = launch {
                pollerInstance.snapshot.collect { frame ->
                    if (latestPrefs.useSimulator) return@collect
                    repo.publishObd(frame.copy(usingSimulator = false))
                }
            }
            val pollJob = pollerInstance.start()
            try {
                pollJob.join()
            } finally {
                frameJob.cancel()
                statsJob.cancel()
                repo.publishPollStats(PollStats())
                infoJob.cancel()
                prefsWatcher.cancel()
                pollerInstance.stop()
                if (poller === pollerInstance) poller = null
                conn.close()
                if (connection === conn) connection = null
            }
            SessionEnd.RETRY
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
            return repo.rawVehicleInfo.value
        }
        return withPausedPoller(statusWhileBusy = "Reading vehicle info…") {
            p.refreshVehicleInfo()
            p.vehicleInfo.value
        } ?: repo.rawVehicleInfo.value
    }

    /** Runs [block] with exclusive use of the adapter (poller waits on the same lock). */
    private suspend fun <T> withPausedPoller(
        statusWhileBusy: String,
        block: (ObdConnection) -> T
    ): T? {
        val conn = connection
        if (conn == null || !conn.isOpen || poller == null) {
            return null
        }
        if (!_scanBusy.compareAndSet(false, true)) return null
        val previousStatus = repo.obdStatus.value
        setStatus(statusWhileBusy)
        return try {
            withContext(Dispatchers.IO) {
                connectionLock.withLock { block(conn) }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, statusWhileBusy, t)
            null
        } finally {
            _scanBusy.value = false
            setStatus(previousStatus)
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
        repo.publishVehicleInfo(VehicleInfo())
        if (publishIdle) {
            setStatus("Stopped")
            publishPhoneOnlyIdle()
        }
    }

    private fun publishPhoneOnlyIdle() {
        val gps = repo.gps.value
        repo.publishObd(
            VehicleSnapshot(
                timestampMs = System.currentTimeMillis(),
                gpsSpeedMps = gps.speedMps,
                gpsHasFix = gps.hasFix,
                obdConnected = false,
                usingSimulator = false,
                alarms = AlarmEvaluator.evaluate(
                    VehicleSnapshot(gpsSpeedMps = gps.speedMps, gpsHasFix = gps.hasFix),
                    WrxProfile.AlarmThresholds(),
                    expectObd = true
                )
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
