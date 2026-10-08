package de.maniac103.squeezeclient.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

sealed interface TextResource {
    @Composable
    fun resolve(): String

    class Raw(val text: String) : TextResource {
        @Composable
        override fun resolve() = text
    }

    class StringRes(val resId: Int, vararg args: Any) : TextResource {
        private val argsArray = args
        @Composable
        override fun resolve() = stringResource(resId, *argsArray)
    }

    class PluralRes(val resId: Int, val quantity: Int, vararg args: Any) : TextResource {
        private val argsArray = args
        @Composable
        override fun resolve() = pluralStringResource(resId, quantity, *argsArray)
    }
}