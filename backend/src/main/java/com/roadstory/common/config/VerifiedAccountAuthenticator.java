package com.roadstory.common.config;

import java.util.Optional;

public interface VerifiedAccountAuthenticator {
    /** Returns the stable server account ID only after verifying the supplied credential. */
    Optional<String> authenticate(String bearerToken);
}
