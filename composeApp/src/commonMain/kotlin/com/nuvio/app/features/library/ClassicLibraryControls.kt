package com.nuvio.app.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.i18n.localizedMediaTypeLabel
import com.nuvio.app.core.ui.ClassicLayoutConfig
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.library_sort_provider_order
import org.jetbrains.compose.resources.stringResource

/**
 * Stremio-style library controls: one dropdown that holds the library filters (All, Movie,
 * Series) and, below them, the custom playlists; then the sort options as a row of tabs.
 *
 * Selecting a type shows the main library filtered to that type. Selecting a playlist shows
 * that playlist on its own, so playlists never mix into the main grid.
 */
@Composable
internal fun ClassicLibraryControls(
    sections: List<LibrarySection>,
    sourceMode: LibrarySourceMode,
    selectedSectionKey: String?,
    selectedType: String?,
    sortOption: LibrarySortOption,
    onLibraryTypeSelected: (mainSectionKey: String?, type: String?) -> Unit,
    onPlaylistSelected: (sectionKey: String) -> Unit,
    onSortSelected: (LibrarySortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mainSection = classicMainLibrarySection(sections, sourceMode)
    val playlists = classicLibraryPlaylists(sections, sourceMode)
    val mainItems = mainSection?.items ?: sections.flatMap { it.items }
    val types = remember(mainItems) {
        mainItems
            .map { (it.mediaCategory ?: it.type).trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val onPlaylist = playlists.firstOrNull { it.type == selectedSectionKey }

    // Keys: "type:" (All), "type:<type>", "list:<section key>".
    val selectedKey = if (onPlaylist != null) {
        "list:${onPlaylist.type}"
    } else {
        "type:${selectedType?.trim()?.lowercase()?.takeIf { it in types }.orEmpty()}"
    }
    val options = buildList {
        add(ClassicLibraryOption("type:", "All", header = null))
        types.forEach { type -> add(ClassicLibraryOption("type:$type", localizedMediaTypeLabel(type), header = null)) }
        playlists.forEachIndexed { index, section ->
            add(
                ClassicLibraryOption(
                    key = "list:${section.type}",
                    label = localizedLibrarySectionTitle(section.type, section.displayTitle),
                    header = if (index == 0) ClassicLayoutConfig.LIBRARY_PLAYLISTS_HEADER else null,
                ),
            )
        }
    }
    val selectedLabel = options.firstOrNull { it.key == selectedKey }?.label ?: "All"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ClassicLibraryDropdown(
            label = selectedLabel,
            selectedKey = selectedKey,
            options = options,
            onSelected = { option ->
                when {
                    option.key.startsWith("list:") -> onPlaylistSelected(option.key.removePrefix("list:"))
                    else -> onLibraryTypeSelected(mainSection?.type, option.key.removePrefix("type:").ifBlank { null })
                }
            },
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            availableLibrarySortOptions(sourceMode).forEach { option ->
                val selected = option == sortOption
                Text(
                    text = classicSortLabel(option),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSortSelected(option) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}

private data class ClassicLibraryOption(
    val key: String,
    val label: String,
    /** Small heading shown above this option (used to start the playlists group). */
    val header: String?,
)

@Composable
private fun ClassicLibraryDropdown(
    label: String,
    selectedKey: String,
    options: List<ClassicLibraryOption>,
    onSelected: (ClassicLibraryOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val width = ClassicLayoutConfig.LIBRARY_DROPDOWN_WIDTH_DP.dp
    val headerFocus = remember { FocusRequester() }
    val currentFocus = remember { FocusRequester() }
    var returnFocusToHeader by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            // Put the selector on the option in use, like the season dropdown.
            delay(16)
            runCatching { currentFocus.requestFocus() }
        } else if (returnFocusToHeader) {
            returnFocusToHeader = false
            delay(16)
            runCatching { headerFocus.requestFocus() }
        }
    }
    val closeList = {
        returnFocusToHeader = true
        expanded = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .width(width)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .focusRequester(headerFocus)
                .clickable(enabled = options.size > 1) { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .width(width)
                    .heightIn(max = ClassicLayoutConfig.LIBRARY_DROPDOWN_MAX_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .onPreviewKeyEvent { event ->
                        // Esc closes the list without changing anything.
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            closeList()
                            true
                        } else {
                            false
                        }
                    }
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    if (option.header != null) {
                        Text(
                            text = option.header,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    val selected = option.key == selectedKey
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color.Transparent,
                            )
                            .then(if (selected) Modifier.focusRequester(currentFocus) else Modifier)
                            .clickable {
                                closeList()
                                onSelected(option)
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * The "main" library: everything for the local library, otherwise the watchlist-like list
 * (Trakt watchlist, for one), falling back to the first list.
 */
internal fun classicMainLibrarySection(
    sections: List<LibrarySection>,
    sourceMode: LibrarySourceMode,
): LibrarySection? {
    if (!sourceMode.isRemoteTrackingSource) return null
    return sections.firstOrNull { it.type.contains("watchlist", ignoreCase = true) }
        ?: sections.firstOrNull()
}

/** Every list other than the main library; these go in the dropdown's playlists group. */
internal fun classicLibraryPlaylists(
    sections: List<LibrarySection>,
    sourceMode: LibrarySourceMode,
): List<LibrarySection> {
    val main = classicMainLibrarySection(sections, sourceMode) ?: return emptyList()
    return sections.filter { it.type != main.type }
}

@Composable
private fun classicSortLabel(option: LibrarySortOption): String =
    when (option) {
        LibrarySortOption.DEFAULT -> stringResource(Res.string.library_sort_provider_order)
        LibrarySortOption.ADDED_DESC -> "Recent"
        LibrarySortOption.ADDED_ASC -> "Oldest"
        LibrarySortOption.RELEASED_DESC -> "Newest release"
        LibrarySortOption.RELEASED_ASC -> "Oldest release"
        LibrarySortOption.TITLE_ASC -> "A-Z"
        LibrarySortOption.TITLE_DESC -> "Z-A"
    }
