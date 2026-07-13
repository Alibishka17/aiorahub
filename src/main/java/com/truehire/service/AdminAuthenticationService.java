package com.truehire.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthenticationService {

    private final String username;
    private final String passwordHash;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public AdminAuthenticationService(@Value("${app.admin.username}") String username,
                                      @Value("${app.admin.password-hash}") String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public boolean authenticate(String suppliedUsername, String suppliedPassword) {
        return passwordHash.startsWith("$2")
                && username.equals(suppliedUsername)
                && suppliedPassword != null
                && encoder.matches(suppliedPassword, passwordHash);
    }

    public boolean isConfigured() {
        return passwordHash.startsWith("$2");
    }
}
