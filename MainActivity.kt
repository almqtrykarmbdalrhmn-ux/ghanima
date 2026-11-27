package com.honestreward.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.delay
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.load(this)
        Pal.load(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION") run { window.statusBarColor = android.graphics.Color.TRANSPARENT; window.navigationBarColor = android.graphics.Color.TRANSPARENT }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        MobileAds.initialize(this) {}
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        setContent { GhanimaTheme { Surface(Modifier.fillMaxSize(), color = Color.Transparent) { Root() } } }
    }
}

@Composable fun Root() {
    val ctx = LocalContext.current
    var splash by remember { mutableStateOf(true) }
    var logged by remember { mutableStateOf(Session.load(ctx)) }
    var adminPre by remember { mutableStateOf<String?>(null) }
    val act = ctx as? android.app.Activity
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        if (act != null) {
            val c = androidx.core.view.WindowCompat.getInsetsController(act.window, view)
            val light = !Pal.dark && !splash
            c.isAppearanceLightStatusBars = light; c.isAppearanceLightNavigationBars = light
        }
    }
    LaunchedEffect(logged, splash) {
        if (logged && !splash && act != null && !AppOpen.tried) {
            try { if (Api.obj("GET", "/ads/status").optBoolean("app_open", false)) AppOpen.maybeShow(act) } catch (e: Exception) { }
        }
    }
    if (splash) { Splash { splash = false }; return }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Pal.bgTop, Pal.bgBottom)))) {
        GeoBackground()
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Banner()
            val pre = adminPre
            when {
                pre != null -> AdminCode(pre, { adminPre = null; logged = true }, { adminPre = null })
                !logged -> Auth({ logged = true }, { adminPre = it })
                else -> Main { Session.save(ctx, null); logged = false }
            }
        }
    }
}

@Composable fun Main(logout: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var showEarn by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var me by remember { mutableStateOf<JSONObject?>(null) }
    val refresh: suspend () -> Unit = {
        try { me = Api.obj("GET", "/me") }
        catch (e: Exception) { if (e is ApiError && e.code == 401 && (e.message ?: "").startsWith("HTTP")) logout() else Toast.err(errText(e)) }
    }
    LaunchedEffect(Unit) { refresh() }
    val uid = me?.optString("id") ?: ""
    LaunchedEffect(uid) { if (uid.isNotBlank()) while (true) { pollNotifications(ctx, uid); delay(60000) } }
    BackHandler(enabled = showEarn || showTerms) { showEarn = false; showTerms = false }
    val m = me
    if (m == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PURPLE) }
    } else if (m.optString("role") == "admin") {
        Admin(logout)
    } else {
        val labels = listOf(tr("الرئيسية", "Home"), tr("المتجر", "Store"), tr("طلباتي", "Orders"), tr("حسابي", "Account"))
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            NavigationBar(containerColor = Pal.glass) {
                labels.forEachIndexed { i, l ->
                    NavigationBarItem(
                        selected = tab == i && !showEarn && !showTerms, onClick = { tab = i; showEarn = false; showTerms = false },
                        icon = {
                            Icon(when (i) { 0 -> Icons.Filled.Home; 1 -> Icons.Filled.ShoppingCart; 2 -> Icons.AutoMirrored.Filled.List; else -> Icons.Filled.Person }, null)
                        },
                        label = { Text(l, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = PURPLE, indicatorColor = PURPLE, unselectedIconColor = Pal.soft, unselectedTextColor = Pal.soft)
                    )
                }
            }
        }) { pad ->
            Box(Modifier.padding(pad).padding(14.dp)) {
                when {
                    showTerms -> TermsScreen { showTerms = false }
                    showEarn -> Earn(m, refresh) { showEarn = false }
                    tab == 0 -> Home(m, { showEarn = true }, { tab = 1 })
                    tab == 1 -> Store(refresh) { tab = 2 }
                    tab == 2 -> Orders()
                    else -> Profile(m, logout) { showTerms = true }
                }
            }
        }
    }
}

private fun notifText(n: JSONObject): Pair<String, String> = when (n.optString("title")) {
    "order_completed" -> tr("تم تنفيذ طلبك", "Your order was completed") to tr("تم شحن طلبك بنجاح", "Your top-up has been delivered")
    "order_rejected" -> tr("تم رفض طلبك", "Your order was rejected") to tr("أُعيدت النقاط إلى رصيدك", "Your points were returned")
    else -> n.optString("title") to n.optString("body")
}

@Suppress("DEPRECATION")
private fun showSystemNotification(ctx: Context, id: Int, title: String, body: String) {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val b: Notification.Builder
    if (Build.VERSION.SDK_INT >= 26) {
        nm.createNotificationChannel(NotificationChannel("orders", "Orders", NotificationManager.IMPORTANCE_DEFAULT))
        b = Notification.Builder(ctx, "orders")
    } else {
        b = Notification.Builder(ctx)
    }
    b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setAutoCancel(true)
    nm.notify(id, b.build())
}

/** One system notification per new server notification (each id is shown once). */
private suspend fun pollNotifications(ctx: Context, uid: String) {
    try {
        val list = Api.arr("GET", "/notifications").list().sortedBy { it.optString("id").toLongOrNull() ?: 0L }
        val prefs = ctx.getSharedPreferences("hr", 0)
        val key = "last_notif_$uid"
        val last = prefs.getLong(key, -1L)
        val maxId = list.maxOfOrNull { it.optString("id").toLongOrNull() ?: 0L } ?: 0L
        if (last >= 0) list.forEach { n ->
            val id = n.optString("id").toLongOrNull() ?: 0L
            if (id > last) { val (t, b) = notifText(n); showSystemNotification(ctx, id.toInt(), t, b) }
        }
        prefs.edit().putLong(key, maxOf(last, maxId)).apply()
    } catch (e: Exception) { }
}
