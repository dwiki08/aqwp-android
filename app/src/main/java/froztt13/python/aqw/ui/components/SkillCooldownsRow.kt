package froztt13.python.aqw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import froztt13.python.aqw.ui.theme.GeneralTeal
import froztt13.python.aqw.ui.theme.MyApplicationTheme
import froztt13.python.aqw.ui.theme.SunGold
import froztt13.python.aqw.ui.theme.TextMuted
import froztt13.python.aqw.ui.theme.TextPrimary

@Composable
fun SkillCooldownsRow(
    cooldowns: Map<Int, Double>,
    modifier: Modifier = Modifier,
    accentColor: Color = GeneralTeal
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        for (i in 0..5) {
            val cd = cooldowns[i] ?: 0.0
            val isReady = cd <= 0.0
            val isItem = i == 5
            val label = if (isItem) "Item" else "$i"

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                !isReady -> Color(0xFF1E2130)
                                isItem -> SunGold.copy(alpha = 0.25f)
                                else -> accentColor.copy(alpha = 0.2f)
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                !isReady -> Color(0xFF2E3350)
                                isItem -> SunGold.copy(alpha = 0.6f)
                                else -> accentColor.copy(alpha = 0.5f)
                            },
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isReady) label else String.format(
                            java.util.Locale.US,
                            "%.1f",
                            cd
                        ),
                        fontSize = if (isReady) 11.sp else 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            !isReady -> TextMuted
                            isItem -> SunGold
                            else -> TextPrimary
                        }
                    )
                }
                Text(
                    text = if (isItem) "Pot" else if (i == 0) "Auto" else "S$i",
                    fontSize = 8.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D14)
@Composable
private fun SkillCooldownsRowPreview() {
    MyApplicationTheme {
        SkillCooldownsRow(
            cooldowns = mapOf(
                0 to 0.0,
                1 to 2.3,
                2 to 0.0,
                3 to 5.8,
                4 to 0.0,
                5 to 12.0
            )
        )
    }
}
