package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Classic fork: layout switches for the desktop restyle. Everything here is on/off or a size,
 * so the look can be adjusted without touching the screens themselves.
 */
internal object ClassicLayoutConfig {
    /** Show a search bar across the top of Home and Library (sidebar layout only). */
    const val TOP_SEARCH_BAR_ENABLED = true

    const val TOP_SEARCH_BAR_HEIGHT_DP = 64
    const val TOP_SEARCH_FIELD_HEIGHT_DP = 44
    const val TOP_SEARCH_FIELD_MAX_WIDTH_DP = 640

    /** Share of the bar's width the search field takes, up to the maximum above. */
    const val TOP_SEARCH_FIELD_WIDTH_FRACTION = 0.5f

    /** Show sources in a panel on the details page instead of on a separate page. */
    const val INLINE_SOURCES_ENABLED = true

    /** Share of the window the sources panel takes, within the limits below. */
    const val INLINE_SOURCES_WIDTH_FRACTION = 0.38f
    const val INLINE_SOURCES_MIN_WIDTH_DP = 420
    const val INLINE_SOURCES_MAX_WIDTH_DP = 640

    /** When the sources panel opens from the keyboard, move the selector into it. */
    const val INLINE_SOURCES_TAKE_FOCUS = true

    /** Left presses at the panel's left edge needed to close it and return to the page. */
    const val INLINE_SOURCES_LEFT_PRESSES_TO_CLOSE = 2

    /** Selecting a Continue Watching item opens its details page instead of the sources page. */
    const val CONTINUE_WATCHING_OPENS_DETAILS = true

    /** ...and that page opens with the sources panel already showing for the item. */
    const val CONTINUE_WATCHING_OPENS_SOURCES_PANEL = true
}

/** Height the bar occupies, for screens that need to start their content below it. */
@Composable
internal fun classicTopSearchBarHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        ClassicLayoutConfig.TOP_SEARCH_BAR_HEIGHT_DP.dp

/**
 * A search bar that sits across the top of the main screens. Activating it (click or Enter)
 * opens the Search tab with its text box focused; the typing itself happens there.
 *
 * [solidBackground] is off when the bar floats over artwork (the home banner), so the artwork
 * shows through instead of being cut by a dark strip.
 */
@Composable
internal fun ClassicTopSearchBar(
    placeholder: String,
    solidBackground: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fieldShape = RoundedCornerShape((ClassicLayoutConfig.TOP_SEARCH_FIELD_HEIGHT_DP / 2).dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(classicTopSearchBarHeight())
            .background(if (solidBackground) MaterialTheme.colorScheme.background else Color.Transparent)
            .padding(top = statusBarPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(ClassicLayoutConfig.TOP_SEARCH_FIELD_WIDTH_FRACTION)
                .widthIn(max = ClassicLayoutConfig.TOP_SEARCH_FIELD_MAX_WIDTH_DP.dp)
                .height(ClassicLayoutConfig.TOP_SEARCH_FIELD_HEIGHT_DP.dp)
                .clip(fieldShape)
                .background(
                    if (solidBackground) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                    },
                )
                .clickable(role = Role.Button, onClick = onActivate)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
