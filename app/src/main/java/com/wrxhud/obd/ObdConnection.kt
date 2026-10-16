package com.wrxhud.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Classic Bluetooth SPP session to an OBDLink MX+.
 */
class ObdConnection(
    private val adapter: BluetoothAdapter
) {
    companion object {
        private const val TAG = "ObdConnection"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val READ_TIMEOUT_MS = 1500L
    }

    private var socket: BluetoothSocket? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private val open = AtomicBoolean(false)

    val isOpen: Boolean get() = open.get()

    @SuppressLint("MissingPermission")
    fun findMxPlus(): BluetoothDevice? {
        return adapter.bondedDevices?.firstOrNull { device ->
            val name = device.name.orEmpty()
            name.contains("OBDLink", ignoreCase = true) ||
                name.contains("MX+", ignoreCase = true) ||
                name.contains("MX Plus", ignoreCase = true)
        }
    }

    @SuppressLint("MissingPermission")
    fun listBonded(): List<BluetoothDevice> {
        return adapter.bondedDevices?.toList().orEmpty()
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun connect(device: BluetoothDevice) {
        close()
        val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
        adapter.cancelDiscovery()
        sock.connect()
        socket = sock
        writer = OutputStreamWriter(sock.outputStream, Charsets.US_ASCII)
        reader = BufferedReader(InputStreamReader(sock.inputStream, Charsets.US_ASCII))
        open.set(true)
        initialize()
    }

    @Synchronized
    fun close() {
        open.set(false)
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { socket?.close() }
        writer = null
        reader = null
        socket = null
    }

    @Synchronized
    fun sendCommand(command: String, waitForPrompt: Boolean = true): String {
        check(open.get()) { "OBD connection is closed" }
        val w = writer ?: error("No writer")
        val r = reader ?: error("No reader")

        // Drain any leftover bytes before a new request.
        while (r.ready()) {
            r.read()
        }

        w.write(command.trim())
        w.write("\r")
        w.flush()

        if (!waitForPrompt) return ""

        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!r.ready()) {
                Thread.sleep(2)
                continue
            }
            val ch = r.read()
            if (ch < 0) break
            val c = ch.toChar()
            if (c == '>') break
            sb.append(c)
        }
        val response = sb.toString()
        Log.d(TAG, "CMD $command -> ${response.replace("\r", "\\r").replace("\n", "\\n")}")
        return response
    }

    private fun initialize() {
        ObdCommands.INIT_SEQUENCE.forEach { cmd ->
            sendCommand(cmd)
        }
    }
}
