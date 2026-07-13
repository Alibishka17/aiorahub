package com.truehire.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class IntegrationAuthenticationService {

    private final byte[] expectedToken;

    public IntegrationAuthenticationService(@Value("${app.hrme.service-token}") String token) {
        this.expectedToken = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isAuthorized(String authorization) {
        if (expectedToken.length == 0 || authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        byte[] supplied = authorization.substring(7).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedToken, supplied);
    }
}
