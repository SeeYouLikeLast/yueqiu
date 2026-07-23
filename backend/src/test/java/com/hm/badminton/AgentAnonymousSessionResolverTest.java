package com.hm.badminton;

import com.hm.badminton.config.AgentProperties;
import com.hm.badminton.service.agent.impl.AgentAnonymousSessionResolver;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAnonymousSessionResolverTest {

    private final AgentAnonymousSessionResolver resolver = resolver();

    @Test
    void shouldIssueProtectedCookieForNewGuest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String anonymousId = resolver.resolve(request, response);

        assertThat(anonymousId).matches("[A-Za-z0-9_-]{20,64}");
        assertThat(response.getHeader("Set-Cookie"))
                .contains(AgentAnonymousSessionResolver.COOKIE_NAME + "=" + anonymousId)
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/");
    }

    @Test
    void shouldReuseExistingGuestIdentity() {
        String existing = "guest-session-123456789012345";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AgentAnonymousSessionResolver.COOKIE_NAME, existing));
        MockHttpServletResponse response = new MockHttpServletResponse();

        String anonymousId = resolver.resolve(request, response);

        assertThat(anonymousId).isEqualTo(existing);
        assertThat(response.getHeader("Set-Cookie")).contains(existing);
    }

    private AgentAnonymousSessionResolver resolver() {
        AgentProperties properties = new AgentProperties();
        properties.setAnonymousHistoryTtlDays(7);
        return new AgentAnonymousSessionResolver(properties);
    }
}
