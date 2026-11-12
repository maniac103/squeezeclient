/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
 *
 * Portions adapted from SqueezeDrive, Copyright (c) 2026 SqueezeDrive contributors.

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

package de.maniac103.squeezeclient.service.mediasession

import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession

class LibrarySubscriptions(private val session: MediaLibraryService.MediaLibrarySession) {
    private val subsByController = mutableMapOf<MediaSession.ControllerInfo, MutableSet<String>>()

    fun add(controller: MediaSession.ControllerInfo, parentId: String): Boolean {
        val node = parentId.toTreeNode()
        if (node == null || !node.browsable) {
            return false
        }
        subsByController.getOrPut(controller) { mutableSetOf() }.add(parentId)
        return true
    }

    fun remove(controller: MediaSession.ControllerInfo, parentId: String) {
        val parents = subsByController[controller] ?: return
        parents.remove(parentId)
        if (parents.isEmpty()) {
            subsByController.remove(controller)
        }
    }

    fun removeAll(controller: MediaSession.ControllerInfo) {
        subsByController.remove(controller)
    }

    fun notifyChange() {
        subsByController.entries.forEach { (controller, parentIds) ->
            parentIds.forEach {
                session.notifyChildrenChanged(controller, it, Int.MAX_VALUE, null)
            }
        }
    }
}
