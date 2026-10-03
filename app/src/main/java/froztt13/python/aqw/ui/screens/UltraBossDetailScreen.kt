package froztt13.python.aqw.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.PartyStats
import froztt13.python.aqw.data.model.SlotConfig
import froztt13.python.aqw.data.model.SlotTelemetry
import froztt13.python.aqw.data.model.UltraBossConfig
import froztt13.python.aqw.data.model.UltraBossData
import froztt13.python.aqw.data.model.UltraBossInfo
import froztt13.python.aqw.data.model.UltraBossType
import froztt13.python.aqw.ui.components.BotSessionStatsBar
import froztt13.python.aqw.ui.components.CustomOutlinedTextField
import froztt13.python.aqw.ui.components.DefaultTopBar
import froztt13.python.aqw.ui.components.LiveLogConsole
import froztt13.python.aqw.ui.components.LocalNavigateToPlayerState
import froztt13.python.aqw.ui.components.MonsterTelemetryCard
import froztt13.python.aqw.ui.components.ServerDropdown
import froztt13.python.aqw.ui.components.SlotCard
import froztt13.python.aqw.ui.components.TopBarSettingsButton
import froztt13.python.aqw.ui.theme.BgDark
import froztt13.python.aqw.ui.theme.BorderDark
import froztt13.python.aqw.ui.theme.BorderLight
import froztt13.python.aqw.ui.theme.CardDark
import froztt13.python.aqw.ui.theme.ErrorRed
import froztt13.python.aqw.ui.theme.MyApplicationTheme
import froztt13.python.aqw.ui.theme.SuccessGreen
import froztt13.python.aqw.ui.theme.SunGold
import froztt13.python.aqw.ui.theme.TextMuted
import froztt13.python.aqw.ui.theme.TextPrimary
import froztt13.python.aqw.ui.theme.TextSecondary
import froztt13.python.aqw.viewmodel.UltraBossViewModel
import kotlinx.coroutines.launch

@Composable
fun UltraBossDetailScreen(
    bossType: UltraBossType,
    onBack: () -> Unit,
    onNavigateToPlayerState: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: UltraBossViewModel = viewModel()
) {
    val config by viewModel.ultraBossConfig.collectAsState()
    val telemetryMap by viewModel.telemetryMap.collectAsState()
    val partyStats by viewModel.partyStats.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val slotLogs by viewModel.slotLogs.collectAsState()
    val animMsg by viewModel.animMsg.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val isFinished by viewModel.isFinished.collectAsState()

    var showFinishedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(bossType) {
        viewModel.selectBossTab(bossType)
    }

    LaunchedEffect(isFinished) {
        if (isFinished) {
            showFinishedDialog = true
        }
    }

    if (showFinishedDialog) {
        AlertDialog(
            onDismissRequest = {
                showFinishedDialog = false
                viewModel.resetFinishedState()
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Finished",
                    tint = SuccessGreen,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Bot Execution Completed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Ultra Gramiel has been defeated and Quest ID 10301 has been completed!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishedDialog = false
                        viewModel.resetFinishedState()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CardDark,
            shape = RoundedCornerShape(16.dp)
        )
    }

    UltraBossDetailContent(
        modifier = modifier,
        bossType = bossType,
        config = config,
        telemetryMap = telemetryMap,
        partyStats = partyStats,
        logs = logs,
        slotLogs = slotLogs,
        animMsg = animMsg,
        isRunning = isRunning,
        isPaused = isPaused,
        onBack = onBack,
        onNavigateToPlayerState = onNavigateToPlayerState,
        onUpdateSettings = { server, room, autoPotions, useScroll ->
            viewModel.updateSettings(server, room, autoPotions, useScroll)
        },
        onUpdateSlot = { slotKey, slotConfig ->
            viewModel.updateSlot(slotKey, slotConfig)
        },
        onStartBot = { viewModel.startBot() },
        onStopBot = { viewModel.stopBot() },
        onTogglePause = { viewModel.togglePause() },
        onClearLogs = { viewModel.clearLogs() },
        onClearSlotLogs = { slotKey -> viewModel.clearSlotLogs(slotKey) }
    )
}

@Composable
fun UltraBossDetailContent(
    modifier: Modifier = Modifier,
    bossType: UltraBossType,
    config: UltraBossConfig,
    telemetryMap: Map<String, SlotTelemetry>,
    partyStats: PartyStats,
    logs: List<LogEntry>,
    slotLogs: Map<String, List<LogEntry>> = emptyMap(),
    animMsg: String = "",
    isRunning: Boolean,
    isPaused: Boolean,
    onBack: () -> Unit,
    onNavigateToPlayerState: (() -> Unit)? = null,
    onUpdateSettings: (String, Int, Boolean, Boolean) -> Unit,
    onUpdateSlot: (String, SlotConfig) -> Unit,
    onStartBot: () -> Unit,
    onStopBot: () -> Unit,
    onTogglePause: () -> Unit,
    onClearLogs: () -> Unit,
    onClearSlotLogs: (String) -> Unit = {}
) {
    var showSettingsPanel by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val bossInfo = UltraBossData.getInfo(bossType)
    val bossAccentColor = Color(bossInfo.colorHex)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BgDark,
        topBar = {
            DefaultTopBar(
                title = "${bossInfo.title.uppercase()} BOT",
                version = "v0.1",
                statusDotColor = if (isRunning) SuccessGreen else bossAccentColor,
                onBack = onBack,
                actions = {
                    TopBarSettingsButton(
                        active = showSettingsPanel,
                        onClick = { showSettingsPanel = !showSettingsPanel },
                        tintColor = bossAccentColor
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Settings Panel
            AnimatedVisibility(
                visible = showSettingsPanel,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                UltraBossSettingsCard(
                    config = config,
                    accentColor = bossAccentColor,
                    onUpdateSettings = onUpdateSettings
                )
            }

            // Boss Detail Header Card
            UltraBossDetailHeaderCard(
                bossInfo = bossInfo,
                accentColor = bossAccentColor,
                roomNumber = config.roomNumber,
                isRunning = isRunning
            )

            // Control Action Bar
            UltraBossControlBar(
                isRunning = isRunning,
                isPaused = isPaused,
                accentColor = bossAccentColor,
                onStart = onStartBot,
                onStop = onStopBot,
                onTogglePause = onTogglePause
            )

            // Session Stats Bar
            BotSessionStatsBar(
                stats = partyStats,
                isRunning = isRunning,
                isPaused = isPaused,
                botType = bossInfo.title,
                accentColor = bossAccentColor,
                showActionButton = false
            )

            // Boss Animation / Event Message Card
            UltraBossAnimMsgCard(
                animMsg = animMsg,
                accentColor = bossAccentColor,
                isRunning = isRunning
            )

            // View Full Player State Action Button
            val navToPlayer = onNavigateToPlayerState ?: LocalNavigateToPlayerState.current
            if (navToPlayer != null) {
                OutlinedButton(
                    onClick = navToPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        (if (isRunning) bossAccentColor else BorderLight).copy(alpha = 0.4f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = bossAccentColor.copy(alpha = 0.08f),
                        contentColor = bossAccentColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = bossAccentColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VIEW FULL PLAYER STATE & AURAS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = bossAccentColor
                    )
                }
            }

            // Monster Telemetry Card (When Bot is Running)
            if (isRunning) {
                val activeSlotTelemetry = telemetryMap["slot1"] ?: SlotTelemetry()
                if (activeSlotTelemetry.monsters.isNotEmpty()) {
                    MonsterTelemetryCard(
                        monsters = activeSlotTelemetry.monsters,
                        currentCell = activeSlotTelemetry.cell
                    )
                }
            }

            // Party Slot ViewPager section (SlotCard + Slot Console Logs in sync)
            PartySlotTabView(
                config = config,
                telemetryMap = telemetryMap,
                logs = logs,
                slotLogs = slotLogs,
                isRunning = isRunning,
                isPaused = isPaused,
                accentColor = bossAccentColor,
                showTauntToggle = bossType != UltraBossType.GRAMIEL,
                onUpdateSlot = onUpdateSlot,
                onClearSlotLogs = onClearSlotLogs
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun UltraBossSettingsCard(
    config: UltraBossConfig,
    accentColor: Color,
    onUpdateSettings: (String, Int, Boolean, Boolean) -> Unit
) {
    var server by remember(config.server) { mutableStateOf(config.server) }
    var roomText by remember(config.roomNumber) { mutableStateOf(config.roomNumber.toString()) }
    var autoPotions by remember(config.autoPotions) { mutableStateOf(config.autoPotions) }
    var useScroll by remember(config.useScrollOfEnrage) { mutableStateOf(config.useScrollOfEnrage) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "GLOBAL ULTRA BOSS SETTINGS",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                letterSpacing = 0.5.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1.2f)) {
                    ServerDropdown(
                        selectedServer = server,
                        enabled = true,
                        onServerSelected = { newServer ->
                            server = newServer
                            onUpdateSettings(
                                server,
                                roomText.toIntOrNull() ?: config.roomNumber,
                                autoPotions,
                                useScroll
                            )
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    CustomOutlinedTextField(
                        value = roomText,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }
                            roomText = digits
                            val parsed = digits.toIntOrNull()
                            if (parsed != null) {
                                onUpdateSettings(server, parsed, autoPotions, useScroll)
                            }
                        },
                        label = { Text("Room Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Auto Potions & Tonics",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Use Tonic of Phantasm & Felicitous Philtre",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = autoPotions,
                    onCheckedChange = { checked ->
                        autoPotions = checked
                        onUpdateSettings(
                            server,
                            roomText.toIntOrNull() ?: config.roomNumber,
                            autoPotions,
                            useScroll
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Scroll of Enrage (Taunt)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Auto-equip & loop Taunt skill scroll",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = useScroll,
                    onCheckedChange = { checked ->
                        useScroll = checked
                        onUpdateSettings(
                            server,
                            roomText.toIntOrNull() ?: config.roomNumber,
                            autoPotions,
                            useScroll
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor
                    )
                )
            }
        }
    }
}

@Composable
fun UltraBossControlBar(
    isRunning: Boolean,
    isPaused: Boolean,
    accentColor: Color,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTogglePause: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isRunning) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "START ULTRA PARTY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STOP BOT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onTogglePause,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
                        tint = SunGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPaused) "RESUME" else "PAUSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SunGold
                    )
                }
            }
        }
    }
}

@Composable
fun PartySlotTabView(
    config: UltraBossConfig,
    telemetryMap: Map<String, SlotTelemetry>,
    logs: List<LogEntry>,
    slotLogs: Map<String, List<LogEntry>>,
    isRunning: Boolean,
    isPaused: Boolean,
    accentColor: Color,
    showTauntToggle: Boolean = true,
    onUpdateSlot: (String, SlotConfig) -> Unit,
    onClearSlotLogs: (String) -> Unit
) {
    val slotKeys = listOf("slot1", "slot2", "slot3", "slot4")
    val slotLabels =
        listOf("Slot 1 (Master)", "Slot 2 (Slave 1)", "Slot 3 (Slave 2)", "Slot 4 (Slave 3)")

    val pagerState = rememberPagerState(pageCount = { slotKeys.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tab Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            slotLabels.forEachIndexed { index, _ ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (isSelected) accentColor.copy(alpha = 0.6f) else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Slot ${index + 1}",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) TextPrimary else TextSecondary
                    )
                }
            }
        }

        // HorizontalPager Content (SlotCard + Slot LiveLogConsole in sync per page)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val slotKey = slotKeys[page]
            val slotTitle = slotLabels[page]
            val slotConf = config.slots[slotKey] ?: SlotConfig()
            val slotTel = telemetryMap[slotKey] ?: SlotTelemetry()
            val slotUsername = slotConf.username.trim()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Active Slot Configuration Card
                SlotCard(
                    slotKey = slotKey,
                    title = if (isRunning) slotConf.username.ifEmpty { slotTitle } else slotTitle,
                    config = slotConf,
                    telemetry = slotTel,
                    isPartyRunning = isRunning,
                    isPaused = isPaused,
                    accentColor = accentColor,
                    showTauntToggle = showTauntToggle,
                    onConfigChange = { updated ->
                        onUpdateSlot(slotKey, updated)
                    }
                )

                // 3. Dedicated Live Log Console for this Slot
                val currentSlotLogs = slotLogs[slotKey] ?: logs.filter { entry ->
                    entry.username.contains(slotKey, ignoreCase = true) ||
                            (slotUsername.isNotEmpty() && entry.username.contains(
                                slotUsername,
                                ignoreCase = true
                            ))
                }

                LiveLogConsole(
                    logs = currentSlotLogs,
                    slotKey = slotKey,
                    targetUsername = slotUsername,
                    title = "Console Logs - Slot ${page + 1}${if (slotUsername.isNotEmpty()) " ($slotUsername)" else ""}",
                    onClearLogs = { onClearSlotLogs(slotKey) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UltraBossDetailHeaderCard(
    bossInfo: UltraBossInfo,
    accentColor: Color,
    roomNumber: Int,
    isRunning: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                if (isRunning) SuccessGreen.copy(alpha = 0.6f) else accentColor.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Title + Map Tag + Insignia Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
            if (!isRunning)
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
        }
    }
}

@Composable
fun UltraBossAnimMsgCard(
    modifier: Modifier = Modifier,
    animMsg: String,
    accentColor: Color,
    isRunning: Boolean = true
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                            .background(if (isRunning) accentColor else TextMuted)
                    )
                    Text(
                        text = "ANIMATION / EVENT BROADCAST",
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
                        text = "LIVE EVENT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F111D))
                    .border(1.dp, Color(0xFF232840), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (animMsg.isNotBlank()) accentColor.copy(alpha = 0.15f) else Color(
                                0xFF1A1D2D
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "ANIM MSG",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (animMsg.isNotBlank()) accentColor else TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = animMsg.ifBlank { "● ● ●" },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (animMsg.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (animMsg.isNotBlank()) TextPrimary else TextMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(name = "Ultra Gramiel Detail Screen", showBackground = true, backgroundColor = 0xFF090A10)
@Composable
private fun UltraGramielDetailScreenPreview() {
    MyApplicationTheme {
        UltraBossDetailContent(
            bossType = UltraBossType.GRAMIEL,
            config = UltraBossConfig(),
            telemetryMap = mapOf(
                "slot1" to SlotTelemetry(running = true, hp = 2500, maxHp = 2500)
            ),
            partyStats = PartyStats(clearedCount = 3),
            logs = emptyList(),
            isRunning = false,
            isPaused = false,
            onBack = {},
            onUpdateSettings = { _, _, _, _ -> },
            onUpdateSlot = { _, _ -> },
            onStartBot = {},
            onStopBot = {},
            onTogglePause = {},
            onClearLogs = {}
        )
    }
}
