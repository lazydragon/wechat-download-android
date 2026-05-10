package com.example.wechat2docx.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wechat2docx.App
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = (app as App).settings

    val treeUri: StateFlow<String?> = settings.treeUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val embedImages: StateFlow<Boolean> = settings.embedImages
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun saveTreeUri(uri: String) {
        viewModelScope.launch { settings.setTreeUri(uri) }
    }

    fun setEmbedImages(b: Boolean) {
        viewModelScope.launch { settings.setEmbedImages(b) }
    }
}
