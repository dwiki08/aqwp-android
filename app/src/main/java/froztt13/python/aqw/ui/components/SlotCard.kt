package froztt13.python.aqw.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import froztt13.python.aqw.data.SlotConfig
import froztt13.python.aqw.data.SlotTelemetry
import froztt13.python.aqw.ui.theme.CardDark
import froztt13.python.aqw.ui.theme.ErrorRed
import froztt13.python.aqw.ui.theme.MoonCyan
import froztt13.python.aqw.ui.theme.MyApplicationTheme
import froztt13.python.aqw.ui.theme.PrimaryPurple
import froztt13.python.aqw.ui.theme.SuccessGreen
import froztt13.python.aqw.ui.theme.SunGold
import froztt13.python.aqw.ui.theme.TextMuted
import froztt13.python.aqw.ui.theme.TextPrimary
import froztt13.python.aqw.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlotCard(
    modifier: Modifier = Modifier,
    slotKey: String,
    title: String,
    config: SlotConfig,
    telemetry: SlotTelemetry,
    isPartyRunning: Boolean,
    accentColor: Color = PrimaryPurple,
    showTauntToggle: Boolean = true,
    showEclipseTauntToggles: Boolean = false,
    fixedTauntRoleText: String? = null,
    fixedTauntRoleColor: Color = accentColor,
    fixedPrimaryTarget: String? = null,
    onConfigChange: (SlotConfig) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var showAuras by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (telemetry.running) SuccessGreen.copy(alpha = 0.5f) else Color(0xFF2E3350),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Title, Role Badge, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.running) SuccessGreen else Color(0xFF64748B))
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (config.charClass.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = config.charClass,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            )
                        }
                    }
                    if (telemetry.running) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SuccessGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }

            // Real-time Telemetry (when party is running)
            AnimatedVisibility(visible = telemetry.running) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F111A))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Location: Map, Cell, Pad
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Map: ${telemetry.map} (${telemetry.cell}, ${telemetry.pad})",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (telemetry.isConnected) "Connected" else "Connecting...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (telemetry.isConnected) SuccessGreen else SunGold
                        )
                    }

                    // Target Monsters (Active / Default)
                    val activeTarget = telemetry.targetMonsters.ifEmpty { config.defaultTarget }
                    if (activeTarget.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF161928))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                )
                                Text(
                                    text = "Priority",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                modifier = Modifier.weight(1f),
                                text = activeTarget,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                color = TextPrimary
                            )
                        }
                    }

                    // Targeted Monster (Current / Last target from skill)
                    val currentTargeted = telemetry.targetedMonster.ifEmpty { "-" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF161928))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ErrorRed)
                            )
                            Text(
                                text = "Targeted Monster",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            modifier = Modifier.weight(1f),
                            text = currentTargeted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            color = if (currentTargeted == "-") TextMuted else TextPrimary
                        )
                    }

                    // HP Bar
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "HP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                            Text(
                                text = "${telemetry.hp} / ${telemetry.maxHp}",
                                fontSize = 10.sp,
                                color = TextPrimary
                            )
                        }
                        val hpFraction =
                            if (telemetry.maxHp > 0) (telemetry.hp.toFloat() / telemetry.maxHp.toFloat()).coerceIn(
                                0f,
                                1f
                            ) else 0f
                        LinearProgressIndicator(
                            progress = { hpFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = ErrorRed,
                            trackColor = Color(0xFF331B22)
                        )
                    }

                    // MP Bar
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "MP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoonCyan
                            )
                            Text(
                                text = "${telemetry.mp} / ${telemetry.maxMp}",
                                fontSize = 10.sp,
                                color = TextPrimary
                            )
                        }
                        val mpFraction =
                            if (telemetry.maxMp > 0) (telemetry.mp.toFloat() / telemetry.maxMp.toFloat()).coerceIn(
                                0f,
                                1f
                            ) else 0f
                        LinearProgressIndicator(
                            progress = { mpFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MoonCyan,
                            trackColor = Color(0xFF132D38)
                        )
                    }

                    // Skill Cooldowns
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Skill Cooldowns",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        SkillCooldownsRow(
                            cooldowns = telemetry.cooldowns,
                            accentColor = accentColor
                        )
                    }

                    // Active Auras on Player
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Active Auras",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                                if (telemetry.auras.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(PrimaryPurple.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "${telemetry.auras.size}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryPurple
                                        )
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showAuras = !showAuras }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (showAuras) "Hide" else "Show",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (showAuras) PrimaryPurple else TextMuted
                                )
                                Switch(
                                    checked = showAuras,
                                    onCheckedChange = { showAuras = it },
                                    modifier = Modifier.height(24.dp),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = PrimaryPurple,
                                        uncheckedThumbColor = Color.LightGray,
                                        uncheckedTrackColor = Color(0xFF334155)
                                    )
                                )
                            }
                        }

                        AnimatedVisibility(visible = showAuras) {
                            if (telemetry.auras.isEmpty()) {
                                Text(
                                    text = "No active auras on player",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                                )
                            } else {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    telemetry.auras.forEach { aura ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(PrimaryPurple.copy(alpha = 0.15f))
                                                .border(
                                                    1.dp,
                                                    PrimaryPurple.copy(alpha = 0.4f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = aura,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFE2D4F0)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SoE (Scroll of Enrage) Quantity (for taunters / party members)
                    if (config.isTaunter) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF161928))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (telemetry.soeQty < 50) ErrorRed else SunGold)
                                )
                                Text(
                                    text = "Scroll of Enrage (SoE)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "${telemetry.soeQty} pcs",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (telemetry.soeQty < 50) ErrorRed else SunGold
                            )
                        }
                    }
                }
            }

            // Input Fields
            if (telemetry.running.not()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomOutlinedTextField(
                        value = config.username,
                        onValueChange = { onConfigChange(config.copy(username = it)) },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !isPartyRunning,
                        modifier = Modifier.weight(1f),
                        colors = defaultTextFieldColors(accentColor)
                    )

                    CustomOutlinedTextField(
                        value = config.password,
                        onValueChange = { onConfigChange(config.copy(password = it)) },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isPartyRunning,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = TextMuted
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = defaultTextFieldColors(accentColor)
                    )
                }

                // Class Picker Dropdown
                ClassDropdown(
                    selectedClass = config.charClass,
                    enabled = !isPartyRunning,
                    accentColor = accentColor,
                    onClassSelected = { onConfigChange(config.copy(charClass = it)) }
                )

                // Target Monsters Input
                if (fixedPrimaryTarget != null) {
                    val parsedExtra = remember(config.defaultTarget, fixedPrimaryTarget) {
                        if (config.defaultTarget.startsWith(
                                fixedPrimaryTarget,
                                ignoreCase = true
                            )
                        ) {
                            config.defaultTarget.substring(fixedPrimaryTarget.length)
                                .trimStart(',', ' ')
                        } else {
                            config.defaultTarget
                        }
                    }
                    var localExtra by remember(slotKey) { mutableStateOf(parsedExtra) }

                    LaunchedEffect(parsedExtra) {
                        if (parsedExtra != localExtra.trim()) {
                            localExtra = parsedExtra
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF161928))
                                .border(1.dp, Color(0xFF2E3350), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(accentColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "#1 Priority",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor
                                    )
                                }
                                Text(
                                    text = fixedPrimaryTarget,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                        }

                        CustomOutlinedTextField(
                            value = localExtra,
                            onValueChange = { newVal ->
                                localExtra = newVal
                                val clean = newVal.trim()
                                val fullTarget = if (clean.isEmpty()) {
                                    fixedPrimaryTarget
                                } else {
                                    "$fixedPrimaryTarget,$clean"
                                }
                                onConfigChange(config.copy(defaultTarget = fullTarget))
                            },
                            label = { Text("Additional Target Monsters") },
                            placeholder = { Text("e.g. Blessless Deer", color = TextMuted) },
                            supportingText = {
                                Text(
                                    text = "Target Order: $fixedPrimaryTarget${if (localExtra.isNotBlank()) ", $localExtra" else ""}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            },
                            singleLine = true,
                            enabled = !isPartyRunning,
                            modifier = Modifier.fillMaxWidth(),
                            colors = defaultTextFieldColors(accentColor)
                        )
                    }
                } else {
                    CustomOutlinedTextField(
                        value = config.defaultTarget,
                        onValueChange = { onConfigChange(config.copy(defaultTarget = it)) },
                        label = { Text("Default Target Monsters") },
                        placeholder = {
                            Text(
                                "e.g. Ascended Solstice,Blessless Deer",
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        enabled = !isPartyRunning,
                        modifier = Modifier.fillMaxWidth(),
                        colors = defaultTextFieldColors(accentColor)
                    )
                }

                // Fixed Taunt Role Display (when fixedTauntRoleText != null)
                if (fixedTauntRoleText != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = fixedTauntRoleColor.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(1.dp, fixedTauntRoleColor.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = fixedTauntRoleText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = fixedTauntRoleColor
                            )
                            Text(
                                text = if (fixedTauntRoleText.contains(
                                        "Sunset",
                                        ignoreCase = true
                                    )
                                ) {
                                    "Taunts monster when Sun's Warmth occurs"
                                } else {
                                    "Taunts monster when Moonlight Gaze occurs"
                                },
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Taunter Toggle Switch
                if (showTauntToggle && fixedTauntRoleText == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Taunt Role",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Casts scroll/taunt on monster",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = config.isTaunter,
                            onCheckedChange = { onConfigChange(config.copy(isTaunter = it)) },
                            enabled = !isPartyRunning,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }

                // Eclipse Taunt Role Toggles (deprecated / ignored if fixedTauntRoleText != null)
                if (showEclipseTauntToggles && fixedTauntRoleText == null) {
                    Text(
                        text = "Eclipse Taunt Role (Select Max 1)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Moon Haze Taunter",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Taunts when Moonlight Gaze aura occurs",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = config.moonHazeTaunter,
                            onCheckedChange = { checked ->
                                onConfigChange(
                                    config.copy(
                                        moonHazeTaunter = checked,
                                        sunsetKnightTaunter = if (checked) false else config.sunsetKnightTaunter,
                                        isTaunter = checked || (config.sunsetKnightTaunter)
                                    )
                                )
                            },
                            enabled = !isPartyRunning,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sunset Knight Taunter",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Taunts when Sun's Warmth aura occurs",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = config.sunsetKnightTaunter,
                            onCheckedChange = { checked ->
                                onConfigChange(
                                    config.copy(
                                        sunsetKnightTaunter = checked,
                                        moonHazeTaunter = if (checked) false else config.moonHazeTaunter,
                                        isTaunter = checked || (config.moonHazeTaunter)
                                    )
                                )
                            },
                            enabled = !isPartyRunning,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D14)
@Composable
private fun SlotCardIdlePreview() {
    MyApplicationTheme {
        SlotCard(
            slotKey = "slot1",
            title = "Slot 1 (Lord of Order / Lead)",
            config = SlotConfig(
                username = "LordLead",
                charClass = "Lord of Order",
                role = "master",
                isTaunter = true,
                defaultTarget = "Ascended Solstice,Blessless Deer"
            ),
            telemetry = SlotTelemetry(running = false),
            isPartyRunning = false,
            accentColor = PrimaryPurple,
            onConfigChange = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D14)
@Composable
private fun SlotCardActivePreview() {
    MyApplicationTheme {
        SlotCard(
            slotKey = "slot1",
            title = "Slot 1",
            config = SlotConfig(
                username = "LordLead",
                charClass = "Lord of Order",
                role = "master",
                isTaunter = true,
                defaultTarget = "Ascended Solstice,Blessless Deer"
            ),
            telemetry = SlotTelemetry(
                running = true,
                isConnected = true,
                map = "ascendeclipse",
                cell = "r3",
                pad = "Left",
                hp = 4200,
                maxHp = 5000,
                mp = 80,
                maxMp = 100,
                cooldowns = mapOf(0 to 0.0, 1 to 2.1, 2 to 0.0, 3 to 4.5, 4 to 0.0, 5 to 0.0),
                soeQty = 150,
                targetMonsters = "Ascended Solstice,Blessless Deer,Dawn Knight",
                targetedMonster = "Ascended Solstice",
                auras = listOf("Focus", "Radiance", "Sun's Warmth", "Potent Vigor")
            ),
            isPartyRunning = true,
            accentColor = PrimaryPurple,
            onConfigChange = {}
        )
    }
}
