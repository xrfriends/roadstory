package com.roadstory.common.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("local-sync")
class LocalSyncAccountConfiguration {
    private static final Pattern HEX_TOKEN = Pattern.compile("[0-9a-f]{64}");
    private static final String FIRST_TOKEN = "ROAD_STORY_DEV_ACCOUNT_A_TOKEN";
    private static final String SECOND_TOKEN = "ROAD_STORY_DEV_ACCOUNT_B_TOKEN";

    @Bean
    VerifiedAccountAuthenticator localSyncAuthenticator(Environment environment) {
        if (!Arrays.equals(environment.getActiveProfiles(), new String[]{"local-sync"})) {
            throw new IllegalStateException("Use local-sync as the only active profile for development authentication.");
        }
        byte[] first = configuredTokenDigest(environment, FIRST_TOKEN);
        byte[] second = configuredTokenDigest(environment, SECOND_TOKEN);
        if (MessageDigest.isEqual(first, second)) {
            throw new IllegalStateException("The two local-sync bearer tokens must be different.");
        }
        return token -> {
            if (!HEX_TOKEN.matcher(token).matches()) {
                return Optional.empty();
            }
            byte[] candidate = digest(token);
            boolean firstMatches = MessageDigest.isEqual(candidate, first);
            boolean secondMatches = MessageDigest.isEqual(candidate, second);
            if (firstMatches) {
                return Optional.of("dev-account-a");
            }
            return secondMatches ? Optional.of("dev-account-b") : Optional.empty();
        };
    }

    private static byte[] configuredTokenDigest(Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || !HEX_TOKEN.matcher(value).matches() || value.chars().distinct().count() < 12) {
            throw new IllegalStateException(name + " requires an independent 256-bit token generated with openssl rand -hex 32.");
        }
        return digest(value);
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
