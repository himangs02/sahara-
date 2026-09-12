package com.yourteam.sahara.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

@Composable
fun SaharaLandscapeBackground(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Warm Sky Gradient (Top cream fading into soft golden warm glow)
        val skyGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFAFCFA),
                Color(0xFFF4F8F3),
                Color(0xFFFFF8E7),
                Color(0xFFFDE8AD)
            ),
            startY = 0f,
            endY = h * 0.76f
        )
        drawRect(brush = skyGradient, size = size)

        // 2. Rising Sun (Positioned at lower horizon)
        val sunCenter = Offset(w * 0.52f, h * 0.78f)
        val sunRadius = w * 0.22f
        drawCircle(
            color = Color(0xFFFACB6B),
            radius = sunRadius,
            center = sunCenter
        )
        drawCircle(
            color = Color(0xFFF5B642).copy(alpha = 0.4f),
            radius = sunRadius * 0.85f,
            center = sunCenter
        )

        // 3. Back Distant Layer Hills
        val distantHills = Path().apply {
            moveTo(0f, h * 0.78f)
            cubicTo(w * 0.25f, h * 0.74f, w * 0.42f, h * 0.72f, w * 0.58f, h * 0.76f)
            cubicTo(w * 0.75f, h * 0.79f, w * 0.88f, h * 0.74f, w, h * 0.76f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path = distantHills, color = Color(0xFF91BAA2))

        // 4. Midground Layer Hills
        val midHills = Path().apply {
            moveTo(0f, h * 0.82f)
            cubicTo(w * 0.3f, h * 0.77f, w * 0.5f, h * 0.81f, w * 0.72f, h * 0.77f)
            cubicTo(w * 0.88f, h * 0.74f, w * 0.96f, h * 0.79f, w, h * 0.81f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path = midHills, color = Color(0xFF74A587))

        // 5. Foreground Layer Hills (Rolling green bottom ground)
        val foregroundHills = Path().apply {
            moveTo(0f, h * 0.85f)
            cubicTo(w * 0.35f, h * 0.81f, w * 0.6f, h * 0.86f, w, h * 0.82f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path = foregroundHills, color = Color(0xFF5B8E70))

        // 6. Curving Sandy Path
        val pathTrail = Path().apply {
            moveTo(0f, h * 0.96f)
            cubicTo(w * 0.18f, h * 0.90f, w * 0.26f, h * 0.85f, w * 0.22f, h * 0.81f)
            cubicTo(w * 0.20f, h * 0.80f, w * 0.28f, h * 0.79f, w * 0.32f, h * 0.78f)
            cubicTo(w * 0.27f, h * 0.78f, w * 0.18f, h * 0.81f, w * 0.20f, h * 0.83f)
            cubicTo(w * 0.24f, h * 0.87f, w * 0.12f, h * 0.92f, 0f, h * 0.99f)
            close()
        }
        drawPath(path = pathTrail, color = Color(0xFFE3CCAE))

        // 7. Standalone Tree on Right Hill
        val treeBaseX = w * 0.86f
        val treeBaseY = h * 0.74f
        val scale = 1.6f

        // Trunk
        drawRect(
            color = Color(0xFF5D4037),
            topLeft = Offset(treeBaseX - 4f * scale, treeBaseY),
            size = Size(8f * scale, 28f * scale)
        )
        // Foliage
        drawCircle(
            color = Color(0xFF38664B),
            radius = 24f * scale,
            center = Offset(treeBaseX, treeBaseY - 16f * scale)
        )
        drawCircle(
            color = Color(0xFF4C7C5F),
            radius = 18f * scale,
            center = Offset(treeBaseX - 7f * scale, treeBaseY - 22f * scale)
        )
        drawCircle(
            color = Color(0xFF5A8E6F),
            radius = 14f * scale,
            center = Offset(treeBaseX + 5f * scale, treeBaseY - 26f * scale)
        )
    }
}
