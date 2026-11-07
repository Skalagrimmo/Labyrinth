package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GameViewModel
import com.example.ui.theme.*

@Composable
fun SettingsView(
    viewModel: GameViewModel,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SYSTEM CONFIGURATION PANEL",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // ---------------- AUDIO ----------------
        SettingsSectionHeader(title = "AUDIO SUBSYSTEM", color = CyberGreen)
        SettingsToggleRow(
            label = "BACKGROUND MUSIC",
            value = viewModel.bgmEnabled,
            color = CyberGreen,
            testTag = "toggle_bgm",
            onChange = { viewModel.setBgmEnabled(it) }
        )

        Text(
            text = "BGM VOLUME",
            color = CyberMutedText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
        )
        Slider(
            value = viewModel.bgmVolume,
            onValueChange = { viewModel.setBgmVolume(it) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = CyberCyan,
                activeTrackColor = CyberCyan,
                inactiveTrackColor = CyberMutedGreen
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("slider_bgm_volume")
        )
        Text(
            text = "${(viewModel.bgmVolume * 100).toInt()}%",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.End)
        )

        SettingsToggleRow(
            label = "SOUND EFFECTS",
            value = viewModel.sfxEnabled,
            color = CyberGreen,
            testTag = "toggle_sfx",
            onChange = { viewModel.setSfxEnabled(it) }
        )

        // ---------------- HAPTICS ----------------
        SettingsSectionHeader(title = "HAPTIC FEEDBACK", color = CyberAmber)
        SettingsToggleRow(
            label = "VIBRATION",
            value = viewModel.vibrationEnabled,
            color = CyberAmber,
            testTag = "toggle_vibration",
            onChange = { viewModel.setVibrationEnabled(it) }
        )

        // ---------------- SYSTEM INFO ----------------
        SettingsSectionHeader(title = "SYSTEM INFO", color = CyberPurple)
        InfoRow(label = "RUNNER", value = viewModel.uiState.value.runnerName.ifEmpty { "UNREGISTERED" })
        InfoRow(label = "ARCHETYPE", value = viewModel.uiState.value.runnerClass.name)
        InfoRow(label = "BUILD TARGET", value = "API ${Build.VERSION.SDK_INT}")
        InfoRow(label = "DEVICE", value = Build.MODEL)

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, CyberCrimson),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("btn_settings_exit")
        ) {
            Text(
                text = "DISCONNECT",
                color = CyberCrimson,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsSectionHeader(title: String, color: Color) {
    Text(
        text = "// $title //",
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberDark)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    value: Boolean,
    color: Color,
    testTag: String,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = CyberTextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = color,
                checkedTrackColor = CyberMutedGreen,
                uncheckedThumbColor = CyberGrey,
                uncheckedTrackColor = CyberMutedGreen
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = CyberMutedText,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = CyberTextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            textAlign = TextAlign.End
        )
    }
}
