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

package de.maniac103.squeezeclient.ui.playermanagement

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.ConnectionState
import de.maniac103.squeezeclient.databinding.ActivityPlayerManagementBinding
import de.maniac103.squeezeclient.extfuncs.ViewEdge
import de.maniac103.squeezeclient.extfuncs.addSystemBarAndCutoutInsetsListener
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class PlayerManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerManagementBinding
    private lateinit var dragTargetAdapter: DragTargetAdapter
    private var disconnectFinishJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.appbarContainer.addSystemBarAndCutoutInsetsListener(ViewEdge.Top)
        binding.recycler.addSystemBarAndCutoutInsetsListener(ViewEdge.Bottom)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_24dp)
        binding.toolbar.setNavigationOnClickListener { finish() }

        dragTargetAdapter = DragTargetAdapter()

        val itemTouchCallback = PlayerDragMoveCallback(
            dragTargetAdapter,
            { master, slave ->
                lifecycleScope.launch { connectionHelper.syncPlayers(master, slave) }
            },
            { player ->
                lifecycleScope.launch { connectionHelper.unsyncPlayer(player) }
            }
        )
        val itemTouchHelper = ItemTouchHelper(itemTouchCallback)

        val callback = object : PlayerViewHolder.Callback {
            override fun onVolumeChanged(playerId: PlayerId, volume: Int) = lifecycleScope.launch {
                delay(200.milliseconds)
                connectionHelper.setVolume(playerId, volume)
            }

            override fun onPowerToggled(playerId: PlayerId) = lifecycleScope.launch {
                connectionHelper.togglePower(playerId)
            }

            override fun onPlayStateChanged(playerId: PlayerId, playState: PlayerStatus.PlayState) =
                lifecycleScope.launch {
                    connectionHelper.changePlaybackState(playerId, playState)
                }

            override fun onDragInitiated(holder: PlayerViewHolder) {
                itemTouchHelper.startDrag(holder)
            }
        }

        val adapter = PlayerAdapter(callback)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = ConcatAdapter(adapter, dragTargetAdapter)
        itemTouchHelper.attachToRecyclerView(binding.recycler)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerInfoFlow().collectLatest {
                    adapter.submitList(it)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionHelper.state.collectLatest { state ->
                    disconnectFinishJob?.cancel()
                    binding.recycler.isVisible = state is ConnectionState.Connected
                    binding.loadingIndicator.isVisible = !binding.recycler.isVisible
                    when (state) {
                        is ConnectionState.Connected -> {}

                        is ConnectionState.Connecting -> {}

                        is ConnectionState.ConnectionFailure,
                        is ConnectionState.Disconnected -> {
                            disconnectFinishJob = launch {
                                delay(2.seconds)
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun playerInfoFlow(): Flow<List<PlayerData>> = connectionHelper.state
        .mapNotNull { (it as? ConnectionState.Connected)?.players }
        .flatMapMerge { players ->
            val playerIdAndStatusFlows = players.map { p ->
                connectionHelper.playerState(p.id).flatMapLatest { state ->
                    combine(flowOf(p), flowOf(state.playerId), state.playStatus) { data, id, ps ->
                        Triple(data, id, ps)
                    }
                }
            }
            combine(playerIdAndStatusFlows) { it.toList() }
        }
        .map { playerIdsAndStatus ->
            val playerData = playerIdsAndStatus
                .map { (playerData, playerId, status) ->
                    val master = if (status.syncMaster != playerId) status.syncMaster else null
                    val nowPlayingInfo = if (master == null) status.playlist.nowPlaying else null
                    val playbackState = if (master == null) status.playbackState else null
                    val modelName = PLAYER_NAME_MAP[playerData.model]
                        ?: getString(R.string.player_name_software_player, playerData.model)
                    PlayerData(
                        playerId,
                        status.playerName,
                        modelName,
                        playerData.canPowerOff,
                        nowPlayingInfo,
                        playbackState,
                        status.currentVolume,
                        status.powered,
                        master
                    )
                }
            val masterNames = playerData
                .filter { it.master == null }
                .associate { it.playerId to it.name }
                .toMap()
            playerData.sortedWith { a, b ->
                when {
                    // Both are masters -> compare by their name
                    a.master == null && b.master == null -> a.name.compareTo(b.name)

                    // Both are slaves to the same master -> compare by their name
                    a.master == b.master -> a.name.compareTo(b.name)

                    // A is slave of B
                    a.master == b.playerId -> 1

                    // B is slave of A
                    b.master == a.playerId -> -1

                    // Both are slaves to different masters -> compare master names
                    a.master != null && b.master != null -> {
                        val aMasterName = requireNotNull(masterNames[a.master])
                        val bMasterName = requireNotNull(masterNames[b.master])
                        aMasterName.compareTo(bMasterName)
                    }

                    // A is master, but B isn't -> compare name of A to master name of B
                    a.master == null -> a.name.compareTo(requireNotNull(masterNames[b.master]))

                    // B is master, but A isn't -> compare name of B to master name of A
                    else -> requireNotNull(masterNames[a.master]).compareTo(b.name)
                }
            }
        }
        .distinctUntilChanged()

    companion object {
        private val PLAYER_NAME_MAP = mapOf(
            "baby" to "Squeezebox Radio",
            "boom" to "Squeezebox Boom",
            "receiver" to "Squeezebox Receiver",
            "controller" to "Squeezebox Controller",
            "fab4" to "Squeezebox Touch",
            "squeezebox" to "Squeezebox 1",
            "squeezebox2" to "Squeezebox 2",
            "squeezebox3" to "Squeezebox Classic",
            "slimp3" to "SliMP3",
            "transporter" to "Transporter"
        )
    }
}
