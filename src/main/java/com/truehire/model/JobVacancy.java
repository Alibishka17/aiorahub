package com.truehire.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class JobVacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 3000)
    private String description;

    @Column(name = "work_conditions", length = 2000)
    private String conditions;

    @Column(length = 2000)
    private String candidateRequirements;

    @Column(nullable = false)
    private Long employerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VacancyStatus status = VacancyStatus.PUBLISHED;

    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false, length = 100)
    private String interviewTemplateId = "hrme-warsaw";

    @Column(length = 120)
    private String category;

    @Column(length = 120)
    private String city;

    @Column(length = 120)
    private String country;

    private Long salaryMin;

    private Long salaryMax;

    @Column(length = 10)
    private String salaryCurrency;

    @Column(length = 3000)
    private String requiredDocuments;

    @Column(length = 3000)
    private String additionalInfo;

    @Column(nullable = false)
    private long viewCount;

    public JobVacancy() {
    }

    public JobVacancy(String title, String description, String conditions,
                      String candidateRequirements, Long employerId) {
        this.title = title;
        this.description = description;
        this.conditions = conditions;
        this.candidateRequirements = candidateRequirements;
        this.employerId = employerId;
        this.status = VacancyStatus.PUBLISHED;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }

    public String getCandidateRequirements() { return candidateRequirements; }
    public void setCandidateRequirements(String candidateRequirements) { this.candidateRequirements = candidateRequirements; }

    public Long getEmployerId() { return employerId; }
    public void setEmployerId(Long employerId) { this.employerId = employerId; }

    public VacancyStatus getStatus() { return status; }
    public void setStatus(VacancyStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getInterviewTemplateId() { return interviewTemplateId; }
    public void setInterviewTemplateId(String interviewTemplateId) { this.interviewTemplateId = interviewTemplateId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Long getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Long salaryMin) { this.salaryMin = salaryMin; }

    public Long getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Long salaryMax) { this.salaryMax = salaryMax; }

    public String getSalaryCurrency() { return salaryCurrency; }
    public void setSalaryCurrency(String salaryCurrency) { this.salaryCurrency = salaryCurrency; }

    public String getRequiredDocuments() { return requiredDocuments; }
    public void setRequiredDocuments(String requiredDocuments) { this.requiredDocuments = requiredDocuments; }

    public String getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(String additionalInfo) { this.additionalInfo = additionalInfo; }

    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
}
