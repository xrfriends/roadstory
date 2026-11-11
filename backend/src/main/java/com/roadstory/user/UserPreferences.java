package com.roadstory.user;

import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record UserPreferences(@NotNull Map<String, @NotNull Frequency> categories,
                              @NotNull Boolean autoplay, boolean familyMode) {
    static final UserPreferences DEFAULT = new UserPreferences(Map.of(), true, false);

    public UserPreferences {
        if (categories != null) {
            categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
        }
    }

    public enum Frequency { FREQUENT, SOMETIMES, NEVER }
}
