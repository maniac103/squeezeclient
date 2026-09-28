package de.maniac103.squeezeclient.ui.itemlist

import de.maniac103.squeezeclient.model.SlimBrowseItemList

sealed class SlimBrowseItemTrailingWidget {
    class Choice(val label: String) : SlimBrowseItemTrailingWidget()
    class Checkbox(val checked: Boolean) : SlimBrowseItemTrailingWidget()
    class Radio(val checked: Boolean) : SlimBrowseItemTrailingWidget()
    class ContextMenu(val clickListener: () -> Unit) : SlimBrowseItemTrailingWidget()
}

fun SlimBrowseItemList.SlimBrowseItem.extractTrailingWidget(
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) = actions?.let { actions ->
    when {
        actions.checkbox != null ->
            SlimBrowseItemTrailingWidget.Checkbox(actions.checkbox.state)

        actions.radio != null ->
            SlimBrowseItemTrailingWidget.Radio(actions.radio.state)

        actions.choices != null ->
            SlimBrowseItemTrailingWidget.Choice(
                actions.choices.items[actions.choices.selectedIndex].title,
            )

        actions.hasContextMenu ->
            SlimBrowseItemTrailingWidget.ContextMenu {
                contextMenuClickListener(this)
            }

        else -> null
    }
}

