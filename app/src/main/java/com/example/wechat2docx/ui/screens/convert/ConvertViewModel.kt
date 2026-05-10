package com.example.wechat2docx.ui.screens.convert

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.wechat2docx.App
import com.example.wechat2docx.domain.ConversionState
import com.example.wechat2docx.domain.ConvertArticleUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ConvertViewModel(
    app: Application,
    private val url: String,
) : AndroidViewModel(app) {

    private val useCase: ConvertArticleUseCase = ConvertArticleUseCase(
        app = app,
        settings = (app as App).settings,
    )

    private val _state = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val state: StateFlow<ConversionState> = _state.asStateFlow()

    private var currentJob: Job? = null

    init {
        start()
    }

    fun retry() {
        start()
    }

    private fun start() {
        currentJob?.cancel()
        _state.value = ConversionState.Idle
        currentJob = viewModelScope.launch {
            useCase.run(url).collect { _state.value = it }
        }
    }

    class Factory(
        private val app: Application,
        private val url: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return ConvertViewModel(app, url) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConvertViewModel(app, url) as T
        }
    }
}
