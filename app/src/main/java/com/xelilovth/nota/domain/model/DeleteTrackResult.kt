package com.xelilovth.nota.domain.model

import android.content.IntentSender

sealed interface DeleteTrackResult {
    data object Deleted : DeleteTrackResult
    data class RequiresPermission(val intentSender: IntentSender) : DeleteTrackResult
    data object Failed : DeleteTrackResult
}
