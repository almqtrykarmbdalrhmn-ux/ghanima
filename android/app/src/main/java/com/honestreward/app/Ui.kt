package com.honestreward.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

val RED = Color(0xFFD62839)
fun JSONArray.list(): List<JSONObject> = List(length()) { getJSONObject(it) }
fun JSONObject.num(k: String) = optString(k, "0").toLongOrNull() ?: 0L

class Msg(val text: String, val err: Boolean)
object Toast {
    var msg by mutableStateOf<Msg?>(null)
    fun err(t: String) { msg = Msg(t, true) }
    fun ok(t: String) { msg = Msg(t, false) }
}

/** Fully closes the app and opens it again from the start. */
fun restartApp(ctx: Context) {
    val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
    if (i != null) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(i)
    }
    (ctx as? Activity)?.finishAffinity()
    Runtime.getRuntime().exit(0)
}

/** Classic error box in the middle of the screen: red title bar, red message, and a "Restart app" button in the app style. */
@Composable fun ErrorDialog(m: Msg, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(4.dp)
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().background(Pal.dialog, shape).border(2.dp, RED, shape)) {
            Box(Modifier.fillMaxWidth().background(RED).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(tr("حدث خطأ ما", "Something went wrong"), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(m.text, color = RED, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(tr("أعد تشغيل التطبيق وحاول مرة أخرى", "Restart the app and try again"), color = Pal.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                GradButton(tr("إعادة تشغيل التطبيق", "Restart the app")) { restartApp(ctx) }
                Spacer(Modifier.height(6.dp))
                TextButton(onClose, Modifier.fillMaxWidth()) { Text(tr("إغلاق", "Close"), color = Pal.soft) }
            }
        }
    }
}

@Composable fun Banner() {
    Toast.msg?.let { m ->
        if (m.err) {
            ErrorDialog(m) { if (Toast.msg === m) Toast.msg = null }
        } else {
            Text(m.text, color = Color.White, modifier = Modifier.fillMaxWidth().padding(8.dp)
                .background(Color(0xFF16A34A), RoundedCornerShape(14.dp)).padding(12.dp))
            LaunchedEffect(m) { delay(4000); if (Toast.msg === m) Toast.msg = null }
        }
    }
}

fun errText(e: Throwable): String {
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
