package com.phones.dialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class CallItem(val id: Long, val number: String, val name: String?, val type: Int, val date: Long)
data class CallGroup(
    val id: Long, val number: String, val name: String?,
    val type: Int, val date: Long, val count: Int
)

private fun loadCalls(ctx: Context): List<CallItem> {
    val out = mutableListOf<CallItem>()
    ctx.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE, CallLog.Calls.DATE
        ),
        null, null, "${CallLog.Calls.DATE} DESC"
    )?.use { c ->
        while (c.moveToNext() && out.size < 300) {
            out += CallItem(c.getLong(0), c.getString(1) ?: "", c.getString(2), c.getInt(3), c.getLong(4))
        }
    }
    return out
}

private fun dayKey(ms: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(ms: Long): String = when {
    DateUtils.isToday(ms) -> "Today"
    DateUtils.isToday(ms + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(ms))
}

private fun groupCalls(list: List<CallItem>): List<CallGroup> {
    val out = mutableListOf<CallGroup>()
    for (c in list) {
        val last = out.lastOrNull()
        if (last != null && last.number == c.number && last.type == c.type &&
            dayKey(last.date) == dayKey(c.date)
        ) {
            out[out.lastIndex] = last.copy(count = last.count + 1)
        } else {
            out += CallGroup(c.id, c.number, c.name, c.type, c.date, 1)
        }
    }
    return out
}

@Composable
fun RecentsScreen(granted: Boolean) {
    val ctx = LocalContext.current
    var calls by remember { mutableStateOf<List<CallItem>>(emptyList()) }

    LaunchedEffect(granted) {
        if (granted) calls = withContext(Dispatchers.IO) { loadCalls(ctx) }
    }

    val sections = remember(calls) {
        groupCalls(calls).groupBy { dayKey(it.date) }.values
            .map { dayLabel(it.first().date) to it }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        PageHeader(Icons.Filled.FilterList)
        if (!granted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Call log permission do", color = SamGray)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(sections, key = { it.second.first().id }) { (label, rows) ->
                    Text(
                        label, fontSize = 13.sp, color = SamGray,
                        modifier = Modifier.padding(start = 28.dp, top = 14.dp, bottom = 8.dp)
                    )
                    Column(
                        Modifier.padding(horizontal = 10.dp)
                            .clip(RoundedCornerShape(28.dp)).background(SamBg)
                    ) {
                        rows.forEachIndexed { i, g -> CallRow(g, i == rows.lastIndex) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeCallRow(onCall: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentOnCall by rememberUpdatedState(onCall)
    var widthPx by remember { mutableIntStateOf(1) }

    Box(Modifier.fillMaxWidth().onSizeChanged { widthPx = it.width }) {
        Box(
            Modifier.matchParentSize().background(SamGreen).padding(start = 24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                Icons.Filled.Call, null, tint = Color.White,
                modifier = Modifier.size(22.dp).graphicsLayer {
                    alpha = (offset.value / (widthPx * 0.25f)).coerceIn(0f, 1f)
                }
            )
        }
        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(SamBg)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val hit = offset.value > size.width * 0.3f
                            scope.launch {
                                if (hit) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnCall()
                                }
                                offset.animateTo(
                                    0f,
                                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                )
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch {
                                offset.snapTo((offset.value + dx).coerceIn(0f, size.width * 0.5f))
                            }
                        }
                    )
                }
        ) { content() }
    }
}

@Composable
private fun CallRow(g: CallGroup, isLast: Boolean) {
    val ctx = LocalContext.current
    val missed = g.type == CallLog.Calls.MISSED_TYPE
    val (icon, tint) = when (g.type) {
        CallLog.Calls.OUTGOING_TYPE -> Icons.Filled.CallMade to Color(0xFF1FA463)
        CallLog.Calls.MISSED_TYPE -> Icons.Filled.CallMissed to Color(0xFFE5483F)
        CallLog.Calls.VOICEMAIL_TYPE -> Icons.Filled.Voicemail to SamGray
        else -> Icons.Filled.CallReceived to SamGray
    }
    val base = g.name?.takeIf { it.isNotBlank() } ?: g.number.ifBlank { "Unknown" }
    val title = if (g.count > 1) "$base (${g.count})" else base
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(g.date))

    Column {
        SwipeCallRow(onCall = { placeCall(ctx, g.number) }) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { placeCall(ctx, g.number) }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(20.dp))
                Text(
                    title, fontSize = 16.5.sp, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        time, fontSize = 12.sp,
                        color = if (missed) Color(0xFFFF5A52) else SamGray
                    )
                    Spacer(Modifier.height(2.dp))
                    MsgChip(g.number)
                }
            }
        }
        if (!isLast) {
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Color(0xFF3A3A3C))
        }
    }
}

@Composable
private fun MsgChip(number: String) {
    val ctx = LocalContext.current
    Box(
        Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFFFA726))
            .clickable {
                try {
                    if (number.isNotBlank())
                        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
                } catch (e: Exception) { }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
    }
}
