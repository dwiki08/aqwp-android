package froztt13.python.aqw.data.model

data class GeneralTaskInfo(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val defaultQty: Int = 1,
    val trackedItem: String = "",
    val questId: Int = 0
)

data class GeneralSubModuleInfo(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val tasks: List<GeneralTaskInfo> = emptyList()
)

data class QuestRequirementTelemetry(
    val itemId: Int = 0,
    val itemName: String = "",
    val currentQty: Int = 0,
    val requiredQty: Int = 0
) {
    val isSatisfied: Boolean get() = currentQty >= requiredQty
    val progressFraction: Float
        get() = if (requiredQty > 0) (currentQty.toFloat() / requiredQty).coerceIn(0f, 1f) else 0f
    val progressPercent: Int get() = (progressFraction * 100).toInt()
}

data class GeneralBotTelemetry(
    val running: Boolean = false,
    val isConnected: Boolean = false,
    val isPaused: Boolean = false,
    val username: String = "",
    val subModule: String = "",
    val subModuleName: String = "",
    val task: String = "",
    val taskName: String = "",
    val trackedItem: String = "",
    val currentQty: Int = 0,
    val targetQty: Int = 0,
    val status: String = "Idle",
    val message: String = "",
    val map: String = "-",
    val cell: String = "-",
    val pad: String = "-",
    val hp: Int = 0,
    val maxHp: Int = 0,
    val mp: Int = 0,
    val maxMp: Int = 0,
    val isDead: Boolean = false,
    val cooldowns: Map<Int, Double> = emptyMap(),
    val timeRunning: Long = 0L,
    val questRequirements: List<QuestRequirementTelemetry> = emptyList()
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

    val progressFraction: Float
        get() = if (targetQty > 0) (currentQty.toFloat() / targetQty.toFloat()).coerceIn(
            0f,
            1f
        ) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}
