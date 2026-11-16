package com.phones.dialer

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchItem(val name: String, val number: String, val photo: String?)

object SearchStore {
    private const val PREF = "search"
    private const val KEY = "recent"

    fun load(ctx: Context): List<SearchItem> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }.map {
            val p = it.split("\t")
            SearchItem(p.getOrElse(0) { "" }, p.getOrElse(1) { "" }, null)
        }
    }

    private fun save(ctx: Context, list: List<SearchItem>) {
        val raw = list.joinToString("\n") {
            it.name.replace('\t', ' ').replace('\n', ' ') + "\t" +
                it.number.replace('\t', ' ').replace('\n', ' ')
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }

    fun add(ctx: Context, item: SearchItem) {
        val rest = load(ctx).filter { !(it.number == item.number && it.name == item.name) }
        save(ctx, (listOf(item) + rest).take(10))
    }

    fun remove(ctx: Context, item: SearchItem) {
        save(ctx, load(ctx).filter { !(it.number == item.number && it.name == item.name) })
    }

    fun clear(ctx: Context) = save(ctx, emptyList())
}

private fun digits(s: String) = s.filter { it.isDigit() }

private fun loadPool(ctx: Context): List<SearchItem> {
    val out = ArrayList<SearchItem>()
    val seen = HashSet<String>()
    try {
        ctx.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI),
            null, null, "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1) ?: continue
                out += SearchItem(c.getString(0) ?: "", n, c.getString(2))
                seen += digits(n)
            }
        }
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME),
            null, null, "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            var count = 0
            while (c.moveToNext() && count < 500) {
                count++
                val num = c.getString(0) ?: continue
                if (num.isBlank()) continue
                if (seen.add(digits(num))) out += SearchItem(c.getString(1) ?: "", num, null)
            }
        }
    } catch (e: Exception) { }
    return out
}

@Composable
fun SearchScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var pool by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    var recents by remember { mutableStateOf(SearchStore.load(ctx)) }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        pool = withContext(Dispatchers.IO) { loadPool(ctx) }
    }

    val results = remember(query, pool) {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else {
            val ql = q.lowercase()
            val qd = digits(q)
            pool.filter {
                it.name.lowercase().contains(ql) ||
                    (qd.isNotEmpty() && digits(it.number).contains(qd))
            }.take(100)
        }
    }
    val q = query.trim()
    val looksLikeNumber = q.length >= 3 && q.all { it.isDigit() || it in "+*#- " }

    fun callAndClose(item: SearchItem) {
        SearchStore.add(ctx, item)
        placeCall(ctx, item.number)
        onClose()
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(30.dp)).background(SamBg).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack) { onClose() }
            Box(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                if (query.isEmpty()) Text("Search", color = SamGray, fontSize = 16.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(SamGreen),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (q.isNotEmpty()) {
                            SearchStore.add(
                                ctx,
                                if (looksLikeNumber) SearchItem("", q, null) else SearchItem(q, "", null)
                            )
                            recents = SearchStore.load(ctx)
                        }
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            if (query.isNotEmpty()) HeaderIcon(Icons.Filled.Close) { query = "" }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (query.isEmpty()) {
                if (recents.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 28.dp, end = 24.dp, top = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Recent searches", fontSize = 13.sp, color = SamGray,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "Clear all", fontSize = 13.sp, color = SamGreen,
                                modifier = Modifier.clickable {
                                    SearchStore.clear(ctx)
                                    recents = emptyList()
                                }
                            )
                        }
                    }
                    items(recents) { r ->
                        val title = r.name.ifBlank { r.number }
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    if (r.number.isBlank()) query = r.name else callAndClose(r)
                                }
                                .padding(horizontal = 28.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.History, null, tint = SamGray, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    title, fontSize = 17.sp, color = Color.White,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (r.name.isNotBlank() && r.number.isNotBlank()) {
                                    Text(r.number, fontSize = 13.sp, color = SamGray)
                                }
                            }
                            HeaderIcon(Icons.Filled.Close) {
                                SearchStore.remove(ctx, r)
                                recents = SearchStore.load(ctx)
                            }
                        }
                    }
                }
            } else {
                if (looksLikeNumber) {
                    item {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { callAndClose(SearchItem("", q, null)) }
                                .padding(horizontal = 28.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(SamGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Text("Call $q", fontSize = 17.sp, color = Color.White)
                        }
                    }
                }
                items(results) { r ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { callAndClose(r) }
                            .padding(horizontal = 28.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(
                            r.name.ifBlank { r.number }, r.photo, 40.dp,
                            (r.number.hashCode().toLong() and 0x7fffffffL), 18.sp
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                r.name.ifBlank { r.number }, fontSize = 17.sp, color = Color.White,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (r.name.isNotBlank()) Text(r.number, fontSize = 13.sp, color = SamGray)
                        }
                    }
                }
                if (results.isEmpty() && !looksLikeNumber) {
                    item {
                        Text(
                            "Kuch nahi mila", color = SamGray, fontSize = 15.sp,
                            modifier = Modifier.padding(28.dp)
                        )
                    }
                }
            }
        }
    }
}
