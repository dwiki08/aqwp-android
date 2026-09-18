package froztt13.python.aqw.data.model

import froztt13.python.aqw.domain.model.AqwAura

data class SlotTelemetry(
    val running: Boolean = false,
    val isConnected: Boolean = false,
    val isPaused: Boolean = false,
    val map: String = "-",
    val cell: String = "-",
    val pad: String = "-",
    val hp: Int = 0,
    val maxHp: Int = 0,
    val mp: Int = 0,
    val maxMp: Int = 0,
    val isDead: Boolean = false,
    val isInCombat: Boolean = false,
    val isNextTaunter: Boolean = false,
    val isPendingTaunt: Boolean = false,
    val cooldowns: Map<Int, Double> = emptyMap(),
    val tauntError: Boolean = false,
    val soeQty: Int = 0,
    val monsters: List<MonsterTelemetry> = emptyList(),
    val targetMonsters: String = "",
    val targetedMonster: String = "",
    val auras: List<AqwAura> = emptyList()
)
