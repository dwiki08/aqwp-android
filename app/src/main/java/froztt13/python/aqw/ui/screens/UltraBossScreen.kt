package froztt13.python.aqw.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import froztt13.python.aqw.data.model.UltraBossConfig
import froztt13.python.aqw.data.model.UltraBossData
import froztt13.python.aqw.data.model.UltraBossInfo
import froztt13.python.aqw.ui.components.DefaultTopBar
import froztt13.python.aqw.ui.components.TopBarSettingsButton
import froztt13.python.aqw.ui.theme.BgDark
import froztt13.python.aqw.ui.theme.BorderDark
import froztt13.python.aqw.ui.theme.CardDark
import froztt13.python.aqw.ui.theme.MyApplicationTheme
import froztt13.python.aqw.ui.theme.PrimaryPurple
import froztt13.python.aqw.ui.theme.SuccessGreen
import froztt13.python.aqw.ui.theme.TextMuted
import froztt13.python.aqw.ui.theme.TextPrimary
import froztt13.python.aqw.ui.theme.TextSecondary
import froztt13.python.aqw.viewmodel.UltraBossViewModel

@Composable
fun UltraBossScreen(
    onBack: () -> Unit,
    onNavigateToGramiel: () -> Unit,
    onNavigateToMalgor: () -> Unit,
    onNavigateToDrakath: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UltraBossViewModel = viewModel()
) {
    val config by viewModel.ultraBossConfig.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    UltraBossDashboardContent(
        modifier = modifier,
        config = config,
        isRunning = isRunning,
        onBack = onBack,
        onNavigateToGramiel = onNavigateToGramiel,
        onNavigateToMalgor = onNavigateToMalgor,
        onNavigateToDrakath = onNavigateToDrakath,
        onUpdateSettings = { server, room, autoPotions, useScroll ->
            viewModel.updateSettings(server, room, autoPotions, useScroll)
        }
    )
}

@Composable
fun UltraBossDashboardContent(
    modifier: Modifier = Modifier,
    config: UltraBossConfig,
    isRunning: Boolean,
    onBack: () -> Unit,
    onNavigateToGramiel: () -> Unit,
    onNavigateToMalgor: () -> Unit,
    onNavigateToDrakath: () -> Unit,
    onUpdateSettings: (String, Int, Boolean, Boolean) -> Unit
) {
    var showSettingsPanel by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BgDark,
        topBar = {
            DefaultTopBar(
                title = "ULTRA BOSS HUB",
                version = "v0.1",
                statusDotColor = if (isRunning) SuccessGreen else PrimaryPurple,
                onBack = onBack,
                actions = {
                    TopBarSettingsButton(
                        active = showSettingsPanel,
                        onClick = { showSettingsPanel = !showSettingsPanel },
                        tintColor = PrimaryPurple
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Global Settings Panel Toggle
            AnimatedVisibility(
                visible = showSettingsPanel,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                UltraBossSettingsCard(
                    config = config,
                    accentColor = PrimaryPurple,
                    onUpdateSettings = onUpdateSettings
                )
            }

            // Header Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Ultra Raid Modules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Select an endgame Ultra Boss to configure party slots & launch automation",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Boss Cards
            UltraBossMenuCard(
                bossInfo = UltraBossData.GRAMIEL_INFO,
                roomNumber = config.roomNumber,
                onClick = onNavigateToGramiel
            )

            UltraBossMenuCard(
                bossInfo = UltraBossData.MALGOR_INFO,
                roomNumber = config.roomNumber,
                onClick = onNavigateToMalgor
            )

            UltraBossMenuCard(
                bossInfo = UltraBossData.DRAKATH_INFO,
                roomNumber = config.roomNumber,
                onClick = onNavigateToDrakath
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UltraBossMenuCard(
    bossInfo: UltraBossInfo,
    roomNumber: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(bossInfo.colorHex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.5.dp,
                accentColor.copy(alpha = 0.35f),
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Icon + Title + Room Tag + Insignia Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = bossInfo.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF131522))
                                .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "/join ${bossInfo.mapName}-$roomNumber",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MilitaryTech,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = bossInfo.insigniaName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }
            }

            // Recommended Classes Badges
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Recommended Party Comp:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bossInfo.recommendedClasses.forEach { className ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF131522))
                                .border(
                                    1.dp,
                                    accentColor.copy(alpha = 0.3f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = className,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Key Combat Rules
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Key Combat Rules:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
                bossInfo.mechanics.forEach { mechanic ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 2.dp)
                        )
                        Text(
                            text = mechanic,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Action Button
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor.copy(alpha = 0.2f),
                    contentColor = accentColor
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = Brush.horizontalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.5f),
                            accentColor.copy(alpha = 0.2f)
                        )
                    )
                )
            ) {
                Text(
                    text = "OPEN ${bossInfo.title.uppercase()} BOT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = accentColor
                )
            }
        }
    }
}

@Preview(name = "Ultra Boss Hub Dashboard", showBackground = true, backgroundColor = 0xFF090A10)
@Composable
private fun UltraBossDashboardContentPreview() {
    MyApplicationTheme {
        UltraBossDashboardContent(
            config = UltraBossConfig(),
            isRunning = false,
            onBack = {},
            onNavigateToGramiel = {},
            onNavigateToMalgor = {},
            onNavigateToDrakath = {},
            onUpdateSettings = { _, _, _, _ -> }
        )
    }
}
