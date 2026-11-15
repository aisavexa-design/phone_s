package com.phones.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dialNumber = intent?.data?.schemeSpecificPart ?: ""
        setContent { SamTheme { App(dialNumber) } }
    }
}

private val PERMS = arrayOf(
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_PHONE_STATE
)

@Composable
fun App(initialNumber: String) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(PERMS.all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = it.values.all { ok -> ok } }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    LaunchedEffect(granted) {
        if (!granted) {
            permLauncher.launch(PERMS)
        } else {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !rm.isRoleHeld(RoleManager.ROLE_DIALER)
            ) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
    }

    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = SamBg,
        bottomBar = { SamNavBar(tab) { tab = it } }
    ) { pad ->
        Crossfade(tab, Modifier.padding(pad), animationSpec = tween(180), label = "tabs") {
            when (it) {
                0 -> DialpadScreen(initialNumber)
                1 -> RecentsScreen(granted)
                else -> ContactsScreen(granted)
            }
        }
    }
}

@Composable
fun SamNavBar(tab: Int, onTab: (Int) -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(SamBg).padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(40.dp))
                .background(SamNav).padding(6.dp)
        ) {
            NavItem("Keypad", Icons.Filled.Dialpad, tab == 0, Modifier.weight(1f)) { onTab(0) }
            NavItem("Recents", Icons.Filled.History, tab == 1, Modifier.weight(1f)) { onTab(1) }
            NavItem("Contacts", Icons.Outlined.Person, tab == 2, Modifier.weight(1f)) { onTab(2) }
        }
    }
}

@Composable
private fun NavItem(
    label: String, icon: ImageVector, selected: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (selected) SamNavSel else Color.Transparent,
        spring(stiffness = Spring.StiffnessMedium), label = "nav"
    )
    Column(
        modifier.clip(RoundedCornerShape(34.dp)).background(bg)
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Text(
            label, color = Color.White, fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
