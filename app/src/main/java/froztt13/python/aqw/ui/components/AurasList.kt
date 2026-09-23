package froztt13.python.aqw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import froztt13.python.aqw.domain.model.AqwAura
import froztt13.python.aqw.ui.theme.ErrorRed
import froztt13.python.aqw.ui.theme.MyApplicationTheme
import froztt13.python.aqw.ui.theme.PrimaryPurple
import froztt13.python.aqw.ui.theme.SunGold
import froztt13.python.aqw.ui.theme.TextMuted
import froztt13.python.aqw.ui.theme.TextPrimary
import froztt13.python.aqw.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private fun String.capitalizeWords(): String {
    if (isBlank()) return this
    return split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

@Composable
fun AuraBadge(
    aura: AqwAura,
    modifier: Modifier = Modifier,
    accentColor: Color = PrimaryPurple,
    currentTime: Long = System.currentTimeMillis()
) {
    val isFocus = aura.name.equals("Focus", ignoreCase = true)
    val remainingMs = if (aura.expiredAt > 0L) {
        maxOf(0L, aura.expiredAt - currentTime)
    } else {
        -1L
    }

    val timeText = when {
        remainingMs < 0L -> "∞"
        remainingMs == 0L -> "0s"
        else -> {
            val totalSec = (remainingMs + 999) / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            if (min > 0) "${min}m ${sec}s" else "${sec}s"
        }
    }

    val isExpired = remainingMs == 0L
    val badgeBg = when {
        isExpired -> ErrorRed.copy(alpha = 0.15f)
        isFocus -> SunGold.copy(alpha = 0.25f)
        else -> accentColor.copy(alpha = 0.2f)
    }
    val badgeBorder = when {
        isExpired -> ErrorRed.copy(alpha = 0.4f)
        isFocus -> SunGold.copy(alpha = 0.85f)
        else -> accentColor.copy(alpha = 0.4f)
    }
    val borderWidth = if (isFocus) 1.5.dp else 1.dp
    val textColor = when {
        isExpired -> ErrorRed
        isFocus -> SunGold
        else -> accentColor
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeBg)
            .border(borderWidth, badgeBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isFocus) {
                Text(
                    text = "🎯",
                    fontSize = 11.sp
                )
            }

            Text(
                text = aura.name.capitalizeWords(),
                fontSize = 11.sp,
                color = textColor,
                fontWeight = if (isFocus) FontWeight.Bold else FontWeight.SemiBold
            )

            // Aura count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isFocus) SunGold.copy(alpha = 0.35f)
                        else accentColor.copy(alpha = 0.3f)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "x${aura.count}",
                    fontSize = 10.sp,
                    color = if (isFocus) Color.White else SunGold,
                    fontWeight = FontWeight.Bold
                )
            }

            // Time to expired badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isExpired -> ErrorRed.copy(alpha = 0.3f)
                            isFocus -> SunGold.copy(alpha = 0.35f)
                            else -> Color.Black.copy(alpha = 0.3f)
                        }
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = timeText,
                    fontSize = 10.sp,
                    color = when {
                        isExpired -> ErrorRed
                        isFocus -> Color.White
                        else -> TextSecondary
                    },
                    fontWeight = if (isFocus) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AurasList(
    auras: List<AqwAura>,
    modifier: Modifier = Modifier,
    accentColor: Color = PrimaryPurple,
    emptyText: String = "No active auras currently applied."
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(500.milliseconds)
            currentTime = System.currentTimeMillis()
        }
    }

    if (auras.isEmpty()) {
        Text(
            text = emptyText,
            fontSize = 11.sp,
            color = TextMuted,
            modifier = modifier.padding(start = 2.dp, top = 2.dp)
        )
    } else {
        val displayAuras = remember(auras) {
            auras.sortedWith(compareByDescending { it.name.equals("Focus", ignoreCase = true) })
        }
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            displayAuras.forEach { aura ->
                AuraBadge(
                    aura = aura,
                    accentColor = accentColor,
                    currentTime = currentTime
                )
            }
        }
    }
}

@Composable
@JvmName("AurasListStrings")
fun AurasList(
    auras: List<String>,
    modifier: Modifier = Modifier,
    accentColor: Color = PrimaryPurple,
    emptyText: String = "No active auras currently applied."
) {
    AurasList(
        auras = auras.map { AqwAura(name = it) },
        modifier = modifier,
        accentColor = accentColor,
        emptyText = emptyText
    )
}

@Composable
fun AurasSection(
    auras: List<AqwAura>,
    modifier: Modifier = Modifier,
    title: String = "Active Auras / Buffs",
    accentColor: Color = PrimaryPurple,
    emptyText: String = "No active auras currently applied."
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "$title (${auras.size})",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        AurasList(
            auras = auras,
            accentColor = accentColor,
            emptyText = emptyText
        )
    }
}

@Composable
@JvmName("AurasSectionStrings")
fun AurasSection(
    auras: List<String>,
    modifier: Modifier = Modifier,
    title: String = "Active Auras / Buffs",
    accentColor: Color = PrimaryPurple,
    emptyText: String = "No active auras currently applied."
) {
    AurasSection(
        auras = auras.map { AqwAura(name = it) },
        modifier = modifier,
        title = title,
        accentColor = accentColor,
        emptyText = emptyText
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D14)
@Composable
private fun AurasListPreview() {
    MyApplicationTheme {
        AurasSection(
            auras = listOf(
                AqwAura(name = "Focus", count = 1, duration = 10),
                AqwAura(name = "Solar Flare", count = 1, duration = 15),
                AqwAura(name = "Sun's Heat", count = 3, duration = 30)
            )
        )
    }
}
