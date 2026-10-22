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

package de.maniac103.squeezeclient.ui.serversetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.ui.composables.ResourceIconButton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun ServerSetupScreen(
    discoveredServers: List<ServerSetupActivity.ServerDiscoveryResult>?,
    discoveryProgress: Float,
    currentServerConfig: ServerConfiguration?,
    onConnectClicked: (config: ServerConfiguration) -> Unit = {},
    onStartDiscovery: () -> Unit = {},
    onGoBack: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        scrollableState = scrollState
    )
    val appBarScrollable by remember {
        derivedStateOf {
            scrollState.canScrollForward ||
                scrollState.canScrollBackward ||
                scrollBehavior.state.heightOffset != 0f
        }
    }

    val serverAddressState = rememberTextFieldState(currentServerConfig?.hostnameAndPort ?: "")
    val userNameState = rememberTextFieldState(currentServerConfig?.username ?: "")
    val passwordState = rememberTextFieldState(currentServerConfig?.password ?: "")
    var selectedServer by remember {
        mutableStateOf<ServerSetupActivity.ServerDiscoveryResult?>(null)
    }
    var passwordVisible by remember { mutableStateOf(false) }

    val addressHasError by remember {
        derivedStateOf {
            val address = serverAddressState.text
            address.isNotEmpty() && address.let { "http://$it" }.toHttpUrlOrNull() == null
        }
    }
    val addressValid by remember {
        derivedStateOf { serverAddressState.text.isNotEmpty() && !addressHasError }
    }
    val credentialsValid by remember {
        derivedStateOf { userNameState.text.isEmpty() || passwordState.text.isNotEmpty() }
    }

    Scaffold(
        modifier = if (appBarScrollable) {
            Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        } else {
            Modifier
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.app_name))
                },
                subtitle = {
                    Text(text = stringResource(R.string.server_setup_title))
                },
                navigationIcon = {
                    if (onGoBack != null) {
                        ResourceIconButton(
                            drawableResId = R.drawable.ic_arrow_left_24dp,
                            contentDescriptionResId = R.string.content_description_appbar_back,
                            onClick = onGoBack
                        )
                    }
                },
                titleHorizontalAlignment = Alignment.CenterHorizontally,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            // discovered servers
            DiscoveredServersDropdown(
                discoveredServers = discoveredServers,
                onStartDiscovery = onStartDiscovery,
                onEntrySelected = { entry ->
                    selectedServer = entry
                    serverAddressState.setTextAndPlaceCursorAtEnd(
                        when {
                            entry.hostName.isEmpty() ->
                                currentServerConfig?.hostnameAndPort ?: ""

                            entry.port == null -> entry.hostName

                            else -> "${entry.hostName}:${entry.port}"
                        }
                    )
                }
            )

            if (discoveredServers == null) {
                LinearProgressIndicator(
                    progress = { discoveryProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            // server address
            OutlinedTextField(
                state = serverAddressState,
                enabled = selectedServer?.hostName.isNullOrEmpty(),
                isError = addressHasError,
                lineLimits = TextFieldLineLimits.SingleLine,
                supportingText = {
                    if (addressHasError) {
                        Text(
                            stringResource(
                                R.string.server_address_error,
                                serverAddressState.text
                            )
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                placeholder = { Text(stringResource(R.string.server_address_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // user name
            OutlinedTextField(
                state = userNameState,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                placeholder = { Text(stringResource(R.string.server_user_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // password
            OutlinedSecureTextField(
                state = passwordState,
                isError = !credentialsValid,
                supportingText = {
                    if (!credentialsValid) {
                        Text(stringResource(R.string.server_creds_error))
                    }
                },
                textObfuscationMode = if (passwordVisible) {
                    TextObfuscationMode.Visible
                } else {
                    TextObfuscationMode.System
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                placeholder = { Text(stringResource(R.string.server_password_hint)) },
                trailingIcon = {
                    ResourceIconButton(
                        drawableResId = when (passwordVisible) {
                            true -> R.drawable.ic_eye_off_24dp
                            false -> R.drawable.ic_eye_24dp
                        },
                        contentDescriptionResId = when (passwordVisible) {
                            true -> R.string.content_description_hide_password
                            false -> R.string.content_description_show_password
                        },
                        onClick = { passwordVisible = !passwordVisible }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            FilledIconButton(
                onClick = {
                    val address = serverAddressState.text.toString()
                    val config = ServerConfiguration(
                        selectedServer?.serverName ?: address,
                        address,
                        userNameState.text.toString(),
                        passwordState.text.toString()
                    )

                    onConnectClicked(config)
                },
                enabled = addressValid && credentialsValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = stringResource(R.string.server_connect))
            }
        }
    }
}

@Composable
fun DiscoveredServersDropdown(
    discoveredServers: List<ServerSetupActivity.ServerDiscoveryResult>?,
    onStartDiscovery: () -> Unit = {},
    onEntrySelected: (ServerSetupActivity.ServerDiscoveryResult) -> Unit = {}
) {
    var discoveryDropdownExpanded by remember { mutableStateOf(false) }
    val discoveryDropdownState = rememberTextFieldState(
        discoveredServers?.getOrNull(0)?.serverName ?: ""
    )

    ExposedDropdownMenuBox(
        expanded = discoveryDropdownExpanded,
        onExpandedChange = { discoveryDropdownExpanded = it },
        modifier = Modifier
            .padding(vertical = 4.dp)
    ) {
        Box {
            TextField(
                state = discoveryDropdownState,
                readOnly = true,
                enabled = discoveredServers != null,
                lineLimits = TextFieldLineLimits.SingleLine,
                label = {
                    val labelResId = when (discoveredServers) {
                        null -> R.string.server_scanning
                        else -> R.string.server_choose_hint
                    }
                    Text(stringResource(labelResId))
                },
                leadingIcon = {
                    if (discoveredServers != null) {
                        Spacer(Modifier.size(24.dp))
                    }
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = discoveryDropdownExpanded
                    )
                },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )

            if (discoveredServers != null) {
                ResourceIconButton(
                    drawableResId = R.drawable.ic_refresh_24dp,
                    contentDescriptionResId = R.string.content_description_server_setup_refresh,
                    onClick = onStartDiscovery,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }

        ExposedDropdownMenu(
            expanded = discoveryDropdownExpanded,
            onDismissRequest = { discoveryDropdownExpanded = false }
        ) {
            discoveredServers?.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.serverName) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    onClick = {
                        discoveryDropdownState.setTextAndPlaceCursorAtEnd(entry.serverName)
                        discoveryDropdownExpanded = false
                        onEntrySelected(entry)
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun ServerSetupScreenPreview() {
    val results = listOf(
        ServerSetupActivity.ServerDiscoveryResult("Server name", "Host", null),
        ServerSetupActivity.ServerDiscoveryResult("Server name 2", "1.2.3.4", 1234)
    )
    ServerSetupScreen(results, 0.5F, null, onGoBack = {})
}
