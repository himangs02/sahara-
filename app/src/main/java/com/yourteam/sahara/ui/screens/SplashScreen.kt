package com.yourteam.sahara.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourteam.sahara.R

@Composable
fun SplashScreen(
    onGetStartedClick: () -> Unit
) {

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        // =========================================================
        // 1. SPLASH ARTWORK
        // =========================================================
        //
        // IMPORTANT:
        // The artwork contains the leaves, logo, Sahara branding,
        // mountains, lake, village, hut, flowers, etc.
        //
        // It DOES NOT contain the Get Started button.
        //

        Image(
            painter = painterResource(
                id = R.drawable.sahara_splash_background
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )


        // =========================================================
        // 2. REAL GET STARTED BUTTON
        // =========================================================

        Button(
            onClick = onGetStartedClick,

            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.78f)
                .height(80.dp)
                .navigationBarsPadding()
                .padding(bottom = 5.dp),

            shape = RoundedCornerShape(38.dp),

            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF19796F),
                contentColor = Color.White
            ),

            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 3.dp
            )
        ) {

            Text(
                text = stringResource(
                    id = R.string.get_started
                ),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}