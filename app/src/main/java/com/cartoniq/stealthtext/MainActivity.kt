package com.cartoniq.stealthtext

import android.graphics.Color as AndroidColor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

enum class Screen { Home, Vault, Tools, Settings, HideText, HideFile, HideVoice, HideMark, Reveal, Split, Shamir, InvisibleText, Scan }

private val tabScreens = setOf(Screen.Home, Screen.Vault, Screen.Tools, Screen.Settings)

class MainActivity : ComponentActivity(), SensorEventListener {
    private var lastShake = 0L
    private var stoppedAt = 0L
    private var sensorManager: SensorManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        if (!AppState.started) {
            AppState.started = true
            AppState.lang = Prefs.lang(this)
            AppState.onboarded = Prefs.onboarded(this)
            AppState.locked = Prefs.hasPin(this)
        }
        setContent { StealthApp(this) }
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0L && Prefs.hasPin(this) && SystemClock.elapsedRealtime() - stoppedAt > 30_000L) {
            AppState.locked = true
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        refreshShake()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    fun refreshShake() {
        val sm = sensorManager ?: return
        sm.unregisterListener(this)
        if (!Prefs.shake(this)) return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        try {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (e: SecurityException) {
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (g > 2.7f) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastShake > 1500L) {
                lastShake = now
                panic()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    /** Locks the app at once. Without a PIN there is nothing to unlock with, so the app closes instead. */
    fun panic() {
        if (!AppState.onboarded) return
        if (Prefs.hasPin(this)) {
            AppState.locked = true
        } else {
            finishAndRemoveTask()
        }
    }
}

@Composable
fun StealthApp(activity: MainActivity) {
    var screen by remember { mutableStateOf(Screen.Home) }
    var back by remember { mutableStateOf(Screen.Home) }
    var revealUri by remember { mutableStateOf<Uri?>(null) }
    val open: (Screen, Screen) -> Unit = { s, b ->
        if (s == Screen.Reveal) revealUri = null
        screen = s
        back = b
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Cyan,
            onPrimary = Ink,
            secondary = Magenta,
            background = Bg,
            surface = Color.White,
            onSurface = Ink,
            onBackground = Ink
        )
    ) {
        val dir = if (Lang.isRtl(AppState.lang)) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
        Box(Modifier.fillMaxSize().prismBackground().systemBarsPadding().imePadding()) {
            if (!AppState.onboarded) {
                OnboardingScreen(onDone = {
                    Prefs.putOnboarded(activity)
                    AppState.onboarded = true
                })
            } else if (AppState.crashed) {
                FakeCrashScreen(onClose = {
                    AppState.crashed = false
                    AppState.locked = true
                    activity.finishAffinity()
                })
            } else if (AppState.locked) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    if (Prefs.disguise(activity)) CalculatorScreen() else PinLockScreen()
                }
            } else {
                BackHandler(enabled = screen !in tabScreens) { screen = back }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (screen) {
                            Screen.Home -> HomeScreen { s -> open(s, Screen.Home) }
                            Screen.Vault -> VaultScreen { u ->
                                revealUri = u
                                screen = Screen.Reveal
                                back = Screen.Vault
                            }
                            Screen.Tools -> ToolsScreen { s -> open(s, Screen.Tools) }
                            Screen.Settings -> SettingsScreen()
                            Screen.HideText -> HideScreen(HideMode.Text) { screen = back }
                            Screen.HideFile -> HideScreen(HideMode.File) { screen = back }
                            Screen.HideVoice -> HideScreen(HideMode.Voice) { screen = back }
                            Screen.HideMark -> HideScreen(HideMode.Mark) { screen = back }
                            Screen.Reveal -> RevealScreen(revealUri) { screen = back }
                            Screen.Split -> SplitScreen { screen = back }
                            Screen.Shamir -> ShamirScreen { screen = back }
                            Screen.InvisibleText -> InvisibleTextScreen { screen = back }
                            Screen.Scan -> ScanScreen { screen = back }
                        }
                        PanicButton(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp)) {
                            activity.panic()
                        }
                    }
                    if (screen in tabScreens) {
                        BottomNav(screen) { t -> screen = t }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun BottomNav(current: Screen, onSelect: (Screen) -> Unit) {
    val items = listOf(Screen.Home to tr("Home"), Screen.Vault to tr("Vault"), Screen.Tools to tr("Tools"), Screen.Settings to tr("Settings"))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = 0.85f)).padding(6.dp)
    ) {
        items.forEach { (s, label) ->
            val sel = s == current
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                    .background(if (sel) Sun else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(s) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (sel) Ink else Sub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
