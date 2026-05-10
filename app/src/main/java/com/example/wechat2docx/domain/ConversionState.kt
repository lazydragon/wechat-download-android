package com.example.wechat2docx.domain

import android.content.Context
import com.example.wechat2docx.R

sealed class ConversionState {
    data object Idle : ConversionState()
    data class Fetching(val url: String) : ConversionState()
    data object Parsing : ConversionState()
    data class DownloadingImages(val done: Int, val total: Int) : ConversionState()
    data object BuildingDocx : ConversionState()
    data class Saving(val fileName: String) : ConversionState()
    data class Success(val fileName: String, val location: String) : ConversionState()
    data class Failure(val message: String) : ConversionState()

    companion object {
        /** 0..5 inclusive; used to drive the linear progress bar. */
        fun progressStep(s: ConversionState): Int = when (s) {
            is Idle -> 0
            is Fetching -> 1
            is Parsing -> 2
            is DownloadingImages -> 3
            is BuildingDocx -> 4
            is Saving -> 5
            is Success -> 5
            is Failure -> 0
        }

        fun label(ctx: Context, s: ConversionState): String = when (s) {
            is Idle -> ctx.getString(R.string.stage_idle)
            is Fetching -> ctx.getString(R.string.stage_fetching)
            is Parsing -> ctx.getString(R.string.stage_parsing)
            is DownloadingImages ->
                ctx.getString(R.string.stage_downloading_images, s.done, s.total)
            is BuildingDocx -> ctx.getString(R.string.stage_building_docx)
            is Saving -> ctx.getString(R.string.stage_saving, s.fileName)
            is Success -> ctx.getString(R.string.convert_success)
            is Failure -> ctx.getString(R.string.convert_failure)
        }
    }
}
