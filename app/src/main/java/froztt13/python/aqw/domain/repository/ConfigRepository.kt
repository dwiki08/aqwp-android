package froztt13.python.aqw.domain.repository

import froztt13.python.aqw.data.model.EclipseConfig
import froztt13.python.aqw.data.model.GeneralBotConfig
import froztt13.python.aqw.data.model.SlaveryConfig
import froztt13.python.aqw.data.model.TempleConfig
import froztt13.python.aqw.data.model.WeeklyDoomConfig

interface ConfigRepository {
    // Type-safe Eclipse operations
    suspend fun saveEclipseConfig(config: EclipseConfig): Boolean
    suspend fun loadEclipseConfig(): EclipseConfig?
    suspend fun resetEclipseConfig(): EclipseConfig

    // Type-safe Temple operations
    suspend fun saveTempleConfig(config: TempleConfig): Boolean
    suspend fun loadTempleConfig(): TempleConfig?
    suspend fun resetTempleConfig(): TempleConfig

    // Type-safe Weekly Doom operations
    suspend fun saveDoomConfig(config: WeeklyDoomConfig): Boolean
    suspend fun loadDoomConfig(): WeeklyDoomConfig?
    suspend fun resetDoomConfig(): WeeklyDoomConfig

    // Type-safe Slavery operations
    suspend fun saveSlaveryConfig(config: SlaveryConfig): Boolean
    suspend fun loadSlaveryConfig(): SlaveryConfig?
    suspend fun resetSlaveryConfig(): SlaveryConfig

    // Type-safe General Bot operations
    suspend fun saveGeneralConfig(config: GeneralBotConfig): Boolean
    suspend fun loadGeneralConfig(): GeneralBotConfig?
    suspend fun resetGeneralConfig(): GeneralBotConfig

    // Raw JSON string persistence (backward-compatibility)
    suspend fun saveRawConfig(key: String, content: String): Boolean
    suspend fun loadRawConfig(key: String): String?
    suspend fun resetRawConfig(key: String): String?
}
