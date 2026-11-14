package com.example.roy.domain.auth

internal interface SessionStore {
    suspend fun read(): AuthSession?
    suspend fun replace(session: AuthSession)
    suspend fun clear()
}
