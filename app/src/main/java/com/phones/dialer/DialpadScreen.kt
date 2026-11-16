package com.phones.dialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Voicemail
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
        val tm = ctx.getSystemService(TelecomManager::class.java)
        tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
    } catch (e: Exception) {
        Toast.makeText(ctx, "Call fail: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun addContact(ctx: Context, number: String) {
    try {
        val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number)
        ctx.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Contacts app nahi mila", Toast.LENGTH_SHORT).show()
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
    val openSearch = LocalSearchOpen.current

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, end = 24.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (number.isNotEmpty()) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .clickable { addContact(ctx, number) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            HeaderIcon(Icons.Filled.Search) { openSearch() }
            HeaderIcon(Icons.Filled.MoreVert) { openCallSettings(ctx) }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Text(
                number, fontSize = 34.sp, fontWeight = FontWeight.Light,
                color = Color.White, maxLines = 1,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp))
                .background(SamBg)
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = 80.dp)
        ) {
            KEYS.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    row.forEach { (digit, letters) ->
                        DialKey(digit, letters, Modifier.weight(1f)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            number += digit
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CallButton { placeCall(ctx, number) }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (number.isNotEmpty()) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).combinedClickable(
                                onClick = { number = number.dropLast(1) },
                                onLongClick = { number = "" }
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace, null,
                                tint = Color.White, modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(digit: String, letters: String, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "scale"
    )
    val bg by animateColorAsState(
        if (pressed) Color(0x22FFFFFF) else Color.Transparent,
        spring(stiffness = Spring.StiffnessMedium), label = "bg"
    )
    Box(
        modifier.height(68.dp).scale(scale).clip(RoundedCornerShape(34.dp)).background(bg)
            .combinedClickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit, fontSize = 31.sp, fontWeight = FontWeight.Light, color = Color.White)
            if (digit == "1") {
                Icon(Icons.Filled.Voicemail, null, tint = SamGray, modifier = Modifier.size(16.dp))
            } else if (letters.isNotEmpty()) {
                Text(letters, fontSize = 11.sp, color = SamGray)
            }
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
        Modifier.size(66.dp).scale(scale).clip(CircleShape).background(SamGreen)
            .combinedClickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
}
