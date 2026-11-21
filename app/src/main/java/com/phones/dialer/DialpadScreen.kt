package com.phones.dialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SoftEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

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

/** Spring press feel: fast squish, bouncy release, soft glow, haptic on touch-down. */
private fun Modifier.springPress(
    pressedScale: Float = 0.88f,
    glowAlpha: Float = 0.16f,
    haptic: Int = HapticFeedbackConstants.KEYBOARD_TAP,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) spring(dampingRatio = 0.85f, stiffness = 1000f)
        else spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "pressScale"
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) tween(70) else tween(450, easing = FastOutSlowInEasing),
        label = "pressGlow"
    )
    val latestClick by rememberUpdatedState(onClick)
    val latestLong by rememberUpdatedState(onLongClick)
    val hasLong = onLongClick != null
    val onLong: (Offset) -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        latestLong?.invoke()
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawBehind {
            if (glowAlpha > 0f && glow > 0.01f) {
                drawCircle(
                    Color.White.copy(alpha = glowAlpha * glow),
                    radius = size.minDimension / 2f
                )
            }
        }
        .pointerInput(hasLong) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    view.performHapticFeedback(haptic)
                    tryAwaitRelease()
                    pressed = false
                },
                onLongPress = if (hasLong) onLong else null,
                onTap = { latestClick() }
            )
        }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DialpadScreen(initial: String = "") {
    var number by remember { mutableStateOf(initial) }
    val ctx = LocalContext.current
    val openSearch = LocalSearchOpen.current

    var pool by remember { mutableStateOf<List<Pair<SearchItem, String>>>(emptyList()) }
    LaunchedEffect(number.isNotEmpty()) {
        if (number.isNotEmpty() && pool.isEmpty()) {
            pool = withContext(Dispatchers.IO) { loadPool(ctx).map { it to digits(it.number) } }
        }
    }
    val suggestion = remember(number, pool) {
        val q = digits(number)
        if (q.length < 2) null else pool.filter { it.second.contains(q) }
            .minWithOrNull(
                compareBy<Pair<SearchItem, String>>(
                    {
                        val d = it.second
                        val n = if (d.length > 10) d.takeLast(10) else d
                        if (d.startsWith(q) || n.startsWith(q)) 0 else 1
                    },
                    { if (it.first.name.isBlank()) 1 else 0 }
                )
            )?.first
    }

    val numSize by animateFloatAsState(
        targetValue = when {
            number.length > 16 -> 22f
            number.length > 13 -> 26f
            number.length > 10 -> 30f
            else -> 34f
        },
        animationSpec = tween(260, easing = SoftEase),
        label = "numSize"
    )

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, end = 24.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = number.isNotEmpty(),
                enter = fadeIn(tween(300)) +
                    scaleIn(spring(dampingRatio = 0.6f, stiffness = 400f), initialScale = 0.6f) +
                    expandHorizontally(tween(300, easing = SoftEase)),
                exit = fadeOut(tween(160)) +
                    scaleOut(tween(160), targetScale = 0.7f) +
                    shrinkHorizontally(tween(220))
            ) {
                HeaderIcon(Icons.Filled.Add) { addContact(ctx, number) }
            }
            HeaderIcon(Icons.Filled.Search) { openSearch() }
                        HeaderIcon(Icons.Filled.MoreVert) { openCallSettings(ctx) }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = suggestion,
                    transitionSpec = {
                        (fadeIn(tween(380, delayMillis = 90, easing = SoftEase)) +
                            slideInVertically(tween(520, easing = SoftEase)) { it / 3 } +
                            scaleIn(tween(520, easing = SoftEase), initialScale = 0.88f))
                            .togetherWith(
                                fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 0.94f)
                            )
                            .using(SizeTransform(clip = false) { _, _ ->
                                tween(520, easing = SoftEase)
                            })
                    },
                    contentAlignment = Alignment.BottomCenter,
                    label = "suggestion"
                ) { s ->
                    if (s != null) {
                        SuggestionPill(s) { number = s.number }
                    } else {
                        Spacer(Modifier.height(0.dp))
                    }
                }
                Text(
                    number, fontSize = numSize.sp, fontWeight = FontWeight.Light,
                    color = Color.White, maxLines = 1
                )
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp))
                .background(SamBg)
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = 94.dp)
        ) {
            KEYS.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    row.forEach { (digit, letters) ->
                        DialKey(
                            digit, letters, Modifier.weight(1f),
                            longPress = if (digit == "0") ({ number += "+" }) else null
                        ) { number += digit }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CallButton { placeCall(ctx, number) }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AnimatedVisibility(
                        visible = number.isNotEmpty(),
                        enter = fadeIn(tween(220)) +
                            scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.6f),
                        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.7f)
                    ) {
                        Box(
                            Modifier.size(56.dp)
                                .springPress(0.85f, 0.16f, onLongClick = { number = "" }) {
                                    number = number.dropLast(1)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace, null,
                                tint = Color.White, modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionPill(s: SearchItem, onPick: () -> Unit) {
    Row(
        Modifier.padding(bottom = 14.dp)
            .springPress(0.96f, 0f, HapticFeedbackConstants.CONTEXT_CLICK, onClick = onPick)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF232326))
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            s.name.ifBlank { s.number }, s.photo, 36.dp,
            s.number.hashCode().toLong() and 0x7fffffffL, 16.sp
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                s.name.ifBlank { s.number }, fontSize = 16.sp,
                color = Color.White, maxLines = 1
            )
            if (s.name.isNotBlank()) {
                Text(s.number, fontSize = 12.sp, color = SamGray)
            }
        }
    }
}

@Composable
private fun DialKey(
    digit: String, letters: String, modifier: Modifier,
    longPress: (() -> Unit)?, onClick: () -> Unit
) {
    Box(
        modifier.height(68.dp)
            .springPress(0.86f, 0.16f, onLongClick = longPress, onClick = onClick),
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

@Composable
private fun CallButton(onClick: () -> Unit) {
    Box(
        Modifier.size(66.dp)
            .springPress(0.92f, 0f, HapticFeedbackConstants.CONTEXT_CLICK, onClick = onClick)
            .clip(CircleShape)
            .background(SamGreen),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
}
