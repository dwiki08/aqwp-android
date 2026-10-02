package froztt13.python.aqw.data.model

data class BotSummary(
    val running: Boolean = false,
    val count: Int = 0,
    val members: List<String> = emptyList(),
    val currentUsername: String = "",
    val subModule: String = "",
    val task: String = "",
    val timeRunning: Long = 0L
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

data class HubOverview(
    val temple: BotSummary = BotSummary(),
    val eclipse: BotSummary = BotSummary(),
    val doom: BotSummary = BotSummary(),
    val slavery: BotSummary = BotSummary(),
    val general: BotSummary = BotSummary(),
    val ultraBoss: BotSummary = BotSummary()
) {
    val anyRunning: Boolean
        get() = temple.running || eclipse.running || doom.running || slavery.running || general.running || ultraBoss.running

    val activeCount: Int
        get() = (if (temple.running) 1 else 0) +
                (if (eclipse.running) 1 else 0) +
                (if (doom.running) 1 else 0) +
                (if (slavery.running) 1 else 0) +
                (if (general.running) 1 else 0) +
                (if (ultraBoss.running) 1 else 0)
}
