package com.example.wechat2docx.ui.screens.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wechat2docx.App
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = (app as App).settings

    val lastResult: StateFlow<String?> = settings.lastResult
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val treeUri: StateFlow<String?> = settings.treeUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lastSavedUri: StateFlow<Uri?> = settings.lastSavedUri
        .map { s -> s?.let { runCatching { Uri.parse(it) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
