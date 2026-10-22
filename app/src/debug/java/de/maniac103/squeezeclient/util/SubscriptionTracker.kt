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

package de.maniac103.squeezeclient.util

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

private const val TAG = "SubscriptionTracker"

class SubscriptionTracker {
    private val instances = ConcurrentHashMap<String, SubscriptionTrackerInstance>()

    @OptIn(ExperimentalStdlibApi::class)
    internal operator fun get(tag: String) =
        instances.getOrPutIfMissing(tag, { SubscriptionTrackerInstance(tag) })

    fun dumpActive() {
        instances.values.forEach { it.dumpActive() }
    }
}

internal class SubscriptionTrackerInstance(private val tag: String) {
    private val nextId = AtomicInteger(0)
    private val active = ConcurrentHashMap<Int, Throwable>()

    internal suspend fun <T> collectTracked(
        upstream: SharedFlow<T>,
        collector: FlowCollector<T>
    ): Nothing {
        val id = onSubscribe()
        var cause: Throwable? = null
        try {
            upstream.collect(collector)
        } catch (e: Throwable) {
            cause = e
            throw e
        } finally {
            onUnsubscribe(id, cause)
        }
    }

    internal fun onSubscribe(): Int {
        val id = nextId.incrementAndGet()
        active[id] = Throwable("Subscriber #$id was created here")
        Log.d(TAG, "$tag: subscribed #$id (active=${active.size})")
        return id
    }

    internal fun onUnsubscribe(id: Int, cause: Throwable?) {
        active.remove(id)
        Log.d(TAG, "$tag: unsubscribed #$id (active=${active.size}), cause=$cause")
    }

    internal fun dumpActive() {
        Log.d(TAG, "For $tag: ${active.size} active subscriber(s)")
        active.forEach { (id, trace) -> Log.d(TAG, "still active: #$id", trace) }
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class TrackedStateFlow<T>(
    private val delegate: StateFlow<T>,
    tracker: SubscriptionTracker,
    tag: String
) : StateFlow<T> by delegate {
    private val trackerInstance = tracker[tag]

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        trackerInstance.collectTracked(delegate, collector)
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class TrackedSharedFlow<T>(
    private val delegate: SharedFlow<T>,
    tracker: SubscriptionTracker,
    tag: String
) : SharedFlow<T> by delegate {
    private val trackerInstance = tracker[tag]

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        trackerInstance.collectTracked(delegate, collector)
    }
}

fun <T> Flow<T>.tracked(tracker: SubscriptionTracker, tag: String): Flow<T> {
    val trackerInstance = tracker[tag]
    val delegate = this

    return flow {
        val id = trackerInstance.onSubscribe()
        var cause: Throwable? = null
        try {
            delegate.collect {
                emit(it)
            }
        } catch (e: Throwable) {
            cause = e
            throw e
        } finally {
            trackerInstance.onUnsubscribe(id, cause)
        }
    }
}

fun <T> SharedFlow<T>.tracked(tracker: SubscriptionTracker, tag: String): SharedFlow<T> =
    TrackedSharedFlow(this, tracker, tag)

fun <T> StateFlow<T>.tracked(tracker: SubscriptionTracker, tag: String): StateFlow<T> =
    TrackedStateFlow(this, tracker, tag)
