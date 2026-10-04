package de.maniac103.squeezeclient.ui.composables

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppBarScrollReporter =
    staticCompositionLocalOf<AppBarScrollReporter?> { null }

interface AppBarScrollReporter {
    fun setCanScrollUp(canScrollUp: Boolean)
}