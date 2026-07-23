package com.truehire.service;

import com.truehire.model.ApplicationStatus;
import com.truehire.model.JobApplication;
import com.truehire.model.JobVacancy;
import com.truehire.repository.JobApplicationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class InterviewLaunchService {

    private final JobApplicationRepository applicationRepository;
    private final HrmeInterviewClient hrmeClient;

    public InterviewLaunchService(JobApplicationRepository applicationRepository,
                                  HrmeInterviewClient hrmeClient) {
        this.applicationRepository = applicationRepository;
        this.hrmeClient = hrmeClient;
    }

    public synchronized String launch(JobApplication application, JobVacancy vacancy, String candidateEmail) {
        if (application.getExternalInterviewUrl() != null && !application.getExternalInterviewUrl().isBlank()) {
            return application.getExternalInterviewUrl();
        }
        HrmeInterviewClient.InterviewSession session = hrmeClient.createInterview(
                application.getId(), vacancy.getId(), candidateEmail, vacancy.getInterviewTemplateId(),
                application.getInterviewConfigSnapshot(), application.getSummaryLanguage());
        application.setExternalInterviewId(session.interviewId());
        application.setExternalInterviewToken(session.accessToken());
        application.setExternalInterviewUrl(session.interviewUrl());
        application.setInterviewStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        application.setStatus(ApplicationStatus.INTERVIEW_PENDING);
        applicationRepository.save(application);
        return session.interviewUrl();
    }
}
