package de.maniac103.squeezeclient.ui.contextmenu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ContextMenuBottomSheetViewModel(
    private val application: Application,
    private val playerId: de.maniac103.squeezeclient.model.PlayerId,
    initialItems: List<SlimBrowseItemList.SlimBrowseItem>,
    val parentItem: SlimBrowseItemList.SlimBrowseItem
) : AndroidViewModel(application) {
    private val pageStackInternal =
        MutableStateFlow(listOf(Page(parentItem, initialItems)))
    private val busyItemInternal = MutableStateFlow<SlimBrowseItemList.SlimBrowseItem?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pageFlow = pageStackInternal.map { it.last().items }
    val backNavTitleFlow = pageStackInternal.map { pages ->
        if (pages.size > 1) pages[pages.lastIndex].parent.title else null
    }
    val busyItemFlow = busyItemInternal.asStateFlow()

    fun pushPage(item: SlimBrowseItemList.SlimBrowseItem, fetchAction: JiveAction) {
        val job = viewModelScope.launch {
            val newItems = application.connectionHelper.fetchItemsForAction(
                playerId,
                fetchAction,
                PagingParams.All,
                false
            )
            pageStackInternal.value += Page(item, newItems.items)
        }
        setItemBusy(item, job)
    }

    fun popPage() {
        val newStack = pageStackInternal.value
            .takeIf { it.size > 1 }
            ?.toMutableList()
            ?.also { it.removeAt(it.lastIndex) }
            ?: return

        pageStackInternal.value = newStack
    }

    fun setItemBusy(item: SlimBrowseItemList.SlimBrowseItem, job: Job) {
        busyItemInternal.value = item
        job.invokeOnCompletion {
            busyItemInternal.value = null
        }
    }

    data class Page(
        val parent: SlimBrowseItemList.SlimBrowseItem,
        val items: List<SlimBrowseItemList.SlimBrowseItem>
    )
}
