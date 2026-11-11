package com.example.roy

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform