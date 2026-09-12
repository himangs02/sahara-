package com.yourteam.sahara.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SaharaLogoGraphic(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    tint: Color = Color(0xFF2E7D63)
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Top Vertical Center Leaf
        val topLeaf = Path().apply {
            moveTo(w * 0.5f, h * 0.45f)
            cubicTo(w * 0.38f, h * 0.28f, w * 0.4f, h * 0.05f, w * 0.5f, 0f)
            cubicTo(w * 0.6f, h * 0.05f, w * 0.62f, h * 0.28f, w * 0.5f, h * 0.45f)
            close()
        }

        // Left Big Curved Leaf
        val leftLeaf = Path().apply {
            moveTo(w * 0.48f, h * 0.58f)
            cubicTo(w * 0.12f, h * 0.5f, w * 0.05f, h * 0.2f, w * 0.3f, h * 0.12f)
            cubicTo(w * 0.48f, h * 0.15f, w * 0.52f, h * 0.38f, w * 0.48f, h * 0.58f)
            close()
        }

        // Right Big Curved Leaf
        val rightLeaf = Path().apply {
            moveTo(w * 0.52f, h * 0.58f)
            cubicTo(w * 0.88f, h * 0.5f, w * 0.95f, h * 0.2f, w * 0.7f, h * 0.12f)
            cubicTo(w * 0.52f, h * 0.15f, w * 0.48f, h * 0.38f, w * 0.52f, h * 0.58f)
            close()
        }

        // Small Bottom Left Leaf Accent
        val bottomLeftLeaf = Path().apply {
            moveTo(w * 0.45f, h * 0.68f)
            cubicTo(w * 0.22f, h * 0.65f, w * 0.2f, h * 0.52f, w * 0.32f, h * 0.45f)
            cubicTo(w * 0.42f, h * 0.48f, w * 0.46f, h * 0.6f, w * 0.45f, h * 0.68f)
            close()
        }

        // Small Bottom Right Leaf Accent
        val bottomRightLeaf = Path().apply {
            moveTo(w * 0.55f, h * 0.68f)
            cubicTo(w * 0.78f, h * 0.65f, w * 0.8f, h * 0.52f, w * 0.68f, h * 0.45f)
            cubicTo(w * 0.58f, h * 0.48f, w * 0.54f, h * 0.6f, w * 0.55f, h * 0.68f)
            close()
        }

        drawPath(leftLeaf, color = tint)
        drawPath(rightLeaf, color = tint.copy(alpha = 0.95f))
        drawPath(topLeaf, color = Color(0xFF388E73))
        drawPath(bottomLeftLeaf, color = Color(0xFF43A082))
        drawPath(bottomRightLeaf, color = Color(0xFF388E73))
    }
}

@Composable
fun SaharaHeaderLogo(
    modifier: Modifier = Modifier,
    logoSize: Dp = 80.dp,
    titleFontSize: Int = 38,
    showTagline: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SaharaLogoGraphic(size = logoSize)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sahara",
            fontSize = titleFontSize.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D63)
        )
        if (showTagline) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A Cognitive Companion",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF556B63)
            )
            Text(
                text = "For Brighter Tomorrows",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF6B7280)
            )
        }
    }
}
