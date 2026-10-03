package froztt13.python.aqw.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import froztt13.python.aqw.data.model.LogEntry
import froztt13.python.aqw.data.model.LogEntryType
import froztt13.python.aqw.data.network.AqwSocketClient
import froztt13.python.aqw.ui.theme.ErrorRed
import froztt13.python.aqw.ui.theme.MoonCyan
import froztt13.python.aqw.ui.theme.MyApplicationTheme
import froztt13.python.aqw.ui.theme.SuccessGreen
import froztt13.python.aqw.ui.theme.SunGold
import froztt13.python.aqw.ui.theme.TextMuted
import froztt13.python.aqw.ui.theme.TextSecondary
import froztt13.python.aqw.utils.stripAnsi

@Composable
fun LiveLogConsole(
    modifier: Modifier = Modifier,
    logs: List<LogEntry>,
    packetLogs: List<LogEntry>? = null,
    slotKey: String? = null,
    targetUsername: String? = null,
    includeSystemLogs: Boolean = true,
    onClearLogs: () -> Unit,
    onClearPacketLogs: (() -> Unit)? = null,
    title: String = "Console Logs"
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val rawPacketLogs = packetLogs ?: AqwSocketClient.packetLogs.collectAsState().value
    val clearPacketsAction = onClearPacketLogs ?: { AqwSocketClient.clearPacketLogs() }

    val cleanSlotKey = slotKey?.trim()?.takeIf { it.isNotEmpty() }
    val cleanUsername = targetUsername?.trim()?.takeIf { it.isNotEmpty() }

    val effectiveLogs = remember(logs, cleanSlotKey, cleanUsername, includeSystemLogs) {
        if (cleanSlotKey == null && cleanUsername == null) {
            logs
        } else {
            logs.filter { entry ->
                val u = entry.username.trim()
                val isSystem = includeSystemLogs && (
                        u.equals("System", ignoreCase = true) ||
                                entry.botType == LogEntryType.SYSTEM
                        )
                val matchesSlot = cleanSlotKey != null && (
                        u.equals(cleanSlotKey, ignoreCase = true) ||
                                u.contains("[$cleanSlotKey]", ignoreCase = true) ||
                                entry.message.contains("[$cleanSlotKey]", ignoreCase = true)
                        )
                val matchesUser = cleanUsername != null && (
                        u.equals(cleanUsername, ignoreCase = true) ||
                                u.contains("($cleanUsername)", ignoreCase = true)
                        )
                isSystem || matchesSlot || matchesUser
            }
        }
    }

    val effectivePacketLogs = remember(rawPacketLogs, cleanSlotKey, cleanUsername) {
        if (cleanSlotKey == null && cleanUsername == null) {
            rawPacketLogs
        } else {
            rawPacketLogs.filter { entry ->
                val u = entry.username.trim()
                val matchesSlot =
                    cleanSlotKey != null && u.contains(cleanSlotKey, ignoreCase = true)
                val matchesUser =
                    cleanUsername != null && u.contains(cleanUsername, ignoreCase = true)
                (matchesSlot || matchesUser) /*&& !entry.message.contains("%gar%")*/
            }
        }
    }

    val consoleListState = rememberLazyListState()
    val packetListState = rememberLazyListState()

    LaunchedEffect(effectiveLogs.size) {
        if (effectiveLogs.isNotEmpty() && selectedTab == 0) {
            consoleListState.animateScrollToItem(effectiveLogs.size - 1)
        }
    }

    LaunchedEffect(effectivePacketLogs.size) {
        if (effectivePacketLogs.isNotEmpty() && selectedTab == 1) {
            packetListState.animateScrollToItem(effectivePacketLogs.size - 1)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .border(1.dp, Color(0xFF2E3350), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090A10))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab switcher (Console Logs & Packets Sent)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConsoleTabButton(
                        title = title,
                        count = effectiveLogs.size,
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    ConsoleTabButton(
                        title = "Packets Sent",
                        count = effectivePacketLogs.size,
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                }

                IconButton(
                    onClick = {
                        if (selectedTab == 0) {
                            onClearLogs()
                        } else {
                            clearPacketsAction()
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = if (selectedTab == 0) "Clear Console Logs" else "Clear Packet Logs",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTab == 0) {
                // Tab 0: Console Logs
                if (effectiveLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No logs yet. Logs will stream in real-time here.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                } else {
                    LazyColumn(
                        state = consoleListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(effectiveLogs, key = { it.id }) { log ->
                            val cleanMessage = log.message.stripAnsi()
                            val lower = cleanMessage.lowercase()
                            val textColor = when {
                                log.botType == LogEntryType.ERROR || "error" in lower || "exception" in lower || "dead" in lower || "failed" in lower -> ErrorRed
                                log.botType == LogEntryType.WARNING || "warning" in lower || "taunt" in lower || "soe" in lower -> SunGold
                                log.botType == LogEntryType.SYSTEM || "login" in lower || "connecting" in lower || "joined" in lower -> MoonCyan
                                "cleared" in lower || "success" in lower || "connected to" in lower || "completed" in lower -> SuccessGreen
                                else -> TextSecondary
                            }
                            SelectionContainer {
                                Text(
                                    text = "[${log.formattedTime}] $cleanMessage",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            } else {
                // Tab 1: Packet Sent Logs
                if (effectivePacketLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No outgoing packets yet. Packets sent via AqwSocketClient will appear here.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                } else {
                    LazyColumn(
                        state = packetListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(effectivePacketLogs, key = { it.id }) { log ->
                            val cleanPacket = log.message.stripAnsi()
                            val lower = cleanPacket.lowercase()
                            val packetColor = when {
                                cleanPacket.startsWith("<msg") -> SunGold
                                "movetocell" in lower || "jumpcell" in lower -> MoonCyan
                                "gar" in lower || "usercastspell" in lower -> SuccessGreen
                                "craftitem" in lower || "getdrop" in lower -> Color(0xFFCE93D8)
                                "keepalive" in lower || "ping" in lower -> TextMuted
                                else -> Color(0xFF90CAF9)
                            }
                            val senderPrefix =
                                if (log.username.isNotBlank()) "[${log.username}] " else ""
                            SelectionContainer {
                                Text(
                                    text = "[${log.formattedTime}] $senderPrefix$cleanPacket",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = packetColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleTabButton(
    title: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF1E2338) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, MoonCyan.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else TextMuted
            )
            if (count > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MoonCyan.copy(alpha = 0.2f) else Color(0xFF1A1D2B)
                ) {
                    Text(
                        text = "$count",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MoonCyan else TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D14)
@Composable
private fun LiveLogConsolePreview() {
    MyApplicationTheme {
        LiveLogConsole(
            logs = listOf(
                LogEntry(
                    botType = LogEntryType.SYSTEM,
                    username = "LordLead",
                    message = "Connected to room 9099"
                ),
                LogEntry(
                    botType = LogEntryType.INFO,
                    username = "LordLead",
                    message = "Taunting boss: Success!"
                ),
                LogEntry(
                    botType = LogEntryType.ERROR,
                    username = "LordLead",
                    message = "Error: target not found (recovered)"
                )
            ),
            packetLogs = listOf(
                LogEntry(
                    botType = LogEntryType.PACKET,
                    username = "slot1 (LordLead)",
                    message = "%xt%zm%gar%1%1%Left%...%"
                )
            ),
            onClearLogs = {}
        )
    }
}
