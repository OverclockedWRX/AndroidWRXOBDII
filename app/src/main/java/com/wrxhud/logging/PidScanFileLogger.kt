package com.wrxhud.logging

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.wrxhud.obd.PidScanLogEntry
import com.wrxhud.obd.PidScanRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Writes a human-readable PID scan dump to public Downloads for offline reference.
 */
object PidScanFileLogger {
    private const val TAG = "PidScanFileLogger"

    /**
     * @return display file name, or null on failure
     */
    fun write(context: Context, rows: List<PidScanRow>): String? {
        if (rows.isEmpty()) return null
        val now = System.currentTimeMillis()
        val entries = rows.map { it.toLogEntry(now) }
        return writeEntries(context, entries, now)
    }

    fun writeEntries(context: Context, entries: List<PidScanLogEntry>, atMs: Long): String? {
        if (entries.isEmpty()) return null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(atMs))
        val fileName = "WRX_PID_scan_$stamp.txt"
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(atMs))
        val body = buildString {
            appendLine("WRX Race HUD PID scan log")
            appendLine("scanned_at=$iso")
            appendLine("count=${entries.size}")
            appendLine()
            appendLine("address\traw_hex\tcandidates")
            entries.forEach { e ->
                val cands = e.candidates.joinToString("; ") { "${it.decode.label}=${it.display}" }
                appendLine("${e.addressLabel()}\t${e.rawHex}\t$cands")
            }
        }

        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.applicationContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
            } ?: return null
            Log.i(TAG, "PID scan log → Download/$fileName (${entries.size} rows)")
            entries.forEach { e ->
                Log.i(
                    TAG,
                    "PID ${e.addressLabel()} raw=${e.rawHex} " +
                        e.candidates.joinToString { "${it.decode.name}=${it.display}" }
                )
            }
            fileName
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to write PID scan log", t)
            null
        }
    }
}
