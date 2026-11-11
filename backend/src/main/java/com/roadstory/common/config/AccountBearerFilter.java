package com.roadstory.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

final class AccountBearerFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_HEADER_LENGTH = 8192;
    private final RequestMatcher accountPaths;
    private final VerifiedAccountAuthenticator authenticator;

    AccountBearerFilter(RequestMatcher accountPaths, VerifiedAccountAuthenticator authenticator) {
        this.accountPaths = accountPaths;
        this.authenticator = authenticator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !accountPaths.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        var headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (headers.hasMoreElements()) {
            String header = headers.nextElement();
            if (!headers.hasMoreElements() && header.length() > BEARER_PREFIX.length()
                && header.length() <= MAX_HEADER_LENGTH
                && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                authenticator.authenticate(header.substring(BEARER_PREFIX.length())).ifPresent(accountId -> {
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(accountId, null, List.of()));
                    SecurityContextHolder.setContext(context);
                });
            }
        }
        chain.doFilter(request, response);
    }
}
