package com.roadstory.common.config;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSyncAccountConfigurationTest {
    private final LocalSyncAccountConfiguration configuration = new LocalSyncAccountConfiguration();

    @Test
    void independentTokensMapToSeparateAccounts() {
        String firstToken = token();
        String secondToken = token();
        var authenticator = configuration.localSyncAuthenticator(environment(firstToken, secondToken));

        assertEquals("dev-account-a", authenticator.authenticate(firstToken).orElseThrow());
        assertEquals("dev-account-b", authenticator.authenticate(secondToken).orElseThrow());
        assertTrue(authenticator.authenticate(token()).isEmpty());
        assertTrue(authenticator.authenticate("dev-account-a").isEmpty());
    }

    @Test
    void missingWeakAndDuplicateTokensPreventDevelopmentAuthentication() {
        String configured = token();
        assertThrows(IllegalStateException.class,
            () -> configuration.localSyncAuthenticator(environment(null, configured)));
        assertThrows(IllegalStateException.class,
            () -> configuration.localSyncAuthenticator(environment("f".repeat(64), configured)));
        var exception = assertThrows(IllegalStateException.class,
            () -> configuration.localSyncAuthenticator(environment(configured, configured)));
        assertFalse(exception.getMessage().contains(configured));
    }

    @Test
    void mixedProfilesPreventDevelopmentAuthentication() {
        MockEnvironment environment = environment(token(), token());
        environment.setActiveProfiles("local-sync", "production");
        assertThrows(IllegalStateException.class, () -> configuration.localSyncAuthenticator(environment));
    }

    private static MockEnvironment environment(String firstToken, String secondToken) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-sync");
        if (firstToken != null) {
            environment.setProperty("ROAD_STORY_DEV_ACCOUNT_A_TOKEN", firstToken);
        }
        environment.setProperty("ROAD_STORY_DEV_ACCOUNT_B_TOKEN", secondToken);
        return environment;
    }

    private static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
