package com.roadstory.pleos.platform

import android.content.Context

object PlatformRuntimeFactory {
    fun create(context: Context): PleosRuntimeGateway = FakeRuntimeGateway
}

private object FakeRuntimeGateway : PleosRuntimeGateway {
    override fun initializeVehicleCapabilityCheck() = Unit
    override fun initializeNavigationEvents() = Unit
    override fun close() = Unit
}

