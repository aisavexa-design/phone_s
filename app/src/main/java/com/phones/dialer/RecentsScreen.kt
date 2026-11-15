package com.phones.dialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.text.format.DateUtils
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
    val type: Int, val date: Long, val count: Int, val time: String
)

sealed interface RecentEntry { val key: String }
data class HeaderEntry(val label: String, override val key: String) : RecentEntry
data class RowEntry(
    val g: CallGroup, val first: Boolean, val last: Boolean, override val key: String
) : RecentEntry

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

private fun buildEntries(calls: List<CallItem>): List<RecentEntry> {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val groups = mutableListOf<CallGroup>()
    for (c in calls) {
        val last = groups.lastOrNull()
        if (last != null && last.number == c.number && last.type == c.type &&
            dayKey(last.date) == dayKey(c.date)
        ) {
            groups[groups.lastIndex] = last.copy(count = last.count + 1)
        } else {
            groups += CallGroup(
                c.id, c.number, c.name, c.type, c.date, 1, fmt.format(Date(c.date))
            )
        }
    }
    val out = ArrayList<RecentEntry>()
    for (rows in groups.groupBy { dayKey(it.date) }.values) {
        out += HeaderEntry(dayLabel(rows.first().date), "h${rows.first().id}")
        rows.forEachIndexed { i, g ->
            out += RowEntry(g, i == 0, i == rows.lastIndex, "r${g.id}")
        }
    }
    return out
}

@Composable
fun RecentsScreen(granted: Boolean) {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf<List<RecentEntry>>(emptyList()) }

    LaunchedEffect(granted) {
        if (granted) entries = withContext(Dispatchers.Default) { buildEntries(loadCalls(ctx)) }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        PageHeader(Icons.Filled.FilterList)
        if (!granted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Call log permission do", color = SamGray)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 140.dp)) {
                items(entries, key = { it.key }, contentType = { it is HeaderEntry }) { e ->
                    when (e) {
                        is HeaderEntry -> Text(
                            e.label, fontSize = 13.sp, color = SamGray,
                            modifier = Modifier.padding(start = 28.dp, top = 14.dp, bottom = 8.dp)
                        )
                        is RowEntry -> CallRow(e)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeCallRow(onCall: () -> Unit, content: @Composable () -> Unit) {
    var dx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentOnCall by rememberUpdatedState(onCall)

    Box(Modifier.fillMaxWidth().drawBehind { if (dx > 0f) drawRect(SamGreen) }) {
        Icon(
            Icons.Filled.Call, null, tint = Color.White,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp).size(22.dp)
                .graphicsLayer { alpha = (dx / 150f).coerceIn(0f, 1f) }
        )
        Box(
            Modifier.offset { IntOffset(dx.roundToInt(), 0) }
                .background(SamBg)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dx > size.width * 0.3f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnCall()
                            }
                            val from = dx
                            scope.launch {
                                animate(
                                    from, 0f,
                                    animationSpec = spring(
                                        Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium
                                    )
                                ) { v, _ -> dx = v }
                            }
                        },
                        onDragCancel = { dx = 0f },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            dx = (dx + delta).coerceIn(0f, size.width * 0.5f)
                        }
                    )
                }
        ) { content() }
    }
}

@Composable
private fun CallRow(e: RowEntry) {
    val g = e.g
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
    val top = if (e.first) 28.dp else 0.dp
    val bottom = if (e.last) 28.dp else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)

    Column(Modifier.padding(horizontal = 10.dp).clip(shape).background(SamBg)) {
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
                        g.time, fontSize = 12.sp,
                        color = if (missed) Color(0xFFFF5A52) else SamGray
                    )
                    Spacer(Modifier.height(2.dp))
                    MsgChip(g.number)
                }
            }
        }
        if (!e.last) {
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
