package froztt13.python.aqw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import froztt13.python.aqw.data.EclipseTauntInfo
import froztt13.python.aqw.data.TaunterTargetInfo
import froztt13.python.aqw.ui.theme.BorderDark
import froztt13.python.aqw.ui.theme.EclipseMagenta
import froztt13.python.aqw.ui.theme.ErrorRed
import froztt13.python.aqw.ui.theme.MoonCyan
import froztt13.python.aqw.ui.theme.SunGold
import froztt13.python.aqw.ui.theme.SurfaceDark
import froztt13.python.aqw.ui.theme.TextMuted
import froztt13.python.aqw.ui.theme.TextPrimary
import froztt13.python.aqw.ui.theme.TextSecondary

@Composable
fun EclipseTauntOverviewCard(
    modifier: Modifier = Modifier,
    tauntInfo: EclipseTauntInfo,
    isRunning: Boolean = true
) {

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) SunGold else TextMuted)
                    )
                    Text(
                        text = "MISCELLANEOUS / TAUNT ROTATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF22273D))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIVE QUEUE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(
                color = Color(0xFF1E2338),
                thickness = 1.dp
            )

//            // Sun Side Row
//            TauntTargetRow(
//                title = "SUN SIDE",
//                monsterName = "Sunset Knight",
//                accentColor = SunGold,
//                targetInfo = tauntInfo.sunSide,
//                defaultSlotLabel = "P1 / P2"
//            )
//
//            HorizontalDivider(
//                color = Color(0xFF1E2338),
//                thickness = 1.dp
//            )
//
//            // Moon Side Row
//            TauntTargetRow(
//                title = "MOON SIDE",
//                monsterName = "Moon Haze",
//                accentColor = MoonCyan,
//                targetInfo = tauntInfo.moonSide,
//                defaultSlotLabel = "P3 / P4"
//            )
//
//            HorizontalDivider(
//                color = Color(0xFF1E2338),
//                thickness = 1.dp
//            )

            // Light Gather Row
            TauntTargetRow(
                title = "GATHER EVENT",
                monsterName = "Suffocated Light",
                accentColor = EclipseMagenta,
                targetInfo = tauntInfo.lightGather,
                defaultSlotLabel = "P3 / P4"
            )

            HorizontalDivider(
                color = Color(0xFF1E2338),
                thickness = 1.dp
            )

            // Boss Animation / Event Message Section (event.animMsgs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F111D))
                    .border(1.dp, Color(0xFF232840), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (tauntInfo.latestAnimMsg.isNotBlank()) MoonCyan.copy(alpha = 0.15f) else Color(
                                0xFF1A1D2D
                            )
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ANIM MSG",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tauntInfo.latestAnimMsg.isNotBlank()) MoonCyan else TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = tauntInfo.latestAnimMsg.ifBlank { "● ● ●" },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (tauntInfo.latestAnimMsg.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (tauntInfo.latestAnimMsg.isNotBlank()) TextPrimary else TextMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TauntTargetRow(
    title: String,
    monsterName: String,
    accentColor: Color,
    targetInfo: TaunterTargetInfo,
    defaultSlotLabel: String
) {
    val nextSlotFormatted = formatSlotLabel(targetInfo.nextSlot, targetInfo.nextUsername)
    val hasPending = !targetInfo.pendingSlot.isNullOrBlank()
    val pendingFormatted = if (hasPending) {
        formatSlotLabel(targetInfo.pendingSlot, targetInfo.pendingUsername ?: "")
    } else {
        "None"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Role & Target Monster
        Column(
            modifier = Modifier.weight(1.1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (targetInfo.waveCount > 0) {
                    Text(
                        text = "#${targetInfo.waveCount}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                text = monsterName,
                fontSize = 11.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Center Column: Next Taunter
        Column(
            modifier = Modifier.weight(1.2f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "NEXT TAUNTER",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = nextSlotFormatted.ifBlank { defaultSlotLabel },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right Column: Pending Taunt Status
        Column(
            modifier = Modifier.weight(1.1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "PENDING",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (hasPending) ErrorRed.copy(alpha = 0.2f) else Color(0xFF1B1E30))
                    .border(
                        1.dp,
                        if (hasPending) ErrorRed.copy(alpha = 0.6f) else Color(0xFF2E3350),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (hasPending) pendingFormatted else "None",
                    fontSize = 10.sp,
                    fontWeight = if (hasPending) FontWeight.Bold else FontWeight.Normal,
                    color = if (hasPending) ErrorRed else TextMuted
                )
            }
        }
    }
}

private fun formatSlotLabel(slotKey: String, username: String): String {
    val slotPrefix = when (slotKey.lowercase()) {
        "slot1" -> "P1"
        "slot2" -> "P2"
        "slot3" -> "P3"
        "slot4" -> "P4"
        else -> slotKey.uppercase()
    }
    return if (username.isNotBlank() && username != slotKey) {
        "$slotPrefix ($username)"
    } else {
        slotPrefix
    }
}
