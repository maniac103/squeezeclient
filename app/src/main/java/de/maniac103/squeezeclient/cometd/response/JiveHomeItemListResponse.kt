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
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.JiveWindow
import de.maniac103.squeezeclient.model.WindowStyle
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
    data class Window(val titleStyle: String? = null, val iconId: String? = null)

    @Serializable
    data class HomeMenuItemResponse(
        val id: String,
        val node: String,
        // Omitted in case of 'remove' messages
        val text: String = "",
        val weight: Float = 0F,
        val window: Window? = null,
        val windowStyle: WindowStyle? = null
        // All action related attributes are handled manually. This includes:
        // actions, choiceStrings, selectedIndex, input
    ) {
        fun asModelItem(
            actions: JiveActions?,
            fallbackWindowStyle: WindowStyle?
        ): JiveHomeMenuItem {
            val textLines = text.split("\n", limit = 2)
            return JiveHomeMenuItem(
                id, node, weight, textLines[0], textLines.elementAtOrNull(1),
                window?.let { JiveWindow(it.titleStyle) },
                windowStyle ?: fallbackWindowStyle,
                actions?.goAction,
                actions?.doAction,
                actions?.input,
                actions?.choices
            )
        }
    }

    fun asModelItems(json: Json, context: Context): List<JiveHomeMenuItem> {
        val result = items.map { json.parseToJiveHomeMenuItem(it) }.toMutableList()
        result.addAll(
            FixedHomeMenuItems.ITEMS
                .filter { info ->
                    result.any { it.node == info.id } && result.none { it.id == info.id }
                }
                .map { info -> info.asModelItem(context) }
        )
        return result
    }
}

// The server response is incomplete: some items are missing completely, and for some
// other items and response is incomplete and e.g. is missing the window style. Thus we have
// to hardcode the missing data here, like in every other client. See
// JiveMain::jiveMainNodes in JiveMain.lua in the SqueezePlay repo for reference.
private object FixedHomeMenuItems {
    data class ItemInfo(
        val id: String,
        val node: String,
        val weight: Int,
        val titleResId: Int,
        val windowStyle: WindowStyle?
    ) {
        fun asModelItem(context: Context) = JiveHomeMenuItem(
            id,
            node,
            weight.toFloat(),
            context.getString(titleResId),
            null,
            null,
            windowStyle,
            null,
            null,
            null,
            null
        )
    }

    val ITEMS = listOf(
        ItemInfo(
            "extras",
            "home",
            50,
            R.string.item_title_extras,
            null
        ),
        ItemInfo(
            "settings",
            "home",
            1005,
            R.string.item_title_settings,
            null
        ),
        ItemInfo(
            "settingsAudio",
            "settings",
            40,
            R.string.item_title_audio_settings,
            WindowStyle.TextOnlyList
        ),
        ItemInfo(
            "advancedSettings",
            "settings",
            105,
            R.string.item_title_advanced_settings,
            WindowStyle.TextOnlyList
        )
    )
}

fun Json.parseToJiveHomeMenuItem(itemObject: JsonObject): JiveHomeMenuItem {
    val item = decodeFromJsonElement<JiveHomeItemListResponse.HomeMenuItemResponse>(itemObject)
    val actions = combineItemAndBaseActions(itemObject, null)
    val fallback = FixedHomeMenuItems.ITEMS.firstOrNull { it.id == item.id && it.node == item.node }
    return item.asModelItem(actions, fallback?.windowStyle)
}
