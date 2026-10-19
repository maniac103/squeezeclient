/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 */

package de.maniac103.squeezeclient.ui.itemlist

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.SlimBrowseItemList

sealed class SlimBrowseItemTrailingWidget {
    class Choice(val label: String) : SlimBrowseItemTrailingWidget()
    class Checkbox(val checked: Boolean) : SlimBrowseItemTrailingWidget()
    class Radio(val checked: Boolean) : SlimBrowseItemTrailingWidget()
    class ContextMenu(val clickListener: () -> Unit) : SlimBrowseItemTrailingWidget()
}

fun SlimBrowseItemList.SlimBrowseItem.extractTrailingWidget(
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) = actions?.let { actions ->
    when {
        actions.checkbox != null ->
            SlimBrowseItemTrailingWidget.Checkbox(actions.checkbox.state)

        actions.radio != null ->
            SlimBrowseItemTrailingWidget.Radio(actions.radio.state)

        actions.choices != null ->
            SlimBrowseItemTrailingWidget.Choice(
                actions.choices.items[actions.choices.selectedIndex].title
            )

        actions.hasContextMenu ->
            SlimBrowseItemTrailingWidget.ContextMenu {
                contextMenuClickListener(this)
            }

        else -> null
    }
}

@Composable
internal fun SlimBrowseItemTrailingWidgetUi(
    busy: Boolean,
    widget: SlimBrowseItemTrailingWidget?,
    modifier: Modifier = Modifier
) {
    when {
        busy -> {
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                modifier = modifier.size(24.dp)
            )
        }

        widget is SlimBrowseItemTrailingWidget.Choice -> {
            Text(
                text = widget.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = modifier
            )
        }

        widget is SlimBrowseItemTrailingWidget.Checkbox -> {
            Checkbox(
                checked = widget.checked,
                onCheckedChange = null,
                modifier = modifier
            )
        }

        widget is SlimBrowseItemTrailingWidget.Radio -> {
            RadioButton(
                selected = widget.checked,
                onClick = null,
                modifier = modifier
            )
        }

        widget is SlimBrowseItemTrailingWidget.ContextMenu -> {
            IconButton(
                onClick = widget.clickListener,
                modifier = modifier
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_menu_overflow),
                    contentDescription = null // FIXME
                )
            }
        }
    }
}
