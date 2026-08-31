package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberMutedText
import com.example.ui.theme.CyberPurple

/**
 * TutorialOverlay — a lightweight, non-blocking guided hint (plan item 2.1).
 *
 * Shown on top of exploration for brand-new players. Each of the 5 steps carries a
 * title, a short body, and a monospace ASCII illustration. The player can advance with
 * `[ NEXT ]`, skip the whole thing with `[ SKIP ]`, or simply keep playing by dismissing.
 */
private data class TutorialStepSpec(
    val title: String,
    val body: String,
    val art: String
)

private val tutorialSteps = listOf(
    TutorialStepSpec(
        "WELCOME, NETRUNNER",
        "You have breached the Corporate Net. Your integrity, RAM, and credits are shown on the HUD. Survive, hack terminals, and escape deeper floors.",
        "   .-~~~~~~~~~-.\n   __'.........'__\n   .'   ][  ][     `.\n  :  _       ___   :\n  | | |     |   |  |\n  ' '------' '---' '"
    ),
    TutorialStepSpec(
        "MOVEMENT // SWIPE TO NAVIGATE",
        "Swipe on the viewport to move. Drag LEFT/RIGHT to turn, drag UP/DOWN to advance or retreat across the grid.",
        "       |\n       |\n  ←   / \\   →\n       |\n   /   |   \\"
    ),
    TutorialStepSpec(
        "INTERACTION // HACK TERMINALS",
        "Greener floor cells are interactive terminals. Stand next to one, then type:  hack <row> <col>\nCollect items, data fragments, and credits.",
        "  +-----+\n  | >_< |   hack 3 4\n  +-----+"
    ),
    TutorialStepSpec(
        "COMBAT // TURN-BASED",
        "Combat is turn-based. From the terminal:  attack,  defend,  scan,  or use items with  use <item>. Watch your RAM and shield.",
        "   (o)\n   /|\\   TARGET: HOSTILE ICE\n   / \\"
    ),
    TutorialStepSpec(
        "CYBERWARE // CLINIC",
        "Open the Cyberware Clinic with  clinic  to install implants. Each implant occupies a body slot and grants passive bonuses.",
        "  +========+\n  |  ++++  |   IMPLANT SLOTS\n  +========+"
    )
)

@Composable
fun TutorialOverlay(
    uiState: GameViewModel.GameUiState,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!uiState.tutorialActive) return
    val step = tutorialSteps.getOrNull(uiState.tutorialStep.coerceIn(0, tutorialSteps.lastIndex)) ?: tutorialSteps.first()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0B10)),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "[ ${step.title} ]",
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
                        .background(Color.Black, RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = step.art,
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    )
                }
                Text(
                    text = step.body,
                    color = CyberMutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Text(
                    text = "STEP ${uiState.tutorialStep + 1} / ${tutorialSteps.size}",
                    color = CyberPurple,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E5C3B))
                    ) {
                        Text(
                            if (uiState.tutorialStep >= tutorialSteps.lastIndex) "DONE" else "NEXT >",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = onSkip,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1E1E))
                    ) {
                        Text("SKIP", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
