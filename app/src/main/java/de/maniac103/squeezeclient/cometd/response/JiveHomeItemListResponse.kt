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

package de.maniac103.squeezeclient.cometd.response

import android.content.Context
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.jsonParser
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.JiveWindow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class JiveHomeItemListResponse(
    val count: Int,
    val offset: Int,
    @SerialName("item_loop")
    val items: List<JsonObject>
) {
    @Serializable
    data class Window(
        val titleStyle: String? = null,
        val iconId: String? = null
    )

    @Serializable
    data class HomeMenuItemResponse(
        val id: String,
        val node: String,
        val text: String = "", // Omitted in case of 'remove' messages
        val weight: Int = 0,
        val window: Window? = null
        // All action related attributes are handled manually. This includes:
        // actions, choiceStrings, selectedIndex, input
    ) {
        fun asModelItem(actions: JiveActions?): JiveHomeMenuItem {
            val textLines = text.split("\n", limit = 2)
            return JiveHomeMenuItem(
                id, node, weight, textLines[0], textLines.elementAtOrNull(1),
                window?.let { JiveWindow(it.titleStyle) },
                actions?.goAction,
                actions?.doAction,
                actions?.input,
                actions?.choices
            )
        }
    }

    fun asModelItems(context: Context): List<JiveHomeMenuItem> {
        val result = items.map { context.jsonParser.parseToJiveHomeMenuItem(it) }.toMutableList()
        result.addAll(
            FIXED_ITEMS
                .filter { info ->
                    result.any { it.node == info.id } && result.none { it.id == info.id }
                }
                .map { info -> info.asModelItem(context) }
        )
        return result
    }

    private data class FixedHomeMenuInfo(
        val id: String,
        val node: String,
        val weight: Int,
        val titleResId: Int
    ) {
        fun asModelItem(context: Context) = JiveHomeMenuItem(
            id,
            node,
            weight,
            context.getString(titleResId),
            null,
            null,
            null,
            null,
            null,
            null
        )
    }

    companion object {
        private val FIXED_ITEMS = listOf(
            FixedHomeMenuInfo(
                "extras",
                "home",
                1000,
                R.string.item_title_extras
            ),
            FixedHomeMenuInfo(
                "settings",
                "home",
                1001,
                R.string.item_title_settings
            ),
            FixedHomeMenuInfo(
                "advancedSettings",
                "settings",
                1002,
                R.string.item_title_advanced_settings
            )
        )
    }
}

fun Json.parseToJiveHomeMenuItem(itemObject: JsonObject): JiveHomeMenuItem {
    val item = decodeFromJsonElement<JiveHomeItemListResponse.HomeMenuItemResponse>(itemObject)
    val actions = combineItemAndBaseActions(itemObject, null)
    return item.asModelItem(actions)
}
