package de.maniac103.squeezeclient.ui.contextmenu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import coil3.request.ImageRequest
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.itemlist.buildImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

class ItemActionsViewModel(
    application: Application,
    val item: SlimBrowseItemList.SlimBrowseItem
) : AndroidViewModel(application) {
    private val busyActionFlowInternal = MutableStateFlow<ActionItem?>(null)

    val headerFlow = flowOf(
        Header(
            item.title,
            item.subText,
            item.buildImageRequest(application, application.prefs.serverConfig)
        )
    )
    val actionsFlow = flowOf(
        requireNotNull(item.actions).let { actions ->
            listOfNotNull(
                actions.addAction?.let { ActionItem(R.string.action_add, it, null) },
                actions.insertAction?.let { ActionItem(R.string.action_insert, it, null) },
                actions.playAction?.let { ActionItem(R.string.action_play, it, null) },
                actions.downloadData?.let { ActionItem(R.string.action_download, null, it) }
            )
        }
    )
    val busyActionFlow = busyActionFlowInternal.asStateFlow()

    fun setActionBusy(action: ActionItem, job: Job) {
        busyActionFlowInternal.value = action
        job.invokeOnCompletion {
            busyActionFlowInternal.value = null
        }
    }

    data class Header(val title: String, val subText: String?, val imageRequest: ImageRequest?)
    data class ActionItem(
        val labelResId: Int,
        val action: JiveAction?,
        val download: DownloadRequestData?
    )
}