/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
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

package de.maniac103.squeezeclient

import androidx.work.WorkManager
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val globalModule = module {
    single {
        Json {
            coerceInputValues = true
            ignoreUnknownKeys = true
        }
    }

    single {
        Preferences(androidApplication())
    }

    single {
        WorkManager.getInstance(androidContext())
    }

    single {
        val preferences = get<Preferences>()
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val serverConfig = preferences.serverConfig.value
                val authHeader = serverConfig?.credentialsAsAuthorizationHeader
                val actualRequest = when {
                    request.url.host == serverConfig?.url?.host && authHeader != null ->
                        request.newBuilder().addHeader("Authorization", authHeader).build()

                    else -> request
                }
                chain.proceed(actualRequest)
            }
            .followRedirects(true)
            .build()
    }

    single {
        ConnectionHelper(
            appContext = androidApplication(),
            preferences = get<Preferences>(),
            json = get<Json>(),
            httpClient = get<OkHttpClient>()
        )
    }
}
