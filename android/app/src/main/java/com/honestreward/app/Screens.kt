                       package com.honestreward.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

const val SUPPORT_EMAIL = "almqtrykarmbdalrhmn@gmail.com"

@Composable fun Splash(done: () -> Unit) {
    LaunchedEffect(Unit) { delay(1500); done() }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(BRAND)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GiftLogo(132.dp)
            Spacer(Modifier.height(16.dp))
            Text(tr("غنيمة", "Ghanima"), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable fun Auth(onOk: () -> Unit, onAdmin: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reg by remember { mutableStateOf(false) }
    var em by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var terms by remember { mutableStateOf(false) }
    if (terms) { TermsScreen { terms = false }; return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        GiftLogo(96.dp)
        Text(tr("غنيمة", "Ghanima"), color = Pal.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        GlassCard {
            Text(if (reg) tr("إنشاء حساب", "Create account") else tr("تسجيل الدخول", "Log in"), color = Pal.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(em, { em = it }, label = { Text(tr("البريد الإلكتروني", "Email")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(pw, { pw = it }, label = { Text(tr("كلمة المرور", "Password")) }, singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton({ show = !show }) { Text(tr("إظهار", "Show")) } }, modifier = Modifier.fillMaxWidth())
            if (reg) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pw2, { pw2 = it }, label = { Text(tr("تأكيد كلمة المرور", "Confirm password")) }, singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
            GradButton(if (reg) tr("إنشاء حساب", "Create account") else tr("دخول", "Log in"), enabled = !busy) {
                scope.launch {
                    if (reg && pw != pw2) { Toast.err(tr("كلمتا المرور غير متطابقتين", "Passwords do not match")); return@launch }
                    busy = true
                    try {
                        val r = Api.obj("POST", if (reg) "/auth/register" else "/auth/login", JSONObject().put("email", em.trim()).put("password", pw))
                        if (r.has("step")) onAdmin(r.getString("pre")) else { Session.save(ctx, r.getString("token")); onOk() }
                    } catch (e: Exception) {
                        val m = (e as? ApiError)?.message
                        if (m == "email taken") { reg = false; Toast.err(tr("هذا الحساب مسجل مسبقاً", "This account is already registered")) }
                        else if (m == "not registered") { reg = true; Toast.err(tr("هذا الحساب غير مسجل", "This account is not registered")) }
                        else Toast.err(errText(e))
                    } finally { busy = false }
                }
            }
            if (reg) TextButton({ terms = true }) { Text(tr("بإنشاء الحساب توافق على الشروط والقواعد", "By signing up you accept the terms and rules"), fontSize = 12.sp) }
            TextButton({ reg = !reg }) { Text(if (reg) tr("لدي حساب بالفعل", "I already have an account") else tr("إنشاء حساب جديد", "Create a new account")) }
        }
    }
}

@Composable fun AdminCode(pre: String, onDone: () -> Unit, cancel: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        GlassCard {
            Text(tr("هذا حساب الإدارة", "This is the administrator account"), color = Pal.text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Text(tr("أدخل رمز الإدارة الثاني للمتابعة", "Enter the second admin code to continue"), color = Pal.soft)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(code, { code = it }, label = { Text(tr("رمز الإدارة", "Admin code")) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            GradButton(tr("دخول", "Enter"), enabled = !busy) {
                scope.launch {
                    busy = true
                    try {
                        val r = Api.obj("POST", "/auth/admin-code", JSONObject().put("pre", pre).put("code", code))
                        Session.save(ctx, r.getString("token")); onDone()
                    } catch (e: Exception) { Toast.err(errText(e)); if ((e as? ApiError)?.message == "login again") cancel() }
                    finally { busy = false }
                }
            }
            TextButton(cancel) { Text(tr("رجوع", "Back")) }
        }
    }
}

@Composable fun Home(me: JSONObject?, goEarn: () -> Unit, goStore: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GiftLogo(46.dp); Spacer(Modifier.width(10.dp))
            Text(tr("غنيمة", "Ghanima"), color = Pal.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(BRAND), RoundedCornerShape(28.dp)).padding(22.dp)) {
            Column {
                Text(tr("رصيد نقاطك", "Your points"), color = Color.White.copy(alpha = 0.85f))
                Text("${me?.num("balance") ?: "…"}", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("${tr("المكتسب", "Earned")}: ${me?.num("earned") ?: 0}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    Text("${tr("المستبدل", "Redeemed")}: ${me?.num("redeemed") ?: 0}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
            }
        }
        GradButton(tr("اكسب النقاط", "Earn points"), icon = Icons.Filled.PlayArrow) { goEarn() }
        GradButton(tr("المتجر", "Store"), icon = Icons.Filled.ShoppingCart) { goStore() }
    }
}

@Composable fun WaitOverlay(text: String, progress: Float) {
    Box(Modifier.fillMaxSize().background(Pal.bgTop.copy(alpha = 0.97f)).clickable { }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                if (progress <= 0f) CircularProgressIndicator(modifier = Modifier.size(240.dp), strokeWidth = 16.dp, color = PURPLE)
                else CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(240.dp), strokeWidth = 16.dp, color = PURPLE, trackColor = PURPLE.copy(alpha = 0.15f))
                if (progress > 0f) Text("${(progress * 100).toInt()}%", color = Pal.text, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(24.dp))
            Text(text, color = Pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable fun Earn(me: JSONObject?, refresh: suspend () -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<JSONObject?>(null) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var note by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableStateOf(0) }
    suspend fun loadStatus() { try { st = Api.obj("GET", "/ads/status") } catch (e: Exception) { Toast.err(errText(e)) } }
    LaunchedEffect(Unit) { loadStatus() }
    LaunchedEffect(st) { var w = st?.optInt("wait") ?: 0; wait = w; while (w > 0) { delay(1000); w -= 1; wait = w } }
    fun start() {
        val s0 = st ?: return
        when {
            !s0.optBoolean("enabled") -> note = tr("الإعلانات متوقفة حالياً", "Ads are paused right now")
            s0.optInt("today") >= s0.optInt("limit") -> note = tr("وصلت إلى الحد اليومي من الإعلانات", "You reached today's ad limit")
            wait > 0 -> note = tr("انتظر قليلاً قبل الإعلان التالي", "Wait a moment before the next ad")
            else -> {
                note = null; progress = 0f; overlay = tr("جارٍ تجهيز الإعلان…", "Preparing the ad…")
                RewardedAd.load(ctx, BuildConfig.ADMOB_REWARDED_UNIT, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        ad.setServerSideVerificationOptions(ServerSideVerificationOptions.Builder().setUserId(me?.optString("id") ?: "").build())
                        scope.launch {
                            val before = runCatching { Api.obj("GET", "/me").num("balance") }.getOrDefault(me?.num("balance") ?: 0L)
                            overlay = null
                            ad.show(ctx as Activity) { _ ->
                                scope.launch {
                                    // Points are NEVER added by the app: the server adds them after Google's signed callback. We only poll.
                                    overlay = tr("جارٍ تأكيد المكافأة…", "Confirming your reward…")
                                    var ok = false
                                    for (i in 1..20) {
                                        progress = i / 20f
                                        delay(1500)
                                        try { if (Api.obj("GET", "/me").num("balance") > before) { ok = true; break } } catch (e: Exception) { }
                                    }
                                    overlay = null
                                    if (ok) { refresh(); loadStatus(); note = tr("تمت إضافة 10 نقاط", "+10 points added") }
                                    else note = tr("لم يصل التأكيد بعد، سيُضاف الرصيد عند وصوله", "Confirmation not received yet; points will be added when it arrives")
                                }
                            }
                        }
                    }
                    override fun onAdFailedToLoad(e: LoadAdError) {
                        overlay = null
                        note = tr("لديك مكافأة سابقة، حاول لاحقاً بعد بضع دقائق أو ساعات", "You already have a previous reward, try again in a few minutes or hours") + "\n[AdMob " + e.code + ": " + e.message + "]"
                    }
                })
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            GlassCard(Modifier.width(220.dp)) {
                Text(tr("إعلانات اليوم", "Today's ads"), color = Pal.soft, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("${st?.optInt("today") ?: 0}/${st?.optInt("limit") ?: 200}", color = Pal.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(30.dp))
            if (wait > 0) {
                val total = (st?.optInt("interval") ?: 60).coerceAtLeast(1)
                Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { wait / total.toFloat() }, modifier = Modifier.fillMaxSize(), strokeWidth = 14.dp, color = PURPLE, trackColor = PURPLE.copy(alpha = 0.15f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$wait", color = Pal.text, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                        Text(tr("ثانية للإعلان التالي", "s until the next ad"), color = Pal.soft, fontSize = 12.sp)
                    }
                }
            } else Box(
                Modifier.size(190.dp).clip(CircleShape).background(Brush.linearGradient(BRAND), CircleShape)
                    .clickable(enabled = overlay == null) { start() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(100.dp)) }
            Spacer(Modifier.height(18.dp))
            Text(tr("10 نقاط لكل إعلان", "10 points per ad"), color = Pal.text, fontWeight = FontWeight.Bold)
            note?.let { Text(it, color = PURPLE, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp)) }
        }
        IconButton(back, Modifier.align(Alignment.TopStart)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Pal.text) }
        overlay?.let { WaitOverlay(it, progress) }
    }
}

@Composable fun Store(refresh: suspend () -> Unit, onOrdered: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var pk by remember { mutableStateOf(listOf<JSONObject>()) }
    var sel by remember { mutableStateOf<JSONObject?>(null) }
    var pid by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { try { pk = Api.arr("GET", "/packages").list() } catch (e: Exception) { Toast.err(errText(e)) } }
    val game = if (tab == 0) "pubg" else "freefire"
    val unit = if (tab == 0) tr("شدة", "UC") else tr("جوهرة", "Diamonds")
    val shown = pk.filter { it.optString("game") == game }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegTabs(listOf(tr("شدات ببجي", "PUBG UC"), tr("جواهر فري فاير", "Free Fire Diamonds")), tab) { tab = it }
        LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(shown) { p ->
                val idx = shown.indexOf(p)
                GlassCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ProductArt(idx + (if (tab == 0) 0 else 3), 88.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("${p.optInt("amount")} $unit", color = Pal.text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("${p.optString("price_points")} ${tr("نقطة", "pts")}", color = PURPLE, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        GradButton(tr("استبدال", "Redeem")) { sel = p; pid = "" }
                    }
                }
            }
        }
    }
    sel?.let { p ->
        AlertDialog(onDismissRequest = { sel = null }, containerColor = Pal.dialog,
            title = { Text("${p.optInt("amount")} $unit = ${p.optString("price_points")} ${tr("نقطة", "pts")}", color = Pal.text) },
            text = { OutlinedTextField(pid, { pid = it.filter { c -> c.isDigit() } }, label = { Text(tr("ID حسابك في اللعبة", "Your in-game account ID")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { Button(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        Api.call("POST", "/orders", JSONObject().put("package_id", p.getInt("id")).put("player_id", pid))
                        sel = null; Toast.ok(tr("تم إرسال طلبك وحجز النقاط", "Order sent and points reserved")); refresh(); onOrdered()
                    } catch (e: Exception) { Toast.err(errText(e)) } finally { busy = false }
                }
            }) { Text(tr("تأكيد", "Confirm")) } },
            dismissButton = { TextButton({ sel = null }) { Text(tr("إلغاء", "Cancel")) } })
    }
}

@Composable fun Orders() {
    var tab by remember { mutableStateOf(0) }
    var os by remember { mutableStateOf(listOf<JSONObject>()) }
    LaunchedEffect(Unit) { while (true) { try { os = Api.arr("GET", "/orders").list() } catch (e: Exception) { }; delay(20000) } }
    val shown = os.filter { when (tab) { 0 -> it.optString("status") !in listOf("completed", "rejected"); 1 -> it.optString("status") == "completed"; else -> it.optString("status") == "rejected" } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegTabs(listOf(tr("قيد الانتظار", "Pending"), tr("تم التنفيذ", "Completed"), tr("مرفوض", "Rejected")), tab) { tab = it }
        if (shown.isEmpty()) Text(tr("لا توجد طلبات هنا", "No orders here"), color = Pal.soft, modifier = Modifier.padding(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown) { o ->
                GlassCard {
                    val unit = if (o.optString("game") == "pubg") tr("شدة", "UC") else tr("جوهرة", "Diamonds")
                    Text("${o.optInt("amount")} $unit", color = Pal.text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text("${o.optString("cost")} ${tr("نقطة", "pts")}", color = PURPLE, fontWeight = FontWeight.Bold)
                    Text("ID: ${o.optString("player_id")}", color = Pal.soft, fontSize = 13.sp)
                    Text(o.optString("created_at").take(10), color = Pal.soft, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable fun Profile(me: JSONObject?, logout: () -> Unit, openTerms: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf("") }
    var n1 by remember { mutableStateOf("") }
    var n2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Text(tr("البريد الإلكتروني", "Email"), color = Pal.soft, fontSize = 12.sp)
            Text(me?.optString("email") ?: "", color = Pal.text, fontWeight = FontWeight.Bold)
        }
        GlassCard {
            SectionTitle(tr("تغيير كلمة المرور", "Change password"))
            OutlinedTextField(cur, { cur = it }, label = { Text(tr("كلمة المرور الحالية", "Current password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(n1, { n1 = it }, label = { Text(tr("كلمة المرور الجديدة", "New password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(n2, { n2 = it }, label = { Text(tr("تأكيد كلمة المرور الجديدة", "Confirm new password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Spacer(Modifier.height(12.dp))
            GradButton(tr("حفظ كلمة المرور", "Save password"), enabled = !busy) {
                scope.launch {
                    if (n1 != n2) { Toast.err(tr("كلمتا المرور غير متطابقتين", "Passwords do not match")); return@launch }
                    busy = true
                    try {
                        Api.call("POST", "/me/password", JSONObject().put("current", cur).put("password", n1))
                        cur = ""; n1 = ""; n2 = ""; Toast.ok(tr("تم تغيير كلمة المرور", "Password changed"))
                    } catch (e: Exception) { Toast.err(errText(e)) } finally { busy = false }
                }
            }
        }
        GlassCard {
            SectionTitle(tr("المظهر", "Theme"))
            SegTabs(listOf(tr("فاتح", "Light"), tr("داكن", "Dark")), if (Pal.dark) 1 else 0) { Pal.set(ctx, it == 1) }
            SectionTitle(tr("اللغة", "Language"))
            SegTabs(listOf("العربية", "English"), if (Lang.ar) 0 else 1) { Lang.set(ctx, it == 0) }
        }
        GradButton(tr("تواصل مع الدعم", "Contact support"), icon = Icons.Filled.Email) {
            ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")))
        }
        OutlinedButton(openTerms, Modifier.fillMaxWidth()) { Text(tr("الشروط والقواعد", "Terms and rules")) }
        OutlinedButton(logout, Modifier.fillMaxWidth()) { Text(tr("تسجيل الخروج", "Log out")) }
    }
}

@Composable fun TermsScreen(back: () -> Unit) {
    val rules = listOf(
        "استخدام أي برامج خارجية أو تحايل على الإعلانات يؤدي إلى حظر الحساب." to "Using any external software or tricking the ads leads to an account ban.",
        "في حال المخالفة لن يصلك أي شحن وتسقط نقاطك." to "In case of violation you will receive no top-ups and your points are forfeited.",
        "حساب واحد لكل شخص." to "One account per person.",
        "تأكد من صحة ID حسابك، فالشحن لـ ID خاطئ مسؤوليتك." to "Make sure your account ID is correct; top-ups to a wrong ID are your responsibility.",
        "يتم الشحن يدوياً، فقد يتأخر تنفيذ الطلبات." to "Top-ups are done manually, so orders may be delayed.",
        "عند رفض الطلب تعود نقاطك تلقائياً." to "When an order is rejected, your points are returned automatically.",
        "التطبيق غير تابع لـ PUBG ولا Free Fire." to "The app is not affiliated with PUBG or Free Fire."
    )
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Pal.text) }
            Text(tr("الشروط والقواعد", "Terms and rules"), color = Pal.text, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rules.size) { i -> GlassCard { Text("${i + 1}. " + tr(rules[i].first, rules[i].second), color = Pal.text) } }
        }
    }
}

@Composable fun Admin(logout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var orders by remember { mutableStateOf(listOf<JSONObject>()) }
    var confirm by remember { mutableStateOf<Pair<JSONObject, String>?>(null) }
    var msgFor by remember { mutableStateOf<JSONObject?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    suspend fun load() { try { orders = Api.arr("GET", "/admin/orders").list() } catch (e: Exception) { Toast.err(errText(e)) } }
    LaunchedEffect(Unit) { while (true) { load(); delay(15000) } }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("لوحة الإدارة", "Admin panel"), color = Pal.text, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            IconButton({ scope.launch { load() } }) { Icon(Icons.Filled.Refresh, null, tint = Pal.text) }
            TextButton(logout) { Text(tr("خروج", "Log out")) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(orders) { o ->
                val final = o.optString("status") in listOf("completed", "rejected")
                GlassCard {
                    val unit = if (o.optString("game") == "pubg") "UC" else "Diamonds"
                    Text("${o.optInt("amount")} $unit · ${o.optString("cost")} pts", color = Pal.text, fontWeight = FontWeight.ExtraBold)
                    Text(o.optString("email"), color = Pal.soft, fontSize = 13.sp)
                    Text("ID: ${o.optString("player_id")}", color = Pal.text)
                    Text(o.optString("status"), color = if (final) Pal.soft else PURPLE, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button({ confirm = o to "completed" }, Modifier.weight(1f), enabled = !final) { Text(tr("تنفيذ", "Execute"), fontSize = 12.sp) }
                        OutlinedButton({ confirm = o to "rejected" }, Modifier.weight(1f), enabled = !final) { Text(tr("رفض", "Reject"), fontSize = 12.sp) }
                        OutlinedButton({ msgFor = o; title = ""; body = "" }, Modifier.weight(1f)) { Text(tr("رسالة", "Message"), fontSize = 12.sp) }
                    }
                }
            }
        }
    }
    confirm?.let { (o, st) ->
        AlertDialog(onDismissRequest = { confirm = null }, containerColor = Pal.dialog,
            title = { Text(if (st == "completed") tr("تأكيد التنفيذ؟", "Confirm execution?") else tr("تأكيد الرفض؟", "Confirm rejection?"), color = Pal.text) },
            text = { Text("${o.optString("email")} · ID ${o.optString("player_id")}", color = Pal.soft) },
            confirmButton = { Button({
                scope.launch {
                    try { Api.call("POST", "/admin/orders/${o.optString("id")}/status", JSONObject().put("status", st)); confirm = null; load() }
                    catch (e: Exception) { Toast.err(errText(e)) }
                }
            }) { Text(tr("تأكيد", "Confirm")) } },
            dismissButton = { TextButton({ confirm = null }) { Text(tr("إلغاء", "Cancel")) } })
    }
    msgFor?.let { o ->
        AlertDialog(onDismissRequest = { msgFor = null }, containerColor = Pal.dialog,
            title = { Text(tr("رسالة للمستخدم", "Message to user"), color = Pal.text) },
            text = { Column {
                OutlinedTextField(title, { title = it }, label = { Text(tr("العنوان", "Title")) }, singleLine = true)
                OutlinedTextField(body, { body = it }, label = { Text(tr("الرسالة", "Message")) })
            } },
            confirmButton = { Button({
                scope.launch {
                    try { Api.call("POST", "/admin/notify", JSONObject().put("user_id", o.optString("user_id")).put("title", title).put("body", body)); msgFor = null; Toast.ok(tr("أُرسلت الرسالة", "Message sent")) }
                    catch (e: Exception) { Toast.err(errText(e)) }
                }
            }) { Text(tr("إرسال", "Send")) } },
            dismissButton = { TextButton({ msgFor = null }) { Text(tr("إلغاء", "Cancel")) } })
    }
}
