package com.carstensen.parcelcam.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val requiredPhotos = intPreferencesKey("required_photos")
        val saveToGallery = booleanPreferencesKey("save_to_gallery")
        val deleteLocal = booleanPreferencesKey("delete_local")

        val jpegQuality = intPreferencesKey("jpeg_quality")
        val maxResolution = stringPreferencesKey("max_resolution")

        val method = stringPreferencesKey("method")
        val server = stringPreferencesKey("server")
        val share = stringPreferencesKey("share")
        val remotePath = stringPreferencesKey("remote_path")
        val domain = stringPreferencesKey("domain")
        val username = stringPreferencesKey("username")
        val password = stringPreferencesKey("password")
        val port = intPreferencesKey("port")
        val lisIntentUriTemplate = stringPreferencesKey("lis_intent_uri_template")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            requiredPhotos = p[Keys.requiredPhotos] ?: 5,
            saveToGallery = p[Keys.saveToGallery] ?: false,
            deleteLocalAfterUpload = p[Keys.deleteLocal] ?: true,
            jpegQuality = p[Keys.jpegQuality] ?: 80,
            maxResolution = MaxResolution.fromLabel(p[Keys.maxResolution] ?: "2048"),
            // Default to FTP for easier first-time setup; SMB on Android often fails due to auth/policy.
            method = runCatching { UploadMethod.valueOf(p[Keys.method] ?: "FTP") }.getOrDefault(UploadMethod.FTP),
            server = p[Keys.server] ?: "",
            share = p[Keys.share] ?: "",
            remotePath = p[Keys.remotePath] ?: "",
            domain = p[Keys.domain] ?: "",
            username = p[Keys.username] ?: "",
            password = p[Keys.password] ?: "",
            port = p[Keys.port] ?: 0,
            lisIntentUriTemplate = p[Keys.lisIntentUriTemplate] ?: DEFAULT_LIS_INTENT_URI_TEMPLATE
        )
    }

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.requiredPhotos] = s.requiredPhotos
            p[Keys.saveToGallery] = s.saveToGallery
            p[Keys.deleteLocal] = s.deleteLocalAfterUpload
            p[Keys.jpegQuality] = s.jpegQuality.coerceIn(1, 100)
            p[Keys.maxResolution] = MaxResolution.toLabel(s.maxResolution)
            p[Keys.method] = s.method.name
            p[Keys.server] = s.server
            p[Keys.share] = s.share
            p[Keys.remotePath] = s.remotePath
            p[Keys.domain] = s.domain
            p[Keys.username] = s.username
            p[Keys.password] = s.password
            p[Keys.port] = s.port
            p[Keys.lisIntentUriTemplate] = s.lisIntentUriTemplate
        }
    }
}
