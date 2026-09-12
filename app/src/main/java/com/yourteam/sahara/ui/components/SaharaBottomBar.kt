package com.yourteam.sahara.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

sealed class SaharaBottomTab(val route: String, val label: String, val icon: ImageVector) {
    data object Home : SaharaBottomTab("home", "Home", Icons.Default.Home)
    data object Progress : SaharaBottomTab("performance", "Progress", Icons.Default.BarChart)
    data object Voice : SaharaBottomTab("voice", "Voice", Icons.Default.Mic)
    data object Caregiver : SaharaBottomTab("caregiver_login", "Caregiver", Icons.Default.People)
    data object More : SaharaBottomTab("more", "More", Icons.Default.MoreHoriz)
}

@Composable
fun SaharaBottomBar(
    currentRoute: String,
    onTabSelected: (SaharaBottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        SaharaBottomTab.Home,
        SaharaBottomTab.Progress,
        SaharaBottomTab.Voice,
        SaharaBottomTab.Caregiver,
        SaharaBottomTab.More
    )

    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        tabs.forEach { tab ->
            val isSelected = (currentRoute == tab.route)
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}
