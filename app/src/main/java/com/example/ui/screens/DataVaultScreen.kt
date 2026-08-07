package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CosmeticTheme
import com.example.data.PerformanceBuff
import com.example.data.TerminalPromptStyle
import com.example.ui.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataVaultScreen(
    uiState: GameViewModel.GameUiState,
    onUnlockTheme: (String) -> Unit,
    onEquipTheme: (String) -> Unit,
    onUnlockPrompt: (String) -> Unit,
    onEquipPrompt: (String) -> Unit,
    onUnlockBuff: (String) -> Unit,
    onToggleBuff: (String) -> Unit,
    onExitVault: () -> Unit
) {
    val view = LocalView.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Themes, 1: Prompts, 2: Buffs

    val activeTheme = CosmeticTheme.fromId(uiState.activeCosmeticTheme)
    val activePrompt = TerminalPromptStyle.fromId(uiState.activePromptStyle)

    val primaryColor = Color(activeTheme.primaryHex)
    val bgDark = Color(activeTheme.backgroundHex)
    val accentColor = Color(activeTheme.accentHex)
    val textColor = Color(activeTheme.textHex)

    Scaffold(
        containerColor = bgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "💾 DATA FRAGMENT VAULT",
                            color = primaryColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "DECRYPTION ENGINE & COSMETIC EXCHANGE",
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onExitVault()
                        },
                        modifier = Modifier.testTag("btn_vault_exit")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryColor
                        )
                    }
                },
                actions = {
                    Surface(
                        color = primaryColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, primaryColor),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "💾 ${uiState.dataFragments} FRAGS",
                                color = primaryColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgDark)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
        ) {
            // Stats Banner
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ACTIVE TERMINAL THEME: ${activeTheme.title}",
                            color = primaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "PROMPT: ${activePrompt.promptString}",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "EXTRACTED: ${uiState.totalDataFragmentsExtracted}",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "BUFFS ACTIVE: ${uiState.activeBuffs.size}/${uiState.unlockedBuffs.size}",
                            color = Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Tab Navigation Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = primaryColor,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        selectedTab = 0
                    },
                    modifier = Modifier.testTag("tab_themes")
                ) {
                    Text(
                        text = "🎨 THEMES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        selectedTab = 1
                    },
                    modifier = Modifier.testTag("tab_prompts")
                ) {
                    Text(
                        text = "💻 PROMPTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                Tab(
                    selected = selectedTab == 2,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        selectedTab = 2
                    },
                    modifier = Modifier.testTag("tab_buffs")
                ) {
                    Text(
                        text = "⚡ BUFFS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Content Body
            when (selectedTab) {
                0 -> CosmeticThemesList(
                    uiState = uiState,
                    primaryColor = primaryColor,
                    onUnlockTheme = onUnlockTheme,
                    onEquipTheme = onEquipTheme
                )
                1 -> TerminalPromptsList(
                    uiState = uiState,
                    primaryColor = primaryColor,
                    onUnlockPrompt = onUnlockPrompt,
                    onEquipPrompt = onEquipPrompt
                )
                2 -> PerformanceBuffsList(
                    uiState = uiState,
                    primaryColor = primaryColor,
                    onUnlockBuff = onUnlockBuff,
                    onToggleBuff = onToggleBuff
                )
            }
        }
    }
}

@Composable
private fun CosmeticThemesList(
    uiState: GameViewModel.GameUiState,
    primaryColor: Color,
    onUnlockTheme: (String) -> Unit,
    onEquipTheme: (String) -> Unit
) {
    val view = LocalView.current
    val themes = CosmeticTheme.values()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(themes) { theme ->
            val isUnlocked = uiState.unlockedThemes.contains(theme.id)
            val isEquipped = uiState.activeCosmeticTheme == theme.id

            val cardBorderColor = if (isEquipped) primaryColor else if (isUnlocked) Color(0xFF334155) else Color(0xFF1E293B)

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(if (isEquipped) 2.dp else 1.dp, cardBorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = theme.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            if (isEquipped) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = primaryColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, primaryColor)
                                ) {
                                    Text(
                                        text = "EQUIPPED",
                                        color = primaryColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Color Palette Swatches Preview
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(theme.primaryHex)))
                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(theme.accentHex)))
                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(theme.backgroundHex)).border(0.5.dp, Color.White, CircleShape))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = theme.description,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isUnlocked) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onUnlockTheme(theme.id)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            enabled = uiState.dataFragments >= theme.cost,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unlock_theme_${theme.id}")
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "UNLOCK FOR ${theme.cost} FRAGMENTS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else if (!isEquipped) {
                        OutlinedButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onEquipTheme(theme.id)
                            },
                            border = BorderStroke(1.dp, primaryColor),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("equip_theme_${theme.id}")
                        ) {
                            Text(
                                text = "EQUIP THEME",
                                color = primaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalPromptsList(
    uiState: GameViewModel.GameUiState,
    primaryColor: Color,
    onUnlockPrompt: (String) -> Unit,
    onEquipPrompt: (String) -> Unit
) {
    val view = LocalView.current
    val prompts = TerminalPromptStyle.values()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(prompts) { prompt ->
            val isUnlocked = uiState.unlockedPrompts.contains(prompt.id)
            val isEquipped = uiState.activePromptStyle == prompt.id

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(if (isEquipped) 2.dp else 1.dp, if (isEquipped) primaryColor else Color(0xFF334155)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = prompt.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (isEquipped) {
                            Text(
                                text = "ACTIVE PROMPT",
                                color = primaryColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Command Prompt Preview Box
                    Surface(
                        color = Color(0xFF020617),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${prompt.promptString} execute --sector-scan",
                            color = primaryColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = prompt.description,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isUnlocked) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onUnlockPrompt(prompt.id)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            enabled = uiState.dataFragments >= prompt.cost,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unlock_prompt_${prompt.id}")
                        ) {
                            Text(
                                text = "UNLOCK PROMPT (${prompt.cost} FRAGMENTS)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else if (!isEquipped) {
                        OutlinedButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onEquipPrompt(prompt.id)
                            },
                            border = BorderStroke(1.dp, primaryColor),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("equip_prompt_${prompt.id}")
                        ) {
                            Text(
                                text = "EQUIP PROMPT",
                                color = primaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceBuffsList(
    uiState: GameViewModel.GameUiState,
    primaryColor: Color,
    onUnlockBuff: (String) -> Unit,
    onToggleBuff: (String) -> Unit
) {
    val view = LocalView.current
    val buffs = PerformanceBuff.values()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(buffs) { buff ->
            val isUnlocked = uiState.unlockedBuffs.contains(buff.id)
            val isActive = uiState.activeBuffs.contains(buff.id)

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(
                    if (isActive) 2.dp else 1.dp,
                    if (isActive) Color(0xFF10B981) else if (isUnlocked) Color(0xFF334155) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${buff.icon} ${buff.title}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (isActive) {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981))
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = Color(0xFF10B981),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buff.description,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isUnlocked) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onUnlockBuff(buff.id)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            enabled = uiState.dataFragments >= buff.cost,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unlock_buff_${buff.id}")
                        ) {
                            Text(
                                text = "UNLOCK BUFF (${buff.cost} FRAGMENTS)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onToggleBuff(buff.id)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActive) Color(0xFF10B981) else Color(0xFF334155)
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("toggle_buff_${buff.id}")
                        ) {
                            Text(
                                text = if (isActive) "DEACTIVATE BUFF" else "ACTIVATE BUFF",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
