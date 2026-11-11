package com.roadstory.pleos.platform

import org.junit.Assert.*
import org.junit.Test

class FailClosedDrivingRestrictionSourceTest {
    private val restricted = DrivingRestriction(true, 0)
    private val parked = DrivingRestriction(false, 0)

    @Test fun `safe initial frame arrives before platform factory and delayed callback`() {
        val events = mutableListOf<DrivingRestriction>()
        val platform = TestSource(currentValue = restricted)
        val source = FailClosedDrivingRestrictionSource {
            assertEquals(listOf(restricted), events)
            platform
        }

        source.observe(events::add)
        assertTrue(events.all { it.blocksDetailedContent })
        platform.emit(parked)
        assertEquals(parked, events.last())
        assertEquals(parked, source.current())
    }

    @Test fun `factory failure remains restricted and does not retry failed source`() {
        var attempts = 0
        val source = FailClosedDrivingRestrictionSource {
            attempts++
            throw IllegalStateException("unavailable")
        }
        val events = mutableListOf<DrivingRestriction>()

        source.observe(events::add)
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.blocksDetailedContent })
        assertEquals(restricted, source.current())
        source.observe(events::add)
        assertEquals(1, attempts)
    }

    @Test fun `current read failure restores restriction and detaches platform`() {
        val platform = TestSource(currentValue = parked)
        val source = FailClosedDrivingRestrictionSource { platform }
        val events = mutableListOf<DrivingRestriction>()
        source.observe(events::add)
        assertEquals(parked, events.last())

        platform.failCurrent = true
        assertEquals(restricted, source.current())
        assertEquals(restricted, events.last())
        assertEquals(1, platform.closeCalls)
        val count = events.size
        platform.emit(parked)
        assertEquals(count, events.size)
    }

    @Test fun `initial current read failure never registers observation`() {
        val platform = TestSource().apply { failCurrent = true }
        val source = FailClosedDrivingRestrictionSource { platform }
        val events = mutableListOf<DrivingRestriction>()

        source.observe(events::add)
        assertTrue(events.all { it.blocksDetailedContent })
        assertEquals(0, platform.observeCalls)
        assertEquals(1, platform.closeCalls)
    }

    @Test fun `unrestricted callback followed by registration failure is never published`() {
        val platform = TestSource(currentValue = parked).apply {
            onObserve = { callback ->
                callback(parked)
                throw IllegalStateException("registration failed")
            }
        }
        val source = FailClosedDrivingRestrictionSource { platform }
        val events = mutableListOf<DrivingRestriction>()

        source.observe(events::add)
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.blocksDetailedContent })
        assertEquals(restricted, source.current())
        assertEquals(1, platform.closeCalls)
        val count = events.size
        platform.emit(parked)
        assertEquals(count, events.size)
    }

    @Test fun `close ignores late callbacks and tolerates disconnect failure`() {
        val platform = TestSource().apply { failClose = true }
        val source = FailClosedDrivingRestrictionSource { platform }
        val events = mutableListOf<DrivingRestriction>()
        source.observe(events::add)

        source.close()
        source.close()
        val count = events.size
        platform.emit(parked)
        source.observe(events::add)
        assertEquals(count, events.size)
        assertEquals(restricted, source.current())
        assertEquals(1, platform.closeCalls)
    }

    @Test fun `closing from initial callback does not initialize platform`() {
        var initialized = false
        val source = FailClosedDrivingRestrictionSource {
            initialized = true
            TestSource()
        }

        source.observe { source.close() }
        assertFalse(initialized)
        assertEquals(restricted, source.current())
    }

    private class TestSource(
        var currentValue: DrivingRestriction = DrivingRestriction(true, 0),
    ) : DrivingRestrictionSource {
        var failCurrent = false
        var failClose = false
        var observeCalls = 0
        var closeCalls = 0
        var onObserve: ((DrivingRestriction) -> Unit) -> Unit = {}
        private var listener: ((DrivingRestriction) -> Unit)? = null

        override fun current(): DrivingRestriction {
            if (failCurrent) throw IllegalStateException("current unavailable")
            return currentValue
        }

        override fun observe(listener: (DrivingRestriction) -> Unit) {
            observeCalls++
            this.listener = listener
            onObserve(listener)
        }

        fun emit(value: DrivingRestriction) {
            currentValue = value
            listener?.invoke(value)
        }

        override fun close() {
            closeCalls++
            if (failClose) throw IllegalStateException("disconnect failed")
            // Keep the callback so tests can model an event already queued before disconnect.
        }
    }
}
