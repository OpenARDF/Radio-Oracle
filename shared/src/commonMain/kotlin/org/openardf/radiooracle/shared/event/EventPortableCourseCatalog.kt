package org.openardf.radiooracle.shared.event

import kotlinx.serialization.Serializable

/** Shared fields preserved by storage adapters which predate the portable control catalog. */
@Serializable
data class EventPortableCourseCatalog(
    val version: Int = 1,
    val controls: List<EventControl>,
    val courseMappings: List<EventCategoryData>,
    val courseDraft: EventCourseDraft? = null
)
