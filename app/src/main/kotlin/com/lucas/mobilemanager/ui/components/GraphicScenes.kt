package com.lucas.mobilemanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.lucas.mobilemanager.ui.theme.GraphicColors as G

enum class SceneKind { FOCUS, PROFILES, SCROLL, BALANCE, SHIELD }

// A finite entrance: no perpetual animation or timer after the illustration settles.
@Composable
fun entranceProgress(): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    return progress.value
}

@Composable
fun GraphicScene(kind: SceneKind, modifier: Modifier = Modifier) {
    val progress = entranceProgress()
    Canvas(modifier) {
        val unit = minOf(size.width, size.height) / 200f
        translate((size.width - 200 * unit) / 2, (size.height - 200 * unit) / 2) {
            scale(unit, unit, pivot = Offset.Zero) {
                scale(.9f + .1f * progress, pivot = Offset(100f, 100f)) {
                    drawCircle(Brush.radialGradient(listOf(G.lavender.copy(alpha = .7f), G.lavender.copy(alpha = 0f)), Offset(100f, 100f), 98f), 98f, Offset(100f, 100f))
                    drawOval(G.deep.copy(alpha = .12f), Offset(38f, 164f), Size(134f, 18f))
                    drawCircle(Brush.linearGradient(listOf(G.apricot, Color(0xFFFFA95B))), 13f, Offset(30f, 54f + 5f * (1f - progress)))
                    drawCircle(G.mint, 6f, Offset(172f, 145f))
                    sparkle(172f, 40f, G.iris)
                    sparkle(25f, 137f, G.mint)
                    when (kind) {
                        SceneKind.FOCUS -> phone(progress)
                        SceneKind.PROFILES -> cards(progress)
                        SceneKind.SCROLL -> scrollCard(progress)
                        SceneKind.BALANCE -> balance(progress)
                        SceneKind.SHIELD -> shield(progress)
                    }
                }
            }
        }
    }
}

private fun DrawScope.tile(x: Float, y: Float, width: Float, height: Float, colors: List<Color>, radius: Float = 18f) {
    drawRoundRect(G.deep.copy(alpha = .14f), Offset(x + 5f, y + 7f), Size(width, height), CornerRadius(radius))
    drawRoundRect(Brush.linearGradient(colors, Offset(x, y), Offset(x + width, y + height)), Offset(x, y), Size(width, height), CornerRadius(radius))
    drawRoundRect(Color.White.copy(alpha = .38f), Offset(x + 1, y + 1), Size(width - 2, height - 2), CornerRadius(radius), style = Stroke(1f))
}

private fun DrawScope.sparkle(x: Float, y: Float, color: Color) {
    drawLine(color, Offset(x - 5, y), Offset(x + 5, y), 2.5f, StrokeCap.Round)
    drawLine(color, Offset(x, y - 5), Offset(x, y + 5), 2.5f, StrokeCap.Round)
}

private fun DrawScope.check(center: Offset, radius: Float) {
    drawCircle(G.deep.copy(alpha = .15f), radius, center + Offset(3f, 5f))
    drawCircle(Brush.linearGradient(listOf(G.mintLight, G.mint), center - Offset(radius, radius), center + Offset(radius, radius)), radius, center)
    val path = Path().apply {
        moveTo(center.x - radius * .4f, center.y)
        lineTo(center.x - radius * .08f, center.y + radius * .3f)
        lineTo(center.x + radius * .45f, center.y - radius * .3f)
    }
    drawPath(path, G.ink, style = Stroke(radius * .13f, cap = StrokeCap.Round))
}

private fun DrawScope.phone(progress: Float) {
    rotate(-10f + 4f * progress, Offset(100f, 100f)) {
        tile(56f, 25f, 87f, 139f, listOf(Color.White, Color(0xFFF1EAFE)))
        drawRoundRect(Brush.linearGradient(listOf(G.lavender, G.iris)), Offset(64f, 48f), Size(71f, 93f), CornerRadius(11f))
        drawLine(G.lavender, Offset(88f, 36f), Offset(110f, 36f), 4f, StrokeCap.Round)
        drawLine(G.iris, Offset(89f, 153f), Offset(109f, 153f), 4f, StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = .2f), 29f, Offset(100f, 94f))
        drawLine(Color.White, Offset(91f, 83f), Offset(91f, 105f), 6f, StrokeCap.Round)
        drawLine(Color.White, Offset(108f, 83f), Offset(108f, 105f), 6f, StrokeCap.Round)
    }
    check(Offset(145f, 143f - 7 * progress), 30f)
    rotate(12f, Offset(38f, 104f)) {
        tile(12f, 84f, 43f, 42f, listOf(G.blue, G.iris), 12f)
        drawCircle(Color.White, 12f, Offset(34f, 105f), style = Stroke(2.5f))
        drawLine(Color.White, Offset(34f, 105f), Offset(34f, 97f), 2.5f, StrokeCap.Round)
        drawLine(Color.White, Offset(34f, 105f), Offset(40f, 108f), 2.5f, StrokeCap.Round)
    }
}

private fun DrawScope.cards(progress: Float) {
    rotate(-13f, Offset(95f, 100f)) { tile(40f, 33f, 100f, 124f, listOf(G.mintLight, G.mint)) }
    rotate(10f - 4f * progress, Offset(105f, 100f)) {
        tile(56f, 40f, 100f, 124f, listOf(Color.White, Color(0xFFF1EAFE)))
        tile(70f, 57f, 39f, 37f, listOf(G.lavender, G.iris), 10f)
        drawCircle(Color.White, 7f, Offset(90f, 75f))
        for (i in 0..2) drawLine(if (i == 0) G.iris else G.lavender, Offset(72f, 111f + i * 13), Offset(138f - i * 9, 111f + i * 13), 5f, StrokeCap.Round)
    }
    check(Offset(148f, 143f), 25f)
}

private fun DrawScope.scrollCard(progress: Float) {
    rotate(-8f + progress * 3f, Offset(100f, 100f)) {
        tile(48f, 25f, 105f, 141f, listOf(Color.White, Color(0xFFF0E9FF)))
        tile(60f, 39f, 81f, 68f, listOf(G.iris, G.deep), 12f)
        val play = Path().apply { moveTo(92f, 59f); lineTo(92f, 87f); lineTo(114f, 73f); close() }
        drawPath(play, Color.White)
        drawLine(G.lavender, Offset(64f, 124f), Offset(132f, 124f), 5f, StrokeCap.Round)
        drawLine(G.lavender, Offset(64f, 139f), Offset(112f, 139f), 5f, StrokeCap.Round)
    }
    drawCircle(G.deep.copy(alpha = .15f), 30f, Offset(147f, 148f))
    drawCircle(Brush.linearGradient(listOf(G.mintLight, G.mint)), 30f, Offset(144f, 142f))
    drawLine(G.ink, Offset(136f, 131f), Offset(136f, 153f), 6f, StrokeCap.Round)
    drawLine(G.ink, Offset(152f, 131f), Offset(152f, 153f), 6f, StrokeCap.Round)
}

private fun DrawScope.balance(progress: Float) {
    drawArc(G.iris.copy(alpha = .2f), 15f, 280f, false, Offset(39f, 36f), Size(124f, 124f), style = Stroke(16f, cap = StrokeCap.Round))
    drawArc(Brush.linearGradient(listOf(G.mint, G.iris)), -90f, 240f * progress, false, Offset(39f, 36f), Size(124f, 124f), style = Stroke(16f, cap = StrokeCap.Round))
    tile(68f, 64f, 66f, 68f, listOf(Color.White, Color(0xFFF1EAFE)), 22f)
    drawLine(G.iris, Offset(92f, 87f), Offset(92f, 109f), 6f, StrokeCap.Round)
    drawLine(G.iris, Offset(110f, 87f), Offset(110f, 109f), 6f, StrokeCap.Round)
    check(Offset(150f, 146f), 22f)
}

private fun DrawScope.shield(progress: Float) {
    val path = Path().apply {
        moveTo(100f, 29f); lineTo(153f, 49f); lineTo(149f, 110f)
        quadraticTo(143f, 145f, 100f, 170f); quadraticTo(57f, 145f, 51f, 110f)
        lineTo(47f, 49f); close()
    }
    drawPath(path, Brush.linearGradient(listOf(G.lavender, G.iris, G.deep), Offset(50f, 30f), Offset(150f, 170f)))
    check(Offset(100f, 96f + 5f * (1f - progress)), 30f)
}
