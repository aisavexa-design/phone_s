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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
        containerColor = Color.Black,
        bottomBar = {
            NavigationBar(containerColor = Color.Black) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Dialpad, null) }, label = { Text("Keypad") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.History, null) }, label = { Text("Recents") }
                )
            }
        }
    ) { pad ->
        Crossfade(tab, Modifier.padding(pad), animationSpec = tween(180), label = "tabs") {
            if (it == 0) DialpadScreen(initialNumber) else RecentsScreen(granted)
        }
    }
}
