package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.ClassicLayoutConfig
import com.nuvio.app.core.ui.ClassicUiScale
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.formatRuntimeForDisplay
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** The tabs that can show the description panel; each can be turned off in Settings. */
internal enum class ClassicPanelTab(val label: String, val storeKey: String) {
    HOME("Home", "classic_desc_panel_home"),
    SEARCH("Search", "classic_desc_panel_search"),
    LIBRARY("Library", "classic_desc_panel_library"),
}

/**
 * Classic fork: Stremio-style description panel. The poster that last had the selector (or a
 * click) is shown on the right of Home, Search and Library with its details and an Open button.
 */
internal object ClassicDescriptionPanel {
    var selected by mutableStateOf<MetaPreview?>(null)
        private set

    /** What the Open button does: the same as pressing Enter on the poster. */
    var onOpen: (() -> Unit)? by mutableStateOf(null)
        private set

    private val enabled = mutableStateOf(ClassicPanelTab.entries.associateWith { true })
    private var store: ClassicUiScale.Store? = null

    fun load(newStore: ClassicUiScale.Store) {
        store = newStore
        enabled.value = ClassicPanelTab.entries.associateWith { tab ->
            newStore.getString(tab.storeKey)?.toBooleanStrictOrNull() ?: true
        }
    }

    fun isEnabled(tab: ClassicPanelTab): Boolean =
        ClassicLayoutConfig.DESCRIPTION_PANEL_ENABLED && enabled.value[tab] != false

    fun setEnabled(tab: ClassicPanelTab, value: Boolean) {
        enabled.value = enabled.value + (tab to value)
        store?.putString(tab.storeKey, value.toString())
    }

    fun select(item: MetaPreview, open: (() -> Unit)?) {
        if (selected?.id != item.id || selected?.type != item.type) selected = item
        onOpen = open
    }

    fun clear() {
        selected = null
        onOpen = null
    }
}

/** Put on a poster: when it gets the selector, the description panel shows it. */
internal fun Modifier.classicDescriptionPanelTracker(item: MetaPreview, onClick: (() -> Unit)?): Modifier =
    if (!ClassicLayoutConfig.DESCRIPTION_PANEL_ENABLED) {
        this
    } else {
        onFocusChanged { state ->
            if (state.hasFocus) ClassicDescriptionPanel.select(item, onClick)
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClassicDescriptionPanelView(modifier: Modifier = Modifier) {
    val item = ClassicDescriptionPanel.selected
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (item == null) {
            Text(
                text = "Move the selector onto a title to see its details here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
            return@Box
        }

        // Full details (runtime, director, cast) load a moment after the selector settles,
        // so running along a row does not fire a request for every poster passed.
        var details by remember(item.type, item.id) {
            mutableStateOf(MetaDetailsRepository.peek(item.type, item.id))
        }
        LaunchedEffect(item.type, item.id) {
            if (details != null) return@LaunchedEffect
            delay(ClassicLayoutConfig.DESCRIPTION_PANEL_DETAILS_DELAY_MS)
            details = try {
                MetaDetailsRepository.fetch(item.type, item.id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
        }
        val meta: MetaDetails? = details

        val backdrop = meta?.background ?: item.banner
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .alpha(0.45f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val logo = meta?.logo ?: item.logo
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 110.dp),
                )
            } else {
                Text(
                    text = meta?.name ?: item.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            val runtime = formatRuntimeForDisplay(meta?.runtime)
            val year = (meta?.releaseInfo ?: item.releaseInfo)?.let { Regex("""\d{4}""").find(it)?.value }
            val rating = (meta?.imdbRating ?: item.imdbRating)?.takeIf { it.isNotBlank() }
            val facts = listOfNotNull(runtime, year, rating?.let { "IMDb $it" })
            if (facts.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    facts.forEach { fact ->
                        Text(
                            text = fact,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            val description = meta?.description ?: item.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = ClassicLayoutConfig.DESCRIPTION_PANEL_MAX_DESCRIPTION_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            PanelChips(title = "GENRES", values = meta?.genres?.takeIf { it.isNotEmpty() } ?: item.genres)
            PanelChips(title = "DIRECTORS", values = meta?.director.orEmpty())
            PanelChips(
                title = "CAST",
                values = meta?.cast.orEmpty().map { it.name }.take(ClassicLayoutConfig.DESCRIPTION_PANEL_MAX_CAST),
            )

            val open = ClassicDescriptionPanel.onOpen
            if (open != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = open, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Open", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelChips(title: String, values: List<String>) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
