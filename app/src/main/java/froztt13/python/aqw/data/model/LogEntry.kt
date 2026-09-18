package froztt13.python.aqw.data.model

private val logIdCounter = java.util.concurrent.atomic.AtomicLong(1L)

data class LogEntry(
    val id: Long = logIdCounter.incrementAndGet(),
    val timestamp: Long = System.currentTimeMillis(),
    val botType: String,
    val username: String,
    val message: String
) {
    val formattedTime: String
        get() = java.text.SimpleDateFormat(
            "HH:mm:ss.SSS",
            java.util.Locale.getDefault()
        ).format(java.util.Date(timestamp))
}
