package com.roadstory.common.config;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    ObjectProvider<VerifiedAccountAuthenticator> authenticators) throws Exception {
        var accountPaths = PathPatternRequestMatcher.withDefaults().matcher("/v1/users/me/**");
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests.requestMatchers(accountPaths).authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getOutputStream().write("""
                    {"type":"about:blank","title":"Unauthorized","status":401,
                    "detail":"로그인이 필요합니다.","code":"AUTHENTICATION_REQUIRED"}
                    """.getBytes(StandardCharsets.UTF_8));
            }));
        VerifiedAccountAuthenticator authenticator = authenticators.getIfAvailable();
        if (authenticator != null) {
            http.addFilterBefore(new AccountBearerFilter(accountPaths, authenticator),
                AnonymousAuthenticationFilter.class);
        }
        return http.build();
    }
}
