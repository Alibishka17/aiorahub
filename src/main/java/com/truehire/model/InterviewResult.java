package com.truehire.model;

import jakarta.persistence.*;

@Entity
public class InterviewResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long applicationId;

    private String aiLanguageScore;

    @Column(length = 2000)
    private String aiSoftSkillsComment;

    private String interviewRecordUrl;

    private boolean isPassed;

    public InterviewResult() {
    }

    public InterviewResult(Long applicationId, String aiLanguageScore,
                           String aiSoftSkillsComment, String interviewRecordUrl, boolean isPassed) {
        this.applicationId = applicationId;
        this.aiLanguageScore = aiLanguageScore;
        this.aiSoftSkillsComment = aiSoftSkillsComment;
        this.interviewRecordUrl = interviewRecordUrl;
        this.isPassed = isPassed;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public String getAiLanguageScore() { return aiLanguageScore; }
    public void setAiLanguageScore(String aiLanguageScore) { this.aiLanguageScore = aiLanguageScore; }

    public String getAiSoftSkillsComment() { return aiSoftSkillsComment; }
    public void setAiSoftSkillsComment(String aiSoftSkillsComment) { this.aiSoftSkillsComment = aiSoftSkillsComment; }

    public String getInterviewRecordUrl() { return interviewRecordUrl; }
    public void setInterviewRecordUrl(String interviewRecordUrl) { this.interviewRecordUrl = interviewRecordUrl; }

    public boolean isPassed() { return isPassed; }
    public void setPassed(boolean passed) { isPassed = passed; }
}
