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

    @Column(length = 16)
    private String interviewMode;

    @Column(length = 2)
    private String interviewLanguage;

    @Column(length = 2000)
    private String interviewIntro;

    @Column(length = 2000)
    private String interviewOutro;

    @Column(length = 10000)
    private String interviewCustomPrompt;

    @Column(length = 12000)
    private String interviewQuestionsJson;

    @Column(length = 12000)
    private String assessmentCriteriaJson;

    @Column(nullable = false)
    private boolean allowFollowUpQuestions;

    @Column(nullable = false)
    private boolean allowQuestionRephrasing;

    @Column(nullable = false)
    private boolean allowQuestionReordering;

    @Column(nullable = false)
    private boolean allowQuestionSkipping;

    @Column(nullable = false)
    private boolean allowVacancyQuestions;

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

    public String getInterviewMode() { return interviewMode; }
    public void setInterviewMode(String interviewMode) { this.interviewMode = interviewMode; }

    public String getInterviewLanguage() { return interviewLanguage; }
    public void setInterviewLanguage(String interviewLanguage) { this.interviewLanguage = interviewLanguage; }

    public String getInterviewIntro() { return interviewIntro; }
    public void setInterviewIntro(String interviewIntro) { this.interviewIntro = interviewIntro; }

    public String getInterviewOutro() { return interviewOutro; }
    public void setInterviewOutro(String interviewOutro) { this.interviewOutro = interviewOutro; }

    public String getInterviewCustomPrompt() { return interviewCustomPrompt; }
    public void setInterviewCustomPrompt(String interviewCustomPrompt) { this.interviewCustomPrompt = interviewCustomPrompt; }

    public String getInterviewQuestionsJson() { return interviewQuestionsJson; }
    public void setInterviewQuestionsJson(String interviewQuestionsJson) { this.interviewQuestionsJson = interviewQuestionsJson; }

    public String getAssessmentCriteriaJson() { return assessmentCriteriaJson; }
    public void setAssessmentCriteriaJson(String assessmentCriteriaJson) { this.assessmentCriteriaJson = assessmentCriteriaJson; }

    public boolean isAllowFollowUpQuestions() { return allowFollowUpQuestions; }
    public void setAllowFollowUpQuestions(boolean allowFollowUpQuestions) { this.allowFollowUpQuestions = allowFollowUpQuestions; }

    public boolean isAllowQuestionRephrasing() { return allowQuestionRephrasing; }
    public void setAllowQuestionRephrasing(boolean allowQuestionRephrasing) { this.allowQuestionRephrasing = allowQuestionRephrasing; }

    public boolean isAllowQuestionReordering() { return allowQuestionReordering; }
    public void setAllowQuestionReordering(boolean allowQuestionReordering) { this.allowQuestionReordering = allowQuestionReordering; }

    public boolean isAllowQuestionSkipping() { return allowQuestionSkipping; }
    public void setAllowQuestionSkipping(boolean allowQuestionSkipping) { this.allowQuestionSkipping = allowQuestionSkipping; }

    public boolean isAllowVacancyQuestions() { return allowVacancyQuestions; }
    public void setAllowVacancyQuestions(boolean allowVacancyQuestions) { this.allowVacancyQuestions = allowVacancyQuestions; }

    public boolean hasCustomInterviewConfiguration() {
        return interviewMode != null && interviewQuestionsJson != null && assessmentCriteriaJson != null;
    }

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
