package com.example.roy.domain.map

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class LoadPoint(private val repository: PointRepository) {
    suspend operator fun invoke(pointId: String): PointResult<Point> {
        val result = repository.getPoint(pointId)
        currentCoroutineContext().ensureActive()
        return result
    }
}
