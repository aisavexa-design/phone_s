package com.phones.dialer

import android.content.Context
import android.provider.CallLog
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CallItem(val id: Long, val number: String, val name: String?, val type: Int, val date: Long)

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
        while (c.moveToNext() && out.size < 200) {
            out += CallItem(c.getLong(0), c.getString(1) ?: "", c.getString(2), c.getInt(3), c.getLong(4))
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

    if (!granted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Call log permission do", color = Color.Gray)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(calls, key = { it.id }) { item ->
            val missed = item.type == CallLog.Calls.MISSED_TYPE
            Column(
                Modifier.fillMaxWidth()
                    .clickable { placeCall(ctx, item.number) }
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(
                    item.name?.takeIf { it.isNotBlank() } ?: item.number.ifBlank { "Unknown" },
                    fontSize = 18.sp, color = if (missed) SamRed else Color.White
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(item.date).toString(),
                    fontSize = 13.sp, color = Color.Gray
                )
            }
        }
    }
}
