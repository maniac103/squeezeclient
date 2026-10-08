package de.maniac103.squeezeclient.ui.nowplaying

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NowPlayingFragment : Fragment() {
    interface Listener {
        fun onContextMenuAction(
            action: JiveAction,
            parentItem: SlimBrowseItemList.SlimBrowseItem,
            contextItem: SlimBrowseItemList.SlimBrowseItem
        ): Job?
        fun showVolumePopup()
    }

    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)
    private val motionState = NowPlayingMotionState(
        initialNowPlayingState = MotionAnchor.Collapsed,
        initialPlaylistState = MotionAnchor.Collapsed
    )
    private var contentBounds = RectF()
    private val listener get() = requireParentAs<Listener>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val wrapper = PlayerTouchContainer(inflater.context)
        wrapper.hitTest = { x, y -> contentBounds.contains(x, y) }

        val compose = ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val density = LocalDensity.current

                MaterialTheme {
                    Scaffold(
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        NowPlayingScreen(
                            playerId = playerId,
                            motionState = motionState,
                            serverConfig = prefs.serverConfig,
                            onShowVolumePopup = {
                                listener.showVolumePopup()
                            },
                            onInfoMenuItemGoAction = { action, parentItem, contextItem ->
                                listener.onContextMenuAction(action, parentItem, contextItem)
                            },
                            onContentBoundsChanged = { bounds ->
                                contentBounds = with (density) {
                                    RectF(
                                        bounds.left.toPx(),
                                        bounds.top.toPx(),
                                        bounds.right.toPx(),
                                        bounds.bottom.toPx()
                                    )
                                }
                            },
                            insets = innerPadding
                        )
                    }
                }
            }
        }

        wrapper.addView(
            compose,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        return wrapper
    }

    fun expandIfNeeded() = lifecycleScope.launch {
        motionState.nowPlaying.expand()
    }

    companion object {
        fun create(playerId: PlayerId) = NowPlayingFragment().apply {
            arguments = Bundle().apply {
                putParcelable("playerId", playerId)
            }
        }
    }
}

class PlayerTouchContainer(
    context: Context
) : FrameLayout(context) {

    var hitTest: ((Float, Float) -> Boolean)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (hitTest?.invoke(event.x, event.y) == false) {
                return false
            }
        }

        return super.dispatchTouchEvent(event)
    }
}