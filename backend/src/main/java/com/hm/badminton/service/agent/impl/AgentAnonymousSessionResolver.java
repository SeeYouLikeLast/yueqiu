package com.hm.badminton.service.agent.impl;

import com.hm.badminton.config.AgentProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** Issues an unguessable, HttpOnly browser identity for anonymous AI history. */
@Component
public class AgentAnonymousSessionResolver {

    public static final String COOKIE_NAME = "hm_agent_guest";

    private final Duration cookieTtl;

    public AgentAnonymousSessionResolver(AgentProperties properties) {
        this.cookieTtl = Duration.ofDays(Math.max(1, properties.getAnonymousHistoryTtlDays()));
    }

    public String resolve(HttpServletRequest request, HttpServletResponse response) {
        String anonymousId = findCookie(request);
        if (anonymousId == null) {
            anonymousId = UUID.randomUUID().toString().replace("-", "");
        }
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, anonymousId)
                .httpOnly(true)
                .secure(isHttps(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(cookieTtl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return anonymousId;
    }

    private String findCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())
                    && cookie.getValue() != null
                    && cookie.getValue().matches("[A-Za-z0-9_-]{20,64}")) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean isHttps(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && forwardedProto.equalsIgnoreCase("https");
    }
}
