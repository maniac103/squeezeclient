/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation,
 *   either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package de.maniac103.squeezeclient.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.ConnectionState
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.databinding.ActivityMainBinding
import de.maniac103.squeezeclient.databinding.NavDrawerHeaderBinding
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelableOrNull
import de.maniac103.squeezeclient.extfuncs.lastSelectedPlayer
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.putLastSelectedPlayer
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.extfuncs.useVolumeButtonsForPlayerVolume
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.Player
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.service.MediaService
import de.maniac103.squeezeclient.ui.bottomsheets.InfoBottomSheet
import de.maniac103.squeezeclient.ui.bottomsheets.SliderBottomSheetFragment
import de.maniac103.squeezeclient.ui.common.BaseSlimBrowseItemListFragment
import de.maniac103.squeezeclient.ui.common.ScrollingListFragment
import de.maniac103.squeezeclient.ui.common.TitleProvidingFragment
import de.maniac103.squeezeclient.ui.itemlist.JiveHomeListItemFragment
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemListFragment
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseSubItemListFragment
import de.maniac103.squeezeclient.ui.nowplaying.NowPlayingFragment
import de.maniac103.squeezeclient.ui.playermanagement.PlayerManagementActivity
import de.maniac103.squeezeclient.ui.prefs.SettingsActivity
import de.maniac103.squeezeclient.ui.search.LibrarySearchResultsFragment
import de.maniac103.squeezeclient.ui.search.RadioSearchResultsFragment
import de.maniac103.squeezeclient.ui.search.SearchFragment
import de.maniac103.squeezeclient.ui.slideshow.GalleryFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class MainActivity :
    AppCompatActivity(),
    JiveHomeListItemFragment.NavigationListener,
    SliderBottomSheetFragment.ChangeListener,
    BaseSlimBrowseItemListFragment.NavigationListener,
    SlimBrowseSubItemListFragment.DataRefreshProvider,
    NowPlayingFragment.ContextMenuListener,
    ConnectionErrorHintFragment.Listener,
    SearchFragment.Listener {

    private var currentPlayerScope: CoroutineScope? = null
    private var pendingNavigation: Job? = null

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerHeaderBinding: NavDrawerHeaderBinding

    private var allPlayers: List<Player>? = null
    private var player: Player? = null
    private var playerIsActive = false
    private var currentPlayerVolume = 0
    private var homeMenu: Map<String, JiveHomeMenuItem> = mapOf()
    private var consecutiveUnsuccessfulConnectAttempts = 0

    private val mainListFragment get() =
        supportFragmentManager.findFragmentById(binding.container.id)
    private val nowPlayingFragment get() =
        supportFragmentManager.findFragmentById(binding.playerContainer.id) as? NowPlayingFragment
    private val searchFragment get() =
        supportFragmentManager.findFragmentById(binding.searchContainer.id) as? SearchFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.open()
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.search -> {
                    player?.id?.let { playerId ->
                        supportFragmentManager.commit {
                            add(binding.searchContainer.id, SearchFragment.create(playerId))
                        }
                    }
                }
            }
            true
        }

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                in FIRST_PLAYER_MENU_ID..LAST_PLAYER_MENU_ID -> {
                    val playerIndex = menuItem.itemId - FIRST_PLAYER_MENU_ID
                    allPlayers?.elementAtOrNull(playerIndex)?.let {
                        changePlayer(it)
                        prefs.edit {
                            putLastSelectedPlayer(it.id)
                        }
                    }
                }
                R.id.manage_players ->
                    startActivity(Intent(this, PlayerManagementActivity::class.java))
                R.id.menu_settings ->
                    startActivity(Intent(this, SettingsActivity::class.java))
            }
            binding.drawerLayout.close()
            true
        }

        drawerHeaderBinding = NavDrawerHeaderBinding.bind(binding.navigationView.getHeaderView(0))
        drawerHeaderBinding.serverSetup.setOnClickListener { openServerSetup(true) }
        ViewCompat.setOnApplyWindowInsetsListener(drawerHeaderBinding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = insets.top)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.container) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                prefs.serverConfig?.let { drawerHeaderBinding.serverName.text = it.name }
                connectionHelper.state.collect { state -> updateContentForConnectionState(state) }
            }
        }

        binding.breadcrumbsHome.setOnClickListener {
            clearBackStack()
        }

        supportFragmentManager.apply {
            addOnBackStackChangedListener { updateBreadcrumbs() }
            registerFragmentLifecycleCallbacks(
                object : FragmentLifecycleCallbacks() {
                    override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                        super.onFragmentStarted(fm, f)
                        if (f.id == binding.container.id) {
                            val scrollTarget = (f as? ScrollingListFragment)?.scrollingTargetView
                            binding.appbarContainer.setLiftOnScrollTargetView(scrollTarget)
                        }
                    }
                },
                false
            )
        }
        savedInstanceState?.let {
            player = savedInstanceState.getParcelableOrNull("player", Player::class)
            updateBreadcrumbs()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        player?.let { outState.putParcelable("player", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        val activePlayerId = if (playerIsActive) player?.id else null
        if (activePlayerId != null && !isChangingConfigurations) {
            MediaService.start(this, activePlayerId)
        }
        currentPlayerScope?.cancel()
        currentPlayerScope = null
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("deprecation")
    override fun onBackPressed() = when {
        searchFragment != null -> onCloseSearch()
        nowPlayingFragment?.collapseIfExpanded() == true -> {}
        supportFragmentManager.backStackEntryCount > 0 -> {
            if (!binding.loadingIndicator.isVisible) {
                supportFragmentManager.popBackStack()
            }
            Unit
        }
        else -> super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val player = if (prefs.useVolumeButtonsForPlayerVolume) player else null
        return when {
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && player != null -> {
                lifecycleScope.launch {
                    connectionHelper.setVolume(player.id, currentPlayerVolume - 5)
                }
                true
            }

            keyCode == KeyEvent.KEYCODE_VOLUME_UP && player != null -> {
                lifecycleScope.launch {
                    connectionHelper.setVolume(player.id, currentPlayerVolume + 5)
                }
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (prefs.useVolumeButtonsForPlayerVolume && player != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onNodeSelected(nodeId: String) {
        val player = this.player ?: return
        if (homeMenu.values.any { it.node == nodeId }) {
            val f = JiveHomeListItemFragment.create(player.id, nodeId)
            replaceMainContent(f, "home:$nodeId", nodeId != "home")
        }
    }

    override fun onOpenSubItemList(
        item: SlimBrowseItemList.SlimBrowseItem,
        items: List<SlimBrowseItemList.SlimBrowseItem>
    ) {
        val player = this.player ?: return
        val f = SlimBrowseSubItemListFragment.create(player.id, item, items)
        val level = supportFragmentManager.backStackEntryCount
        replaceMainContent(f, "l$level-p${item.listPosition}-subitems", true)
    }

    override fun onOpenWebLink(title: String, link: Uri) {
        val intent = CustomTabsIntent.Builder()
            .build()
        intent.launchUrl(this, link)
    }

    override suspend fun provideRefreshedSubItemList(
        item: SlimBrowseItemList.SlimBrowseItem
    ): List<SlimBrowseItemList.SlimBrowseItem> {
        val player = this.player ?: return emptyList()
        val fm = supportFragmentManager
        val backStackCount = fm.backStackEntryCount
        val parentTag = fm.getBackStackEntryAt(backStackCount - 2).name
        val parentFragment = fm.findFragmentByTag(parentTag) as? SlimBrowseItemListFragment
            ?: throw IllegalStateException("Can't provide sub items for non item list parent")
        val items = connectionHelper.fetchItemsForAction(
            player.id,
            parentFragment.fetchAction,
            PagingParams.All
        )
        val updatedItem = items.items[item.listPosition]
        return updatedItem.subItems ?: emptyList()
    }

    override fun onGoAction(title: String, action: JiveAction): Job? =
        handleGoAction(listOf(title), action)

    override fun onGoAction(
        title: String,
        actionTitle: String?,
        action: JiveAction,
        nextWindow: SlimBrowseItemList.NextWindow?
    ): Job? {
        return if (nextWindow != null) {
            lifecycleScope.launch {
                player?.let { connectionHelper.executeAction(it.id, action) }
                handleNextWindow(nextWindow)
            }
        } else {
            handleGoAction(listOfNotNull(title, actionTitle), action)
        }
    }

    override fun onContextMenuAction(
        title: String,
        actionTitle: String?,
        action: JiveAction
    ): Job? {
        clearBackStack()
        return handleGoAction(listOfNotNull(title, actionTitle), action)
    }

    private fun handleGoAction(title: List<String>, action: JiveAction): Job? {
        val player = this.player ?: return null
        pendingNavigation?.cancel()
        pendingNavigation = currentPlayerScope?.launch {
            if (action.isSlideshow) {
                val items = connectionHelper.fetchSlideshowImages(player.id, action)
                val f = GalleryFragment.create(items, title.joinToString(" › "))
                val level = supportFragmentManager.backStackEntryCount
                replaceMainContent(f, "l$level-gallery", true)
                return@launch
            }
            // Fetch first item of page to check whether we're dealing with a slider or a normal page
            val result = connectionHelper.fetchItemsForAction(player.id, action, PagingParams(0, 1))
            val firstItem = result.items.getOrNull(0)
            when {
                result.totalCount == 1 && firstItem?.actions?.slider != null -> {
                    val f = SliderBottomSheetFragment.create(title.last(), firstItem.actions.slider)
                    f.show(supportFragmentManager, "slider")
                }
                result.totalCount == 0 && result.window?.textArea != null -> {
                    val f = InfoBottomSheet.create(
                        result.title ?: title.last(),
                        result.window.textArea
                    )
                    f.show(supportFragmentManager, "info")
                }
                else -> {
                    val f = SlimBrowseItemListFragment.create(
                        player.id,
                        title.joinToString(" › "),
                        action,
                        result.window?.windowStyle
                    )
                    val level = supportFragmentManager.backStackEntryCount
                    replaceMainContent(f, "l$level-items", true)
                }
            }
        }
        return pendingNavigation
    }

    private fun handleNextWindow(nextWindow: SlimBrowseItemList.NextWindow) {
        when (nextWindow) {
            SlimBrowseItemList.NextWindow.Parent ->
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStackImmediate()
                }
            SlimBrowseItemList.NextWindow.GrandParent ->
                if (supportFragmentManager.backStackEntryCount > 1) {
                    supportFragmentManager.popBackStackImmediate()
                    supportFragmentManager.popBackStackImmediate()
                }
            SlimBrowseItemList.NextWindow.Home,
            SlimBrowseItemList.NextWindow.MyMusic ->
                clearBackStack()
            SlimBrowseItemList.NextWindow.NowPlaying -> {
                clearBackStack()
                nowPlayingFragment?.expandIfNeeded()
            }
            else -> {}
        }
    }

    override fun onSliderChanged(input: JiveAction): Job = lifecycleScope.launch {
        player?.let { player -> connectionHelper.executeAction(player.id, input) }
    }

    override fun onCloseSearch() {
        searchFragment?.let {
            supportFragmentManager.commit { remove(it) }
        }
    }

    override fun onOpenLocalSearchPage(searchTerm: String, type: LibrarySearchRequest.Mode) {
        onCloseSearch()
        player?.id?.let { playerId ->
            val f = LibrarySearchResultsFragment.create(playerId, type, searchTerm)
            clearBackStack()
            replaceMainContent(f, "localsearch-$searchTerm", true)
        }
    }

    override fun onOpenRadioSearchPage(searchTerm: String) {
        onCloseSearch()
        player?.id?.let { playerId ->
            val f = RadioSearchResultsFragment.create(playerId, searchTerm)
            clearBackStack()
            replaceMainContent(f, "radiosearch-$searchTerm", true)
        }
    }

    override fun onActionInvoked(index: Int, tag: String?) = when (tag) {
        ACTION_TAG_RECONNECT -> connectionHelper.connect()
        ACTION_TAG_SERVER_SETUP -> openServerSetup(true)
        else -> {}
    }

    private fun clearBackStack() = supportFragmentManager.apply {
        while (backStackEntryCount > 0) {
            popBackStackImmediate()
        }
    }

    private fun updatePlayerList(players: List<Player>) {
        binding.navigationView.menu.apply {
            removeGroup(R.id.menu_players)
            players.forEachIndexed { index, player ->
                // FIXME: update name from player state
                add(R.id.menu_players, FIRST_PLAYER_MENU_ID + index, Menu.NONE, player.name).apply {
                    setIcon(R.drawable.ic_player_16dp)
                }
            }
            setGroupCheckable(R.id.menu_players, true, true)
        }
        this.allPlayers = players
    }

    private fun changePlayer(player: Player) {
        allPlayers
            ?.indexOfFirst { it.id == player.id }
            ?.let { index ->
                binding.navigationView.menu.findItem(FIRST_PLAYER_MENU_ID + index)?.isChecked = true
            }

        if (player.id != this.player?.id) {
            currentPlayerScope?.cancel()
            currentPlayerScope = null
            homeMenu = emptyMap()
            this.player = player
            clearBackStack()
            supportFragmentManager.commit {
                mainListFragment?.let { remove(it) }
                replace(binding.playerContainer.id, NowPlayingFragment.create(player.id))
            }
        }

        updatePlayerDependentMenuItems()
        if (currentPlayerScope == null) {
            val scope = lifecycleScope + Job()
            currentPlayerScope = scope
            updatePlayerStateSubscriptions(player.id, scope)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun updatePlayerStateSubscriptions(playerId: PlayerId, scope: CoroutineScope) {
        val stateFlow = connectionHelper.playerState(playerId)
        stateFlow.flatMapLatest { it.playStatus }.onEach { status ->
            binding.toolbar.subtitle = status.playerName
            currentPlayerVolume = status.currentVolume
            playerIsActive =
                status.powered && status.playbackState == PlayerStatus.PlayState.Playing
        }.launchIn(scope)

        stateFlow.flatMapLatest { it.homeMenu }.onEach { items ->
            homeMenu = items
            if (mainListFragment == null || mainListFragment is ConnectionErrorHintFragment) {
                onNodeSelected("home")
            }
        }.launchIn(scope)

        stateFlow.flatMapLatest { it.displayStatus }.onEach { message ->
            android.util.Log.d("foo", "display message $message")
            // TODO: improve this
            // Snackbar.make(binding.coordinatorLayout, message.text.joinToString(" "), Snackbar.LENGTH_SHORT).show()
        }.launchIn(scope)
    }

    private fun updateContentForConnectionState(state: ConnectionState) = when (state) {
        is ConnectionState.Disconnected -> {
            hideContentAndShowLoadingIndicator()
            if (prefs.serverConfig == null) {
                openServerSetup(false)
            } else if (++consecutiveUnsuccessfulConnectAttempts < 3) {
                connectionHelper.connect()
            } else {
                val f = ConnectionErrorHintFragment.create(
                    R.drawable.ic_cloud_question_24dp,
                    R.string.connection_error_text_no_connection,
                    action1LabelResId = R.string.connection_error_action_retry,
                    action2LabelResId = R.string.connection_error_action_setup_server,
                    action1Tag = ACTION_TAG_RECONNECT,
                    action2Tag = ACTION_TAG_SERVER_SETUP
                )
                showConnectionErrorHint(f)
            }
        }
        is ConnectionState.Connecting -> {
            hideContentAndShowLoadingIndicator()
        }
        is ConnectionState.Connected -> {
            consecutiveUnsuccessfulConnectAttempts = 0
            updatePlayerList(state.players)
            val lastActivePlayer = prefs.lastSelectedPlayer
            val activePlayer = lastActivePlayer
                .let { active -> state.players.find { it.id == active } }
                ?: state.players.firstOrNull()
            if (activePlayer != null) {
                showContentAndHideLoadingIndicator()
                if (lastActivePlayer == null) {
                    prefs.edit {
                        putLastSelectedPlayer(activePlayer.id)
                    }
                }
                changePlayer(activePlayer)
            } else {
                val f = ConnectionErrorHintFragment.create(
                    R.drawable.ic_player_16dp,
                    R.string.connection_error_text_no_player
                )
                showConnectionErrorHint(f)
            }
        }
    }

    private fun replaceMainContent(content: Fragment, tag: String, addToBackStack: Boolean) {
        supportFragmentManager.commit {
            replace(binding.container.id, content, tag)
            if (addToBackStack) {
                addToBackStack(tag)
            }
        }
    }

    private fun updatePlayerDependentMenuItems() {
        val isInLoadingOrErrorState = binding.loadingIndicator.isVisible ||
            mainListFragment is ConnectionErrorHintFragment

        binding.toolbar.menu.findItem(R.id.search)?.let {
            it.isVisible = player != null && !isInLoadingOrErrorState
        }
        binding.navigationView.menu.findItem(R.id.manage_players)?.let {
            val players = allPlayers
            it.isVisible = players != null && players.size > 1 && !isInLoadingOrErrorState
        }
    }

    private fun showContentAndHideLoadingIndicator() {
        supportFragmentManager.commit {
            mainListFragment?.let { show(it) }
            nowPlayingFragment?.let { show(it) }
            searchFragment?.let { show(it) }
        }
        binding.nowplayingPlaceholder?.isVisible = true
        binding.loadingIndicator.isVisible = false
        binding.toolbar.subtitle = player?.name
        updatePlayerDependentMenuItems()
        updateBreadcrumbs()
    }

    private fun hideContentAndShowLoadingIndicator() {
        supportFragmentManager.commit {
            mainListFragment?.let { hide(it) }
            nowPlayingFragment?.let { hide(it) }
            searchFragment?.let { hide(it) }
        }
        binding.nowplayingPlaceholder?.isVisible = false
        binding.breadcrumbsContainer.isVisible = false
        binding.loadingIndicator.isVisible = true
        binding.toolbar.subtitle = null
        binding.navigationView.menu.removeGroup(R.id.menu_players)
        updatePlayerDependentMenuItems()
    }

    private fun showConnectionErrorHint(f: ConnectionErrorHintFragment) {
        clearBackStack()
        supportFragmentManager.commit {
            replace(binding.container.id, f)
            nowPlayingFragment?.let { hide(it) }
            searchFragment?.let { hide(it) }
        }
        binding.nowplayingPlaceholder?.isVisible = false
        binding.breadcrumbsContainer.isVisible = false
        binding.loadingIndicator.isVisible = false
        binding.toolbar.subtitle = null
        binding.navigationView.menu.removeGroup(R.id.menu_players)
        updatePlayerDependentMenuItems()
    }

    private fun updateBreadcrumbs() = supportFragmentManager.apply {
        val breadcrumbs = (0 until backStackEntryCount).mapNotNull { index ->
            val f = findFragmentByTag(getBackStackEntryAt(index).name)
            (f as? TitleProvidingFragment)?.title
        }
        binding.breadcrumbsContainer.isVisible = breadcrumbs.isNotEmpty()
        binding.breadcrumbs.text =
            breadcrumbs.joinToString(separator = " › ", prefix = " › ")
    }

    private fun openServerSetup(allowBack: Boolean) {
        val intent = ServerSetupActivity.createIntent(this, allowBack)
        startActivity(intent)
    }

    companion object {
        private const val FIRST_PLAYER_MENU_ID = 1000
        private const val LAST_PLAYER_MENU_ID = 1100

        private const val ACTION_TAG_RECONNECT = "reconnect"
        private const val ACTION_TAG_SERVER_SETUP = "serversetup"
    }
}
