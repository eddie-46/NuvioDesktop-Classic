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

    /** Show page: season picker and a vertical episode list in a column beside the info. */
    const val DETAILS_EPISODE_COLUMN_ENABLED = true
    const val DETAILS_EPISODE_COLUMN_WIDTH_DP = 460
    /** Season picker in that column: a drop-down (true) or the row of season chips (false). */
    const val DETAILS_SEASON_DROPDOWN_ENABLED = true
    const val DETAILS_SEASON_DROPDOWN_WIDTH_DP = 240
    const val DETAILS_SEASON_DROPDOWN_MAX_HEIGHT_DP = 320

    /** Opening the season dropdown puts the selector on the season you are in, not the top. */
    const val DETAILS_SEASON_DROPDOWN_FOCUS_CURRENT = true

    /**
     * Desktop library in the Stremio style: always a grid of everything (no rows with View All
     * pages), one dropdown holding All / Movie / Series and then the custom playlists, and the
     * sort options as tabs. Off brings back Nuvio's own rows/grid layouts.
     */
    const val LIBRARY_STREMIO_STYLE = true

    /**
     * Stremio-style description panel on the right of Home, Search and Library, showing the
     * poster that has the selector. Each tab can be turned off in Settings > Look > Layout.
     */
    const val DESCRIPTION_PANEL_ENABLED = true
    const val DESCRIPTION_PANEL_WIDTH_DP = 420

    /** Wait this long on a poster before loading its runtime, director and cast. */
    const val DESCRIPTION_PANEL_DETAILS_DELAY_MS = 300L
    const val DESCRIPTION_PANEL_MAX_DESCRIPTION_LINES = 8
    const val DESCRIPTION_PANEL_MAX_CAST = 6

    /** Watched / Not watched filter tabs after the sort tabs in the library. */
    const val LIBRARY_WATCHED_FILTER_ENABLED = true

    /**
     * Library, category "See all" and collection-folder grids size their posters the same way
     * Search does, so they follow the interface size setting. Off brings back the 7-across cap.
     */
    const val GRIDS_MATCH_SEARCH_SIZE = true
    const val LIBRARY_DROPDOWN_WIDTH_DP = 240
    const val LIBRARY_DROPDOWN_MAX_HEIGHT_DP = 420

    /** Heading shown above the custom playlists in the library dropdown. */
    const val LIBRARY_PLAYLISTS_HEADER = "Playlists"
    /** Space kept clear above the column for the fullscreen button. */
    const val DETAILS_EPISODE_COLUMN_TOP_PADDING_DP = 96
    /** Darkening behind the column so the list reads over artwork (0 = none). */
    const val DETAILS_EPISODE_COLUMN_SCRIM_ALPHA = 0.28f
    /** How solid the episode cards in the list are (0 = see-through, 1 = solid). */
    const val EPISODE_LIST_CARD_ALPHA = 0.92f
    /** Up/Down stay inside the column they start in (info, episodes, sources). */
    const val DETAILS_COLUMNS_KEEP_VERTICAL_ARROWS = true

    /** Show sources in a panel on the details page instead of on a separate page. */
    const val INLINE_SOURCES_ENABLED = true

    /** Share of the window the sources panel takes, within the limits below. */
    const val INLINE_SOURCES_WIDTH_FRACTION = 0.38f
    const val INLINE_SOURCES_MIN_WIDTH_DP = 420
    const val INLINE_SOURCES_MAX_WIDTH_DP = 640

    /** When the sources panel opens from the keyboard, move the selector into it. */
    const val INLINE_SOURCES_TAKE_FOCUS = true

    /**
     * Left at the panel's left edge: false moves the selector back to the page and keeps the
     * panel open (Esc or the panel's back arrow closes it); true closes the panel.
     */
    const val INLINE_SOURCES_LEFT_CLOSES_PANEL = false

    /** Left presses at the panel's left edge needed to close it and return to the page. */
    // Only used when INLINE_SOURCES_LEFT_CLOSES_PANEL is true.
    const val INLINE_SOURCES_LEFT_PRESSES_TO_CLOSE = 1

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
