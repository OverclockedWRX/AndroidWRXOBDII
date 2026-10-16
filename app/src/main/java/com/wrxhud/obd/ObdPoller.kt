package com.wrxhud.obd

import android.util.Log
import com.wrxhud.vehicle.AlarmEvaluator
import com.wrxhud.vehicle.GearCalculator
import com.wrxhud.vehicle.UnitConverters
import com.wrxhud.vehicle.VehicleSnapshot
import com.wrxhud.vehicle.WrxProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-threaded OBD reader: RPM every cycle, slower PIDs round-robined.
 */
class ObdPoller(
    private val connection: ObdConnection,
    private val scope: CoroutineScope,
    private val gearConfigProvider: () -> GearConfig,
    private val gpsSpeedProvider: () -> Pair<Float?, Boolean>,
    private val thresholdsProvider: () -> WrxProfile.AlarmThresholds
) {
    data class GearConfig(
        val transmission: WrxProfile.TransmissionProfile,
        val tireCircumferenceM: Double
    )

    companion object {
        private const val TAG = "ObdPoller"
        private val ECU_HEADER = 0x7E0
    }

    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "obd-poller").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val running = AtomicBoolean(false)
    private var job: Job? = null

    private val supportedPids = mutableSetOf<Int>()
    private var mapKpa: Float? = null
    private var baroKpa: Float? = null
    private var oilTempMode22Did: Int? = null
    private var useMode01OilTemp: Boolean = false
    private var oilTempSourceLabel: String = "none"
    private var odometerSupported: Boolean = false

    private data class SlowPid(
        val name: String,
        val request: () -> Unit
    )

    fun start() {
        if (!running.compareAndSet(false, true)) return
        job = scope.launch(dispatcher) {
            try {
                discoverCapabilities()
                var slowIndex = 0
                val slowPids = buildSlowQueue()
                while (isActive && running.get() && connection.isOpen) {
                    pollRpm()
                    if (slowPids.isNotEmpty()) {
                        slowPids[slowIndex % slowPids.size].request()
                        slowIndex++
                    }
                    publishAlarms()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Poller failed", t)
                _snapshot.update { it.copy(obdConnected = false) }
            } finally {
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        job?.cancel()
        job = null
    }

    private fun discoverCapabilities() {
        supportedPids.clear()
        // Bitmasks: 0x00 → PIDs 01-20/32, 0x20 → 21-40, 0x40 → 41-60
        fun mergeSupport(basePid: Int, offset: Int) {
            val raw = connection.sendCommand(ObdCommands.mode01(basePid))
            val data = StnParser.parseMode01(raw, basePid) ?: return
            StnParser.decodeSupportedPidBitmask(data).forEach { bit ->
                supportedPids.add(bit + offset)
            }
        }
        mergeSupport(0x00, 0)
        if (supportedPids.contains(0x20)) mergeSupport(0x20, 0x20)
        if (supportedPids.contains(0x40)) mergeSupport(0x40, 0x40)
        if (supportedPids.contains(0x60)) mergeSupport(0x60, 0x60)
        if (supportedPids.contains(0x80)) mergeSupport(0x80, 0x80)
        if (supportedPids.contains(0xA0)) mergeSupport(0xA0, 0xA0)
        Log.i(TAG, "Supported PIDs: $supportedPids")

        discoverOilTemp()
        discoverOdometer()
    }

    private fun discoverOdometer() {
        odometerSupported = false
        val advertised = supportedPids.contains(WrxProfile.Pid.ODOMETER)
        // Always try a live read once — some ECUs omit the bitmask but still answer.
        val raw = connection.sendCommand(ObdCommands.mode01(WrxProfile.Pid.ODOMETER))
        val data = StnParser.parseMode01(raw, WrxProfile.Pid.ODOMETER)
        val km = data?.let { StnParser.decodeOdometerKm(it) }
        if (km != null) {
            odometerSupported = true
            _snapshot.update { it.copy(odometerKm = km) }
            Log.i(TAG, "Odometer confirmed via 01 A6 = $km km (advertised=$advertised)")
        } else {
            Log.w(TAG, "Odometer 01 A6 not available (advertised=$advertised, raw=${StnParser.normalize(raw)})")
        }
    }

    /**
     * Confirm oil temp by trying known sources and accepting the first plausible reading.
     * Order: SAE 01 5C → Mode 22 candidates under 7E0.
     */
    private fun discoverOilTemp() {
        useMode01OilTemp = false
        oilTempMode22Did = null
        oilTempSourceLabel = "none"

        // Prefer standard oil-temp PID when advertised or when a live read works.
        val tryMode01 = supportedPids.contains(WrxProfile.Pid.OIL_TEMP) || supportedPids.isEmpty()
        if (tryMode01) {
            val raw = connection.sendCommand(ObdCommands.mode01(WrxProfile.Pid.OIL_TEMP))
            val data = StnParser.parseMode01(raw, WrxProfile.Pid.OIL_TEMP)
            val temp = data?.let { StnParser.decodeTempC(it) }
            if (temp != null && temp in -20f..160f) {
                useMode01OilTemp = true
                oilTempSourceLabel = "01 5C"
                _snapshot.update { it.copy(oilTempC = temp, oilTempSource = oilTempSourceLabel) }
                Log.i(TAG, "Oil temp confirmed via Mode 01 PID 5C = $temp C")
                return
            }
        }

        connection.sendCommand(ObdCommands.setHeader(ECU_HEADER))
        for (candidate in WrxProfile.oilTempCandidates) {
            val raw = connection.sendCommand(ObdCommands.mode22(candidate.did))
            Log.i(TAG, "Oil temp probe 22 ${"%04X".format(candidate.did)} -> ${StnParser.normalize(raw)}")
            val data = StnParser.parseMode22(raw, candidate.did) ?: continue
            val value = candidate.scale(data) ?: continue
            if (!candidate.plausible(value)) continue
            oilTempMode22Did = candidate.did
            oilTempSourceLabel = "22 ${"%04X".format(candidate.did)}"
            _snapshot.update { it.copy(oilTempC = value, oilTempSource = oilTempSourceLabel) }
            Log.i(TAG, "Oil temp confirmed via $oilTempSourceLabel = $value C")
            break
        }
        connection.sendCommand("ATSH7DF")

        if (oilTempMode22Did == null && !useMode01OilTemp) {
            Log.w(TAG, "Oil temp: no DID confirmed (ECU did not return a plausible value)")
            _snapshot.update { it.copy(oilTempSource = "none") }
        }
    }

    private fun buildSlowQueue(): List<SlowPid> {
        val list = mutableListOf<SlowPid>()
        list += SlowPid("coolant") { pollMode01(WrxProfile.Pid.COOLANT) { data ->
            _snapshot.update { it.copy(coolantC = StnParser.decodeTempC(data)) }
        } }
        list += SlowPid("iat") { pollMode01(WrxProfile.Pid.IAT) { data ->
            _snapshot.update { it.copy(intakeAirTempC = StnParser.decodeTempC(data)) }
        } }
        list += SlowPid("map") { pollMode01(WrxProfile.Pid.MAP) { data ->
            mapKpa = StnParser.decodePressureKpa(data)
            updateBoost()
        } }
        list += SlowPid("baro") { pollMode01(WrxProfile.Pid.BARO) { data ->
            baroKpa = StnParser.decodePressureKpa(data)
            updateBoost()
        } }
        list += SlowPid("speed") { pollMode01(WrxProfile.Pid.SPEED) { data ->
            val kph = StnParser.decodeSpeedKph(data)
            _snapshot.update { it.copy(obdSpeedKph = kph) }
            updateGear()
        } }
        list += SlowPid("mil") { pollMode01(WrxProfile.Pid.MONITOR_STATUS) { data ->
            _snapshot.update { it.copy(milOn = StnParser.decodeMil(data)) }
        } }
        list += SlowPid("voltage") { pollMode01(WrxProfile.Pid.CONTROL_MODULE_VOLTAGE) { data ->
            _snapshot.update { it.copy(batteryVolts = StnParser.decodeVoltage(data)) }
        } }
        if (supportedPids.contains(WrxProfile.Pid.FUEL_LEVEL) || supportedPids.isEmpty()) {
            list += SlowPid("fuel") { pollMode01(WrxProfile.Pid.FUEL_LEVEL) { data ->
                _snapshot.update { it.copy(fuelLevelPercent = StnParser.decodePercent(data)) }
            } }
        }
        if (odometerSupported) {
            list += SlowPid("odometer") {
                pollMode01(WrxProfile.Pid.ODOMETER) { data ->
                    StnParser.decodeOdometerKm(data)?.let { km ->
                        _snapshot.update { it.copy(odometerKm = km) }
                    }
                }
            }
        }
        if (useMode01OilTemp) {
            list += SlowPid("oilTemp") {
                pollMode01(WrxProfile.Pid.OIL_TEMP) { data ->
                    val temp = StnParser.decodeTempC(data)
                    if (temp != null && temp in -20f..160f) {
                        _snapshot.update {
                            it.copy(oilTempC = temp, oilTempSource = oilTempSourceLabel)
                        }
                    }
                }
            }
        } else {
            oilTempMode22Did?.let { did ->
                list += SlowPid("oilTemp") {
                    pollMode22(did) { data ->
                        val candidate = WrxProfile.oilTempCandidates.first { it.did == did }
                        val value = candidate.scale(data)
                        if (value != null && candidate.plausible(value)) {
                            _snapshot.update {
                                it.copy(oilTempC = value, oilTempSource = oilTempSourceLabel)
                            }
                        }
                    }
                }
            }
        }
        return list
    }

    private fun pollRpm() {
        val raw = connection.sendCommand(ObdCommands.mode01(WrxProfile.Pid.RPM))
        val data = StnParser.parseMode01(raw, WrxProfile.Pid.RPM) ?: return
        val rpm = StnParser.decodeRpm(data) ?: return
        val now = System.currentTimeMillis()
        _snapshot.update { current ->
            current.copy(
                timestampMs = now,
                previousRpm = current.rpm,
                rpm = rpm,
                rpmSampleAgeMs = if (current.timestampMs == 0L) 0 else (now - current.timestampMs),
                obdConnected = true,
                usingSimulator = false
            )
        }
        updateGear()
    }

    private fun pollMode01(pid: Int, onData: (ByteArray) -> Unit) {
        val raw = connection.sendCommand(ObdCommands.mode01(pid))
        val data = StnParser.parseMode01(raw, pid) ?: return
        onData(data)
    }

    private fun pollMode22(did: Int, onData: (ByteArray) -> Unit) {
        connection.sendCommand(ObdCommands.setHeader(ECU_HEADER))
        val raw = connection.sendCommand(ObdCommands.mode22(did))
        connection.sendCommand("ATSH7DF")
        val data = StnParser.parseMode22(raw, did) ?: return
        onData(data)
    }

    private fun updateBoost() {
        val map = mapKpa ?: return
        val baro = baroKpa ?: 101f
        val boostKpa = map - baro
        _snapshot.update { it.copy(boostPsi = UnitConverters.kpaToPsi(boostKpa)) }
    }

    private fun updateGear() {
        val rpm = _snapshot.value.rpm?.toDouble() ?: return
        val kph = _snapshot.value.obdSpeedKph ?: return
        val speedMps = UnitConverters.kphToMps(kph).toDouble()
        val cfg = gearConfigProvider()
        val gear = GearCalculator.estimateGear(
            rpm = rpm,
            speedMps = speedMps,
            transmission = cfg.transmission,
            tireCircumferenceM = cfg.tireCircumferenceM
        )
        val (gpsSpeed, gpsFix) = gpsSpeedProvider()
        _snapshot.update {
            it.copy(
                gear = gear,
                gpsSpeedMps = gpsSpeed,
                gpsHasFix = gpsFix
            )
        }
    }

    private fun publishAlarms() {
        val (gpsSpeed, gpsFix) = gpsSpeedProvider()
        _snapshot.update { current ->
            val withGps = current.copy(gpsSpeedMps = gpsSpeed, gpsHasFix = gpsFix)
            withGps.copy(alarms = AlarmEvaluator.evaluate(withGps, thresholdsProvider(), expectObd = true))
        }
    }
}
