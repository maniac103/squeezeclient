package de.maniac103.squeezeclient

import android.content.SharedPreferences
import androidx.core.content.edit
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.ServerConfiguration
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

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
            override val value = mapper(
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
        }
    }

    val lastSelectedPlayer: Preference<PlayerId?> get() =
        stringPreference("active_player", null)
            .map { id -> id?.let { PlayerId(it) } }

    val fadeInDuration: Preference<Duration> get() =
        intPreference("fade_in_duration", 0)
            .map { it.toDuration(DurationUnit.SECONDS) }

    enum class DownloadFolderStructure(val prefValue: String) {
        AsOnServer("server"),
        Album("album"),
        Artist("artist"),
        AlbumUnderArtist("albumunderartist"),
        ArtistAlbum("artistalbum")
    }

    val downloadFolderStructure: Preference<DownloadFolderStructure> get() =
        stringPreference(
            "download_path_structure",
            DownloadFolderStructure.AlbumUnderArtist.prefValue
        )
            .map { value -> DownloadFolderStructure.entries.first { value == it.prefValue } }

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

    val localPlayerVolumeMode: Preference<LocalPlayerVolumeMode> get() =
        stringPreference(
            "local_player_volume_mode",
            LocalPlayerVolumeMode.DeviceWhilePlaying.prefValue
        )
            .map { value -> LocalPlayerVolumeMode.entries.first { value == it.prefValue } }

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

    private fun booleanPreference(key: String, defaultValue: Boolean) =
        PreferenceImpl(prefs, key) { getBoolean(key, defaultValue) }

    private fun intPreference(key: String, defaultValue: Int) =
        PreferenceImpl(prefs, key) { getInt(key, defaultValue) }

    private fun stringPreference(key: String, defaultValue: String?) =
        PreferenceImpl(prefs, key) { getString(key, defaultValue) }

    interface Preference<T> {
        val value: T
        fun asFlow(): Flow<T>
    }

    private class PreferenceImpl<T>(
        private val prefs: SharedPreferences,
        private val key: String,
        private val valueGetter: SharedPreferences.() -> T
    ) : Preference<T> {
        override val value get() = valueGetter(prefs)

        override fun asFlow() = callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (key == changedKey) {
                    trySend(valueGetter(prefs))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            if (prefs.contains(key)) {
                send(valueGetter(prefs))
            }
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

        fun <R> map(transform: (T) -> R): PreferenceImpl<R> = PreferenceImpl(prefs, key) {
            transform(valueGetter())
        }
    }
}