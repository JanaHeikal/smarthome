package com.example.smarthome

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Classic Bluetooth "serial cable" (SPP) to the HC-05.
 * Receives bytes, cuts them into lines at '\n', and hands each line to [onLine].
 */
class BluetoothSerial(
    private val scope: CoroutineScope,
    private val onLine: (String) -> Unit,
    private val onClosed: (unexpected: Boolean) -> Unit
) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var intentional = false
    private var readJob: Job? = null

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (socket != null) closeQuietly()
            intentional = false
            val s = device.createRfcommSocketToServiceRecord(sppUuid)
            s.connect()                       // blocks until connected or throws IOException
            socket = s
            output = s.outputStream
            readJob = scope.launch(Dispatchers.IO) { readLoop(s) }
        }
    }

    fun send(line: String) {
        val o = output ?: return
        scope.launch(Dispatchers.IO) {
            try {
                synchronized(o) {
                    o.write((line + "\n").toByteArray(Charsets.US_ASCII))
                    o.flush()
                }
            } catch (e: IOException) {
                // the read loop notices a dead link and reports it
            }
        }
    }

    fun disconnect() {
        intentional = true
        closeQuietly()
    }

    private fun closeQuietly() {
        val s = socket
        socket = null
        output = null
        try { s?.close() } catch (e: IOException) { }
        readJob?.cancel()
        readJob = null
    }

    private fun readLoop(s: BluetoothSocket) {
        val buf = ByteArray(256)
        val sb = StringBuilder()
        try {
            val input = s.inputStream
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                sb.append(String(buf, 0, n, Charsets.US_ASCII))
                while (true) {
                    val i = sb.indexOf("\n")
                    if (i < 0) break
                    val line = sb.substring(0, i).trim()
                    sb.delete(0, i + 1)
                    if (line.isNotEmpty()) onLine(line)
                }
                if (sb.length > 512) sb.setLength(0)   // garbage protection
            }
        } catch (e: IOException) {
            // link closed or lost
        } finally {
            val unexpected = (socket === s) && !intentional
            if (socket === s) { socket = null; output = null }
            try { s.close() } catch (e: IOException) { }
            onClosed(unexpected)
        }
    }
}
