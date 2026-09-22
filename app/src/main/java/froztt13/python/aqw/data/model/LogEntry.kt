package froztt13.python.aqw.data.model

private val logIdCounter = java.util.concurrent.atomic.AtomicLong(1L)

/**
 * Categorization and severity of log entries emitted across sessions and bots:
 * - [SYSTEM]: Text from the server, such as AqwEvent.ServerBroadcast, chat, and AFK notices.
 * - [INFO]: All info text from bots, such as taunt eclipse, movement, attacks, and activity status.
 * - [WARNING]: Warning text from the server, such as AqwEvent.Warning and session notices.
 * - [ERROR]: Error logs from the application or bot action failures.
 * - [PACKET]: All raw network packet logs (inbound/outbound).
 */
enum class LogEntryType(val key: String) {
    SYSTEM("system"),   // gray
    INFO("info"),       // gray
    WARNING("warning"), // orange
    ERROR("error"),     // red
    PACKET("packet");

    companion object {
        fun from(value: String?): LogEntryType {
            if (value.isNullOrBlank()) return INFO
            return entries.firstOrNull {
                it.key.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            } ?: INFO
        }
    }
}

data class LogEntry(
    val id: Long = logIdCounter.incrementAndGet(),
    val timestamp: Long = System.currentTimeMillis(),
    val botType: LogEntryType = LogEntryType.INFO,
    val username: String,
    val message: String
) {
    val type: LogEntryType get() = botType

    /**
     * Compatibility constructor accepting String for [botType].
     */
    constructor(
        id: Long = logIdCounter.incrementAndGet(),
        timestamp: Long = System.currentTimeMillis(),
        botType: String,
        username: String,
        message: String
    ) : this(
        id = id,
        timestamp = timestamp,
        botType = LogEntryType.from(botType),
        username = username,
        message = message
    )

    val formattedTime: String
        get() = java.text.SimpleDateFormat(
            "HH:mm:ss.SSS",
            java.util.Locale.getDefault()
        ).format(java.util.Date(timestamp))
}
