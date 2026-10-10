package de.maniac103.squeezeclient.ui.contextmenu

import androidx.lifecycle.ViewModel
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

class ItemActionsViewModel(
    val item: SlimBrowseItemList.SlimBrowseItem
) : ViewModel() {
    private val busyActionFlowInternal = MutableStateFlow<ActionItem?>(null)

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

    data class ActionItem(
        val labelResId: Int,
        val action: JiveAction?,
        val download: DownloadRequestData?
    )
}