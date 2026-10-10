package com.speedbeat.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpeedBeatApp(this) }
    }

    fun startGps(onSpeed: (Float) -> Unit, onStatus: (String) -> Unit) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { onStatus("مجوز مکان لازم است؛ دکمه اتصال GPS را بزن") ; return }
        try {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) != true) {
                onStatus("مکان گوشی خاموش است؛ از تنظیمات گوشی Location را روشن کن")
                return
            }
            stopGps()
            val listener = LocationListener { loc: Location ->
                if (loc.hasSpeed()) {
                    val kmh = (loc.speed * 3.6f).coerceIn(0f, 240f)
                    runOnUiThread { onSpeed(kmh); onStatus("GPS متصل است • سرعت به‌روز می‌شود") }
                }
            }
            locationListener = listener
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener)
            onStatus("در انتظار سیگنال GPS… در فضای باز دقیق‌تر است")
        } catch (_: SecurityException) {
            onStatus("مجوز مکان در دسترس نیست؛ تنظیمات برنامه را بررسی کن")
        } catch (_: Exception) {
            onStatus("GPS در دسترس نیست؛ Location را روشن کن")
        }
    }

    fun stopGps() {
        try { locationListener?.let { locationManager?.removeUpdates(it) } } catch (_: Exception) { }
        locationListener = null
    }

    fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    override fun onDestroy() { stopGps(); super.onDestroy() }
}

private enum class BeatStyle(val label: String, val samples: List<Int>) {
    PHONK("Phonk", listOf(R.raw.phonk_01, R.raw.phonk_02, R.raw.phonk_03, R.raw.phonk_04)),
    EDM("EDM", listOf(R.raw.edm_01, R.raw.edm_02, R.raw.edm_03, R.raw.edm_04, R.raw.edm_05, R.raw.edm_06))
}

@Composable
private fun SpeedBeatApp(activity: MainActivity) {
    // A fresh random category is selected whenever the app Activity starts.
    var style by remember { mutableStateOf(BeatStyle.values().random()) }
    val engine = remember { LayeredAudioEngine(activity) }
    var playing by remember { mutableStateOf(false) }
    var gpsEnabled by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(0f) }
    var baseBpm by remember { mutableIntStateOf(110) }
    var autoBpm by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.75f) }
    var layerInterval by remember { mutableIntStateOf(10) }
    var gpsStatus by remember { mutableStateOf("برای افزودن لایه‌ها با سرعت، GPS را متصل کن") }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val prefs = remember { activity.getSharedPreferences("speedbeat_permissions", Context.MODE_PRIVATE) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        prefs.edit().putBoolean("location_request_made", true).apply()
        if (permissionGranted) {
            gpsEnabled = true
            activity.startGps({ speed = it }, { gpsStatus = it })
        } else {
            gpsEnabled = false
            gpsStatus = "مجوز مکان داده نشد؛ از دکمه اتصال GPS یا تنظیمات استفاده کن"
        }
    }

    fun requestGps() {
        val hasPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        permissionGranted = hasPermission
        if (hasPermission) {
            if (gpsEnabled) { activity.stopGps(); gpsEnabled = false; gpsStatus = "ردیابی GPS متوقف است" }
            else { gpsEnabled = true; activity.startGps({ speed = it }, { gpsStatus = it }) }
        } else {
            val asked = prefs.getBoolean("location_request_made", false)
            val rationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) || activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (asked && !rationale) { gpsStatus = "مجوز قبلاً رد شده؛ از تنظیمات برنامه فعالش کن"; activity.openAppSettings() }
            else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            val asked = prefs.getBoolean("location_request_made", false)
            val rationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) || activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (asked && !rationale) gpsStatus = "برای استفاده از GPS، مجوز مکان را از تنظیمات برنامه فعال کن"
            else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            gpsEnabled = true
            activity.startGps({ speed = it }, { gpsStatus = it })
        }
    }

    val currentBpm = if (autoBpm) (baseBpm + speed * 0.6f).roundToInt().coerceIn(60, 180) else baseBpm
    val wantedLayers = ((speed / layerInterval).toInt() + 1).coerceIn(1, style.samples.size)

    LaunchedEffect(currentBpm, volume, playing) {
        engine.setBpm(currentBpm)
        engine.setVolume(volume)
    }
    LaunchedEffect(style, wantedLayers, playing) {
        if (playing) engine.setLayers(style, wantedLayers) else engine.stop()
    }
    DisposableEffect(Unit) { onDispose { engine.stop(); activity.stopGps() } }

    val bg = Color(0xFF10111A)
    val panel = Color(0xFF1B1D2B)
    val accent = Color(0xFFB46BFF)
    MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = accent, onBackground = Color(0xFFF5F3FF), onSurface = Color(0xFFF5F3FF))) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("SpeedBeat", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("هرچه سریع‌تر، لایه‌های بیشتر", fontSize = 12.sp, color = Color(0xFFB8B6CA))
                    }
                    Text("♫", fontSize = 34.sp, color = accent)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("سرعت", "${speed.toInt()}", "km/h", Modifier.weight(1f), Color(0xFF36D9C5))
                    MetricCard("تمپو", "$currentBpm", "BPM", Modifier.weight(1f), accent)
                    MetricCard("لایه‌ها", "$wantedLayers", "صدا", Modifier.weight(1f), Color(0xFFFFA94D))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { requestGps() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (gpsEnabled) Color(0xFF285C55) else Color(0xFF33354A))) {
                        Text(if (gpsEnabled) "GPS فعال" else "اتصال GPS")
                    }
                    Button(onClick = { playing = !playing }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (playing) Color(0xFFB13F65) else accent)) {
                        Text(if (playing) "■ توقف موسیقی" else "▶ شروع موسیقی", color = Color.White)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(gpsStatus, modifier = Modifier.fillMaxWidth(), color = if (gpsEnabled) Color(0xFF36D9C5) else Color(0xFFFFB45C), fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("دسته‌بندی صداها", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("شروع برنامه: انتخاب تصادفی • می‌توانی دستی هم تغییر بدهی", fontSize = 10.sp, color = Color(0xFFB8B6CA))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BeatStyle.values().forEach { item ->
                                FilterChip(selected = style == item, onClick = { style = item }, label = { Text(item.label) })
                            }
                            OutlinedButton(onClick = { style = BeatStyle.values().random() }) { Text("🎲 تصادفی") }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text("نمونه‌های این دسته: ${style.samples.size}", color = Color(0xFFB8B6CA), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("فاصله‌ی افزودن هر لایه", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("هر $layerInterval km/h", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text("با هر افزایش این مقدار در سرعت، یک صدای دیگر اضافه می‌شود.", fontSize = 10.sp, color = Color(0xFFB8B6CA))
                        Slider(value = layerInterval.toFloat(), onValueChange = { layerInterval = it.roundToInt().coerceIn(5, 30) }, valueRange = 5f..30f, steps = 24)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("۵ km/h", fontSize = 10.sp, color = Color(0xFF85869B)); Text("۳۰ km/h", fontSize = 10.sp, color = Color(0xFF85869B))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("تنظیم BPM دستی", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("$baseBpm BPM", color = accent, fontWeight = FontWeight.Bold)
                        }
                        Slider(value = baseBpm.toFloat(), onValueChange = { baseBpm = it.roundToInt().coerceIn(60, 180) }, valueRange = 60f..180f, steps = 119)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("۶۰ BPM", fontSize = 10.sp, color = Color(0xFF85869B)); Text("۱۸۰ BPM", fontSize = 10.sp, color = Color(0xFF85869B))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("تغییر BPM با GPS", fontSize = 12.sp)
                                Text("در حالت روشن، سرعت تمپو را هم بالا می‌برد", fontSize = 10.sp, color = Color(0xFFB8B6CA))
                            }
                            Switch(checked = autoBpm, onCheckedChange = { autoBpm = it })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ولوم", fontSize = 12.sp, color = Color.White)
                    Text("${(volume * 100).roundToInt()}٪", fontSize = 12.sp, color = accent)
                }
                Slider(value = volume, onValueChange = { volume = it }, valueRange = 0f..1f)
                Spacer(Modifier.weight(1f))
                Text("برای آزمایش GPS در فضای باز باش؛ صداها از نمونه‌های صوتی ارسالی ساخته شده‌اند.", color = Color(0xFF85869B), fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, unit: String, modifier: Modifier, tint: Color) {
    Column(modifier.background(Color(0xFF1B1D2B), RoundedCornerShape(18.dp)).padding(12.dp)) {
        Text(title, color = Color(0xFFB8B6CA), fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = tint)
            Spacer(Modifier.width(4.dp)); Text(unit, color = Color(0xFFB8B6CA), fontSize = 10.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

/** Plays one randomly chosen sample per active layer. Samples are looped as supplied. */
private class LayeredAudioEngine(private val context: Context) {
    private data class ActiveSample(val resourceId: Int, val player: MediaPlayer)
    private val players = mutableListOf<ActiveSample>()
    private var currentStyle: BeatStyle? = null
    private var bpm = 110
    private var volume = 0.75f

    @Synchronized fun setBpm(value: Int) {
        bpm = value.coerceIn(60, 180)
        players.forEach { sample ->
            try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) sample.player.playbackParams = PlaybackParams().setSpeed((bpm / 110f).coerceIn(0.55f, 1.65f)) } catch (_: Exception) { }
        }
    }

    @Synchronized fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        updateVolumes()
    }

    @Synchronized fun setLayers(style: BeatStyle, count: Int) {
        if (currentStyle != style) { releasePlayers(); currentStyle = style }
        val target = count.coerceIn(0, style.samples.size)
        while (players.size > target) {
            val sample = players.removeAt(players.lastIndex)
            try { sample.player.stop() } catch (_: Exception) { }
            try { sample.player.release() } catch (_: Exception) { }
        }
        while (players.size < target) {
            // Avoid duplicate clips until every sample in this category is in use.
            val usedResources = players.map { it.resourceId }.toSet()
            val available = style.samples.filterNot { it in usedResources }.ifEmpty { style.samples }
            val resource = available[Random.nextInt(available.size)]
            try {
                val player = MediaPlayer.create(context, resource) ?: break
                player.isLooping = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) player.playbackParams = PlaybackParams().setSpeed((bpm / 110f).coerceIn(0.55f, 1.65f))
                val perLayer = (volume / kotlin.math.sqrt(target.toDouble())).toFloat().coerceIn(0f, 1f)
                player.setVolume(perLayer, perLayer)
                player.setOnErrorListener { mp, _, _ -> try { mp.release() } catch (_: Exception) { }; true }
                players.add(ActiveSample(resource, player))
                player.start()
            } catch (_: Exception) { break }
        }
        updateVolumes()
    }

    private fun updateVolumes() {
        val count = players.size.coerceAtLeast(1)
        val perLayer = (volume / kotlin.math.sqrt(count.toDouble())).toFloat().coerceIn(0f, 1f)
        players.forEach { try { it.player.setVolume(perLayer, perLayer) } catch (_: Exception) { } }
    }

    @Synchronized fun stop() { releasePlayers(); currentStyle = null }

    private fun releasePlayers() {
        players.forEach { try { it.player.stop() } catch (_: Exception) { }; try { it.player.release() } catch (_: Exception) { } }
        players.clear()
    }
}
