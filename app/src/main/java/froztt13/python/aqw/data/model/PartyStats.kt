package froztt13.python.aqw.data.model

data class PartyStats(
    val timeRunning: Long = 0L,
    val clearedCount: Int = 0
) {
    val formattedTime: String
        get() {
            val hours = timeRunning / 3600
            val minutes = (timeRunning % 3600) / 60
            val seconds = timeRunning % 60
            return if (hours > 0) {
                String.format(
                    java.util.Locale.getDefault(),
                    "%02d:%02d:%02d",
                    hours,
                    minutes,
                    seconds
                )
            } else {
                String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }
}
