package froztt13.python.aqw

import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import froztt13.python.aqw.data.repository.ConfigRepositoryImpl
import froztt13.python.aqw.ui.navigation.AppNavHost
import froztt13.python.aqw.ui.theme.BgDark
import froztt13.python.aqw.ui.theme.MyApplicationTheme
import android.graphics.Color as AndroidColor

class MainActivity : ComponentActivity() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        // Initialize ConfigRepository storage directory
        ConfigRepositoryImpl.instance.init(filesDir)

        // Acquire Partial WakeLock to keep game socket active
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AQWBot:BackgroundWakeLock"
            )
            wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours
        } catch (e: Exception) {
            Log.w("MainActivity", "Could not acquire WakeLock: ${e.message}")
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    color = BgDark
                ) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Error releasing WakeLock: ${e.message}")
        }
    }
}