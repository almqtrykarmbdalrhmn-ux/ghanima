package com.honestreward.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Translucent "watercolor" geometric shapes (circles, rounded squares, triangles) behind every screen. */
@Composable fun GeoBackground() {
    if (Pal.dark) {
        Image(painterResource(R.drawable.ghanima_bg), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val a = 0.12f
            drawCircle(PURPLE.copy(alpha = a), 130.dp.toPx(), Offset(w * 0.92f, h * 0.10f))
            drawCircle(CYAN.copy(alpha = a), 80.dp.toPx(), Offset(w * 0.05f, h * 0.28f))
            drawCircle(MAROON.copy(alpha = a), 110.dp.toPx(), Offset(w * 0.10f, h * 0.92f))
            drawCircle(BLUE.copy(alpha = a * 0.8f), 60.dp.toPx(), Offset(w * 0.88f, h * 0.70f))
            rotate(25f, Offset(w * 0.75f, h * 0.45f)) {
                drawRoundRect(BLUE.copy(alpha = a), Offset(w * 0.62f, h * 0.38f), Size(w * 0.28f, w * 0.28f), CornerRadius(26.dp.toPx()))
            }
            rotate(-18f, Offset(w * 0.2f, h * 0.55f)) {
                drawRoundRect(MAROON.copy(alpha = a), Offset(w * 0.08f, h * 0.48f), Size(w * 0.24f, w * 0.24f), CornerRadius(22.dp.toPx()))
            }
            val t1 = Path().apply { moveTo(w * 0.5f, h * 0.05f); lineTo(w * 0.7f, h * 0.17f); lineTo(w * 0.35f, h * 0.20f); close() }
            drawPath(t1, CYAN.copy(alpha = a))
            val t2 = Path().apply { moveTo(w * 0.55f, h * 0.86f); lineTo(w * 0.85f, h * 0.95f); lineTo(w * 0.45f, h * 0.98f); close() }
            drawPath(t2, PURPLE.copy(alpha = a))
        }
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r * 0.28f, c.y - r * 0.28f); lineTo(c.x + r, c.y); lineTo(c.x + r * 0.28f, c.y + r * 0.28f)
        lineTo(c.x, c.y + r); lineTo(c.x - r * 0.28f, c.y + r * 0.28f); lineTo(c.x - r, c.y); lineTo(c.x - r * 0.28f, c.y - r * 0.28f); close()
    }
    drawPath(p, color)
}

/** App logo: white gift box with an electric-blue ribbon and cyan sparkles on the maroon-purple-blue gradient. */
@Composable fun GiftLogo(s: Dp) {
    Image(painterResource(R.drawable.ghanima_logo), contentDescription = null, modifier = Modifier.size(s).clip(RoundedCornerShape(s * 0.2f)))
}

/** Original illustrations for the store cards (no official game logos, no currency symbols). */
@Composable fun ProductArt(kind: Int, s: Dp = 88.dp) {
    val k = ((kind % 8) + 8) % 8
    val pairs = listOf(listOf(PURPLE, BLUE), listOf(MAROON, PURPLE), listOf(BLUE, CYAN), listOf(PURPLE, CYAN))
    Canvas(Modifier.size(s)) {
        val w = size.width
        drawRoundRect(Brush.linearGradient(pairs[k % 4]), cornerRadius = CornerRadius(w * 0.22f))
        val dark = Color(0xFF241A3A)
        val skin = Color(0xFFF3C9A5)
        val white = Color.White
        fun rr(c: Color, x: Float, y: Float, ww: Float, hh: Float, r: Float) =
            drawRoundRect(c, Offset(w * x, w * y), Size(w * ww, w * hh), CornerRadius(w * r))
        when (k) {
            0 -> { // soldier with helmet
                rr(dark, 0.30f, 0.56f, 0.40f, 0.30f, 0.08f)
                drawCircle(skin, w * 0.13f, Offset(w * 0.5f, w * 0.43f))
                drawArc(white, 180f, 180f, true, Offset(w * 0.35f, w * 0.28f), Size(w * 0.30f, w * 0.30f))
                rr(CYAN, 0.33f, 0.41f, 0.34f, 0.05f, 0.02f)
                drawCircle(dark, w * 0.018f, Offset(w * 0.455f, w * 0.48f))
                drawCircle(dark, w * 0.018f, Offset(w * 0.545f, w * 0.48f))
            }
            1 -> { // rifle
                rr(white, 0.16f, 0.47f, 0.66f, 0.07f, 0.02f)
                rr(white, 0.36f, 0.51f, 0.30f, 0.12f, 0.03f)
                val stock = Path().apply { moveTo(w * 0.10f, w * 0.47f); lineTo(w * 0.30f, w * 0.47f); lineTo(w * 0.30f, w * 0.62f); lineTo(w * 0.12f, w * 0.66f); close() }
                drawPath(stock, white)
                rr(dark, 0.52f, 0.60f, 0.07f, 0.17f, 0.02f)
                rr(dark, 0.40f, 0.61f, 0.06f, 0.13f, 0.02f)
                rr(CYAN, 0.46f, 0.39f, 0.18f, 0.05f, 0.02f)
            }
            2 -> { // parachute
                drawArc(white, 180f, 180f, true, Offset(w * 0.14f, w * 0.16f), Size(w * 0.72f, w * 0.52f))
                drawArc(CYAN, 180f, 180f, true, Offset(w * 0.36f, w * 0.16f), Size(w * 0.28f, w * 0.52f))
                drawLine(white, Offset(w * 0.18f, w * 0.42f), Offset(w * 0.5f, w * 0.72f), w * 0.012f)
                drawLine(white, Offset(w * 0.5f, w * 0.42f), Offset(w * 0.5f, w * 0.72f), w * 0.012f)
                drawLine(white, Offset(w * 0.82f, w * 0.42f), Offset(w * 0.5f, w * 0.72f), w * 0.012f)
                rr(dark, 0.42f, 0.72f, 0.16f, 0.13f, 0.03f)
            }
            3 -> { // shield
                val sp = Path().apply {
                    moveTo(w * 0.5f, w * 0.14f); lineTo(w * 0.80f, w * 0.26f); lineTo(w * 0.80f, w * 0.52f)
                    cubicTo(w * 0.80f, w * 0.70f, w * 0.64f, w * 0.80f, w * 0.5f, w * 0.88f)
                    cubicTo(w * 0.36f, w * 0.80f, w * 0.20f, w * 0.70f, w * 0.20f, w * 0.52f)
                    lineTo(w * 0.20f, w * 0.26f); close()
                }
                drawPath(sp, white)
                scale(0.72f, Offset(w * 0.5f, w * 0.5f)) { drawPath(sp, BLUE) }
                sparkle(Offset(w * 0.5f, w * 0.5f), w * 0.12f, white)
            }
            4 -> { // masked fighter
                drawCircle(dark, w * 0.27f, Offset(w * 0.5f, w * 0.46f))
                rr(skin, 0.33f, 0.37f, 0.34f, 0.12f, 0.04f)
                rr(white, 0.30f, 0.50f, 0.40f, 0.20f, 0.06f)
                drawCircle(white, w * 0.03f, Offset(w * 0.43f, w * 0.43f))
                drawCircle(white, w * 0.03f, Offset(w * 0.57f, w * 0.43f))
                drawCircle(dark, w * 0.014f, Offset(w * 0.43f, w * 0.43f))
                drawCircle(dark, w * 0.014f, Offset(w * 0.57f, w * 0.43f))
                rr(dark, 0.22f, 0.72f, 0.56f, 0.15f, 0.07f)
            }
            5 -> { // pistol
                rr(white, 0.18f, 0.38f, 0.62f, 0.14f, 0.03f)
                rr(dark, 0.74f, 0.40f, 0.10f, 0.10f, 0.02f)
                val grip = Path().apply { moveTo(w * 0.26f, w * 0.50f); lineTo(w * 0.44f, w * 0.50f); lineTo(w * 0.40f, w * 0.78f); lineTo(w * 0.25f, w * 0.78f); close() }
                drawPath(grip, white)
                rr(dark, 0.46f, 0.54f, 0.14f, 0.025f, 0.01f)
                rr(CYAN, 0.30f, 0.35f, 0.06f, 0.03f, 0.01f)
            }
            6 -> { // backpack
                rr(white, 0.27f, 0.30f, 0.46f, 0.52f, 0.12f)
                drawRoundRect(white, Offset(w * 0.41f, w * 0.20f), Size(w * 0.18f, w * 0.14f), CornerRadius(w * 0.06f), style = Stroke(width = w * 0.03f))
                rr(CYAN, 0.33f, 0.55f, 0.34f, 0.20f, 0.06f)
                drawLine(dark.copy(alpha = 0.5f), Offset(w * 0.30f, w * 0.46f), Offset(w * 0.70f, w * 0.46f), w * 0.014f)
            }
            else -> { // first-aid box
                rr(white, 0.20f, 0.34f, 0.60f, 0.46f, 0.07f)
                drawRoundRect(white, Offset(w * 0.38f, w * 0.22f), Size(w * 0.24f, w * 0.16f), CornerRadius(w * 0.05f), style = Stroke(width = w * 0.03f))
                rr(MAROON, 0.465f, 0.42f, 0.07f, 0.30f, 0.01f)
                rr(MAROON, 0.35f, 0.535f, 0.30f, 0.07f, 0.01f)
            }
        }
    }
}
