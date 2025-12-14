package org.shotrush.atom.util

import org.bukkit.NamespacedKey
import net.momirealms.craftengine.core.util.Key as CEKey

data class Key(val namespace: String, val key: String) {
    override fun toString(): String {
        return "$namespace:$key"
    }

    fun toCEKey() = CEKey(namespace, key)
    fun toBukkitKey() = NamespacedKey(namespace, key)

    companion object {
        fun of(string: String): Key {
            val parts = string.split(":")
            if (parts.size == 1) return Key("atom", parts[0])
            return Key(parts[0], parts[1])
        }
    }
}

fun CEKey.asAtomKey(): Key = Key(namespace, value)