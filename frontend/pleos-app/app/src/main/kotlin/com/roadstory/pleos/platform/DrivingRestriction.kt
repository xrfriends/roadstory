package com.roadstory.pleos.platform

data class DrivingRestriction(
    val distractionOptimizationRequired: Boolean,
    val activeRestrictions: Int,
) {
    val blocksDetailedContent: Boolean get() = distractionOptimizationRequired || activeRestrictions != 0

    companion object {
        val RESTRICTED = DrivingRestriction(true, 0)
    }
}

interface DrivingRestrictionSource : AutoCloseable {
    fun current(): DrivingRestriction
    fun observe(listener: (DrivingRestriction) -> Unit)
}

