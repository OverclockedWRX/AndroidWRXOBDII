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

    private var supportedPids: Set<Int> = emptySet()
    private var mapKpa: Float? = null
    private var baroKpa: Float? = null
    private var confirmedOilTempDid: Int? = null
    private var confirmedOilSwitchDid: Int? = null

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
        // Supported PID bitmask 01..20
        val supportRaw = connection.sendCommand(ObdCommands.mode01(0x00))
        val supportData = StnParser.parseMode01(supportRaw, 0x00)
        supportedPids = supportData?.let { StnParser.decodeSupportedPidBitmask(it) }.orEmpty()
        Log.i(TAG, "Supported PIDs (1-32): $supportedPids")

        // Probe Mode 22 oil temp candidates (header 7E0)
        connection.sendCommand(ObdCommands.setHeader(ECU_HEADER))
        for (candidate in WrxProfile.oilTempCandidates) {
            if (!candidate.confirmed) continue
            val raw = connection.sendCommand(ObdCommands.mode22(candidate.did))
            val data = StnParser.parseMode22(raw, candidate.did) ?: continue
            val value = candidate.scale(data) ?: continue
            if (candidate.plausible(value)) {
                confirmedOilTempDid = candidate.did
                Log.i(TAG, "Confirmed oil temp DID 0x${"%04X".format(candidate.did)}")
                break
            }
        }
        for (candidate in WrxProfile.oilPressureSwitchCandidates) {
            if (!candidate.confirmed) continue
            val raw = connection.sendCommand(ObdCommands.mode22(candidate.did))
            val data = StnParser.parseMode22(raw, candidate.did) ?: continue
            val value = candidate.scale(data) ?: continue
            if (candidate.plausible(value)) {
                confirmedOilSwitchDid = candidate.did
                Log.i(TAG, "Confirmed oil switch DID 0x${"%04X".format(candidate.did)}")
                break
            }
        }
        // Restore default broadcast header for mode 01
        connection.sendCommand("ATSH7DF")
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
        confirmedOilTempDid?.let { did ->
            list += SlowPid("oilTemp") { pollMode22(did) { data ->
                val candidate = WrxProfile.oilTempCandidates.first { it.did == did }
                val value = candidate.scale(data)
                if (value != null && candidate.plausible(value)) {
                    _snapshot.update { it.copy(oilTempC = value) }
                }
            } }
        }
        confirmedOilSwitchDid?.let { did ->
            list += SlowPid("oilSwitch") { pollMode22(did) { data ->
                val candidate = WrxProfile.oilPressureSwitchCandidates.first { it.did == did }
                val value = candidate.scale(data)
                if (value != null && candidate.plausible(value)) {
                    // 1 = switch closed / pressure OK on many Subarus; treat 0 as low.
                    _snapshot.update { it.copy(oilPressureLow = value == 0f, oilPressurePsi = null) }
                }
            } }
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
