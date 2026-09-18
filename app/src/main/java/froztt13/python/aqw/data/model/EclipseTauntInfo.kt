package froztt13.python.aqw.data.model

data class TaunterTargetInfo(
    val nextSlot: String = "",
    val nextUsername: String = "",
    val pendingSlot: String? = null,
    val pendingUsername: String? = null,
    val waveCount: Int = 0
)

data class EclipseTauntInfo(
    val sunSide: TaunterTargetInfo = TaunterTargetInfo(nextSlot = "slot1"),
    val moonSide: TaunterTargetInfo = TaunterTargetInfo(nextSlot = "slot3"),
    val lightGather: TaunterTargetInfo = TaunterTargetInfo(nextSlot = "slot3"),
    val sunConverge: TaunterTargetInfo = TaunterTargetInfo(nextSlot = "slot1"),
    val moonConverge: TaunterTargetInfo = TaunterTargetInfo(nextSlot = "slot3"),
    val latestAnimMsg: String = "",
    val animMsgTimestamp: Long = 0L
)
