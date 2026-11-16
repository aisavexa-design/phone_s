package com.phones.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private const val DUR = 460
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
    var prevTab by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    val navBg by animateColorAsState(
        if (tab == 0) SamBg else Color.Black,
        tween(300), label = "navBg"
    )

    BackHandler(enabled = searching) { searching = false }

    CompositionLocalProvider(LocalSearchOpen provides { searching = true }) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(DUR, easing = HeavyEase)) { it } togetherWith
                            fadeOut(tween(DUR), targetAlpha = 0.999f))
                            .apply { targetContentZIndex = 1f }
                    } else {
                        (EnterTransition.None togetherWith
                            slideOutHorizontally(tween(DUR, easing = HeavyEase)) { it })
                            .apply { targetContentZIndex = -1f }
                    }
                },
                label = "tabs"
            ) { t ->
                val other = if (t == tab) prevTab else tab
                val behind = t < other
                val p by transition.animateFloat(
                    transitionSpec = { tween(DUR, easing = HeavyEase) },
                    label = "depth"
                ) { s -> if (s == EnterExitState.Visible) 0f else 1f }

                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            if (behind) {
                                val s = 1f - 0.08f * p
                                scaleX = s
                                scaleY = s
                                translationX = -size.width * 0.2f * p
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            if (behind) drawRect(Color.Black.copy(alpha = 0.5f * p))
                        }
                ) {
                    when (t) {
                        0 -> DialpadScreen(initialNumber)
                        1 -> RecentsScreen(granted)
                        else -> ContactsScreen(granted)
                    }
                }
            }
            SamNavBar(tab, navBg, Modifier.align(Alignment.BottomCenter)) {
                if (it != tab) {
                    prevTab = tab
                    tab = it
                }
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
fun SamNavBar(tab: Int, bg: Color, modifier: Modifier = Modifier, onTab: (Int) -> Unit) {
    val glass = RoundedCornerShape(40.dp)
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.9f), bg)))
            .navigationBarsPadding()
            .padding(start = 72.dp, end = 72.dp, top = 30.dp, bottom = 10.dp),
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
            .clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(
            label, color = Color.White, fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
