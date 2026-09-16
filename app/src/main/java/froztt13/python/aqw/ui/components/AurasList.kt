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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import froztt13.python.aqw.core.model.AqwAura
import froztt13.python.aqw.ui.theme.ErrorRed
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
    val badgeBg = if (isExpired) ErrorRed.copy(alpha = 0.15f) else accentColor.copy(alpha = 0.2f)
    val badgeBorder = if (isExpired) ErrorRed.copy(alpha = 0.4f) else accentColor.copy(alpha = 0.4f)
    val textColor = if (isExpired) ErrorRed else accentColor

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeBg)
            .border(1.dp, badgeBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = aura.name.capitalizeWords(),
                fontSize = 11.sp,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )

            // Aura count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.3f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "x${aura.count}",
                    fontSize = 10.sp,
                    color = SunGold,
                    fontWeight = FontWeight.Bold
                )
            }

            // Time to expired badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isExpired) ErrorRed.copy(alpha = 0.3f)
                        else Color.Black.copy(alpha = 0.3f)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = timeText,
                    fontSize = 10.sp,
                    color = if (isExpired) ErrorRed else TextSecondary,
                    fontWeight = FontWeight.Medium
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
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

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
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            auras.forEach { aura ->
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
