package de.maniac103.squeezeclient.ui.itemlist

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.maniac103.squeezeclient.databinding.FragmentComposeBinding
import de.maniac103.squeezeclient.extfuncs.ViewEdge
import de.maniac103.squeezeclient.extfuncs.addSystemBarAndCutoutInsetsListener
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.extfuncs.showActionTimePicker
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.MainContentChild
import de.maniac103.squeezeclient.ui.bottomsheets.ChoicesBottomSheetFragment
import de.maniac103.squeezeclient.ui.bottomsheets.InputBottomSheetFragment
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class JiveHomeItemListComposeFragment :
    ViewBindingFragment<FragmentComposeBinding>(FragmentComposeBinding::inflate),
    MainContentChild,
    ChoicesBottomSheetFragment.SelectionListener,
    InputBottomSheetFragment.InputSubmitListener {

    interface NavigationListener {
        fun onNodeSelected(nodeId: String)
        fun onGoAction(title: String, action: JiveAction): Job?
    }

    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)
    private val nodeId get() = requireArguments().getString("nodeId")!!
    private val listener get() = requireParentAs<NavigationListener>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val titleFlow get() = viewModel.homeMenuFlow
        .mapNotNull { it[nodeId]?.title }
        .map { listOf(it) }
    override val iconFlow = flowOf(null)

    override val scrollingTargetView: View? get() = null

    private val viewModel: JiveHomeItemListViewModel by viewModels {
        SavedStateViewModelFactory(requireActivity().application, this, requireArguments())
    }


    override fun onBindingCreated(binding: FragmentComposeBinding) {
        binding.compose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    // TODO: insets handling
                    JiveHomeItemList(viewModel) { item -> handleItemSelected(item) }
                }
            }
        }
        binding.root.enableMainContentBackground()
    }

    // ChoicesBottomSheetFragment.SelectionListener implementation

    override fun onChoiceSelected(choice: JiveAction, extraData: Bundle?) = lifecycleScope.launch {
        connectionHelper.executeAction(playerId, choice)
    }

    // InputBottomSheetFragment.InputSubmitListener implementation

    override fun onInputSubmitted(title: String, action: JiveAction, isGoAction: Boolean) =
        if (isGoAction) {
            listener.onGoAction(title, action)
        } else {
            lifecycleScope.launch {
                connectionHelper.executeAction(playerId, action)
            }
        }

    // Private implementation details

    private fun handleItemSelected(item: JiveHomeMenuItem) = when {
        item.input != null -> {
            showInput(item)
            null
        }

        item.choices != null -> {
            showChoices(item)
            null
        }

        item.doAction != null -> lifecycleScope.launch {
            connectionHelper.executeAction(playerId, item.doAction)
        }

        item.goAction != null -> {
            listener.onGoAction(item.title, item.goAction)
        }

        else -> {
            listener.onNodeSelected(item.id)
            null
        }
    }

    private fun showInput(item: JiveHomeMenuItem) {
        val input = requireNotNull(item.input)
        if (input.type == JiveActions.Input.Type.Time) {
            showActionTimePicker(item.title, input) {
                onInputSubmitted(item.title, input.action.withInputValue(it), false)
            }
        } else {
            val f = InputBottomSheetFragment.createForInput(item.title, input)
            f.show(childFragmentManager, "input")
        }
    }

    private fun showChoices(item: JiveHomeMenuItem) {
        val choices = item.choices ?: return
        val f = ChoicesBottomSheetFragment.create(item.title, choices, null)
        f.show(childFragmentManager, "choices")
    }

    companion object {
        fun create(playerId: PlayerId, nodeId: String) = JiveHomeItemListComposeFragment().apply {
            arguments = Bundle().apply {
                putParcelable("playerId", playerId)
                putString("nodeId", nodeId)
            }
        }
    }
}

@Composable
fun JiveHomeItemList(
    viewModel: JiveHomeItemListViewModel = viewModel(),
    itemSelectionListener: (JiveHomeMenuItem) -> Unit = {}
) {
    val menuEntries by viewModel.homeMenuItemsFlow.collectAsState(emptyList())

    LazyColumn(
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        items(menuEntries) { entry ->
            TwoLineListItemWithIcon(
                title = entry.title,
                subtext = entry.subText,
                iconResourceId = entry.iconResourceId,
                modifier = Modifier.clickable { itemSelectionListener(entry.source) }
            )
        }
    }
}