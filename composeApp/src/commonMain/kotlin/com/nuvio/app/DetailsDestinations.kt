package com.nuvio.app

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.ClassicInlineSources
import com.nuvio.app.core.ui.ClassicLayoutConfig
import com.nuvio.app.core.ui.LocalClassicInlineSourcesClose
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.navigation.StreamRoute
import com.nuvio.app.features.details.MetaDetailsScreen
import com.nuvio.app.features.details.PersonDetailScreen
import com.nuvio.app.features.details.TmdbEntityBrowseScreen
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbEntityKind
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.navigation.DetailRoute
import com.nuvio.app.navigation.EntityBrowseRoute
import com.nuvio.app.navigation.NuvioNavigator
import com.nuvio.app.navigation.PersonDetailRoute
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.person_role_creator
import nuvio.composeapp.generated.resources.person_role_director
import nuvio.composeapp.generated.resources.person_role_writer
import org.jetbrains.compose.resources.stringResource

internal typealias ContentPlayAction = (
    type: String,
    videoId: String,
    parentMetaId: String,
    parentMetaType: String,
    title: String,
    logo: String?,
    poster: String?,
    background: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    episodeThumbnail: String?,
    pauseDescription: String?,
    resumePositionMs: Long?,
) -> Unit

@Composable
internal fun rememberOpenMeta(navController: NuvioNavigator): (MetaPreview) -> Unit {
    val scope = rememberCoroutineScope()
    return { preview ->
        scope.launch {
            val resolvedId = if (preview.id.startsWith("tmdb:")) {
                val tmdbId = preview.id.removePrefix("tmdb:").toIntOrNull()
                val resolved = tmdbId?.let {
                    TmdbService.tmdbToImdb(
                        tmdbId = it,
                        mediaType = preview.type,
                    )
                } ?: preview.id
                resolved
            } else {
                preview.id
            }
            navController.openPreview(preview.copy(id = resolvedId))
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun DetailsDestination(
    route: DetailRoute,
    navController: NuvioNavigator,
    onPlay: ContentPlayAction,
    onPlayManually: ContentPlayAction,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    inlineSources: (@Composable (StreamRoute) -> Unit)? = null,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    val onOpenMeta = rememberOpenMeta(navController)

    // Classic fork: this page hosts the sources panel while it is on screen.
    val inlineSourcesEnabled = inlineSources != null && ClassicInlineSources.enabled
    if (inlineSourcesEnabled) {
        DisposableEffect(route) {
            ClassicInlineSources.attach(route)
            onDispose {
                ClassicInlineSources.detach(route, stillOnBackStack = route in navController.routes)
            }
        }
    }
    val inlineRequest = if (inlineSourcesEnabled) {
        ClassicInlineSources.request?.takeIf { it.host == route }
    } else {
        null
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val inlinePanelWidth = (maxWidth * ClassicLayoutConfig.INLINE_SOURCES_WIDTH_FRACTION).coerceIn(
        ClassicLayoutConfig.INLINE_SOURCES_MIN_WIDTH_DP.dp,
        ClassicLayoutConfig.INLINE_SOURCES_MAX_WIDTH_DP.dp,
    )
    Row(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
    val directorRole = stringResource(Res.string.person_role_director)
    val writerRole = stringResource(Res.string.person_role_writer)
    val creatorRole = stringResource(Res.string.person_role_creator)
    MetaDetailsScreen(
        type = route.type,
        id = route.id,
        onBack = onBack,
        onPlay = onPlay,
        onPlayManually = onPlayManually,
        onOpenMeta = onOpenMeta,
        onCastClick = { person, avatarTransitionKey ->
            val tmdbId = person.tmdbId
            if (tmdbId != null && tmdbId > 0) {
                navController.navigate(
                    PersonDetailRoute(
                        personId = tmdbId,
                        personName = person.name,
                        personPhoto = person.photo,
                        castAvatarTransitionKey = avatarTransitionKey,
                        preferCrew = person.role?.let {
                            it.equals("Director", ignoreCase = true) ||
                                it.equals(directorRole, ignoreCase = true) ||
                                it.equals("Writer", ignoreCase = true) ||
                                it.equals(writerRole, ignoreCase = true) ||
                                it.equals("Creator", ignoreCase = true) ||
                                it.equals(creatorRole, ignoreCase = true)
                        } ?: false,
                    ),
                )
            }
        },
        onCompanyClick = { company, entityKind ->
            val tmdbId = company.tmdbId
            if (tmdbId != null && tmdbId > 0) {
                navController.navigate(
                    EntityBrowseRoute(
                        entityKind = entityKind,
                        entityId = tmdbId,
                        entityName = company.name,
                        sourceType = route.type,
                    ),
                )
            }
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        modifier = Modifier.fillMaxSize(),
    )
    }
    if (inlineRequest != null && inlineSources != null) {
        Box(
            modifier = Modifier
                .width(inlinePanelWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned { ClassicInlineSources.panelBounds = it.boundsInWindow() },
        ) {
            DisposableEffect(Unit) {
                onDispose { ClassicInlineSources.panelBounds = null }
            }
            CompositionLocalProvider(
                LocalClassicInlineSourcesClose provides { ClassicInlineSources.close(route) },
            ) {
                key(inlineRequest.launchId) {
                    inlineSources(StreamRoute(launchId = inlineRequest.launchId, title = inlineRequest.title))
                }
            }
        }
    }
    }
    }
    // Back closes the sources panel first, then the page.
    PlatformBackHandler(enabled = inlineRequest != null) {
        ClassicInlineSources.close(route)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun PersonDestination(
    route: PersonDetailRoute,
    navController: NuvioNavigator,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    PersonDetailScreen(
        personId = route.personId,
        personName = route.personName,
        initialProfilePhoto = route.personPhoto,
        avatarTransitionKey = route.castAvatarTransitionKey,
        preferCrew = route.preferCrew,
        onBack = onBack,
        onOpenMeta = rememberOpenMeta(navController),
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal fun EntityDestination(
    route: EntityBrowseRoute,
    navController: NuvioNavigator,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    TmdbEntityBrowseScreen(
        entityKind = TmdbEntityKind.fromRouteValue(route.entityKind),
        entityId = route.entityId,
        entityName = route.entityName,
        sourceType = route.sourceType,
        onBack = onBack,
        onOpenMeta = rememberOpenMeta(navController),
        modifier = Modifier.fillMaxSize(),
    )
}
