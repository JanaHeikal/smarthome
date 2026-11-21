package com.example.smarthome

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

// =====================================================================  root screen
@Composable
fun SmartHomeApp(vm: SmartHomeViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snack.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.openPicker() else vm.showMessage("Bluetooth permission is needed to find the HC-05")
    }
    val onConnect: () -> Unit = {
        val needs = Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (needs) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) else vm.openPicker()
    }
    val needConnect: () -> Unit = { vm.showMessage("Connect to the HC-05 first") }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Header(ui, onConnect, vm::disconnect)
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnimatedVisibility(ui.alarm) { AlarmBanner() }
                TemperatureCard(ui)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToggleTile(
                        modifier = Modifier.weight(1f), icon = Icons.Filled.Lightbulb, title = "Lamp",
                        on = ui.lamp, enabled = ui.connected, accent = Pal.Orange
                    ) { if (ui.connected) vm.setLamp(!ui.lamp) else needConnect() }
                    ToggleTile(
                        modifier = Modifier.weight(1f), icon = Icons.Filled.Power, title = "Plug",
                        on = ui.plug, enabled = ui.connected, accent = Pal.Blue
                    ) { if (ui.connected) vm.setPlug(!ui.plug) else needConnect() }
                }
                DoorCard(ui)
                LimitCard(ui, onApply = { if (ui.connected) vm.setLimit(it) else needConnect() })
                LogCard(ui, onClear = vm::clearLog)
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }
    }

    if (ui.showPicker) DevicePicker(ui.devices, onPick = vm::connect, onDismiss = vm::closePicker)
}

// =====================================================================  header
@Composable
fun Header(ui: UiState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(Pal.Navy, Color(0xFF1D4ED8))))
    ) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text("Smart Home", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Lamp  \u2022  Plug  \u2022  Door  \u2022  Temperature", color = Color(0xFFCBD5E1), fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dot = when {
                    ui.connected -> Color(0xFF4ADE80)
                    ui.connecting -> Pal.Orange
                    else -> Color(0xFFF87171)
                }
                Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        ui.connected -> "Connected to ${ui.deviceName}"
                        ui.connecting -> "Connecting\u2026"
                        else -> "Not connected"
                    },
                    color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
                when {
                    ui.connecting -> CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 3.dp)
                    ui.connected -> TextButton(onClick = onDisconnect) { Text("Disconnect", color = Color(0xFFFCA5A5)) }
                    else -> Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Pal.Navy),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Filled.Bluetooth, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =====================================================================  alarm banner
@Composable
fun AlarmBanner() {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha"
    )
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Pal.Red),
        modifier = Modifier.fillMaxWidth().alpha(pulse)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Temperature above the limit!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("The room is hotter than the limit you set.", color = Color(0xFFFEE2E2), fontSize = 12.sp)
            }
        }
    }
}

// =====================================================================  temperature
@Composable
fun TemperatureCard(ui: UiState) {
    val accent by animateColorAsState(if (ui.alarm) Pal.Red else Pal.Teal, label = "accent")
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Thermostat, null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text("ROOM TEMPERATURE", color = muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                ui.limit?.let {
                    Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.15f)) {
                        Text("limit $it\u00b0", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    ui.temp?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                    color = accent, fontSize = 64.sp, fontWeight = FontWeight.Bold
                )
                Text("\u00b0C", color = accent, fontSize = 24.sp, modifier = Modifier.padding(start = 4.dp, bottom = 14.dp))
            }
            Sparkline(ui.history, ui.limit, accent, Modifier.fillMaxWidth().height(90.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                if (ui.history.size < 2) "Waiting for temperature data\u2026" else "Last ${ui.history.size} seconds",
                color = muted, fontSize = 11.sp
            )
        }
    }
}

@Composable
fun Sparkline(values: List<Float>, limit: Int?, line: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        var lo = values.min()
        var hi = values.max()
        limit?.let { lo = minOf(lo, it.toFloat()); hi = maxOf(hi, it.toFloat()) }
        if (hi - lo < 1f) { hi += 0.5f; lo -= 0.5f }
        val pad = 8.dp.toPx()
        fun y(v: Float) = pad + (size.height - 2 * pad) * (1f - (v - lo) / (hi - lo))
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * stepX, y(v))
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        limit?.let {
            drawLine(
                Pal.Red, Offset(0f, y(it.toFloat())), Offset(size.width, y(it.toFloat())),
                strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
            )
        }
    }
}

// =====================================================================  lamp / plug tiles
@Composable
fun ToggleTile(
    modifier: Modifier, icon: ImageVector, title: String,
    on: Boolean, enabled: Boolean, accent: Color, onClick: () -> Unit
) {
    val bg by animateColorAsState(if (on) accent else MaterialTheme.colorScheme.surface, label = "bg")
    val fg by animateColorAsState(if (on) Color.White else MaterialTheme.colorScheme.onSurface, label = "fg")
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = modifier
            .height(140.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(36.dp))
                Switch(
                    checked = on, onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accent, checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF94A3B8)
                    )
                )
            }
            Column {
                Text(title, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (on) "ON" else "OFF", color = fg.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// =====================================================================  door
@Composable
fun DoorCard(ui: UiState) {
    val open = ui.door == "OPEN"
    val color = when (ui.door) { "OPEN" -> Pal.Orange; "CLOSED" -> Pal.Green; else -> Color(0xFF94A3B8) }
    val animated by animateColorAsState(color, label = "door")
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(animated.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MeetingRoom, null, tint = animated, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("DOOR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (ui.door) { "OPEN" -> "Open"; "CLOSED" -> "Closed"; else -> "Unknown" },
                    color = animated, fontSize = 26.sp, fontWeight = FontWeight.Bold
                )
                if (ui.doorSince.isNotEmpty()) {
                    Text("since ${ui.doorSince}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            if (open) {
                Surface(shape = RoundedCornerShape(50), color = Pal.Orange.copy(alpha = 0.18f)) {
                    Text("OPEN", color = Pal.Orange, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}

// =====================================================================  limit
@Composable
fun LimitCard(ui: UiState, onApply: (Int) -> Unit) {
    var draft by remember(ui.limit) { mutableIntStateOf(ui.limit ?: 30) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("TEMPERATURE LIMIT", color = muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(
                    onClick = { if (draft > 0) draft -= 1 },
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                ) { Icon(Icons.Filled.Remove, "Lower") }
                Text("$draft\u00b0C", fontSize = 40.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 28.dp))
                IconButton(
                    onClick = { if (draft < 99) draft += 1 },
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                ) { Icon(Icons.Filled.Add, "Raise") }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pal.Green)
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Set limit", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                ui.limit?.let { "Active limit on the board: $it\u00b0C" } ?: "No limit confirmed by the board yet",
                color = muted, fontSize = 12.sp
            )
        }
    }
}

// =====================================================================  door log
@Composable
fun LogCard(ui: UiState, onClear: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, null, tint = Pal.Purple)
                Spacer(Modifier.width(8.dp))
                Text("DOOR LOG", color = muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (ui.log.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (ui.log.isEmpty()) {
                Text("No door events yet. Open or close the door.", color = muted, fontSize = 13.sp)
            } else {
                ui.log.take(40).forEach { e ->
                    val open = e.state == "OPEN"
                    val c = if (open) Pal.Orange else Pal.Green
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.width(12.dp))
                        Text(if (open) "Opened" else "Closed", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(e.time, color = muted, fontSize = 13.sp)
                    }
                }
                if (ui.log.size > 40) {
                    Text("+ ${ui.log.size - 40} older events", color = muted, fontSize = 12.sp)
                }
            }
        }
    }
}

// =====================================================================  device picker
@Composable
fun DevicePicker(devices: List<BtDevice>, onPick: (BtDevice) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose your HC-05", fontWeight = FontWeight.Bold) },
        text = {
            if (devices.isEmpty()) {
                Text("No paired devices yet.\nPair the HC-05 in Android Bluetooth settings first (PIN 1234 or 0000), then come back.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    devices.forEach { d ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(d) }
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.BluetoothConnected, null, tint = Pal.Blue)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(d.name, fontWeight = FontWeight.SemiBold)
                                    Text(d.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ctx.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                Text("Bluetooth settings")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
