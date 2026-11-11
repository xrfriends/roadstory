package com.roadstory.collection.domain;

import java.net.URI;

public record DataSource(Long id, String sourceCode, String name, String provider, String sourceType,
                         URI baseUrl, String licenseType, boolean commercialUseAllowed,
                         boolean derivativeAllowed, boolean attributionRequired, boolean apiKeyRequired,
                         String updateCycle, boolean enabled) {
}
