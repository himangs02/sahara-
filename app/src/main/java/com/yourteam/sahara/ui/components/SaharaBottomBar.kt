package com.yourteam.sahara.ui.components

import androidx.compose.ui.res.stringResource
import com.yourteam.sahara.R

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

sealed class SaharaBottomTab(val route: String, val label: Int, val icon: ImageVector) {
    data object Home : SaharaBottomTab("home", R.string.nav_home, Icons.Default.Home)
    data object Progress : SaharaBottomTab("performance", R.string.nav_progress, Icons.Default.BarChart)
    data object Voice : SaharaBottomTab("voice", R.string.nav_voice, Icons.Default.Mic)
    data object Caregiver : SaharaBottomTab("caregiver_home", R.string.nav_caregiver, Icons.Default.People)
    data object More : SaharaBottomTab("more", R.string.nav_more, Icons.Default.MoreHoriz)
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
                        contentDescription = stringResource(tab.label)
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.label),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}
