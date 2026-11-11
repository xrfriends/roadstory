package com.roadstory.pleos.platform

import android.content.Context

/** One SDK subscription shared by the visible Activity and an active destination player. Main thread only. */
internal object NavigationSession {
    private var active: NavigationGateway? = null
    private var leases = 0

    fun acquire(context: Context): NavigationGateway {
        val gateway = active ?: NavigationGatewayFactory.create(context.applicationContext).also { active = it }
        leases++
        return object : NavigationGateway by gateway {
            private var closed = false

            override fun close() {
                if (closed) return
                closed = true
                if (--leases == 0) {
                    active = null
                    gateway.close()
                }
            }
        }
    }
}
