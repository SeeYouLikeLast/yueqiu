package com.hm.badminton.service.auth.impl;

import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.service.auth.IJwtService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class JwtService implements IJwtService {

    private final byte[] secret;
    private final long expireSeconds;

    public JwtService(@Value("${hm.jwt.secret}") String secret,
                      @Value("${hm.jwt.expire-minutes}") long expireMinutes) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expireSeconds = expireMinutes * 60;
    }

    public String createToken(LoginUser user) {
        long expiresAt = Instant.now().getEpochSecond() + expireSeconds;
        String payload = user.getId() + ":" + nullToEmpty(user.getPhone()) + ":" + expiresAt;
        String encodedPayload = base64(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public Optional<TokenPayload> parse(String token) {
        if (token == null || token.isBlank() || !token.contains(".")) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", 2);
        if (!sign(parts[0]).equals(parts[1])) {
            return Optional.empty();
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] values = payload.split(":", 3);
        if (values.length != 3) {
            return Optional.empty();
        }
        long expiresAt = Long.parseLong(values[2]);
        if (expiresAt < Instant.now().getEpochSecond()) {
            return Optional.empty();
        }
        return Optional.of(new TokenPayload(Long.parseLong(values[0]), values[1], expiresAt));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return base64(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign token", e);
        }
    }

    private String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenPayload {
        private Long userId;
        private String phone;
        private long expiresAt;
    }
}



