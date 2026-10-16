package com.wrxhud.gps

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * USB host NMEA GPS (u-blox, Prolific, FTDI, CH340, CDC ACM, etc.).
 */
class UsbNmeaGps(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onUpdate: (GpsReading) -> Unit
) {
    companion object {
        private const val TAG = "UsbNmeaGps"
        private const val ACTION_USB_PERMISSION = "com.wrxhud.gps.USB_PERMISSION"
        private val BAUDS = intArrayOf(9600, 38400, 115200, 4800)
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var job: Job? = null
    private var port: UsbSerialPort? = null
    private var permissionReceiverRegistered = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && device != null) {
                openAndRead(device)
            } else {
                Log.w(TAG, "USB permission denied")
                onUpdate(GpsReading(source = GpsSourceKind.USB))
            }
        }
    }

    fun start() {
        stop()
        registerPermissionReceiver()
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = drivers.firstOrNull()
        if (driver == null) {
            Log.w(TAG, "No USB serial GPS attached")
            onUpdate(GpsReading(source = GpsSourceKind.USB))
            return
        }
        val device = driver.device
        if (usbManager.hasPermission(device)) {
            openAndRead(device)
        } else {
            val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                flags
            )
            usbManager.requestPermission(device, pi)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { port?.close() }
        port = null
        unregisterPermissionReceiver()
    }

    private fun openAndRead(device: UsbDevice) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                ?: run {
                    onUpdate(GpsReading(source = GpsSourceKind.USB))
                    return@launch
                }
            val connection = usbManager.openDevice(device)
                ?: run {
                    onUpdate(GpsReading(source = GpsSourceKind.USB))
                    return@launch
                }
            val serialPort = driver.ports.firstOrNull()
                ?: run {
                    connection.close()
                    onUpdate(GpsReading(source = GpsSourceKind.USB))
                    return@launch
                }
            try {
                serialPort.open(connection)
                port = serialPort
                var opened = false
                for (baud in BAUDS) {
                    runCatching {
                        serialPort.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                        opened = true
                        Log.i(TAG, "USB GPS open @ $baud on ${device.deviceName}")
                    }
                    if (opened) break
                }
                if (!opened) {
                    onUpdate(GpsReading(source = GpsSourceKind.USB))
                    return@launch
                }

                val buffer = ByteArray(1024)
                val lineBuf = StringBuilder()
                var speed: Float? = null
                var hasFix = false
                while (isActive) {
                    val n = serialPort.read(buffer, 500)
                    if (n <= 0) {
                        delay(20)
                        continue
                    }
                    val chunk = String(buffer, 0, n, Charsets.US_ASCII)
                    for (ch in chunk) {
                        if (ch == '\n' || ch == '\r') {
                            if (lineBuf.isNotEmpty()) {
                                val update = NmeaParser.parseLine(lineBuf.toString())
                                lineBuf.clear()
                                if (update != null) {
                                    update.speedMps?.let { speed = it }
                                    update.hasFix?.let { hasFix = it }
                                    if (update.speedMps != null || update.hasFix != null) {
                                        onUpdate(
                                            GpsReading(
                                                speedMps = speed,
                                                hasFix = hasFix,
                                                source = GpsSourceKind.USB
                                            )
                                        )
                                    }
                                }
                            }
                        } else {
                            lineBuf.append(ch)
                            if (lineBuf.length > 256) lineBuf.clear()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "USB GPS failed", t)
                onUpdate(GpsReading(source = GpsSourceKind.USB))
            } finally {
                runCatching { serialPort.close() }
                if (port === serialPort) port = null
            }
        }
    }

    private fun registerPermissionReceiver() {
        if (permissionReceiverRegistered) return
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }
        permissionReceiverRegistered = true
    }

    private fun unregisterPermissionReceiver() {
        if (!permissionReceiverRegistered) return
        runCatching { context.unregisterReceiver(permissionReceiver) }
        permissionReceiverRegistered = false
    }
}
