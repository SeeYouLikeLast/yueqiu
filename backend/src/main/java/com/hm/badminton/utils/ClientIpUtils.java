package com.hm.badminton.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 解析经 Nginx 转发后的客户端地址。
 * 应用只监听 127.0.0.1，因此 X-Real-IP / X-Forwarded-For 由受信任的本机 Nginx 覆盖写入。
 */
public final class ClientIpUtils {

    private ClientIpUtils() {
    }

    public static String resolve(HttpServletRequest request) {
        String ip = firstAddress(request.getHeader("X-Real-IP"));
        if (ip == null) {
            ip = firstAddress(request.getHeader("X-Forwarded-For"));
        }
        if (ip == null || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return normalize(ip);
    }

    private static String firstAddress(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String address = header.split(",", 2)[0].trim();
        return address.isBlank() ? null : address;
    }

    private static String normalize(String ip) {
        if (ip == null || ip.isBlank()) {
            return "";
        }
        if (ip.startsWith("::ffff:")) {
            return ip.substring("::ffff:".length());
        }
        return ip;
    }
}
