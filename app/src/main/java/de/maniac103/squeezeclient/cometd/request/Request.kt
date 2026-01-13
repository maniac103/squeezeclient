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

package de.maniac103.squeezeclient.cometd.request

import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

open class Request protected constructor(
    private val playerId: PlayerId?,
    private val page: PagingParams?,
    vararg cmd: String
) {
    protected val cmd = mutableListOf<String>().apply { addAll(cmd) }
    protected val params = mutableMapOf<String, String?>()

    fun toMessageJson(): JsonArray = buildJsonArray {
        add(playerId?.id ?: "")
        add(
            buildJsonArray {
                cmd.forEach { add(it) }
                page?.let {
                    add(it.start)
                    add(it.page)
                }
                params
                    .filterValues { v -> v != null }
                    .forEach { (k, v) -> add("$k:$v") }
            }
        )
    }
}
