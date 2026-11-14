package com.example.roy.domain.map

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class LoadViewport(private val repository: PointRepository) {
    suspend operator fun invoke(query: ViewportQuery): PointResult<List<Point>> {
        val pointsById = linkedMapOf<String, Point>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        do {
            currentCoroutineContext().ensureActive()
            val result = repository.getViewportPage(query, cursor)
            currentCoroutineContext().ensureActive()
            when (result) {
                is PointResult.Failure -> return result
                is PointResult.Success -> {
                    val page = result.value
                    page.items.forEach { pointsById[it.id] = it }
                    cursor = page.nextCursor
                    if (cursor != null && !seenCursors.add(cursor)) {
                        return PointResult.Failure(PointError.UnexpectedResponse)
                    }
                }
            }
        } while (cursor != null)
        return PointResult.Success(pointsById.values.toList())
    }
}
