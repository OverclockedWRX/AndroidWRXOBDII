package com.wrxhud.gps

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * Classic Bluetooth SPP NMEA GPS (Holux, Qstarz, generic BT-GPS, etc.).
 */
class BluetoothNmeaGps(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onUpdate: (GpsReading) -> Unit
) {
    companion object {
        private const val TAG = "BluetoothNmeaGps"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private val GPS_NAME_HINTS = listOf(
            "gps", "gn-", "gnss", "nmea", "holux", "qstarz", "garmin", "ublox", "u-blox",
            "bt-gps", "bluetooth gps", "g-mouse", "gm-", "navilock", "globalsat"
        )
        private val EXCLUDE_HINTS = listOf("obd", "mx+", "elm", "stn")
    }

    private var job: Job? = null
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    fun start(preferredAddress: String? = null) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter
            if (adapter == null || !adapter.isEnabled) {
                Log.w(TAG, "Bluetooth unavailable")
                onUpdate(GpsReading(source = GpsSourceKind.BLUETOOTH))
                return@launch
            }
            val device = findGpsDevice(adapter, preferredAddress)
            if (device == null) {
                Log.w(TAG, "No paired Bluetooth GPS found")
                onUpdate(GpsReading(source = GpsSourceKind.BLUETOOTH))
                return@launch
            }
            try {
                adapter.cancelDiscovery()
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                sock.connect()
                socket = sock
                Log.i(TAG, "Connected to Bluetooth GPS: ${device.name}")
                val reader = BufferedReader(InputStreamReader(sock.inputStream, Charsets.US_ASCII))
                var speed: Float? = null
                var hasFix = false
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val update = NmeaParser.parseLine(line) ?: continue
                    update.speedMps?.let { speed = it }
                    update.hasFix?.let { hasFix = it }
                    if (update.speedMps != null || update.hasFix != null) {
                        onUpdate(
                            GpsReading(
                                speedMps = speed,
                                hasFix = hasFix,
                                source = GpsSourceKind.BLUETOOTH
                            )
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Bluetooth GPS failed", t)
                onUpdate(GpsReading(source = GpsSourceKind.BLUETOOTH))
            } finally {
                runCatching { socket?.close() }
                socket = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { socket?.close() }
        socket = null
    }

    @SuppressLint("MissingPermission")
    private fun findGpsDevice(adapter: BluetoothAdapter, preferredAddress: String?): BluetoothDevice? {
        val bonded = adapter.bondedDevices.orEmpty()
        if (!preferredAddress.isNullOrBlank()) {
            bonded.firstOrNull { it.address.equals(preferredAddress, ignoreCase = true) }?.let { return it }
        }
        return bonded.firstOrNull { device ->
            val name = device.name.orEmpty().lowercase()
            if (EXCLUDE_HINTS.any { name.contains(it) }) return@firstOrNull false
            GPS_NAME_HINTS.any { name.contains(it) }
        }
    }
}
