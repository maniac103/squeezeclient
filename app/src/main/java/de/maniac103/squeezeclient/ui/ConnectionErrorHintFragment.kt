/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024-2026 Danny Baumann
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

package de.maniac103.squeezeclient.ui

import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.getParcelableList
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.ui.common.ComposeFragment
import kotlinx.parcelize.Parcelize

class ConnectionErrorHintFragment : ComposeFragment() {
    fun interface Listener {
        fun onActionInvoked(tag: String?)
    }

    @Composable
    override fun createContent() {
        val args = requireArguments()

        Scaffold { innerPadding ->
            ErrorHintContent(
                iconResId = args.getInt("icon"),
                textResId = args.getInt("text"),
                subtext = args.getString("subtext"),
                buttons = args.getParcelableList("buttons", ActionButtonSpec::class),
                buttonClickListener = { tag -> requireParentAs<Listener>().onActionInvoked(tag) },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    companion object {
        fun create(
            @DrawableRes iconResId: Int,
            @StringRes textResId: Int,
            subtext: String? = null,
            buttons: List<ActionButtonSpec> = emptyList(),
        ) = ConnectionErrorHintFragment().apply {
            arguments = Bundle().apply {
                putInt("icon", iconResId)
                putInt("text", textResId)
                putString("subtext", subtext)
                putParcelableArrayList("buttons", ArrayList(buttons))
            }
        }
    }
}

@Parcelize
data class ActionButtonSpec(val labelResId: Int, val tag: String) : Parcelable

@Composable
internal fun ErrorHintContent(
    iconResId: Int,
    textResId: Int,
    subtext: String?,
    buttons: List<ActionButtonSpec>,
    buttonClickListener: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        when {
            maxWidth > maxHeight && maxHeight >= 480.dp ->
                LandscapeTabletErrorHintContent(
                    iconResId,
                    textResId,
                    subtext,
                    buttons,
                    buttonClickListener
                )

            maxWidth > maxHeight ->
                LandscapePhoneErrorHintContent(
                    iconResId,
                    textResId,
                    subtext,
                    buttons,
                    buttonClickListener
                )

            else ->
                PortraitErrorHintContent(
                    iconResId,
                    textResId,
                    subtext,
                    buttons,
                    buttonClickListener
                )
        }
    }
}

@Composable
internal fun PortraitErrorHintContent(
    iconResId: Int,
    textResId: Int,
    subtext: String?,
    buttons: List<ActionButtonSpec>,
    buttonClickListener: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        MainIcon(iconResId)

        HintMainText(
            textResId,
            modifier = Modifier.padding(16.dp)
        )
        HintSubText(
            subtext,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        )

        buttons.forEach { button ->
            ActionButton(button, buttonClickListener)
        }
    }
}

@Composable
internal fun LandscapePhoneErrorHintContent(
    iconResId: Int,
    textResId: Int,
    subtext: String?,
    buttons: List<ActionButtonSpec>,
    buttonClickListener: (String) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1F)
        ) {
            MainIcon(
                iconResId = iconResId,
                modifier = Modifier
                    .weight(1F)
            )
            Box(
                modifier = Modifier.weight(1F)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HintMainText(
                        textResId,
                        modifier = Modifier.padding(16.dp)
                    )
                    HintSubText(
                        subtext,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            buttons.forEach { button ->
                ActionButton(button, buttonClickListener)
            }
        }
    }
}

@Composable
internal fun LandscapeTabletErrorHintContent(
    iconResId: Int,
    textResId: Int,
    subtext: String?,
    buttons: List<ActionButtonSpec>,
    buttonClickListener: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        MainIcon(
            iconResId,
            modifier = Modifier.weight(2F)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1F)
        ) {
            HintMainText(
                textResId,
                modifier = Modifier.padding(top = 32.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
            )
            HintSubText(
                subtext,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1F)
        ) {
            buttons.forEach { button ->
                ActionButton(button, buttonClickListener)
            }
        }
    }
}

@Composable
internal fun MainIcon(iconResId: Int, modifier: Modifier = Modifier) = Icon(
    ImageVector.vectorResource(iconResId),
    contentDescription = null,
    tint = MaterialTheme.colorScheme.secondary,
    modifier = modifier.size(192.dp)
)

@Composable
internal fun HintMainText(textResId: Int, modifier: Modifier = Modifier) = Text(
    text = stringResource(textResId),
    style = MaterialTheme.typography.titleLarge,
    modifier = modifier
)

@Composable
internal fun HintSubText(subtext: String?, modifier: Modifier = Modifier) = subtext?.let {
    Text(
        text = it,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
    )
}

@Composable
internal fun ActionButton(
    button: ActionButtonSpec,
    buttonClickListener: (String) -> Unit
) = Button(
    onClick = { buttonClickListener(button.tag) },
    shapes = ButtonDefaults.shapes()
) {
    Text(stringResource(button.labelResId))
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
fun ErrorHintContentPreview() {
    ErrorHintContent(
       R.drawable.ic_home_24dp,
        R.string.connection_error_text_connection_failure,
        "Something happened",
        listOf(
            ActionButtonSpec(R.string.connection_error_action_retry, "first"),
            ActionButtonSpec(R.string.connection_error_action_setup_server, "second")
        ),
        buttonClickListener = {}
    )
}