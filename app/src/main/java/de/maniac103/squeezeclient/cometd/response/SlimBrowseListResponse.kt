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

import de.maniac103.squeezeclient.cometd.BooleanAsIntSerializer
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

// https://wiki.slimdevices.com/index.php/SBS_SqueezePlay_interface.html#Introduction
@Serializable
data class SlimBrowseListResponse(
    val title: String? = null,
    val window: SlimBrowseItemList.Window? = null,
    val offset: Int = 0,
    val count: Int = 0,
    @SerialName("item_loop")
    val items: List<JsonObject> = emptyList(),
    // https://wiki.slimdevices.com/index.php/SBS_SqueezePlay_interface.html#.3Cbase_fields.3E
    val base: JsonObject? = null
) {
    @Serializable
    data class ActionWindow(
        @Serializable(with = BooleanAsIntSerializer::class) val isContextMenu: Boolean
    )

    // https://wiki.slimdevices.com/index.php/SBS_SqueezePlay_interface.html#.3Citem_fields.3E
    @Serializable
    data class Item(
        // slider does not have text, hence text must be optional
        val text: String = "",
        @SerialName("textkey")
        val textKey: String? = null,
        @SerialName("icon-id")
        val iconId: String? = null,
        val icon: String? = null,
        val type: SlimBrowseItemList.ItemType? = null,
        val trackType: SlimBrowseItemList.TrackType? = null,
        val nextWindow: SlimBrowseItemList.NextWindow? = null,
        @SerialName("item_loop")
        val subItems: List<JsonObject>? = null,
        @SerialName("weblink")
        val webLink: String? = null
        // All action related attributes are parsed manually below. This includes:
        // actions, input, choiceStrings, selectedIndex, radio, checkbox,
        // slider, min, max, initial, sliderIcons
    )

    fun asModelItems(json: Json, infoProvider: ((JsonObject) -> String?)? = null): SlimBrowseItemList {
        val modelItems = items.filter { it.isNotEmpty() }.mapIndexed { index, itemObject ->
            val item = json.decodeFromJsonElement<Item>(itemObject)
            val textLines = item.text.split("\n", limit = 2)
            val actions = json.combineItemAndBaseActions(itemObject, base)
            val nextWindow = item.nextWindow
                ?: base?.get("nextWindow")?.let {
                    json.decodeFromJsonElement<SlimBrowseItemList.NextWindow>(it)
                }
            val subItems = item.subItems?.let {
                SlimBrowseListResponse(items = it).asModelItems(json, infoProvider).items
            }
            val extraInfo = (itemObject["commonParams"] as? JsonObject)
                ?.let { infoProvider?.invoke(it) }

            SlimBrowseItemList.SlimBrowseItem(
                offset + index,
                textLines[0],
                textLines.elementAtOrNull(1),
                extraInfo,
                item.textKey,
                item.type,
                item.trackType,
                item.icon,
                item.iconId,
                actions,
                nextWindow,
                subItems,
                item.webLink
            )
        }
        return SlimBrowseItemList(modelItems, offset, count, title, window)
    }
}

// https://wiki.slimdevices.com/index.php/SBS_SqueezePlay_interface.html#.3Cinput_fields.3E
@Serializable
data class SlimBrowseInputResponse(
    val title: String? = null,
    val len: Int,
    val allowedChars: String? = null,
    // might be a string or a number
    val initialText: JsonPrimitive? = null,
    @SerialName("_inputStyle")
    val inputStyle: JiveActions.Input.Type = JiveActions.Input.Type.Text
) {
    fun toJiveInput(doAction: JiveAction?, goAction: JiveAction?): JiveActions.Input? {
        val action = doAction ?: goAction ?: return null
        val initialText = initialText?.let {
            if (it.isString) it.content else it.long.toString()
        }

        return JiveActions.Input(
            len,
            initialText,
            allowedChars,
            inputStyle,
            action,
            doAction == null
        )
    }
}

// https://wiki.slimdevices.com/index.php/SBS_SqueezePlay_interface.html#.3Cactions_fields.3E
fun Json.combineItemAndBaseActions(item: JsonObject, base: JsonObject?): JiveActions {
    val itemActionsObj = item["actions"] as? JsonObject
    val baseActionsObj = (base?.get("actions") as? JsonObject)
    val availableKeys = mutableListOf<String>().apply {
        itemActionsObj?.let { addAll(it.keys) }
        baseActionsObj?.let { addAll(it.keys) }
    }

    val mapActionObjectToJiveAction = { actionObj: JsonObject, itemParamsObj: JsonObject? ->
        val cmd = actionObj["cmd"]?.jsonArray?.map { it.jsonPrimitive.content }
        val window = actionObj["window"]?.let {
            decodeFromJsonElement<SlimBrowseListResponse.ActionWindow>(it)
        }
        val nextWindow = actionObj["nextWindow"]?.let {
            decodeFromJsonElement<SlimBrowseItemList.NextWindow>(it)
        }
        val mergedParams: MutableMap<String, String?> = mutableMapOf("useContextMenu" to "1")
        // Include params from action object itself
        actionObj["params"]?.jsonObject?.forEach { (k, elem) ->
            mergedParams[k] = elem.jsonPrimitive.contentOrNull
        }
        // In case of the base action object, also include the item params
        itemParamsObj?.forEach { (k, elem) ->
            mergedParams[k] = elem.jsonPrimitive.contentOrNull
        }
        cmd?.let { JiveAction(cmd, mergedParams, nextWindow, window?.isContextMenu) }
    }

    val actionMap = availableKeys.mapNotNull { key ->
        val itemActionObj = itemActionsObj?.get(key) as? JsonObject
        val baseActionObj = baseActionsObj?.get(key) as? JsonObject
        val redirectedItemParamsObj = baseActionObj
            ?.get("itemsParams")?.jsonPrimitive?.content
            ?.let { item[it]?.jsonObject }
        val takenActionObj = itemActionObj?.takeIf { !it.containsKey("choices") }
            ?: baseActionObj?.takeIf { redirectedItemParamsObj != null }
            ?: return@mapNotNull null
        mapActionObjectToJiveAction(takenActionObj, redirectedItemParamsObj)?.let { key to it }
    }.toMap()

    val goActionName = item["goAction"]?.jsonPrimitive?.content ?: "go"
    val doAction = actionMap["do"]
    val goAction = actionMap[goActionName]
    val input = item["input"]?.let { decodeFromJsonElement<SlimBrowseInputResponse>(it) }
        ?.toJiveInput(doAction, goAction)

    // https://wiki.slimdevices.com/index.php/SBS_SqueezePlay_interface.html#Choices_array_in_Do_action
    val choiceActions = itemActionsObj
        ?.get("do")
        ?.jsonObject
        ?.get("choices")
        ?.jsonArray
        ?.mapNotNull {
            mapActionObjectToJiveAction(it.jsonObject, null)
        }
    val choiceStrings = item["choiceStrings"]?.jsonArray?.map { it.jsonPrimitive.content }
    val selectedIndex = item["selectedIndex"]?.jsonPrimitive?.int
    val choices = if (
        choiceActions != null &&
        choiceStrings != null &&
        selectedIndex != null &&
        choiceActions.size == choiceStrings.size
    ) {
        val choices = choiceActions.mapIndexed { i, action ->
            JiveActions.Choice(choiceStrings[i], action)
        }
        JiveActions.Choices(choices, selectedIndex - 1)
    } else {
        null
    }

    // https://wiki.slimdevices.com/index.php/SBS_SqueezePlay_interface.html#Slider_actions
    val sliderValue = item["slider"]?.jsonPrimitive?.int
    val sliderMin = item["min"]?.jsonPrimitive?.int
    val sliderMax = item["max"]?.jsonPrimitive?.int
    val sliderInitialValue = item["initial"]?.jsonPrimitive?.int
    val sliderIcons = item["sliderIcons"]?.jsonPrimitive?.content
    val slider = if (
        sliderValue != null &&
        doAction != null &&
        sliderMin != null &&
        sliderMax != null &&
        sliderInitialValue != null &&
        sliderIcons != null
    ) {
        JiveActions.Slider(sliderMin, sliderMax, sliderInitialValue, sliderIcons, doAction)
    } else {
        null
    }

    // https://wiki.slimdevices.com/index.php/SBS_SqueezePlay_interface.html#Go_Do.2C_On_and_Off_actions
    val onAction = actionMap["on"]
    val offAction = actionMap["off"]
    val checkboxValue = item["checkbox"]?.jsonPrimitive?.int
    val checkbox = if (checkboxValue != null && onAction != null && offAction != null) {
        JiveActions.Checkbox(checkboxValue > 0, onAction, offAction)
    } else {
        null
    }

    val radioValue = item["radio"]?.jsonPrimitive?.int
    val radio = if (radioValue != null && doAction != null) {
        JiveActions.Radio(radioValue > 0, doAction)
    } else {
        null
    }

    val itemIsLocal = item["trackType"]?.jsonPrimitive?.content == "local"
    val moreAction = actionMap["more"]?.withAdditionalParams(mapOf("xmlBrowseInterimCM" to "1"))
    val playAction = actionMap["play"]
    val downloadData = when {
        itemIsLocal && (goAction != null || moreAction != null) -> {
            val action = moreAction ?: requireNotNull(goAction)
            action.params["track_id"]?.let { trackId ->
                DownloadRequestData(
                    listOf("titles"),
                    mapOf("track_id" to trackId),
                    DownloadSongInfoListResponse.SERVER_TAGS
                )
            }
        }
        playAction?.cmd == listOf("playlistcontrol") && playAction.params["cmd"] == "load" -> when {
            playAction.params.containsKey("folder_id") -> {
                DownloadRequestData(
                    listOf("musicfolder"),
                    mapOf("recursive" to "1", "folder_id" to playAction.params["folder_id"]),
                    "cu"
                )
            }
            playAction.params.containsKey("playlist_id") -> {
                DownloadRequestData(
                    listOf("playlists", "tracks"),
                    mapOf("playlist_id" to playAction.params["playlist_id"]),
                    DownloadSongInfoListResponse.SERVER_TAGS
                )
            }
            else -> {
                // Depending on server settings, for a single track playAction might point to either
                // the track or the album it belongs to, so prefer the parameters of the moreAction
                // if available
                val params = (moreAction ?: playAction).params.filter { (k, v) ->
                    k in setOf("track_id", "album_id", "artist_id", "genre_id", "year") && v != null
                }
                DownloadRequestData(
                    listOf("titles"),
                    params,
                    DownloadSongInfoListResponse.SERVER_TAGS
                )
            }
        }
        else -> null
    }

    val addAction = actionMap["add"]
    val insertAction = actionMap["add-hold"]

    val onClickRefresh = (item["onClick"] ?: base?.get("onClick"))?.let {
        decodeFromJsonElement<JiveActions.RefreshBehavior>(it)
    }

    return JiveActions(
        goAction,
        if (radio == null && slider == null) doAction else null,
        moreAction,
        playAction,
        addAction,
        insertAction,
        downloadData,
        choices,
        checkbox,
        radio,
        slider,
        input,
        onClickRefresh
    )
}
