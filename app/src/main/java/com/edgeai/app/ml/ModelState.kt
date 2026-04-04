package com.edgeai.app.ml

sealed interface ModelState {
    data object NotLoaded : ModelState
    data class Downloading(val progress: Float) : ModelState
    data object Loading : ModelState
    data object Ready : ModelState
    data class Error(val message: String, val cause: Throwable? = null) : ModelState
}
