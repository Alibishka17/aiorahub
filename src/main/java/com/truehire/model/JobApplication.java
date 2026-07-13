package com.truehire.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long vacancyId;

    private Long candidateId;

    private String guestFirstName;

    private String guestLastName;

    private String guestEmail;

    private String guestPhone;

    @Column(nullable = false)
    private boolean guestTelegramEnabled;

    @Column(nullable = false)
    private boolean guestWhatsappEnabled;

    @Column(unique = true, length = 64)
    private String guestAccessToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    private LocalDateTime createdAt;

    @Column(length = 3000)
    private String recruiterMessage;

    private LocalDateTime confirmedAt;

    public JobApplication() {
    }

    public JobApplication(Long vacancyId, Long candidateId, ApplicationStatus status) {
        this.vacancyId = vacancyId;
        this.candidateId = candidateId;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public static JobApplication guest(Long vacancyId,
                                       String firstName,
                                       String lastName,
                                       String email,
                                       String phone,
                                       boolean telegramEnabled,
                                       boolean whatsappEnabled,
                                       String accessToken) {
        JobApplication application = new JobApplication();
        application.setVacancyId(vacancyId);
        application.setStatus(ApplicationStatus.INTERVIEW_PENDING);
        application.setCreatedAt(LocalDateTime.now());
        application.setGuestFirstName(firstName);
        application.setGuestLastName(lastName);
        application.setGuestEmail(email);
        application.setGuestPhone(phone);
        application.setGuestTelegramEnabled(telegramEnabled);
        application.setGuestWhatsappEnabled(whatsappEnabled);
        application.setGuestAccessToken(accessToken);
        return application;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVacancyId() { return vacancyId; }
    public void setVacancyId(Long vacancyId) { this.vacancyId = vacancyId; }

    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }

    public String getGuestFirstName() { return guestFirstName; }
    public void setGuestFirstName(String guestFirstName) { this.guestFirstName = guestFirstName; }

    public String getGuestLastName() { return guestLastName; }
    public void setGuestLastName(String guestLastName) { this.guestLastName = guestLastName; }

    public String getGuestEmail() { return guestEmail; }
    public void setGuestEmail(String guestEmail) { this.guestEmail = guestEmail; }

    public String getGuestPhone() { return guestPhone; }
    public void setGuestPhone(String guestPhone) { this.guestPhone = guestPhone; }

    public boolean isGuestTelegramEnabled() { return guestTelegramEnabled; }
    public void setGuestTelegramEnabled(boolean guestTelegramEnabled) { this.guestTelegramEnabled = guestTelegramEnabled; }

    public boolean isGuestWhatsappEnabled() { return guestWhatsappEnabled; }
    public void setGuestWhatsappEnabled(boolean guestWhatsappEnabled) { this.guestWhatsappEnabled = guestWhatsappEnabled; }

    public String getGuestAccessToken() { return guestAccessToken; }
    public void setGuestAccessToken(String guestAccessToken) { this.guestAccessToken = guestAccessToken; }

    public boolean isGuest() { return candidateId == null; }

    public String getGuestName() {
        return ((guestFirstName == null ? "" : guestFirstName) + " "
                + (guestLastName == null ? "" : guestLastName)).trim();
    }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getRecruiterMessage() { return recruiterMessage; }
    public void setRecruiterMessage(String recruiterMessage) { this.recruiterMessage = recruiterMessage; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
