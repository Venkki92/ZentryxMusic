package com.zentryx.music.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * Transitions smoothly between album art images (or fallback placeholder) using a 350ms
 * eased crossfade animation whenever the active track's artwork changes.
 */
@Composable
fun CrossfadeAlbumArt(
    artworkPath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    durationMillis: Int = 350,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current
    val resolvedPath = remember(artworkPath) {
        artworkPath?.takeIf { it.isNotBlank() && File(it).exists() }
    }

    Crossfade(
        targetState = resolvedPath,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        modifier = modifier,
        label = "albumArtCrossfade"
    ) { targetPath ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (targetPath != null) {
                val request = remember(targetPath) {
                    ImageRequest.Builder(context)
                        .data(File(targetPath))
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                fallback()
            }
        }
    }
}
