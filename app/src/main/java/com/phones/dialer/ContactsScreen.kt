package com.phones.dialer

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactRow(
    val id: Long, val name: String, val number: String,
    val photo: String?, val starred: Boolean
)

data class ContactsData(
    val contacts: List<ContactRow>,
    val profileName: String?,
    val profilePhoto: String?
)

private fun loadData(ctx: Context): ContactsData {
    val map = LinkedHashMap<Long, ContactRow>()
    ctx.contentResolver.query(
        Phone.CONTENT_URI,
        arrayOf(
            Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER,
            Phone.PHOTO_THUMBNAIL_URI, Phone.STARRED
        ),
        null, null, "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
    )?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            if (!map.containsKey(id)) {
                map[id] = ContactRow(
                    id, c.getString(1) ?: "", c.getString(2) ?: "",
                    c.getString(3), c.getInt(4) == 1
                )
            }
        }
    }
    var pName: String? = null
    var pPhoto: String? = null
    try {
        ctx.contentResolver.query(
            ContactsContract.Profile.CONTENT_URI,
            arrayOf(
                ContactsContract.Profile.DISPLAY_NAME,
                ContactsContract.Profile.PHOTO_THUMBNAIL_URI
            ),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                pName = c.getString(0)
                pPhoto = c.getString(1)
            }
        }
    } catch (e: Exception) { }
    return ContactsData(map.values.toList(), pName, pPhoto)
}

private fun letterOf(name: String): String {
    val ch = name.firstOrNull()?.uppercaseChar()
    return if (ch != null && ch.isLetter()) ch.toString() else "&"
}

@Composable
fun ContactsScreen(granted: Boolean) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(ContactsData(emptyList(), null, null)) }

    LaunchedEffect(granted) {
        if (granted) data = withContext(Dispatchers.IO) { loadData(ctx) }
    }

    val favs = remember(data) { data.contacts.filter { it.starred } }
    val groups = remember(data) {
        data.contacts.groupBy { letterOf(it.name) }.toList()
            .sortedBy { if (it.first == "&") "" else it.first }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        PageHeader(Icons.Filled.Add)
        if (!granted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Contacts permission do", color = SamGray)
            }
            return@Column
        }
         LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 140.dp)) {
            if (favs.isNotEmpty()) {
                item {
                    Row(
                        Modifier.padding(start = 28.dp, top = 12.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Star, null, tint = SamGray, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Favourites", fontSize = 13.sp, color = SamGray)
                    }
                    Column(
                        Modifier.padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        favs.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { c ->
                                    FavTile(c, Modifier.weight(1f)) { placeCall(ctx, c.number) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            if (data.profileName != null) {
                item {
                    Text(
                        "My profile", fontSize = 13.sp, color = SamGray,
                        modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp)
                    )
                    Pill {
                        Avatar(data.profileName ?: "", data.profilePhoto, 36.dp, 0L, 17.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(data.profileName ?: "", fontSize = 16.5.sp, color = Color.White)
                    }
                }
            }
            item {
                Spacer(Modifier.height(10.dp))
                Pill {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFB5B5B5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Groups, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("Groups", fontSize = 16.5.sp, color = Color.White)
                }
            }
            groups.forEach { (letter, rows) ->
                item(key = "h$letter") {
                    Text(
                        letter, fontSize = 14.sp, color = SamGray,
                        modifier = Modifier.padding(start = 28.dp, top = 18.dp, bottom = 6.dp)
                    )
                }
                items(rows, key = { "c${it.id}" }) { c ->
                    Pill(onClick = { placeCall(ctx, c.number) }) {
                        Avatar(c.name, c.photo, 36.dp, c.id, 17.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            c.name, fontSize = 16.5.sp, color = Color.White,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    var m = Modifier.padding(horizontal = 10.dp, vertical = 2.dp).fillMaxWidth()
        .clip(RoundedCornerShape(40.dp)).background(SamBg)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(
        m.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
private fun FavTile(c: ContactRow, modifier: Modifier, onClick: () -> Unit) {
    val (a, b) = TILE_COLORS[c.id.mod(TILE_COLORS.size.toLong()).toInt()]
    val bmp = rememberPhoto(c.photo)
    Column(
        modifier.aspectRatio(0.85f).clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(a, b)))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(59.dp).clip(CircleShape)
                .then(if (bmp == null) Modifier.border(1.dp, Color(0x66FFFFFF), CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    c.name.take(1).uppercase(), fontSize = 33.sp,
                    fontWeight = FontWeight.Light, color = Color.White
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            c.name, fontSize = 12.sp, color = Color.White, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
