package org.openardf.radiooracle.backend.commands

import java.util.UUID

object AppCommandEventIds {
    fun parse(rawValue: String?): List<UUID>? {
        val tokens = rawValue
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (tokens.isEmpty()) {
            return null
        }
        return tokens.map { token ->
            runCatching { UUID.fromString(token) }.getOrNull() ?: return null
        }
    }
}
