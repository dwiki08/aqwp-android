package froztt13.python.aqw.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import froztt13.python.aqw.data.engine.AqwSession
import froztt13.python.aqw.domain.model.AqwAura
import froztt13.python.aqw.domain.model.AqwFaction
import froztt13.python.aqw.domain.model.AqwItem
import froztt13.python.aqw.domain.model.AqwOtherPlayer
import froztt13.python.aqw.domain.model.AqwPlayerState
import froztt13.python.aqw.domain.model.AqwQuest
import froztt13.python.aqw.domain.model.AqwShop
import froztt13.python.aqw.domain.model.AqwSkill
import froztt13.python.aqw.ui.components.AurasSection
import froztt13.python.aqw.ui.components.CustomOutlinedTextField
import froztt13.python.aqw.ui.components.DefaultTopBar
import froztt13.python.aqw.ui.theme.BgDark
import froztt13.python.aqw.ui.theme.BorderDark
import froztt13.python.aqw.ui.theme.CardDark
import froztt13.python.aqw.ui.theme.DoomCrimson
import froztt13.python.aqw.ui.theme.DoomGold
import froztt13.python.aqw.ui.theme.ErrorRed
import froztt13.python.aqw.ui.theme.GeneralTeal
import froztt13.python.aqw.ui.theme.LegionBlue
import froztt13.python.aqw.ui.theme.MyApplicationTheme
import froztt13.python.aqw.ui.theme.PrimaryPurple
import froztt13.python.aqw.ui.theme.SuccessGreen
import froztt13.python.aqw.ui.theme.SunGold
import froztt13.python.aqw.ui.theme.SurfaceDark
import froztt13.python.aqw.ui.theme.TextMuted
import froztt13.python.aqw.ui.theme.TextPrimary
import froztt13.python.aqw.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlayerStateScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSessions = remember { AqwSession.activeSessions }
    var selectedSessionIndex by remember { mutableIntStateOf(0) }

    fun getCurrentState(): AqwPlayerState? {
        val session = activeSessions.getOrNull(selectedSessionIndex)
        return session?.playerState?.snapshot() ?: AqwSession.getPrimaryPlayerState()?.snapshot()
    }

    var playerState by remember { mutableStateOf(getCurrentState()) }
    var lastRefreshTime by remember {
        mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
    }

    fun checkIsBotRunning(): Boolean {
        val session = activeSessions.getOrNull(selectedSessionIndex)
        val sessionConnected =
            session?.isConnected?.value == true || AqwSession.activeSessions.any { it.isConnected.value }
        val generalRunning =
            froztt13.python.aqw.domain.bot.general.NativeGeneralBot.telemetry.value.running
        val eclipseRunning = froztt13.python.aqw.domain.bot.eclipse.NativeEclipseBot.isRunning
        val slaveryRunning = froztt13.python.aqw.domain.bot.slavery.NativeSlaveryBot.isRunning
        val templeRunning = froztt13.python.aqw.domain.bot.temple.NativeTempleBot.isRunning
        return sessionConnected || generalRunning || eclipseRunning || slaveryRunning || templeRunning
    }

    var isRunning by remember { mutableStateOf(checkIsBotRunning()) }

    fun refreshData() {
        playerState = getCurrentState()
        lastRefreshTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        isRunning = checkIsBotRunning()
    }

    // Auto-refresh every 2 seconds if bot is running
    LaunchedEffect(selectedSessionIndex) {
        while (isActive) {
            delay(2000.milliseconds)
            if (checkIsBotRunning()) {
                refreshData()
            } else {
                isRunning = false
            }
        }
    }

    val scope = rememberCoroutineScope()

    val accountNames = remember(activeSessions.size) {
        activeSessions.mapIndexed { index, session ->
            session.playerState.username.ifBlank { "Account ${index + 1}" }
        }
    }

    PlayerStateContent(
        playerState = playerState,
        accountNames = accountNames,
        selectedSessionIndex = selectedSessionIndex,
        lastRefreshTime = lastRefreshTime,
        isBotRunning = isRunning,
        onBack = onBack,
        onSelectSessionIndex = { index ->
            selectedSessionIndex = index
            refreshData()
        },
        onPickDrop = { itemId ->
            val session = activeSessions.getOrNull(selectedSessionIndex)
            if (session != null) {
                scope.launch(Dispatchers.IO) {
                    session.item.getItemDrop(itemId)
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun PlayerStateContent(
    modifier: Modifier = Modifier,
    playerState: AqwPlayerState?,
    accountNames: List<String> = emptyList(),
    selectedSessionIndex: Int = 0,
    lastRefreshTime: String = "",
    isBotRunning: Boolean = false,
    onBack: () -> Unit = {},
    onSelectSessionIndex: (Int) -> Unit = {},
    onPickDrop: (Int) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        containerColor = BgDark,
        topBar = {
            DefaultTopBar(
                title = "PLAYER STATE",
                onBack = onBack,
                containerColor = SurfaceDark,
                statusDotColor = if (playerState?.isDead == false) SuccessGreen else DoomCrimson
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Account Selector Chip Row (if multiple active sessions exist)
            if (accountNames.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accountNames.forEachIndexed { index, uName ->
                        FilterChip(
                            selected = selectedSessionIndex == index,
                            onClick = { onSelectSessionIndex(index) },
                            label = { Text(uName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeneralTeal.copy(alpha = 0.25f),
                                selectedLabelColor = GeneralTeal,
                                containerColor = CardDark,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }

            // Refresh Status Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Snapshot: $lastRefreshTime",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isBotRunning) SuccessGreen else TextMuted)
                    )
                    Text(
                        text = if (isBotRunning) "Auto (2s)" else "Bot Idle",
                        fontSize = 11.sp,
                        color = if (isBotRunning) SuccessGreen else TextMuted,
                        fontWeight = if (isBotRunning) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            if (playerState == null || playerState.username.isBlank()) {
                // Empty / Disconnected State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No Active Player Data",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isBotRunning)
                                    "Bot is running. Waiting for real-time telemetry from server..."
                                else
                                    "Connect an AQW bot or login to view real-time player telemetry, inventory, skills, and factions.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            } else {
                // Active Player Data
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        PlayerSummaryHeaderCard(state = playerState)
                    }

                    item {
                        PlayerVitalsCard(state = playerState)
                    }

                    item {
                        val tabs = listOf(
                            "Skills (${playerState.skills.size})",
                            "Inventory (${playerState.inventory.size})",
                            "Temp (${playerState.tempInventory.size})",
                            "Drops (${playerState.droppedItems.size})",
                            "Bank (${playerState.bank.size})",
                            "Quests & Shops",
                            "Factions (${playerState.factions.size})",
                            "Players (${playerState.playersInMap.size})"
                        )
                        ScrollableTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = SurfaceDark,
                            contentColor = TextPrimary,
                            edgePadding = 12.dp,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = GeneralTeal
                                )
                            }
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = {
                                        Text(
                                            text = title,
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedTab == index) GeneralTeal else TextSecondary
                                        )
                                    }
                                )
                            }
                        }
                    }

                    when (selectedTab) {
                        0 -> { // Skills & Auras
                            item { SkillsSection(skills = playerState.skills) }
                            item { AurasSection(auras = playerState.auras) }
                        }

                        1 -> { // Inventory
                            item {
                                ItemListSection(
                                    items = playerState.inventory,
                                    title = "Inventory Items"
                                )
                            }
                        }

                        2 -> { // Temp Inventory
                            item {
                                ItemListSection(
                                    items = playerState.tempInventory,
                                    title = "Temp Inventory Items"
                                )
                            }
                        }

                        3 -> { // Drops
                            item {
                                ItemListSection(
                                    items = playerState.droppedItems,
                                    title = "Dropped Items",
                                    actionLabel = "Pick Up",
                                    onItemAction = { item -> onPickDrop(item.itemId) }
                                )
                            }
                        }

                        4 -> { // Bank
                            item { ItemListSection(items = playerState.bank, title = "Bank Items") }
                        }

                        5 -> { // Quests & Shops
                            item {
                                QuestsSection(
                                    quests = playerState.loadedQuests,
                                    playerState = playerState
                                )
                            }
                            item { ShopsSection(shops = playerState.loadedShops) }
                        }

                        6 -> { // Factions
                            item { FactionsSection(factions = playerState.factions) }
                        }

                        7 -> { // Players in Room
                            item { PlayersInMapSection(players = playerState.playersInMap.values.toList()) }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSummaryHeaderCard(state: AqwPlayerState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder()
            .copy(brush = SolidColor(BorderDark))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = state.username.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "CharID: ${state.charId} | RoomUserID: ${state.roomUserId}",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusBadge(
                            text = if (state.isDead) "DEAD" else "ALIVE",
                            color = if (state.isDead) ErrorRed else SuccessGreen
                        )
                        StatusBadge(
                            text = if (state.isInCombat) "IN COMBAT" else "IDLE",
                            color = if (state.isInCombat) DoomGold else TextSecondary
                        )
                    }
                }
            }

            // Location details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Map: ${state.mapName.ifBlank { state.areaName.ifBlank { "Unknown" } }} (${state.areaId})",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Cell: ${state.cell} [${state.pad}]",
                    fontSize = 12.sp,
                    color = GeneralTeal,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PlayerVitalsCard(state: AqwPlayerState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder()
            .copy(brush = SolidColor(BorderDark))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // HP Bar
            val hpFraction =
                if (state.maxHp > 0) (state.currentHp.toFloat() / state.maxHp.toFloat()).coerceIn(
                    0f,
                    1f
                ) else 0f
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Health (HP)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        "${state.currentHp} / ${state.maxHp} (${(hpFraction * 100).toInt()}%)",
                        fontSize = 12.sp,
                        color = DoomCrimson,
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress = { hpFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = DoomCrimson,
                    trackColor = BorderDark,
                    strokeCap = StrokeCap.Round
                )
            }

            // MP Bar
            val mpFraction =
                if (state.maxMp > 0) (state.mp.toFloat() / state.maxMp.toFloat()).coerceIn(
                    0f,
                    1f
                ) else 0f
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Mana (MP)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        "${state.mp} / ${state.maxMp} (${(mpFraction * 100).toInt()}%)",
                        fontSize = 12.sp,
                        color = LegionBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress = { mpFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = LegionBlue,
                    trackColor = BorderDark,
                    strokeCap = StrokeCap.Round
                )
            }

            // Economic & Combat stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "Gold", value = "${state.gold}", color = SunGold)
                StatItem(label = "Gold Farmed", value = "+${state.goldFarmed}", color = DoomGold)
                StatItem(label = "Exp Farmed", value = "+${state.expFarmed}", color = PrimaryPurple)
                StatItem(
                    label = "CDR (Haste)",
                    value = "${(state.cdReduction * 100).toInt()}%",
                    color = GeneralTeal
                )
                StatItem(label = "Mana Cost", value = "${state.manaCost}x", color = LegionBlue)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = TextMuted)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SkillsSection(skills: List<AqwSkill>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Active Skills (${skills.size})",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        if (skills.isEmpty()) {
            Text("No active skills loaded.", fontSize = 12.sp, color = TextMuted)
        } else {
            skills.forEach { skill ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder()
                        .copy(brush = SolidColor(BorderDark))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(GeneralTeal.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${skill.index}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeneralTeal
                                )
                            }
                            Column {
                                Text(
                                    skill.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    "Ref: ${skill.id.ifBlank { "slot" }} | Target: ${skill.tgt} (max: ${skill.tgtMax})",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "CD: ${skill.cdSeconds}s",
                                fontSize = 11.sp,
                                color = SunGold,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "MP: ${skill.mpCost.toInt()}",
                                fontSize = 11.sp,
                                color = LegionBlue,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}


private fun String.capitalizeWords(): String {
    if (isBlank()) return this
    return split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

@Composable
private fun ItemListSection(
    items: List<AqwItem>,
    title: String,
    actionLabel: String? = null,
    onItemAction: ((AqwItem) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items else items.filter {
            it.name.contains(
                searchQuery,
                ignoreCase = true
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text("${filtered.size} / ${items.size} items", fontSize = 11.sp, color = TextMuted)
        }

        CustomOutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search items...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        if (filtered.isEmpty()) {
            Text("No items match your search.", fontSize = 12.sp, color = TextMuted)
        } else {
            filtered.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder()
                        .copy(brush = SolidColor(BorderDark))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    item.name.ifBlank { "Item ${item.itemId}" }.capitalizeWords(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                if (item.isCoins) {
                                    StatusBadge("AC", SunGold)
                                }
                                if (item.isEquipped) {
                                    StatusBadge("EQUIPPED", SuccessGreen)
                                }
                                if (item.isTemp) {
                                    StatusBadge("TEMP", PrimaryPurple)
                                }
                            }
                            Text(
                                text = "ItemID: ${item.itemId} | CharItemID: ${item.charItemId} | Type: ${item.sType.ifBlank { "-" }}",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${item.qty} / ${item.maxQty}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.qty >= item.maxQty && item.maxQty > 1) SunGold else TextPrimary
                            )
                            if (actionLabel != null && onItemAction != null) {
                                Button(
                                    onClick = { onItemAction(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GeneralTeal),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(
                                        horizontal = 10.dp,
                                        vertical = 2.dp
                                    ),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(
                                        text = actionLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BgDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactionsSection(factions: List<AqwFaction>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Factions & Reputation (${factions.size})",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        if (factions.isEmpty()) {
            Text("No factions loaded for this account.", fontSize = 12.sp, color = TextMuted)
        } else {
            factions.forEach { faction ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder()
                        .copy(brush = SolidColor(BorderDark))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                faction.name.ifBlank { "Faction ${faction.factionId}" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Rank ${faction.getRank()}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SunGold
                            )
                        }
                        val repPercent = (faction.rep.toFloat() / 302500f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { repPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = SunGold,
                            trackColor = BorderDark,
                            strokeCap = StrokeCap.Round
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "FactionID: ${faction.factionId}",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                            Text(
                                "${faction.rep} / 302,500 Rep",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestsSection(quests: List<AqwQuest>, playerState: AqwPlayerState? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Loaded Quests (${quests.size})",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        if (quests.isEmpty()) {
            Text("No quest definitions cached in session.", fontSize = 12.sp, color = TextMuted)
        } else {
            quests.forEach { quest ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder()
                        .copy(brush = SolidColor(BorderDark))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                quest.name.ifBlank { "Quest #${quest.questId}" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "#${quest.questId}",
                                fontSize = 12.sp,
                                color = GeneralTeal,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (quest.turnInItems.isNotEmpty()) {
                            Text(
                                "Requirements:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted
                            )
                            quest.turnInItems.forEach { req ->
                                val currentQty = playerState?.let { ps ->
                                    val invItem =
                                        ps.inventory.firstOrNull { it.itemId == req.itemId }
                                    val tempItem =
                                        ps.tempInventory.firstOrNull { it.itemId == req.itemId }
                                    (invItem?.qty ?: 0) + (tempItem?.qty ?: 0)
                                } ?: 0
                                val isComplete = currentQty >= req.qty
                                val itemName = req.name.ifBlank {
                                    playerState?.inventory?.firstOrNull { it.itemId == req.itemId }?.name
                                        ?: playerState?.tempInventory?.firstOrNull { it.itemId == req.itemId }?.name
                                        ?: "ItemID ${req.itemId}"
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${itemName.capitalizeWords()}",
                                        fontSize = 11.sp,
                                        color = if (isComplete) SuccessGreen else TextSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$currentQty / ${req.qty}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isComplete) SuccessGreen else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopsSection(shops: List<AqwShop>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Loaded Shops (${shops.size})",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        if (shops.isEmpty()) {
            Text("No shop definitions cached in session.", fontSize = 12.sp, color = TextMuted)
        } else {
            shops.forEach { shop ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder()
                        .copy(brush = SolidColor(BorderDark))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                shop.shopName.ifBlank { "Shop #${shop.shopId}" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Shop #${shop.shopId}",
                                fontSize = 12.sp,
                                color = SunGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "${shop.items.size} items listed in shop",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayersInMapSection(players: List<AqwOtherPlayer>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Other Players in Area (${players.size})",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        if (players.isEmpty()) {
            Text("No other players detected in current map.", fontSize = 12.sp, color = TextMuted)
        } else {
            players.forEach { other ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder()
                        .copy(brush = SolidColor(BorderDark))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                other.username,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                "Cell: ${other.cell} [${other.pad}]",
                                fontSize = 11.sp,
                                color = GeneralTeal
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "HP: ${other.hp} / ${other.maxHp}",
                                fontSize = 11.sp,
                                color = DoomCrimson
                            )
                            Text("MP: ${other.mp}", fontSize = 11.sp, color = LegionBlue)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Player State - Loaded", showBackground = true, backgroundColor = 0xFF0B0D14)
@Composable
private fun PlayerStateContentLoadedPreview() {
    MyApplicationTheme {
        val mockPlayer = AqwPlayerState(
            username = "HeroOfLore",
            charId = 12345,
            authUserId = 67890,
            roomUserId = 1,
            cell = "r5",
            pad = "Left",
            currentHp = 3450,
            maxHp = 3500,
            mp = 180,
            maxMp = 200,
            isInCombat = true,
            isDead = false,
            gold = 98765432L,
            goldFarmed = 1250000L,
            expFarmed = 450000L,
            areaName = "elemental",
            mapName = "elemental",
            roomNumber = 9099,
            skills = mutableListOf(
                AqwSkill(
                    id = "aa",
                    index = 0,
                    name = "Auto Attack",
                    cdSeconds = 2.0,
                    mpCost = 0.0,
                    tgt = "h"
                ),
                AqwSkill(
                    id = "sk1",
                    index = 1,
                    name = "Shackle",
                    cdSeconds = 4.0,
                    mpCost = 15.0,
                    tgt = "h"
                ),
                AqwSkill(
                    id = "sk2",
                    index = 2,
                    name = "Highlord's Gaze",
                    cdSeconds = 8.0,
                    mpCost = 25.0,
                    tgt = "h"
                ),
                AqwSkill(
                    id = "sk3",
                    index = 3,
                    name = "Unshackle",
                    cdSeconds = 4.0,
                    mpCost = 15.0,
                    tgt = "s"
                ),
                AqwSkill(
                    id = "sk4",
                    index = 4,
                    name = "Armageddon",
                    cdSeconds = 15.0,
                    mpCost = 50.0,
                    tgt = "h"
                )
            ),
            auras = mutableListOf(
                AqwAura(name = "Shackle", count = 1, duration = 15),
                AqwAura(name = "Unshackle", count = 1, duration = 15),
                AqwAura(name = "Highlord's Gaze", count = 3, duration = 10),
                AqwAura(name = "Aspect of the Void", count = 1, duration = 0)
            ),
            inventory = mutableListOf(
                AqwItem(
                    itemId = 101,
                    name = "Void Highlord",
                    qty = 1,
                    maxQty = 1,
                    isCoins = true,
                    sType = "Class",
                    isEquipped = true
                ),
                AqwItem(
                    itemId = 102,
                    name = "Necrotic Sword of Doom",
                    qty = 1,
                    maxQty = 1,
                    isCoins = true,
                    sType = "Sword",
                    isEquipped = true
                ),
                AqwItem(
                    itemId = 103,
                    name = "Diamond of Nulgath",
                    qty = 1000,
                    maxQty = 1000,
                    isCoins = true,
                    sType = "Item"
                )
            ),
            tempInventory = mutableListOf(
                AqwItem(
                    itemId = 201,
                    name = "Charged Mana Golem Core",
                    qty = 1,
                    maxQty = 1,
                    isTemp = true,
                    sType = "Quest Item"
                )
            ),
            droppedItems = mutableListOf(
                AqwItem(
                    itemId = 501,
                    name = "Dark Crystal Shard",
                    qty = 1,
                    maxQty = 1000,
                    isCoins = true,
                    sType = "Item"
                ),
                AqwItem(
                    itemId = 502,
                    name = "Diamond of Nulgath",
                    qty = 1,
                    maxQty = 1000,
                    isCoins = true,
                    sType = "Item"
                )
            ),
            bank = mutableListOf(
                AqwItem(
                    itemId = 301,
                    name = "Unidentified 13",
                    qty = 13,
                    maxQty = 13,
                    isCoins = true,
                    sType = "Item"
                ),
                AqwItem(
                    itemId = 302,
                    name = "Voucher of Nulgath",
                    qty = 1,
                    maxQty = 1,
                    isCoins = false,
                    sType = "Item"
                )
            ),
            factions = mutableListOf(
                AqwFaction(factionId = 1, name = "Nation", rep = 302500),
                AqwFaction(factionId = 2, name = "Legion", rep = 302500),
                AqwFaction(factionId = 3, name = "Blacksmithing", rep = 150000)
            ),
            playersInMap = mutableMapOf(
                "Artix" to AqwOtherPlayer(
                    username = "Artix",
                    cell = "r5",
                    pad = "Spawn",
                    hp = 10000,
                    maxHp = 10000,
                    mp = 1000
                )
            )
        )

        PlayerStateContent(
            playerState = mockPlayer,
            accountNames = listOf("HeroOfLore", "AltFarmer"),
            selectedSessionIndex = 0,
            lastRefreshTime = "12:34:56",
            isBotRunning = true
        )
    }
}

@Preview(name = "Player State - Empty", showBackground = true, backgroundColor = 0xFF0B0D14)
@Composable
private fun PlayerStateContentEmptyPreview() {
    MyApplicationTheme {
        PlayerStateContent(
            playerState = null,
            accountNames = emptyList(),
            selectedSessionIndex = 0,
            lastRefreshTime = "00:00:00",
            isBotRunning = false
        )
    }
}

