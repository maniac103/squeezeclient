/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
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

package de.maniac103.squeezeclient.ui.prefs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.Preferences
import de.maniac103.squeezeclient.R
import kotlin.enums.enumEntries
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class GroupEntryPosition(val index: Int, val size: Int) {
    val isFirst get() = index == 0
    val isLast get() = index == size - 1
}

class PreferenceGroupScope {
    val entries = mutableListOf<@Composable (GroupEntryPosition) -> Unit>()

    fun entry(content: @Composable (GroupEntryPosition) -> Unit) {
        entries.add(content)
    }
}

@Composable
fun PreferenceGroup(title: String, content: PreferenceGroupScope.() -> Unit) = Column(
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier = Modifier.padding(horizontal = 16.dp)
) {
    val scope = PreferenceGroupScope().apply(content)
    val entries = scope.entries

    Text(
        text = title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )
    entries.forEachIndexed { index, entry ->
        key(index) {
            entry(GroupEntryPosition(index, entries.size))
        }
    }
}

@Composable
fun PreferenceCard(
    position: GroupEntryPosition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val outerRadius = 16.dp
    val innerRadius = 4.dp

    val shape = RoundedCornerShape(
        topStart = if (position.isFirst) outerRadius else innerRadius,
        topEnd = if (position.isFirst) outerRadius else innerRadius,
        bottomStart = if (position.isLast) outerRadius else innerRadius,
        bottomEnd = if (position.isLast) outerRadius else innerRadius
    )

    Box(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(modifier)
    ) {
        content()
    }
}

@Composable
fun PreferenceText(title: String, summary: String?, modifier: Modifier = Modifier) = Column(
    modifier = modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMediumEmphasized
    )
    if (summary != null) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun SwitchPreference(
    position: GroupEntryPosition,
    title: String,
    summaryProvider: @Composable (Boolean) -> String?,
    onInterceptValueChange: suspend (Boolean) -> Boolean = { true },
    preference: Preferences.Preference<Boolean>
) {
    val checked by preference.asFlow().collectAsState(preference.value)
    val scope = rememberCoroutineScope()

    val handleUpdate = { newValue: Boolean ->
        scope.launch {
            if (onInterceptValueChange(newValue)) {
                preference.update(newValue)
            }
        }
    }

    PreferenceCard(
        position = position,
        modifier = Modifier.clickable {
            handleUpdate(!checked)
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            PreferenceText(
                title = title,
                summary = summaryProvider(checked),
                modifier = Modifier
                    .weight(1F)
                    .padding(end = 16.dp)
            )
            Switch(
                checked = checked,
                onCheckedChange = { newValue -> handleUpdate(newValue) },
                thumbContent = {
                    val resId = if (checked) R.drawable.ic_check_24dp else R.drawable.ic_close_24dp
                    Icon(
                        imageVector = ImageVector.vectorResource(resId),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
inline fun <reified T : Enum<T>> ListPreference(
    position: GroupEntryPosition,
    title: String,
    crossinline labelProvider: @Composable (T) -> String,
    crossinline summaryProvider: @Composable (T) -> String?,
    preference: Preferences.Preference<T>
) {
    val value by preference.asFlow().collectAsState(preference.value)
    var selectionShown by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PreferenceCard(
        position = position,
        modifier = Modifier.clickable {
            selectionShown = true
        }
    ) {
        PreferenceText(
            title = title,
            summary = summaryProvider(value),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    if (selectionShown) {
        ModalBottomSheet(
            onDismissRequest = { selectionShown = false }
        ) {
            Column {
                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                enumEntries<T>().forEach { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    preference.update(entry)
                                    selectionShown = false
                                }
                            }
                    ) {
                        RadioButton(
                            selected = value == entry,
                            onClick = {}
                        )
                        Text(
                            text = labelProvider(entry)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SliderPreference(
    position: GroupEntryPosition,
    title: String,
    summaryProvider: @Composable (Int) -> String?,
    minValue: Int,
    maxValue: Int,
    preference: Preferences.Preference<Int>
) = PreferenceCard(
    position = position
) {
    val value by preference.asFlow().collectAsState(preference.value)
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        PreferenceText(
            title = title,
            summary = summaryProvider(value)
        )

        val sliderState = rememberSliderState(
            value = value.toFloat(),
            steps = maxValue - minValue - 1,
            trackRange = minValue.toFloat()..maxValue.toFloat()
        )
        val interactionSource = remember { MutableInteractionSource() }

        Slider(
            state = sliderState,
            interactionSource = interactionSource,
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(12.dp)
                )
            },
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    thumbSize = DpSize(4.dp, 32.dp)
                )
            },
            onValueChange = { newValue ->
                sliderState.value = newValue
                scope.launch {
                    preference.update(newValue.toInt())
                }
            }
        )
    }
}

@Preview
@Composable
fun WidgetPreview() {
    PreferenceGroup(title = "Group title") {
        entry { position ->
            val enumPref = object : Preferences.Preference<Preferences.LocalPlayerVolumeMode> {
                override val value = Preferences.LocalPlayerVolumeMode.PlayerOnly
                override fun asFlow() = flowOf(Preferences.LocalPlayerVolumeMode.PlayerOnly)
                override suspend fun update(newValue: Preferences.LocalPlayerVolumeMode) {}
            }
            ListPreference(
                position = position,
                title = "List title",
                labelProvider = { value -> value.prefValue },
                summaryProvider = { value -> "Some description text for list widget: $value" },
                preference = enumPref
            )
        }

        entry { position ->
            val intPref = object : Preferences.Preference<Int> {
                override val value = 3
                override fun asFlow() = flowOf(3)
                override suspend fun update(newValue: Int) {}
            }
            SliderPreference(
                position = position,
                title = "Slider title",
                summaryProvider = { value ->
                    "Some description text for the slider widget: $value"
                },
                minValue = 1,
                maxValue = 5,
                preference = intPref
            )
        }

        entry { position ->
            val booleanPref = object : Preferences.Preference<Boolean> {
                override val value = true
                override fun asFlow() = flowOf(true)
                override suspend fun update(newValue: Boolean) {}
            }
            SwitchPreference(
                position = position,
                title = "Switch title",
                summaryProvider = { value ->
                    "Some description text for the switch widget: $value. " +
                        "This is a very long text that requires wrap around."
                },
                preference = booleanPref
            )
        }
    }
}
