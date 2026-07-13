package com.truehire.controller;

import com.truehire.model.*;
import com.truehire.repository.*;
import com.truehire.service.IntegrationAuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api/integrations/hrme")
public class IntegrationController {

    private final IntegrationAuthenticationService authentication;
    private final JobVacancyRepository vacancyRepository;
    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final InterviewResultRepository resultRepository;

    public IntegrationController(IntegrationAuthenticationService authentication,
                                 JobVacancyRepository vacancyRepository,
                                 JobApplicationRepository applicationRepository,
                                 UserRepository userRepository,
                                 InterviewResultRepository resultRepository) {
        this.authentication = authentication;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.resultRepository = resultRepository;
    }

    @GetMapping("/catalog")
    public ResponseEntity<?> catalog(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!authentication.isAuthorized(authorization)) return unauthorized();
        List<Map<String, Object>> vacancies = vacancyRepository.findAll().stream()
                .filter(vacancy -> vacancy.getStatus() != VacancyStatus.ARCHIVED)
                .sorted(Comparator.comparing(JobVacancy::getCreatedAt).reversed())
                .map(this::vacancyRow)
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("vacancies", vacancies)));
    }

    @PostMapping("/interviews/{applicationId}/complete")
    public ResponseEntity<?> complete(@PathVariable Long applicationId,
                                      @RequestHeader(value = "Authorization", required = false) String authorization,
                                      @RequestBody CompletionRequest request) {
        if (!authentication.isAuthorized(authorization)) return unauthorized();
        JobApplication application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null) return ResponseEntity.notFound().build();
        if (request.interview_id() == null || application.getExternalInterviewId() == null
                || !application.getExternalInterviewId().equals(request.interview_id())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", "Interview does not match application"));
        }

        InterviewResult result = resultRepository.findByApplicationId(applicationId)
                .orElseGet(() -> new InterviewResult(applicationId, null, null, null, false));
        result.setSummary(limit(request.summary(), 3000));
        result.setTranscript(limit(request.transcript(), 12000));
        result.setConclusion(limit(request.summary(), 3000));
        resultRepository.save(result);

        application.setStatus(ApplicationStatus.INTERVIEW_COMPLETED);
        application.setInterviewCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        applicationRepository.save(application);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private Map<String, Object> vacancyRow(JobVacancy vacancy) {
        List<Map<String, Object>> applications = applicationRepository.findByVacancyId(vacancy.getId()).stream()
                .sorted(Comparator.comparing(JobApplication::getCreatedAt).reversed())
                .map(this::applicationRow)
                .toList();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", vacancy.getId());
        row.put("title", vacancy.getTitle());
        row.put("status", vacancy.getStatus().name());
        row.put("template_id", vacancy.getInterviewTemplateId());
        row.put("applications", applications);
        return row;
    }

    private Map<String, Object> applicationRow(JobApplication application) {
        User candidate = application.getCandidateId() == null ? null
                : userRepository.findById(application.getCandidateId()).orElse(null);
        InterviewResult result = resultRepository.findByApplicationId(application.getId()).orElse(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", application.getId());
        row.put("name", candidate != null ? candidate.getName() : application.getGuestName());
        row.put("email", candidate != null ? candidate.getEmail() : application.getGuestEmail());
        row.put("registered", candidate != null);
        row.put("status", application.getStatus().name());
        row.put("interview_id", application.getExternalInterviewId());
        row.put("summary", result == null ? null : result.getSummary());
        return row;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "message", "Unauthorized"));
    }

    private String limit(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record CompletionRequest(Long interview_id, String summary, String transcript) {}
}
