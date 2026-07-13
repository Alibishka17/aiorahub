package com.truehire.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class SiteVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sessionKey;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private LocalDateTime visitedAt;

    public SiteVisit() {}

    public SiteVisit(String sessionKey, String path, LocalDateTime visitedAt) {
        this.sessionKey = sessionKey;
        this.path = path;
        this.visitedAt = visitedAt;
    }

    public Long getId() { return id; }
    public String getSessionKey() { return sessionKey; }
    public String getPath() { return path; }
    public LocalDateTime getVisitedAt() { return visitedAt; }
}
