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

package de.maniac103.squeezeclient.model

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

data class ServerConfiguration(
    val name: String,
    val hostnameAndPort: String,
    val username: String?,
    val password: String?
) {
    val url: HttpUrl get() {
        val url = "http://$hostnameAndPort".toHttpUrl()
        // If no port number was specified, use the default port 9000
        return if (url.port == 80 && hostnameAndPort.lastOrNull { it == ':' } == null) {
            url.newBuilder().port(9000).build()
        } else {
            url
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    val credentialsAsAuthorizationHeader
        get() = if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            null
        } else {
            val auth = Base64.encode("$username:$password".toByteArray())
            "Basic $auth"
        }
}
