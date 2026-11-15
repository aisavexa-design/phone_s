package com.phones.dialer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun placeCall(ctx: Context, number: String) {
    if (number.isBlank()) return
    try {
        ctx.getSystemService(TelecomManager::class.java)
            .placeCall(Uri.fromParts("tel", number, null), Bundle())
    } catch (e: SecurityException) {
        Toast.makeText(ctx, "Phone permission chahiye", Toast.LENGTH_SHORT).show()
    }
}

private val KEYS = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to "")
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen(initial: String = "") {
    var number by remember { mutableStateOf(initial) }
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            number, fontSize = 38.sp, fontWeight = FontWeight.Light,
            color = Color.White, maxLines = 1
        )
        Spacer(Modifier.height(24.dp))

        KEYS.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { (digit, letters) ->
                    DialKey(digit, letters) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        number += digit
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(76.dp))
            CallButton { placeCall(ctx, number) }
            Box(
                Modifier.size(76.dp).clip(CircleShape).combinedClickable(
                    onClick = { if (number.isNotEmpty()) number = number.dropLast(1) },
                    onLongClick = { number = "" }
                ),
                contentAlignment = Alignment.Center
            ) {
                if (number.isNotEmpty())
                    Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun DialKey(digit: String, letters: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "scale"
    )
    val bg by animateColorAsState(
        if (pressed) Color(0x33FFFFFF) else Color.Transparent,
        spring(stiffness = Spring.StiffnessMedium), label = "bg"
    )
    Box(
        Modifier.size(76.dp).scale(scale).clip(CircleShape).background(bg)
            .combinedClickable(
                interactionSource = src, indication = null, onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit, fontSize = 32.sp, fontWeight = FontWeight.Light, color = Color.White)
            if (letters.isNotEmpty())
                Text(letters, fontSize = 10.sp, color = Color.Gray, letterSpacing = 1.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallButton(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "call"
    )
    Box(
        Modifier.size(76.dp).scale(scale).clip(CircleShape).background(SamGreen)
            .combinedClickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
}
