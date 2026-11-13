package com.huastat

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0E1116)
private val CardBg = Color(0xFF181C24)
private val Track = Color(0xFF2A2F3A)
private val Dim = Color(0xFF8A93A3)
private val Accent = Color(0xFF3DDC84)
private val Bad = Color(0xFFFF6B6B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = CardBg)
            ) {
                App(prefs)
            }
        }
    }
}

@Composable
fun App(prefs: SharedPreferences) {
    var host by remember { mutableStateOf(prefs.getString("host", "192.168.8.1") ?: "192.168.8.1") }
    var user by remember { mutableStateOf(prefs.getString("user", "admin") ?: "admin") }
    var pass by remember { mutableStateOf(prefs.getString("pass", "") ?: "") }
    var stats by remember { mutableStateOf<RouterStats?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(host, user, pass) {
        val api = RouterApi(host, user, pass)
        while (true) {
            try {
                stats = withContext(Dispatchers.IO) { api.fetch() }
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            delay(2000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HuaStat", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (error != null) "Not connected" else stats?.let { "${it.network}  •  ${it.isp}" } ?: "Connecting…",
                    color = if (error != null) Bad else Accent,
                    fontSize = 13.sp
                )
            }
            TextButton(onClick = { showSettings = true }) { Text("Settings") }
        }

        error?.let {
            Box(
                Modifier.fillMaxWidth().background(Color(0xFF3A1D1D), RoundedCornerShape(14.dp)).padding(14.dp)
            ) { Text(it, color = Bad, fontSize = 13.sp) }
        }

        stats?.let { s ->
            Section("Cell") {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Info("Cell", s.cellId)
                        Info("Band", s.band)
                        Info("ISP", s.isp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Info("eNB ID", s.enbId)
                        Info("Width", s.width)
                        Info("Network", s.network)
                    }
                }
            }

            Section("Signal") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Gauge("RSSI", fmt(s.rssi, 0), "dBm", frac(s.rssi, -110.0, -50.0), Modifier.weight(1f))
                    Gauge("RSRQ", fmt(s.rsrq, 1), "dB", frac(s.rsrq, -20.0, -3.0), Modifier.weight(1f))
                    Gauge("RSRP", fmt(s.rsrp, 0), "dBm", frac(s.rsrp, -140.0, -44.0), Modifier.weight(1f))
                    Gauge("SINR", fmt(s.sinr, 0), "dB", frac(s.sinr, -5.0, 30.0), Modifier.weight(1f))
                }
            }

            Section("Speed") {
                Row(Modifier.fillMaxWidth()) {
                    Big("↓ Download", fmtRate(s.dlRate), Modifier.weight(1f))
                    Big("↑ Upload", fmtRate(s.ulRate), Modifier.weight(1f))
                }
            }

            Section("Data used") {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Info("Session ↓", fmtBytes(s.curDl))
                        Info("Session ↑", fmtBytes(s.curUl))
                        Info("Connected", fmtTime(s.curTime))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Info("Total ↓", fmtBytes(s.totalDl))
                        Info("Total ↑", fmtBytes(s.totalUl))
                        Info("Total time", "${s.totalTime / 86400} days")
                    }
                }
            }
        }
    }

    if (showSettings) {
        var h by remember { mutableStateOf(host) }
        var u by remember { mutableStateOf(user) }
        var p by remember { mutableStateOf(pass) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Router settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(h, { h = it }, label = { Text("Router address") }, singleLine = true)
                    OutlinedTextField(u, { u = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(
                        p, { p = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    host = h.trim(); user = u.trim(); pass = p
                    prefs.edit().putString("host", host).putString("user", user).putString("pass", pass).apply()
                    showSettings = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title.uppercase(), color = Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        content()
    }
}

@Composable
private fun Info(label: String, value: String) {
    Column {
        Text(label, color = Dim, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Big(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Dim, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Gauge(label: String, value: String, unit: String, fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    val color = Color.hsv(f * 120f, 0.75f, 0.95f)
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            val w = 7.dp.toPx()
            val arcSize = Size(size.width - w, size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            val stroke = Stroke(width = w, cap = StrokeCap.Round)
            drawArc(Track, 135f, 270f, false, topLeft, arcSize, style = stroke)
            if (f > 0f) drawArc(color, 135f, 270f * f, false, topLeft, arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Dim, fontSize = 9.sp)
            Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = Dim, fontSize = 9.sp)
        }
    }
}

private fun frac(v: Double?, min: Double, max: Double): Float =
    if (v == null) 0f else ((v - min) / (max - min)).toFloat().coerceIn(0f, 1f)

private fun fmt(v: Double?, decimals: Int): String =
    if (v == null) "-" else String.format("%.${decimals}f", v)

private fun fmtRate(bytesPerSec: Long): String {
    val bits = bytesPerSec * 8.0
    return if (bits >= 1_000_000) String.format("%.2f Mb/s", bits / 1_000_000)
    else String.format("%.0f kb/s", bits / 1_000)
}

private fun fmtBytes(b: Long): String = when {
    b >= 1_000_000_000_000 -> String.format("%.2f TB", b / 1e12)
    b >= 1_000_000_000 -> String.format("%.2f GB", b / 1e9)
    b >= 1_000_000 -> String.format("%.0f MB", b / 1e6)
    else -> String.format("%.0f KB", b / 1e3)
}

private fun fmtTime(sec: Long): String =
    String.format("%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
