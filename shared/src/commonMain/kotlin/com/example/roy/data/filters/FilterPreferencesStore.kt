package com.example.roy.data.filters

import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.map.PointFilterStore
import kotlinx.serialization.json.Json

internal class FilterPreferencesStore(
    private val readValue: () -> String?,
    private val writeValue: (String) -> Unit,
) : PointFilterStore {
    override fun read(): PointFilter {
        val value = readValue() ?: return PointFilter()
        return try {
            val filter = Json.decodeFromString<PointFilter>(value)
            val min = filter.peopleMin ?: 0
            val max = filter.peopleMax ?: 50
            if (min in 0..max && max <= 50 &&
                (filter.customTopicLabel == null || filter.topicKey == "other")
            ) filter else PointFilter()
        } catch (_: IllegalArgumentException) {
            PointFilter()
        }
    }

    override fun save(filter: PointFilter) {
        writeValue(Json.encodeToString(filter))
    }
}
