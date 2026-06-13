package com.truehire.model;

import jakarta.persistence.*;

@Entity
public class VisaDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long applicationId;

    @Column(nullable = false)
    private String documentType; // Паспорт, Диплом, Контракт

    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VisaStatus status;

    public VisaDocument() {
    }

    public VisaDocument(Long applicationId, String documentType, String fileUrl, VisaStatus status) {
        this.applicationId = applicationId;
        this.documentType = documentType;
        this.fileUrl = fileUrl;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public VisaStatus getStatus() { return status; }
    public void setStatus(VisaStatus status) { this.status = status; }
}
