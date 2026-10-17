package com.wrxhud.obd

import android.util.Log
import com.wrxhud.vehicle.AlarmEvaluator
import com.wrxhud.vehicle.GearCalculator
import com.wrxhud.vehicle.UnitConverters
import com.wrxhud.vehicle.VehicleInfo
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
 * User [PidAssignment]s override auto-discovery for mapped signals.
 */
class ObdPoller(
    private val connection: ObdConnection,
    private val scope: CoroutineScope,
    private val gearConfigProvider: () -> GearConfig,
    private val gpsSpeedProvider: () -> Pair<Float?, Boolean>,
    private val thresholdsProvider: () -> WrxProfile.AlarmThresholds,
    private val assignmentsProvider: () -> List<PidAssignment> = { emptyList() }
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

    private val _vehicleInfo = MutableStateFlow(VehicleInfo())
    val vehicleInfo: StateFlow<VehicleInfo> = _vehicleInfo.asStateFlow()

    private val running = AtomicBoolean(false)
    private var job: Job? = null
    private val paused = AtomicBoolean(false)

    private val supportedPids = mutableSetOf<Int>()
    private var mapKpa: Float? = null
    private var baroKpa: Float? = null
    /** VB FA24 confirmed: SAE Mode 01 PID 0x5C (hard-coded; no Mode 22 hunt). */
    private var oilTempSourceLabel: String = "01 5C"
    private var iatMode22Did: Int? = null
    private var useMode01Iat: Boolean = false
    private var mapMode22Did: Int? = null
    private var useMode01Map: Boolean = false
    /** Mode 01 MAP PID in use: [WrxProfile.Pid.MAP] (0x0B) or [WrxProfile.Pid.MAP_ABS] (0x87). */
    private var mapMode01Pid: Int = WrxProfile.Pid.MAP

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
                var slowPids = buildSlowQueue()
                var lastAssignmentSig = assignmentsProvider().joinToString { it.encode() }
                while (isActive && running.get() && connection.isOpen) {
                    if (paused.get()) {
                        Thread.sleep(50)
                        continue
                    }
                    val sig = assignmentsProvider().joinToString { it.encode() }
                    if (sig != lastAssignmentSig) {
                        lastAssignmentSig = sig
                        discoverCapabilities()
                        slowPids = buildSlowQueue()
                        slowIndex = 0
                    }
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

    /** Pause the poll loop so a scanner can use the connection exclusively. */
    fun pause() {
        paused.set(true)
    }

    fun resume() {
        paused.set(false)
    }

    fun isPaused(): Boolean = paused.get()

    private fun discoverCapabilities() {
        supportedPids.clear()
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

        if (assignment(MappableSignal.OIL_TEMP) == null) {
            // Confirmed on this car: always Mode 01 PID 5C.
            oilTempSourceLabel = "01 5C"
            pollMode01(WrxProfile.Pid.OIL_TEMP) { data ->
                val temp = StnParser.decodeTempC(data)
                if (temp != null && temp in -20f..160f) {
                    _snapshot.update {
                        it.copy(oilTempC = temp, oilTempSource = oilTempSourceLabel)
                    }
                    Log.i(TAG, "Oil temp via hard-coded 01 5C = $temp C")
                }
            }
        } else {
            oilTempSourceLabel = "user"
        }
        if (assignment(MappableSignal.IAT) == null) {
            discoverIat()
        } else {
            useMode01Iat = false
            iatMode22Did = null
        }
        if (assignment(MappableSignal.BOOST_MAP) == null) {
            discoverMap()
        } else {
            useMode01Map = false
            mapMode22Did = null
        }

        refreshVehicleInfo()
    }

    /** Re-query VIN / CALID / adapter identity (also runs after capability discovery). */
    fun refreshVehicleInfo() {
        val mapLabel = when {
            assignment(MappableSignal.BOOST_MAP) != null -> "user"
            useMode01Map -> "01 %02X".format(mapMode01Pid)
            mapMode22Did != null -> "22 %04X".format(mapMode22Did)
            else -> null
        }
        _vehicleInfo.value = VehicleInfoReader.read(
            connection = connection,
            oilTempSource = oilTempSourceLabel,
            mapSource = mapLabel
        )
    }

    private fun assignment(signal: MappableSignal): PidAssignment? =
        assignmentsProvider().firstOrNull { it.signal == signal }

    /** SAE 01 0F when supported; else first plausible Mode 22 IAT candidate (read-only). */
    private fun discoverIat() {
        useMode01Iat = false
        iatMode22Did = null
        val tryMode01 = supportedPids.contains(WrxProfile.Pid.IAT) || supportedPids.isEmpty()
        if (tryMode01) {
            val raw = connection.sendCommand(ObdCommands.mode01(WrxProfile.Pid.IAT))
            val data = StnParser.parseMode01(raw, WrxProfile.Pid.IAT)
            val temp = data?.let { StnParser.decodeTempC(it) }
            if (temp != null && temp in -30f..90f) {
                useMode01Iat = true
                _snapshot.update { it.copy(intakeAirTempC = temp) }
                Log.i(TAG, "IAT confirmed via Mode 01 PID 0F = $temp C")
                return
            }
        }
        connection.sendCommand(ObdCommands.setHeader(ECU_HEADER))
        for (candidate in WrxProfile.iatCandidates) {
            val raw = connection.sendCommand(ObdCommands.mode22(candidate.did))
            val data = StnParser.parseMode22(raw, candidate.did) ?: continue
            val value = candidate.scale(data) ?: continue
            if (!candidate.plausible(value)) continue
            iatMode22Did = candidate.did
            _snapshot.update { it.copy(intakeAirTempC = value) }
            Log.i(TAG, "IAT confirmed via 22 ${"%04X".format(candidate.did)} = $value C")
            break
        }
        connection.sendCommand("ATSH7DF")
        if (iatMode22Did == null && !useMode01Iat) {
            Log.w(TAG, "IAT: no Mode 01/22 source confirmed yet — check PID Log after a deep scan")
        }
    }

    /**
     * MAP for boost/vac: SAE 01 0B → SAE 01 87 (absolute MAP, used on VB FA24) → Mode 22.
     */
    private fun discoverMap() {
        useMode01Map = false
        mapMode22Did = null
        mapMode01Pid = WrxProfile.Pid.MAP

        val try0b = supportedPids.contains(WrxProfile.Pid.MAP) || supportedPids.isEmpty()
        if (try0b) {
            val raw = connection.sendCommand(ObdCommands.mode01(WrxProfile.Pid.MAP))
            val data = StnParser.parseMode01(raw, WrxProfile.Pid.MAP)
            val kpa = data?.let { StnParser.decodePressureKpa(it) }
            if (kpa != null && kpa in 15f..320f) {
                useMode01Map = true
                mapMode01Pid = WrxProfile.Pid.MAP
                mapKpa = kpa
                updateBoost()
                Log.i(TAG, "MAP confirmed via Mode 01 PID 0B = $kpa kPa")
                return
            }
        }

        // VB FA24 scan: 01 0B absent, but 01 87 replies with absolute MAP (~33 kPa at idle).
        val try87 = supportedPids.contains(WrxProfile.Pid.MAP_ABS) ||
            supportedPids.isEmpty() ||
            !supportedPids.contains(WrxProfile.Pid.MAP)
        if (try87) {
            val raw = connection.sendCommand(ObdCommands.mode01(WrxProfile.Pid.MAP_ABS))
            val data = StnParser.parseMode01(raw, WrxProfile.Pid.MAP_ABS)
            val kpa = data?.let { StnParser.decodeAbsoluteMapKpa(it) }
            if (kpa != null && kpa in 15f..320f) {
                useMode01Map = true
                mapMode01Pid = WrxProfile.Pid.MAP_ABS
                mapKpa = kpa
                updateBoost()
                Log.i(TAG, "MAP confirmed via Mode 01 PID 87 = $kpa kPa")
                return
            }
        }

        connection.sendCommand(ObdCommands.setHeader(ECU_HEADER))
        for (candidate in WrxProfile.mapCandidates) {
            val raw = connection.sendCommand(ObdCommands.mode22(candidate.did))
            val data = StnParser.parseMode22(raw, candidate.did) ?: continue
            val value = candidate.scale(data) ?: continue
            if (!candidate.plausible(value)) continue
            mapMode22Did = candidate.did
            mapKpa = value
            updateBoost()
            Log.i(TAG, "MAP confirmed via 22 ${"%04X".format(candidate.did)} = $value kPa")
            break
        }
        connection.sendCommand("ATSH7DF")
        if (mapMode22Did == null && !useMode01Map) {
            Log.w(TAG, "MAP/boost: no Mode 01/22 source confirmed yet — check PID Log after a deep scan")
        }
    }

    private fun buildSlowQueue(): List<SlowPid> {
        val list = mutableListOf<SlowPid>()
        val assignments = assignmentsProvider()

        fun addAssigned(signal: MappableSignal, name: String, apply: (Float) -> Unit) {
            val a = assignments.firstOrNull { it.signal == signal } ?: return
            list += SlowPid(name) { pollAssigned(a, apply) }
        }

        if (assignment(MappableSignal.COOLANT) != null) {
            addAssigned(MappableSignal.COOLANT, "coolant") { v ->
                _snapshot.update { it.copy(coolantC = v) }
            }
        } else {
            list += SlowPid("coolant") {
                pollMode01(WrxProfile.Pid.COOLANT) { data ->
                    _snapshot.update { it.copy(coolantC = StnParser.decodeTempC(data)) }
                }
            }
        }

        if (assignment(MappableSignal.IAT) != null) {
            addAssigned(MappableSignal.IAT, "iat") { v ->
                _snapshot.update { it.copy(intakeAirTempC = v) }
            }
        } else if (useMode01Iat) {
            list += SlowPid("iat") {
                pollMode01(WrxProfile.Pid.IAT) { data ->
                    _snapshot.update { it.copy(intakeAirTempC = StnParser.decodeTempC(data)) }
                }
            }
        } else {
            iatMode22Did?.let { did ->
                list += SlowPid("iat") {
                    pollMode22(did) { data ->
                        val candidate = WrxProfile.iatCandidates.first { it.did == did }
                        val value = candidate.scale(data)
                        if (value != null && candidate.plausible(value)) {
                            _snapshot.update { it.copy(intakeAirTempC = value) }
                        }
                    }
                }
            }
        }

        if (assignment(MappableSignal.BOOST_MAP) != null) {
            addAssigned(MappableSignal.BOOST_MAP, "map") { v ->
                mapKpa = v
                updateBoost()
            }
        } else if (useMode01Map) {
            list += SlowPid("map") {
                pollMode01(mapMode01Pid) { data ->
                    mapKpa = if (mapMode01Pid == WrxProfile.Pid.MAP_ABS) {
                        StnParser.decodeAbsoluteMapKpa(data)
                    } else {
                        StnParser.decodePressureKpa(data)
                    }
                    updateBoost()
                }
            }
        } else {
            mapMode22Did?.let { did ->
                list += SlowPid("map") {
                    pollMode22(did) { data ->
                        val candidate = WrxProfile.mapCandidates.first { it.did == did }
                        val value = candidate.scale(data)
                        if (value != null && candidate.plausible(value)) {
                            mapKpa = value
                            updateBoost()
                        }
                    }
                }
            }
        }

        list += SlowPid("baro") {
            pollMode01(WrxProfile.Pid.BARO) { data ->
                baroKpa = StnParser.decodePressureKpa(data)
                updateBoost()
            }
        }
        list += SlowPid("speed") {
            pollMode01(WrxProfile.Pid.SPEED) { data ->
                val kph = StnParser.decodeSpeedKph(data)
                _snapshot.update { it.copy(obdSpeedKph = kph) }
                updateGear()
            }
        }
        // Absolute throttle (0x11) — widely supported.
        if (supportedPids.contains(WrxProfile.Pid.THROTTLE_ABS) || supportedPids.isEmpty()) {
            list += SlowPid("throttle") {
                pollMode01(WrxProfile.Pid.THROTTLE_ABS) { data ->
                    _snapshot.update { it.copy(throttlePercent = StnParser.decodePercent(data)) }
                }
            }
        }
        // Accelerator pedal D (0x49) — driver demand when ECU supports it.
        if (supportedPids.contains(WrxProfile.Pid.ACCEL_PEDAL_D) || supportedPids.isEmpty()) {
            list += SlowPid("accelPedal") {
                pollMode01(WrxProfile.Pid.ACCEL_PEDAL_D) { data ->
                    _snapshot.update { it.copy(acceleratorPercent = StnParser.decodePercent(data)) }
                }
            }
        }
        list += SlowPid("mil") {
            pollMode01(WrxProfile.Pid.MONITOR_STATUS) { data ->
                _snapshot.update { it.copy(milOn = StnParser.decodeMil(data)) }
            }
        }

        if (assignment(MappableSignal.BATTERY) != null) {
            addAssigned(MappableSignal.BATTERY, "voltage") { v ->
                _snapshot.update { it.copy(batteryVolts = v) }
            }
        } else {
            list += SlowPid("voltage") {
                pollMode01(WrxProfile.Pid.CONTROL_MODULE_VOLTAGE) { data ->
                    _snapshot.update { it.copy(batteryVolts = StnParser.decodeVoltage(data)) }
                }
            }
        }

        if (supportedPids.contains(WrxProfile.Pid.FUEL_LEVEL) || supportedPids.isEmpty()) {
            list += SlowPid("fuel") {
                pollMode01(WrxProfile.Pid.FUEL_LEVEL) { data ->
                    _snapshot.update { it.copy(fuelLevelPercent = StnParser.decodePercent(data)) }
                }
            }
        }

        if (assignment(MappableSignal.AFR) != null) {
            addAssigned(MappableSignal.AFR, "afr") { v ->
                _snapshot.update { it.copy(afr = v) }
            }
        } else {
            val afrPid = when {
                supportedPids.contains(WrxProfile.Pid.O2_EQ_RATIO) || supportedPids.isEmpty() ->
                    WrxProfile.Pid.O2_EQ_RATIO
                supportedPids.contains(WrxProfile.Pid.COMMANDED_EQ_RATIO) ->
                    WrxProfile.Pid.COMMANDED_EQ_RATIO
                else -> null
            }
            if (afrPid != null) {
                list += SlowPid("afr") {
                    pollMode01(afrPid) { data ->
                        StnParser.decodeAfr(data, WrxProfile.STOICH_AFR)?.let { afr ->
                            _snapshot.update { it.copy(afr = afr) }
                        }
                    }
                }
            }
        }

        if (assignment(MappableSignal.OIL_TEMP) != null) {
            addAssigned(MappableSignal.OIL_TEMP, "oilTemp") { v ->
                _snapshot.update {
                    it.copy(oilTempC = v, oilTempSource = "user")
                }
            }
        } else {
            // Hard-coded SAE 01 5C for VB FA24 oil temperature.
            list += SlowPid("oilTemp") {
                pollMode01(WrxProfile.Pid.OIL_TEMP) { data ->
                    val temp = StnParser.decodeTempC(data)
                    if (temp != null && temp in -20f..160f) {
                        _snapshot.update {
                            it.copy(oilTempC = temp, oilTempSource = "01 5C")
                        }
                    }
                }
            }
        }

        if (assignment(MappableSignal.OIL_PRESSURE) != null) {
            addAssigned(MappableSignal.OIL_PRESSURE, "oilPressure") { v ->
                // Decoder stores 1 = Normal, 0 = Low.
                _snapshot.update { it.copy(oilPressureOk = v >= 0.5f) }
            }
        }

        return list
    }

    private fun pollAssigned(assignment: PidAssignment, apply: (Float) -> Unit) {
        val data = when (assignment.mode) {
            0x01 -> {
                val raw = connection.sendCommand(ObdCommands.mode01(assignment.id))
                StnParser.parseMode01(raw, assignment.id)
            }
            0x22 -> {
                val header = assignment.header ?: ECU_HEADER
                connection.sendCommand(ObdCommands.setHeader(header))
                val raw = connection.sendCommand(ObdCommands.mode22(assignment.id))
                connection.sendCommand("ATSH7DF")
                StnParser.parseMode22(raw, assignment.id)
            }
            else -> null
        } ?: return
        val decoded = PidValueDecoder.decode(data, assignment.decode) ?: return
        val value = PidValueDecoder.toSnapshotValue(assignment.signal, assignment.decode, decoded)
        apply(value)
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
