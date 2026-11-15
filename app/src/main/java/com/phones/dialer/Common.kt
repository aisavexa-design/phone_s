package com.phones.dialer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val TILE_COLORS = listOf(
    Color(0xFF9DB4EC) to Color(0xFF8FA8E3),
    Color(0xFFEFA0B0) to Color(0xFFD683CC),
    Color(0xFF8EE8C8) to Color(0xFF85E2C1),
    Color(0xFFFFB487) to Color(0xFFFF9A8B),
    Color(0xFFB69CF0) to Color(0xFF9A8CE6)
)

@Composable
fun PageHeader(firstIcon: ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Phone", fontSize = 34.sp, fontWeight = FontWeight.Bold,
            color = Color.White, modifier = Modifier.weight(1f)
        )
        Icon(firstIcon, null, tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(28.dp))
        Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(28.dp))
        Icon(Icons.Filled.MoreVert, null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun rememberPhoto(uri: String?): ImageBitmap? {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    return bmp
}

@Composable
fun Avatar(name: String, photo: String?, size: Dp, seed: Long, letterSize: TextUnit) {
    val bmp = rememberPhoto(photo)
    val color = TILE_COLORS[seed.mod(TILE_COLORS.size.toLong()).toInt()].first
    Box(
        Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name.take(1).uppercase(), fontSize = letterSize, color = Color.White)
        }
    }
}
