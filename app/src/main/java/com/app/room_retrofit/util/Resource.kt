package com.app.room_retrofit.util

sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null, val isOffline: Boolean = false) : Resource<T>(data, message)
}
