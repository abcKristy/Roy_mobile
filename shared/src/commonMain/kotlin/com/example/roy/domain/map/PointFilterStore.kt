package com.example.roy.domain.map

internal interface PointFilterStore {
    fun read(): PointFilter
    fun save(filter: PointFilter)
}
