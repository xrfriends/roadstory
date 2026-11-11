package com.roadstory.pleos.platform

/** 공식 SDK에서 문서화된 initialize/release 수명주기만 노출한다. */
interface PleosRuntimeGateway : AutoCloseable {
    fun initializeVehicleCapabilityCheck()
    fun initializeNavigationEvents()
}

