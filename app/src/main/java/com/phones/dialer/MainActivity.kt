package com.phones.dialer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private val HeavyEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(transparent),
            navigationBarStyle = SystemBarStyle.dark(transparent)
        )
        val dialNumber = intent?.data?.schemeSpecificPart ?: ""
        setContent { SamTheme { App(dialNumber) } }
    }
}

private val PERMS = arrayOf(
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.WRITE_CALL_LOG,
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

    LaunchedEffect(granted) {
        if (!granted) permLauncher.launch(PERMS)
    }

    var tab by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var first = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (first) first = false else refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pos = remember { Animatable(0f) }
    LaunchedEffect(tab) {
        pos.animateTo(tab.toFloat(), spring(dampingRatio = 1f, stiffness = 300f))
    }

    BackHandler(enabled = searching) { searching = false }

    CompositionLocalProvider(LocalSearchOpen provides { searching = true }) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            for (i in 0..2) {
                key(i) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            val d = i - pos.value
                            translationX = when {
                                d > 0f -> size.width * d
                                d <= -1f -> -size.width
                                else -> size.width * 0.28f * d
                            }
                            alpha = when {
                                d >= 1f || d <= -1f -> 0f
                                d > 0f -> 1f
                                else -> 1f + 0.5f * d
                            }
                        }
                    ) {
                        when (i) {
                            0 -> DialpadScreen(initialNumber)
                            1 -> key(refresh) { RecentsScreen(granted) }
                            else -> key(refresh) { ContactsScreen(granted) }
                        }
                    }
                }
            }
            SamNavBar(tab, Modifier.align(Alignment.BottomCenter)) {
                if (it != tab) tab = it
            }
            AnimatedVisibility(
                visible = searching,
                enter = fadeIn(tween(220)) + slideInVertically(tween(320, easing = HeavyEase)) { it / 10 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 10 },
                modifier = Modifier.fillMaxSize()
            ) {
                SearchScreen(onClose = { searching = false })
            }
        }
    }
}

@Composable
fun SamNavBar(tab: Int, modifier: Modifier = Modifier, onTab: (Int) -> Unit) {
    val bg by animateColorAsState(
        if (tab == 0) SamBg else Color.Black,
        tween(300), label = "navBg"
    )
    val glass = RoundedCornerShape(40.dp)
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.9f), bg)))
            .navigationBarsPadding()
            .padding(start = 58.dp, end = 58.dp, top = 28.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(glass)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF3C3C40), Color(0xFF262628))),
                    alpha = 0.92f
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x14FFFFFF))),
                    glass
                )
                .padding(3.dp)
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
        if (selected) Color(0x40FFFFFF) else Color.Transparent,
        spring(stiffness = Spring.StiffnessMedium), label = "nav"
    )
    Column(
        modifier.clip(RoundedCornerShape(30.dp)).background(bg)
            .clickable(onClick = onClick).padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(
            label, color = Color.White, fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
