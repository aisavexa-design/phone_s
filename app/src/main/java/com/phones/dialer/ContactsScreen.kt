package com.phones.dialer

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactItem(val id: Long, val name: String, val number: String)

private fun loadContacts(ctx: Context): List<ContactItem> {
    val out = mutableListOf<ContactItem>()
    ctx.contentResolver.query(
        Phone.CONTENT_URI,
        arrayOf(Phone._ID, Phone.DISPLAY_NAME, Phone.NUMBER),
        null, null, "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
    )?.use { c ->
        while (c.moveToNext()) {
            out += ContactItem(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "")
        }
    }
    return out
}

@Composable
fun ContactsScreen(granted: Boolean) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<ContactItem>>(emptyList()) }

    LaunchedEffect(granted) {
        if (granted) list = withContext(Dispatchers.IO) { loadContacts(ctx) }
    }

    if (!granted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Contacts permission do", color = SamGray)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 24.dp)) {
        items(list, key = { it.id }) { item ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { placeCall(ctx, item.number) }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(SamNavSel),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item.name.take(1).uppercase(),
                        fontSize = 20.sp, color = Color.White
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(item.name, fontSize = 18.sp, color = Color.White)
                    Text(item.number, fontSize = 13.sp, color = SamGray)
                }
            }
        }
    }
}
