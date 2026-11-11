package com.roadstory.pleos.platform

import com.roadstory.pleos.platform.DrivingRestriction.Companion.RESTRICTED

/** Keeps startup and platform failures restricted until a platform observation succeeds. */
class FailClosedDrivingRestrictionSource(
    private val sourceFactory: () -> DrivingRestrictionSource,
) : DrivingRestrictionSource {
    private val lock = Any()
    private var source: DrivingRestrictionSource? = null
    private var listener: ((DrivingRestriction) -> Unit)? = null
    private var generation = 0L
    private var closed = false
    private var failed = false
    private var value = RESTRICTED

    override fun current(): DrivingRestriction {
        val delegate = try {
            sourceOrNull() ?: return RESTRICTED
        } catch (_: RuntimeException) {
            failClosed()
            return RESTRICTED
        }
        val observed = try {
            delegate.current()
        } catch (_: RuntimeException) {
            failClosed()
            return RESTRICTED
        }
        return synchronized(lock) {
            if (closed || failed) RESTRICTED else observed.also { value = it }
        }
    }

    override fun observe(listener: (DrivingRestriction) -> Unit) {
        val token = synchronized(lock) {
            if (closed) return
            generation++
            val observationGeneration = generation
            this.listener = listener
            value = RESTRICTED
            // Render a safe initial frame even when platform creation/registration is delayed.
            listener(RESTRICTED)
            observationGeneration
        }
        var registering = true
        var pending: DrivingRestriction? = null
        val initial: DrivingRestriction
        try {
            val delegate = sourceOrNull() ?: return
            initial = delegate.current()
            delegate.observe platformCallback@{ updated ->
                synchronized(lock) {
                    if (!isActive(token)) return@platformCallback
                    if (registering) {
                        pending = updated
                    } else {
                        value = updated
                        this.listener?.invoke(updated)
                    }
                }
            }
        } catch (_: RuntimeException) {
            failClosed(token)
            return
        }
        synchronized(lock) {
            if (!isActive(token)) return
            registering = false
            value = pending ?: initial
            this.listener?.invoke(value)
        }
    }

    private fun sourceOrNull(): DrivingRestrictionSource? = synchronized(lock) {
        if (closed || failed) null else source ?: sourceFactory().also { source = it }
    }

    private fun isActive(token: Long) = !closed && !failed && token == generation

    private fun failClosed(expectedGeneration: Long? = null) {
        val toClose = synchronized(lock) {
            if (closed || failed || (expectedGeneration != null && expectedGeneration != generation)) return
            failed = true
            generation++
            value = RESTRICTED
            val delegate = source
            source = null
            listener?.invoke(RESTRICTED)
            delegate
        }
        closeSafely(toClose)
    }

    override fun close() {
        val toClose = synchronized(lock) {
            if (closed) return
            closed = true
            generation++
            listener = null
            value = RESTRICTED
            source.also { source = null }
        }
        closeSafely(toClose)
    }

    private fun closeSafely(delegate: DrivingRestrictionSource?) {
        try {
            delegate?.close()
        } catch (_: RuntimeException) {
            // A failed disconnect must not prevent Activity teardown or reopen unsafe state.
        }
    }
}
