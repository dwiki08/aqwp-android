package froztt13.python.aqw.data.repository

import android.util.Log
import froztt13.python.aqw.data.model.DoomAccount
import froztt13.python.aqw.data.model.EclipseConfig
import froztt13.python.aqw.data.model.GeneralBotConfig
import froztt13.python.aqw.data.model.SlaveSlotConfig
import froztt13.python.aqw.data.model.SlaveryConfig
import froztt13.python.aqw.data.model.SlotConfig
import froztt13.python.aqw.data.model.TempleConfig
import froztt13.python.aqw.data.model.WeeklyDoomConfig
import froztt13.python.aqw.domain.repository.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ConfigRepositoryImpl(
    private var baseConfigDir: File? = null
) : ConfigRepository {

    companion object {
        private const val TAG = "ConfigRepositoryImpl"
        private const val FILE_TEMPLE = "temple_config.json"
        private const val FILE_ECLIPSE = "eclipse_config.json"
        private const val FILE_DOOM = "doom_config.json"
        private const val FILE_SLAVERY = "slavery_config.json"
        private const val FILE_GENERAL = "general_config.json"

        // Singleton instance for backward-compatibility / ease of access
        val instance = ConfigRepositoryImpl()
    }

    fun init(filesDir: File) {
        baseConfigDir = filesDir
    }

    private fun getConfigFile(name: String): File? {
        val dir = baseConfigDir ?: return null
        val fileName = when (name) {
            "temple_load_config", "temple_save_config", "temple_reset_config", FILE_TEMPLE -> FILE_TEMPLE
            "eclipse_load_config", "eclipse_save_config", "eclipse_reset_config", FILE_ECLIPSE -> FILE_ECLIPSE
            "doom_load_config", "doom_save_config", "doom_reset_config", FILE_DOOM -> FILE_DOOM
            "slavery_load_config", "slavery_save_config", "slavery_reset_config", FILE_SLAVERY -> FILE_SLAVERY
            "general_load_config", "general_save_config", "general_reset_config", FILE_GENERAL -> FILE_GENERAL
            else -> if (name.endsWith(".json")) name else "$name.json"
        }
        return File(dir, fileName)
    }

    override suspend fun saveRawConfig(key: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = getConfigFile(key) ?: return@withContext false
                file.writeText(content)
                true
            } catch (e: Exception) {
                Log.e(TAG, "saveRawConfig error ($key): ${e.message}", e)
                false
            }
        }

    override suspend fun loadRawConfig(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = getConfigFile(key) ?: return@withContext null
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            Log.e(TAG, "loadRawConfig error ($key): ${e.message}", e)
            null
        }
    }

    override suspend fun resetRawConfig(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = getConfigFile(key)
            if (file != null && file.exists()) {
                file.delete()
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "resetRawConfig error ($key): ${e.message}", e)
            null
        }
    }

    // --- Eclipse Operations ---
    override suspend fun saveEclipseConfig(config: EclipseConfig): Boolean {
        return saveRawConfig(FILE_ECLIPSE, serializeEclipseConfig(config))
    }

    override suspend fun loadEclipseConfig(): EclipseConfig? {
        val raw = loadRawConfig(FILE_ECLIPSE) ?: return null
        return try {
            parseEclipseConfig(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing Eclipse config: ${e.message}")
            null
        }
    }

    override suspend fun resetEclipseConfig(): EclipseConfig {
        resetRawConfig(FILE_ECLIPSE)
        return EclipseConfig().enforceFixedRoles()
    }

    // --- Temple Operations ---
    override suspend fun saveTempleConfig(config: TempleConfig): Boolean {
        return saveRawConfig(FILE_TEMPLE, serializeTempleConfig(config))
    }

    override suspend fun loadTempleConfig(): TempleConfig? {
        val raw = loadRawConfig(FILE_TEMPLE) ?: return null
        return try {
            parseTempleConfig(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing Temple config: ${e.message}")
            null
        }
    }

    override suspend fun resetTempleConfig(): TempleConfig {
        resetRawConfig(FILE_TEMPLE)
        return TempleConfig()
    }

    // --- Weekly Doom Operations ---
    override suspend fun saveDoomConfig(config: WeeklyDoomConfig): Boolean {
        return saveRawConfig(FILE_DOOM, serializeDoomConfig(config))
    }

    override suspend fun loadDoomConfig(): WeeklyDoomConfig? {
        val raw = loadRawConfig(FILE_DOOM) ?: return null
        return try {
            parseDoomConfig(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing Doom config: ${e.message}")
            null
        }
    }

    override suspend fun resetDoomConfig(): WeeklyDoomConfig {
        resetRawConfig(FILE_DOOM)
        return WeeklyDoomConfig()
    }

    // --- Slavery Operations ---
    override suspend fun saveSlaveryConfig(config: SlaveryConfig): Boolean {
        return saveRawConfig(FILE_SLAVERY, serializeSlaveryConfig(config))
    }

    override suspend fun loadSlaveryConfig(): SlaveryConfig? {
        val raw = loadRawConfig(FILE_SLAVERY) ?: return null
        return try {
            parseSlaveryConfig(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing Slavery config: ${e.message}")
            null
        }
    }

    override suspend fun resetSlaveryConfig(): SlaveryConfig {
        resetRawConfig(FILE_SLAVERY)
        return SlaveryConfig()
    }

    // --- General Bot Operations ---
    override suspend fun saveGeneralConfig(config: GeneralBotConfig): Boolean {
        return saveRawConfig(FILE_GENERAL, serializeGeneralConfig(config))
    }

    override suspend fun loadGeneralConfig(): GeneralBotConfig? {
        val raw = loadRawConfig(FILE_GENERAL) ?: return null
        return try {
            parseGeneralConfig(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing General config: ${e.message}")
            null
        }
    }

    override suspend fun resetGeneralConfig(): GeneralBotConfig {
        resetRawConfig(FILE_GENERAL)
        return GeneralBotConfig()
    }

    // --- Type-safe Data Class Serialization & Parsing ---

    fun parseEclipseConfig(jsonStr: String): EclipseConfig {
        val parsed = try {
            EclipseConfig.fromJson(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing EclipseConfig, falling back to default: ${e.message}")
            EclipseConfig()
        }
        val defaultSlots = EclipseConfig.defaultSlots()
        val mergedSlots = defaultSlots.toMutableMap()
        parsed.slots.forEach { (k, v) ->
            val def = defaultSlots[k] ?: SlotConfig()
            mergedSlots[k] = v.copy(
                charClass = v.charClass.ifBlank { def.charClass },
                role = v.role.ifBlank { def.role },
                defaultTarget = v.defaultTarget.ifBlank { def.defaultTarget }
            )
        }
        return parsed.copy(slots = mergedSlots).enforceFixedRoles()
    }

    fun serializeEclipseConfig(cfg: EclipseConfig): String = cfg.toJson()

    fun parseTempleConfig(jsonStr: String): TempleConfig {
        val parsed = try {
            TempleConfig.fromJson(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing TempleConfig, falling back to default: ${e.message}")
            TempleConfig()
        }
        val defaultSlots = TempleConfig.defaultSlots()
        val mergedSlots = defaultSlots.toMutableMap()
        parsed.slots.forEach { (k, v) ->
            val def = defaultSlots[k] ?: SlotConfig()
            mergedSlots[k] = v.copy(
                charClass = v.charClass.ifBlank { def.charClass },
                role = v.role.ifBlank { def.role },
                defaultTarget = v.defaultTarget.ifBlank { def.defaultTarget }
            )
        }
        return parsed.copy(slots = mergedSlots)
    }

    fun serializeTempleConfig(cfg: TempleConfig): String = cfg.toJson()

    fun parseSlaveryConfig(jsonStr: String): SlaveryConfig {
        val parsed = try {
            SlaveryConfig.fromJson(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing SlaveryConfig, falling back to default: ${e.message}")
            SlaveryConfig()
        }
        val defaultSlots = SlaveryConfig.defaultSlots()
        val mergedSlots = defaultSlots.toMutableMap()
        parsed.slots.forEach { (k, v) ->
            val def = defaultSlots[k] ?: SlaveSlotConfig()
            mergedSlots[k] = v.copy(
                charClass = v.charClass.ifBlank { def.charClass },
                skills = v.skills.ifEmpty { def.skills }
            )
        }
        val lockedZones = parsed.lockedZones.ifEmpty { SlaveryConfig.defaultLockedZones() }
        return parsed.copy(slots = mergedSlots, lockedZones = lockedZones)
    }

    fun serializeSlaveryConfig(cfg: SlaveryConfig): String = cfg.toJson()

    fun parseDoomConfig(jsonStr: String): WeeklyDoomConfig {
        val parsed = try {
            WeeklyDoomConfig.fromJson(jsonStr)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error deserializing WeeklyDoomConfig, falling back to default: ${e.message}"
            )
            WeeklyDoomConfig()
        }
        val accounts = parsed.accounts.ifEmpty { listOf(DoomAccount()) }
        return parsed.copy(accounts = accounts)
    }

    fun serializeDoomConfig(cfg: WeeklyDoomConfig): String = cfg.toJson()

    fun parseGeneralConfig(jsonStr: String): GeneralBotConfig {
        return try {
            GeneralBotConfig.fromJson(jsonStr)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error deserializing GeneralBotConfig, falling back to default: ${e.message}"
            )
            GeneralBotConfig()
        }
    }

    fun serializeGeneralConfig(cfg: GeneralBotConfig): String = cfg.toJson()
}
