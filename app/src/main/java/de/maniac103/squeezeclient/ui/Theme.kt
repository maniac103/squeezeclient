package de.maniac103.squeezeclient.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

object Theme {
    object TextStyles {
        val listItemPrimary @Composable get() = MaterialTheme.typography.titleMedium
        val listItemSecondary @Composable get() = MaterialTheme.typography.bodySmall
    }
}