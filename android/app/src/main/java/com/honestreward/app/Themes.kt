package com.honestreward.app

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.Locale

const val APP_NAME = "Ghanima Games"

// Brand palette: maroon, electric purple, electric blue, cyan (no gold / navy)
val MAROON = Color(0xFF8E1B4A)
val PURPLE = Color(0xFF7C3AED)
val BLUE = Color(0xFF2F6BFF)
val CYAN = Color(0xFF22D3EE)
val BRAND = listOf(MAROON, PURPLE, BLUE)

object Pal {
    var dark by mutableStateOf(false)
    val text: Color get() = if (dark) Color(0xFFF7EEFF) else Color(0xFF241A3A)
    val soft: Color get() = if (dark) Color(0xFFCDB8E6) else Color(0xFF6B5E86)
    val glass: Color get() = if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.72f)
    val glassBorder: Color get() = if (dark) Color.White.copy(alpha = 0.16f) else PURPLE.copy(alpha = 0.18f)
    val bgTop: Color get() = if (dark) Color(0xFF150F4F) else Color.White
    val bgBottom: Color get() = if (dark) Color(0xFF2A1160) else Color(0xFFFAF7FF)
    val dialog: Color get() = if (dark) Color(0xFF231A5E) else Color.White
    fun load(ctx: Context) { dark = ctx.getSharedPreferences("hr", Context.MODE_PRIVATE).getBoolean("dark", true) }
    fun set(ctx: Context, v: Boolean) { dark = v; ctx.getSharedPreferences("hr", Context.MODE_PRIVATE).edit().putBoolean("dark", v).apply() }
}

object Lang {
    var ar by mutableStateOf(Locale.getDefault().language == "ar")
    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("hr", Context.MODE_PRIVATE)
        if (p.contains("lang")) ar = p.getString("lang", "ar") == "ar"
    }
    fun set(ctx: Context, isAr: Boolean) { ar = isAr; ctx.getSharedPreferences("hr", Context.MODE_PRIVATE).edit().putString("lang", if (isAr) "ar" else "en").apply() }
}

fun tr(ar: String, en: String) = if (Lang.ar) ar else en

@Composable fun GhanimaTheme(content: @Composable () -> Unit) {
    val d = Pal.dark
    val scheme = if (d) darkColorScheme(
        primary = PURPLE, onPrimary = Color.White, secondary = CYAN, background = Pal.bgBottom, surface = Pal.bgBottom,
        onSurface = Pal.text, onBackground = Pal.text, surfaceVariant = Pal.glass, onSurfaceVariant = Pal.soft, outline = Pal.soft,
        surfaceContainerHigh = Pal.dialog, surfaceContainerHighest = Pal.dialog, surfaceContainer = Pal.dialog
    ) else lightColorScheme(
        primary = PURPLE, onPrimary = Color.White, secondary = CYAN, background = Pal.bgTop, surface = Pal.bgTop,
        onSurface = Pal.text, onBackground = Pal.text, surfaceVariant = Pal.glass, onSurfaceVariant = Pal.soft, outline = Pal.soft,
        surfaceContainerHigh = Pal.dialog, surfaceContainerHighest = Pal.dialog, surfaceContainer = Pal.dialog
    )
    CompositionLocalProvider(LocalLayoutDirection provides (if (Lang.ar) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
            content = content
        )
    }
}
