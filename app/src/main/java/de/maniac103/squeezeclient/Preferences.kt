package de.maniac103.squeezeclient

import android.content.SharedPreferences
import androidx.core.content.edit
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.ServerConfiguration
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class Preferences(private val prefs: SharedPreferences) {
    val forceGridLayout: Preference<Boolean> get() = booleanPreference("force_list_grid", false)

    val serverConfig: Preference<ServerConfiguration?> get() {
        val namePref = stringPreference("server_name", null)
        val hostNameAndPortPref = stringPreference("server_url", null)
        val userNamePref = stringPreference("user", null)
        val passwordPref = stringPreference("password", null)

        val mapper = { name: String?, hostNameAndPort: String?, userName: String?, password: String? ->
            if (name != null && hostNameAndPort != null) {
                ServerConfiguration(name, hostNameAndPort, userName, password)
            } else {
                null
            }
        }

        return object : Preference<ServerConfiguration?> {
            override val value get() = mapper(
                namePref.value,
                hostNameAndPortPref.value,
                userNamePref.value,
                passwordPref.value
            )

            override fun asFlow(): Flow<ServerConfiguration?> {
                return combine(
                    namePref.asFlow(),
                    hostNameAndPortPref.asFlow(),
                    userNamePref.asFlow(),
                    passwordPref.asFlow()
                ) { name, hostNameAndPort, userName, password ->
                    mapper(name, hostNameAndPort, userName, password)
                }
            }

            override suspend fun update(newValue: ServerConfiguration?) {
                withContext(Dispatchers.IO) {
                    prefs.edit {
                        if (newValue == null) {
                            remove("server_url")
                            remove("server_name")
                            remove("user")
                            remove("password")
                        } else {
                            val hasCreds = !newValue.username.isNullOrEmpty() &&
                                    !newValue.password.isNullOrEmpty()
                            putString("server_url", newValue.hostnameAndPort)
                            putString("server_name", newValue.name)
                            putString("user", if (hasCreds) newValue.username else null)
                            putString("password", if (hasCreds) newValue.password else null)
                        }
                    }
                }
            }
        }
    }

    val lastSelectedPlayer: Preference<PlayerId?> get() = TransformingPreferenceImpl(
        stringPreference("active_player", null),
        transformGet = { value -> value?.let { PlayerId(it) } },
        transformWrite = { value -> value?.id }
    )

    val fadeInDuration: Preference<Duration> get() = TransformingPreferenceImpl(
        intPreference("fade_in_duration", 0),
        transformGet = { value -> value.toDuration(DurationUnit.SECONDS) },
        transformWrite = { value -> value.inWholeSeconds.toInt() }
    )

    enum class DownloadFolderStructure(val prefValue: String) {
        AsOnServer("server"),
        Album("album"),
        Artist("artist"),
        AlbumUnderArtist("albumunderartist"),
        ArtistAlbum("artistalbum")
    }

    val downloadFolderStructure: Preference<DownloadFolderStructure> get() =
        TransformingPreferenceImpl(
            base = stringPreference(
                "download_path_structure",
                DownloadFolderStructure.AlbumUnderArtist.prefValue
            ),
            transformGet = { value -> DownloadFolderStructure.entries.first { value == it.prefValue } },
            transformWrite = { value -> value.prefValue }
        )

    val useVolumeButtonsForPlayerVolume: Preference<Boolean> get() =
        booleanPreference("use_volume_buttons", true)

    val volumeStepSize: Preference<Int> get() = intPreference("volume_step_size", 5)

    val localPlayerEnabled: Preference<Boolean> get() = booleanPreference("local_player_enabled", false)

    val localPlayerName: Preference<String?> get() = stringPreference("local_player_name", null)

    enum class LocalPlayerVolumeMode(val prefValue: String) {
        PlayerOnly("playeronly"),
        Device("device"),
        DeviceWhilePlaying("devicewhileplaying")
    }

    val localPlayerVolumeMode: Preference<LocalPlayerVolumeMode> get() = TransformingPreferenceImpl(
        stringPreference(
            "local_player_volume_mode",
            LocalPlayerVolumeMode.DeviceWhilePlaying.prefValue
        ),
        transformGet = { value -> LocalPlayerVolumeMode.entries.first { value == it.prefValue } },
        transformWrite = { value -> value.prefValue }
    )

    fun getOrCreateDeviceIdentifier(): UUID {
        val existing = prefs.getString("device_identifier", null)
        if (existing != null) {
            return UUID.fromString(existing)
        }
        val uuid = UUID.randomUUID()
        prefs.edit {
            putString("device_identifier", uuid.toString())
        }
        return uuid
    }

    private fun booleanPreference(key: String, defaultValue: Boolean) = PreferenceImpl(
        prefs,
        key,
        valueGetter = { getBoolean(key, defaultValue) },
        valueWriter = { value -> putBoolean(key, value) }
    )

    private fun intPreference(key: String, defaultValue: Int) = PreferenceImpl(
        prefs,
        key,
        valueGetter = { getInt(key, defaultValue) },
        valueWriter = { value -> putInt(key, value) }
    )

    private fun stringPreference(key: String, defaultValue: String?) = PreferenceImpl(
        prefs,
        key,
        valueGetter = { getString(key, defaultValue) },
        valueWriter = { value -> putString(key, value) }
    )

    interface Preference<T> {
        val value: T
        fun asFlow(): Flow<T>
        suspend fun update(newValue: T)
    }

    private class PreferenceImpl<T>(
        private val prefs: SharedPreferences,
        private val key: String,
        private val valueGetter: SharedPreferences.() -> T,
        private val valueWriter: SharedPreferences.Editor.(T) -> Unit
    ) : Preference<T> {
        override val value get() = valueGetter(prefs)

        override fun asFlow() = callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (key == changedKey) {
                    trySend(valueGetter(prefs))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            send(valueGetter(prefs))
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

        override suspend fun update(newValue: T) {
            withContext(Dispatchers.IO) {
                prefs.edit {
                    valueWriter(newValue)
                }
            }
        }
    }

    private class TransformingPreferenceImpl<T, R>(
        private val base: Preference<T>,
        private val transformGet: (T) -> (R),
        private val transformWrite: (R) -> (T)
    ) : Preference<R> {
        override val value get() = transformGet(base.value)

        override fun asFlow(): Flow<R> = base.asFlow().map { transformGet(it) }

        override suspend fun update(newValue: R) {
            base.update(transformWrite(newValue))
        }
    }
}