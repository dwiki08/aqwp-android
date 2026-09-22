package froztt13.python.aqw.domain.coordinator

import froztt13.python.aqw.data.model.BotSummary
import froztt13.python.aqw.data.model.HubOverview
import froztt13.python.aqw.domain.bot.doom.NativeWeeklyDoomBot
import froztt13.python.aqw.domain.bot.eclipse.NativeEclipseBot
import froztt13.python.aqw.domain.bot.general.NativeGeneralBot
import froztt13.python.aqw.domain.bot.slavery.NativeSlaveryBot
import froztt13.python.aqw.domain.bot.temple.NativeTempleBot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Domain Coordinator for aggregating live telemetry across all active bot modules
 * into a consolidated [HubOverview].
 */
object HubCoordinator {

    fun calculateOverview(): HubOverview {
        var overview = HubOverview()

        val doomTelemetry = NativeWeeklyDoomBot.telemetry.value
        if (doomTelemetry.running || doomTelemetry.timeRunning > 0) {
            val doomSummary = BotSummary(
                running = doomTelemetry.running,
                count = doomTelemetry.totalAccounts,
                members = doomTelemetry.accounts.values.map { it.username },
                currentUsername = doomTelemetry.currentUsername,
                subModule = "Native Weekly Doom",
                task = if (doomTelemetry.currentUsername.isNotEmpty()) "Processing ${doomTelemetry.currentUsername}" else "Done",
                timeRunning = doomTelemetry.timeRunning
            )
            overview = overview.copy(doom = doomSummary)
        }

        val templeStatus = NativeTempleBot.status.value
        val templeStats = NativeTempleBot.stats.value
        if (templeStatus.values.any { it.running } || templeStats.timeRunning > 0) {
            val isTempleRunning = templeStatus.values.any { it.running }
            val templeSummary = BotSummary(
                running = isTempleRunning,
                count = templeStatus.size,
                members = templeStatus.keys.toList(),
                currentUsername = templeStatus["slot1"]?.map ?: "",
                subModule = "Native Temple Shrine",
                task = "Runs: ${templeStats.clearedCount}",
                timeRunning = templeStats.timeRunning
            )
            overview = overview.copy(temple = templeSummary)
        }

        val eclipseStatus = NativeEclipseBot.status.value
        val eclipseStats = NativeEclipseBot.stats.value
        if (eclipseStatus.values.any { it.running } || eclipseStats.timeRunning > 0) {
            val isEclipseRunning = eclipseStatus.values.any { it.running }
            val eclipseSummary = BotSummary(
                running = isEclipseRunning,
                count = eclipseStatus.size,
                members = eclipseStatus.keys.toList(),
                currentUsername = eclipseStatus["slot1"]?.map ?: "",
                subModule = "Native Eclipse Shrine",
                task = "Runs: ${eclipseStats.clearedCount}",
                timeRunning = eclipseStats.timeRunning
            )
            overview = overview.copy(eclipse = eclipseSummary)
        }

        val slaveryStatus = NativeSlaveryBot.status.value
        val slaveryStats = NativeSlaveryBot.partyStats.value
        if (slaveryStatus.values.any { it.running } || slaveryStats.timeRunning > 0) {
            val runningSlots = slaveryStatus.filter { it.value.running }
            val slaverySummary = BotSummary(
                running = runningSlots.isNotEmpty(),
                count = runningSlots.size,
                members = runningSlots.keys.toList(),
                currentUsername = runningSlots.values.firstOrNull()?.map ?: "",
                subModule = "Native Slavery Bot",
                task = "Farming",
                timeRunning = slaveryStats.timeRunning
            )
            overview = overview.copy(slavery = slaverySummary)
        }

        val generalTelemetry = NativeGeneralBot.telemetry.value
        if (generalTelemetry.running || generalTelemetry.timeRunning > 0) {
            val generalSummary = BotSummary(
                running = generalTelemetry.running,
                count = if (generalTelemetry.running) 1 else 0,
                members = if (generalTelemetry.username.isNotEmpty()) listOf(generalTelemetry.username) else emptyList(),
                currentUsername = generalTelemetry.username,
                subModule = generalTelemetry.subModule.ifEmpty { "Native General Bot" },
                task = generalTelemetry.task.ifEmpty { generalTelemetry.status },
                timeRunning = generalTelemetry.timeRunning
            )
            overview = overview.copy(general = generalSummary)
        }

        return overview
    }

    /**
     * Reaktif: Menggabungkan StateFlow dari semua bot sehingga UI terupdate otomatis
     * ketika salah satu status bot berubah.
     */
    fun observeOverview(): Flow<HubOverview> =
        combine(
            listOf(
                NativeWeeklyDoomBot.telemetry,
                NativeTempleBot.status,
                NativeTempleBot.stats,
                NativeEclipseBot.status,
                NativeEclipseBot.stats,
                NativeSlaveryBot.status,
                NativeSlaveryBot.partyStats,
                NativeGeneralBot.telemetry
            )
        ) {
            calculateOverview()
        }
}
