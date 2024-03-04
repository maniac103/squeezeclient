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

package de.maniac103.squeezeclient.extfuncs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

sealed class ValueOrCompletion<out T> {
    data class Value<out T>(val value: T) : ValueOrCompletion<T>()
    data class Completion(val exception: Throwable?) : ValueOrCompletion<Nothing>()
}

fun <T> Flow<T>.materializeCompletion(): Flow<ValueOrCompletion<T>> = flow {
    val result = runCatching {
        collect { emit(ValueOrCompletion.Value(it)) }
    }
    emit(ValueOrCompletion.Completion(result.exceptionOrNull()))
}

fun <T> Flow<ValueOrCompletion<T>>.dematerializeCompletion(): Flow<T> = transformWhile { vc ->
    when (vc) {
        is ValueOrCompletion.Value -> {
            emit(vc.value)
            true
        }
        is ValueOrCompletion.Completion -> {
            vc.exception?.let { throw it }
            false
        }
    }
}
