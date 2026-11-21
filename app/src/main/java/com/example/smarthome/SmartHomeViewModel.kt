package com.example.smarthome

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DoorEvent(val time: String, val state: String)
data class BtDevice(val name: String, val address: String)

data class UiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val deviceName: String = "",
    val lamp: Boolean = false,
    val plug: Boolean = false,
    val door: String? = null,          // "OPEN", "CLOSED" or null (unknown yet)
    val doorSince: String = "",
    val temp: Float? = null,
    val history: List<Float> = emptyList(),
    val limit: Int? = null,
    val alarm: Boolean = false,
    val log: List<DoorEvent> = emptyList(),
    val devices: List<BtDevice> = emptyList(),
    val showPicker: Boolean = false,
    val message: String? = null
)

class SmartHomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("smarthome", Context.MODE_PRIVATE)
    private val adapter: BluetoothAdapter? =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _ui = MutableStateFlow(UiState(log = loadLog()))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val serial = BluetoothSerial(
        scope = viewModelScope,
        onLine = { line -> handleLine(line) },
        onClosed = { unexpected -> handleClosed(unexpected) }
    )

    // ------------------------------------------------------------ connection
    @SuppressLint("MissingPermission")
    fun openPicker() {
        val a = adapter
        if (a == null) { showMessage("This phone has no Bluetooth"); return }
        if (!a.isEnabled) { showMessage("Turn Bluetooth on first"); return }
        val last = prefs.getString("last", null)
        val list = a.bondedDevices
            .map { BtDevice(it.name ?: "Unknown device", it.address) }
            .sortedWith(
                compareByDescending<BtDevice> { it.address == last }
                    .thenByDescending { it.name.contains("HC", ignoreCase = true) }
            )
        _ui.update { it.copy(devices = list, showPicker = true) }
    }

    fun closePicker() = _ui.update { it.copy(showPicker = false) }

    fun connect(dev: BtDevice) {
        val a = adapter ?: return
        _ui.update { it.copy(showPicker = false, connecting = true) }
        viewModelScope.launch {
            val result = serial.connect(a.getRemoteDevice(dev.address))
            if (result.isSuccess) {
                prefs.edit().putString("last", dev.address).apply()
                _ui.update { it.copy(connecting = false, connected = true, deviceName = dev.name) }
                serial.send("STATUS?")            // ask the board for its full state
            } else {
                _ui.update {
                    it.copy(connecting = false,
                        message = "Could not connect to ${dev.name}. Is the HC-05 powered and in range?")
                }
            }
        }
    }

    fun disconnect() = serial.disconnect()

    private fun handleClosed(unexpected: Boolean) {
        _ui.update {
            it.copy(
                connected = false, connecting = false,
                message = if (unexpected) "Connection lost" else it.message
            )
        }
    }

    // ------------------------------------------------------------ commands
    fun setLamp(on: Boolean) = serial.send(if (on) "LAMP:1" else "LAMP:0")
    fun setPlug(on: Boolean) = serial.send(if (on) "PLUG:1" else "PLUG:0")
    fun setLimit(value: Int) = serial.send("LIMIT:$value")
    fun requestStatus() = serial.send("STATUS?")

    // ------------------------------------------------------------ incoming lines
    private fun handleLine(line: String) {
        val parts = line.trim().split(":")
        if (parts.size < 2) return
        var key = parts[0]
        var value = parts[1]
        if (key == "ACK" && parts.size >= 3) {      // ACK:LAMP:1  ->  LAMP / 1
            key = parts[1]
            value = parts[2]
        }
        when (key) {
            "TEMP" -> value.toFloatOrNull()?.let { t ->
                _ui.update { it.copy(temp = t, history = (it.history + t).takeLast(60)) }
            }
            "DOOR" -> if (value == "OPEN" || value == "CLOSED") onDoor(value)
            "ALARM" -> _ui.update { it.copy(alarm = value == "1") }
            "LAMP" -> _ui.update { it.copy(lamp = value == "1") }
            "PLUG" -> _ui.update { it.copy(plug = value == "1") }
            "LIMIT" -> value.toIntOrNull()?.let { l -> _ui.update { it.copy(limit = l) } }
        }
    }

    private fun onDoor(state: String) {
        val now = timeNow()
        val previous = _ui.value.door
        if (previous == state) return                      // nothing changed
        if (previous == null) {                            // first value after connecting: not a real event
            _ui.update { it.copy(door = state, doorSince = now) }
            return
        }
        val newLog = (listOf(DoorEvent(now, state)) + _ui.value.log).take(200)
        _ui.update { it.copy(door = state, doorSince = now, log = newLog) }
        saveLog(newLog)
    }

    // ------------------------------------------------------------ door log storage
    fun clearLog() {
        _ui.update { it.copy(log = emptyList()) }
        saveLog(emptyList())
    }

    private fun saveLog(list: List<DoorEvent>) {
        prefs.edit().putString("log", list.joinToString("\n") { "${it.time}|${it.state}" }).apply()
    }

    private fun loadLog(): List<DoorEvent> =
        prefs.getString("log", "")!!.split("\n").mapNotNull {
            val p = it.split("|")
            if (p.size == 2) DoorEvent(p[0], p[1]) else null
        }

    private fun timeNow(): String =
        SimpleDateFormat("d MMM  HH:mm:ss", Locale.getDefault()).format(Date())

    // ------------------------------------------------------------ messages
    fun showMessage(text: String) = _ui.update { it.copy(message = text) }
    fun clearMessage() = _ui.update { it.copy(message = null) }

    override fun onCleared() {
        serial.disconnect()
        super.onCleared()
    }
}
