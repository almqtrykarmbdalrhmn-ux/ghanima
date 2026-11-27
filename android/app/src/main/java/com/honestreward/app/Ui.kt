package com.honestreward.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

val RED = Color(0xFFD62839)
fun JSONArray.list(): List<JSONObject> = List(length()) { getJSONObject(it) }
fun JSONObject.num(k: String) = optString(k, "0").toLongOrNull() ?: 0L

class Msg(val text: String, val err: Boolean, val restart: Boolean = false)
object Toast {
    var msg by mutableStateOf<Msg?>(null)
    /** Connection / server failures offer "restart the app"; normal input errors (wrong password...) only say what is wrong. */
    private fun isConnection(t: String) = t.startsWith("تعذر الاتصال بالخادم") || t.startsWith("Could not reach the server") || t.startsWith("HTTP")
    fun err(t: String) { if (t.isNotBlank()) msg = Msg(t, true, isConnection(t)) }
    fun ok(t: String) { msg = Msg(t, false) }
}

/** Restarts the app screen from the very beginning (splash, session, data reload). */
fun restartApp(ctx: Context) {
    Toast.msg = null
    val act = ctx as? Activity
    if (act != null) { act.recreate(); return }
    val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    ctx.startActivity(i)
}

/** Centered message card (errors and successes): round icon, title, text and a button; pops in with a small spring. */
@Composable fun MessageDialog(m: Msg, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val accent = if (m.err) (if (Pal.dark) Color(0xFFFF5C70) else RED) else Color(0xFF22C55E)
    val shape = RoundedCornerShape(28.dp)
    val pop = remember { Animatable(0.82f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(180)) }
    if (!m.err) LaunchedEffect(m) { delay(3000); onClose() }
    val main = if (m.restart) m.text.substringBefore(" (") else m.text
    val detail = if (m.restart && m.text.contains(" (")) m.text.substringAfter(" (").removeSuffix(")") else ""
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 30.dp).fillMaxWidth()
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = fade.value }
                .background(Pal.dialog, shape).border(1.5.dp, accent.copy(alpha = 0.75f), shape).padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(64.dp).background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.6f))), CircleShape), contentAlignment = Alignment.Center) {
                Text(if (m.err) "!" else "\u2713", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (!m.err) tr("تمت العملية", "Done") else if (m.restart) tr("حدث خطأ ما", "Something went wrong") else tr("تنبيه", "Notice"),
                color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(main, color = Pal.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (m.restart) {
                Spacer(Modifier.height(6.dp))
                Text(tr("تحقق من الإنترنت ثم أعد تشغيل التطبيق وحاول مرة أخرى", "Check your internet, then restart the app and try again"), color = Pal.soft, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (detail.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(detail, color = Pal.soft.copy(alpha = 0.55f), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
            Spacer(Modifier.height(20.dp))
            if (m.restart) {
                GradButton(tr("إعادة تشغيل التطبيق", "Restart the app")) { restartApp(ctx) }
                Spacer(Modifier.height(4.dp))
                TextButton(onClose, Modifier.fillMaxWidth()) { Text(tr("إغلاق", "Close"), color = Pal.soft) }
            } else {
                GradButton(tr("حسناً", "OK"), onClick = onClose)
            }
        }
    }
}

@Composable fun Banner() {
    Toast.msg?.let { m -> MessageDialog(m) { if (Toast.msg === m) Toast.msg = null } }
}

fun errText(e: Throwable): String {
    // A screen left while its request was running (e.g. quick tab switch): not an error, show nothing.
    if (e is java.util.concurrent.CancellationException) return ""
    if (e is ApiError) {
        if (e.code == 401 && (e.message ?: "").startsWith("HTTP")) return tr("انتهت الجلسة، سجّل الدخول من جديد", "Session expired, please log in again")
        return when (e.message) {
            "bad credentials" -> tr("كلمة المرور غير صحيحة", "Wrong password")
            "email taken" -> tr("هذا الحساب مسجل مسبقاً", "This account is already registered")
            "not registered" -> tr("هذا الحساب غير مسجل", "This account is not registered")
            "invalid input" -> tr("بيانات غير صالحة (كلمة المرور 8 أحرف على الأقل)", "Invalid input (password needs 8+ characters)")
            "insufficient points" -> tr("نقاطك غير كافية", "Not enough points")
            "invalid player id" -> tr("ID غير صالح (5 إلى 15 رقماً)", "Invalid ID (5 to 15 digits)")
            "too many attempts, try later" -> tr("محاولات كثيرة، حاول لاحقاً", "Too many attempts, try later")
            "package unavailable" -> tr("هذه الباقة غير متاحة", "This package is unavailable")
            "bad code" -> tr("رمز الإدارة غير صحيح", "Wrong admin code")
            "login again" -> tr("انتهت المهلة، سجّل الدخول من جديد", "Timed out, please log in again")
            else -> e.message ?: "error"
        }
    }
    return tr("تعذر الاتصال بالخادم", "Could not reach the server") + " (" + e.javaClass.simpleName + ": " + (e.message ?: "").take(90) + ")"
}

@Composable fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().background(Pal.glass, shape).border(1.dp, Pal.glassBorder, shape).padding(16.dp), content = content)
}

@Composable fun GradButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(if (enabled) Brush.horizontalGradient(BRAND) else SolidColor(Color(0xFF9A93AD)), shape)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable fun SegTabs(labels: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Pal.glass, RoundedCornerShape(16.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, l ->
            val on = sel == i
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (on) Brush.horizontalGradient(BRAND) else SolidColor(Color.Transparent))
                    .clickable { onSel(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text(l, color = if (on) Color.White else Pal.text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

@Composable fun SectionTitle(t: String) {
    Text(t, color = PURPLE, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
}
