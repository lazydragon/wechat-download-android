package com.example.wechat2docx.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val ctx: Context) {

    companion object {
        val TREE_URI = stringPreferencesKey("tree_uri")
        val EMBED_IMAGES = booleanPreferencesKey("embed_images")
        val LAST_RESULT = stringPreferencesKey("last_result")
        val LAST_SAVED_URI = stringPreferencesKey("last_saved_uri")
    }

    val treeUri: Flow<String?> = ctx.dataStore.data.map { it[TREE_URI] }
    val embedImages: Flow<Boolean> = ctx.dataStore.data.map { it[EMBED_IMAGES] ?: true }
    val lastResult: Flow<String?> = ctx.dataStore.data.map { it[LAST_RESULT] }
    val lastSavedUri: Flow<String?> = ctx.dataStore.data.map { it[LAST_SAVED_URI] }

    suspend fun setTreeUri(v: String) {
        ctx.dataStore.edit { it[TREE_URI] = v }
    }

    suspend fun setEmbedImages(v: Boolean) {
        ctx.dataStore.edit { it[EMBED_IMAGES] = v }
    }

    suspend fun setLastResult(v: String) {
        ctx.dataStore.edit { it[LAST_RESULT] = v }
    }

    suspend fun setLastSavedUri(v: String) {
        ctx.dataStore.edit { it[LAST_SAVED_URI] = v }
    }
}
