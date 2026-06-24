package com.gemwallet.android.application

interface SecurityStore<T> {
    suspend fun contains(key: T): Boolean

    suspend fun getValue(key: T): String

    suspend fun putValue(key: T, value: String)
}