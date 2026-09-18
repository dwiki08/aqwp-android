package froztt13.python.aqw.data.model

import java.util.Locale

data class DoomAccountTelemetry(
    val id: String = "",
    val username: String = "",
    val status: String = "Idle",
    val message: String = "",
    val hasEioda: Boolean = false,
    val wheelDrops: List<String> = emptyList()
)

data class WeeklyDoomTelemetry(
    val running: Boolean = false,
    val currentIndex: Int = 0,
    val currentUsername: String = "",
    val totalAccounts: Int = 0,
    val completedAccounts: Int = 0,
    val timeRunning: Long = 0L,
    val accounts: Map<String, DoomAccountTelemetry> = emptyMap()
) {
    val formattedTime: String
        get() {
            val hours = timeRunning / 3600
            val minutes = (timeRunning % 3600) / 60
            val seconds = timeRunning % 60
            return if (hours > 0) {
                String.format(
                    Locale.getDefault(),
                    "%02d:%02d:%02d",
                    hours,
                    minutes,
                    seconds
                )
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }
}
