package io.github.zyrouge.symphony.ui.helpers

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.ui.unit.Dp
import coil.request.ImageRequest

enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE;

    val isPortrait: Boolean get() = this == PORTRAIT
    val isLandscape: Boolean get() = this == LANDSCAPE

    companion object {
        fun fromConfiguration(configuration: Configuration) = when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> LANDSCAPE
            else -> PORTRAIT
        }

        fun fromConstraints(constraints: BoxWithConstraintsScope) =
            fromDimension(constraints.maxHeight, constraints.maxWidth)

        fun fromDimension(height: Dp, width: Dp) = when {
            width.value > height.value -> LANDSCAPE
            else -> PORTRAIT
        }
    }
}

fun createHandyAsyncImageRequest(
    context: Context,
    data: Any
) = ImageRequest.Builder(context).run {
    data(data)
    crossfade(true)
    build()
}

