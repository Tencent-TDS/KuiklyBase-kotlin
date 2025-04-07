// RUN_PIPELINE_TILL: FRONTEND
// DISABLE_NEXT_PHASE_SUGGESTION
// WITH_STDLIB

import kotlin.collections.Map

class HashMap<K, V> : Map<K, V> {
    override fun containsKey(key: K): Boolean = false
    override fun containsValue(value: V): Boolean = false
    override val size: Int get() = 0
    override fun isEmpty(): Boolean = true
    override fun get(key: K): V? = null

    override val keys: MutableSet<K> get() = mutableSetOf()
    override val values: MutableCollection<V> get() = mutableListOf()
    override val entries: MutableSet<Map.Entry<K, V>> get() = mutableSetOf()
}
