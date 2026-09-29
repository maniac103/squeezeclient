package de.maniac103.squeezeclient.ui.contextmenu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

class ContextMenuItemListViewModel(
    application: Application,
    items: List<SlimBrowseItemList.SlimBrowseItem>
) : AndroidViewModel(application) {
    private val busyItemInternal = MutableStateFlow<SlimBrowseItemList.SlimBrowseItem?>(null)

    val itemsFlow = flowOf(items)
    val busyItemFlow = busyItemInternal.asStateFlow()

    fun setItemBusy(item: SlimBrowseItemList.SlimBrowseItem, job: Job) {
        busyItemInternal.value = item
        job.invokeOnCompletion {
            busyItemInternal.value = null
        }
    }
}