package com.phones.dialer

import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SamTheme { InCallScreen(onDone = { finish() }) } }
    }
}

@Composable
fun InCallScreen(onDone: () -> Unit) {
    val call = CallManager.call
    val state = CallManager.state
    LaunchedEffect(call) { if (call == null) onDone() }

    val number = try { call?.details?.handle?.schemeSpecificPart } catch (e: Exception) { null } ?: ""
    val ringing = state == Call.STATE_RINGING
    val label = when (state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling..."
        Call.STATE_ACTIVE -> "On call"
        Call.STATE_HOLDING -> "On hold"
        else -> "Ending..."
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(120.dp))
        Text(number, fontSize = 36.sp, fontWeight = FontWeight.Light, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 16.sp, color = Color.Gray)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 48.dp),
            horizontalArrangement = if (ringing) Arrangement.SpaceEvenly else Arrangement.Center
        ) {
            RoundAction(SamRed, Icons.Filled.CallEnd) { CallManager.hangup() }
            if (ringing) RoundAction(SamGreen, Icons.Filled.Call) { CallManager.answer() }
        }
    }
}

@Composable
fun RoundAction(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press"
    )
    Box(
        Modifier.size(72.dp).scale(scale).clip(CircleShape).background(color)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
}
