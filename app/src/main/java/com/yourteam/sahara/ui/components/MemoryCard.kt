package com.yourteam.sahara.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.yourteam.sahara.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.model.MemoryCardModel

@Composable
fun MemoryCard(
    card: MemoryCardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The familiar name for this card's picture (generic, or from a cultural content pack --
     * see GameContentProvider), used as its accessibility label. */
    @androidx.annotation.StringRes labelRes: Int? = null
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (card.isFaceUp || card.isMatched)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (card.isFaceUp || card.isMatched) 2.dp else 4.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (card.isFaceUp || card.isMatched) {
                val label = labelRes?.let { stringResource(it) } ?: stringResource(R.string.card_face_up)
                Column(
                    modifier = Modifier.clearAndSetSemantics { contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = card.icon.imageVector,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = if (card.isMatched)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    if (labelRes != null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Back of the card pattern
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = stringResource(R.string.card_hidden),
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                )
            }
        }
    }
}