package com.pulse.app.data.repo

import com.pulse.app.core.model.SourceId

class PriorityMerger(private val priority: List<SourceId>) {

    fun <K> merge(mapBySource: Map<SourceId, Map<K, *>>): List<Pair<K, Any?>> {
        val keys = linkedSetOf<K>()
        for ((_, m) in mapBySource) keys.addAll(m.keys)

        val out = mutableListOf<Pair<K, Any?>>()
        for (key in keys) {
            for (src in priority) {
                val v = mapBySource[src]?.get(key)
                if (v != null) {
                    out.add(key to v)
                    break
                }
            }
        }
        return out
    }

    fun orderedSources(): List<SourceId> =
        priority + SourceId.entries.filter { it !in priority }
}
