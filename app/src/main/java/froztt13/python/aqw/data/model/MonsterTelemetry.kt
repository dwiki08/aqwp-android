package froztt13.python.aqw.data.model

import froztt13.python.aqw.data.engine.AqwAura
import froztt13.python.aqw.data.engine.AqwMonster

data class MonsterTelemetry(
    val monMapId: String = "",
    val monName: String = "",
    val hp: Int = 0,
    val maxHp: Int = 0,
    val isAlive: Boolean = false,
    val auras: List<AqwAura> = emptyList()
) {
    val hpFraction: Float
        get() = if (maxHp > 0) (hp.toFloat() / maxHp.toFloat()).coerceIn(0f, 1f) else 0f
    val hpPercent: Int
        get() = if (maxHp > 0) ((hp.toDouble() / maxHp.toDouble()) * 100).toInt() else 0

    // Backward-compatible aliases matching AqwMonster naming
    val name: String get() = monName
    val currentHp: Int get() = hp
}

/**
 * Creates an immutable, thread-safe [MonsterTelemetry] snapshot from mutable [AqwMonster].
 */
fun AqwMonster.toTelemetry(): MonsterTelemetry = MonsterTelemetry(
    monMapId = this.monMapId,
    monName = this.name,
    hp = this.currentHp,
    maxHp = this.maxHp,
    isAlive = this.isAlive,
    auras = this.auras.toList()
)

