package com.truehire.controller;

import com.truehire.model.*;
import com.truehire.repository.*;
import com.truehire.service.CvStorageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employer")
public class EmployerController {

    private final UserRepository userRepository;
    private final JobVacancyRepository vacancyRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewResultRepository resultRepository;
    private final CvStorageService cvStorageService;

    public EmployerController(UserRepository userRepository,
                              JobVacancyRepository vacancyRepository,
                              JobApplicationRepository applicationRepository,
                              InterviewResultRepository resultRepository,
                              CvStorageService cvStorageService) {
        this.userRepository = userRepository;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.resultRepository = resultRepository;
        this.cvStorageService = cvStorageService;
    }

    private User currentEmployer(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) return null;
        return userRepository.findById((Long) userId)
                .filter(u -> u.getRole() == Role.EMPLOYER)
                .orElse(null);
    }

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        User employer = currentEmployer(session);
        if (employer == null) return "redirect:/login?role=EMPLOYER";

        List<JobVacancy> vacancies = vacancyRepository.findByEmployerIdAndStatusNot(
                employer.getId(), VacancyStatus.ARCHIVED);
        Map<Long, JobVacancy> vacancyById = vacancies.stream()
                .collect(Collectors.toMap(JobVacancy::getId, Function.identity()));

        List<Map<String, Object>> responses = new ArrayList<>();
        if (!vacancies.isEmpty()) {
            List<Long> ids = new ArrayList<>(vacancyById.keySet());
            for (JobApplication app : applicationRepository.findByVacancyIdIn(ids)) {
                User candidate = app.getCandidateId() == null
                        ? null
                        : userRepository.findById(app.getCandidateId()).orElse(null);
                Map<String, Object> row = new HashMap<>();
                row.put("app", app);
                row.put("vacancy", vacancyById.get(app.getVacancyId()));
                row.put("candidate", candidate);
                row.put("registered", candidate != null);
                row.put("displayName", candidate != null ? candidate.getName() : app.getGuestName());
                row.put("email", candidate != null ? candidate.getEmail() : app.getGuestEmail());
                row.put("phone", candidate != null ? candidate.getPhone() : app.getGuestPhone());
                row.put("telegramEnabled", candidate != null
                        ? candidate.isTelegramEnabled() : app.isGuestTelegramEnabled());
                row.put("whatsappEnabled", candidate != null
                        ? candidate.isWhatsappEnabled() : app.isGuestWhatsappEnabled());
                row.put("result", resultRepository.findByApplicationId(app.getId()).orElse(null));
                responses.add(row);
            }
        }

        model.addAttribute("user", employer);
        model.addAttribute("vacancies", vacancies);
        model.addAttribute("responses", responses);
        return "employer";
    }

    @PostMapping("/vacancies")
    public String createVacancy(@RequestParam String title,
                                @RequestParam String description,
                                @RequestParam String conditions,
                                @RequestParam String candidateRequirements,
                                HttpSession session) {
        User employer = currentEmployer(session);
        if (employer == null) return "redirect:/login?role=EMPLOYER";

        vacancyRepository.save(new JobVacancy(
                title, description, conditions, candidateRequirements, employer.getId()));
        return "redirect:/employer";
    }

    @PostMapping("/vacancies/{id}/publish")
    public String publishVacancy(@PathVariable Long id, HttpSession session) {
        return updateVacancyStatus(id, VacancyStatus.PUBLISHED, session);
    }

    @PostMapping("/vacancies/{id}/hide")
    public String hideVacancy(@PathVariable Long id, HttpSession session) {
        return updateVacancyStatus(id, VacancyStatus.HIDDEN, session);
    }

    @PostMapping("/vacancies/{id}/archive")
    public String archiveVacancy(@PathVariable Long id, HttpSession session) {
        return updateVacancyStatus(id, VacancyStatus.ARCHIVED, session);
    }

    private String updateVacancyStatus(Long id, VacancyStatus status, HttpSession session) {
        User employer = currentEmployer(session);
        if (employer == null) return "redirect:/login?role=EMPLOYER";
        vacancyRepository.findById(id)
                .filter(v -> v.getEmployerId().equals(employer.getId()))
                .ifPresent(v -> {
                    v.setStatus(status);
                    vacancyRepository.save(v);
                });
        return "redirect:/employer#vacancies";
    }

    /** Работодатель выдаёт Job Offer кандидату, прошедшему AI-интервью. */
    @PostMapping("/applications/{id}/offer")
    public String grantOffer(@PathVariable Long id,
                             @RequestParam String recruiterMessage,
                             HttpSession session) {
        User employer = currentEmployer(session);
        if (employer == null) return "redirect:/login?role=EMPLOYER";

        applicationRepository.findById(id).ifPresent(app -> {
            JobVacancy vacancy = vacancyRepository.findById(app.getVacancyId()).orElse(null);
            boolean ownVacancy = vacancy != null && vacancy.getEmployerId().equals(employer.getId());
            if (ownVacancy && app.getCandidateId() != null
                    && app.getStatus() == ApplicationStatus.INTERVIEW_COMPLETED) {
                app.setStatus(ApplicationStatus.OFFER_GRANTED);
                app.setRecruiterMessage(recruiterMessage.trim());
                app.setConfirmedAt(LocalDateTime.now());
                applicationRepository.save(app);
            }
        });
        return "redirect:/employer";
    }

    @GetMapping("/candidates/{candidateId}/cv")
    public ResponseEntity<Resource> downloadCandidateCv(@PathVariable Long candidateId,
                                                         HttpSession session) throws IOException {
        User employer = currentEmployer(session);
        if (employer == null) return ResponseEntity.status(401).build();

        User candidate = userRepository.findById(candidateId)
                .filter(u -> u.getRole() == Role.CANDIDATE && u.getCvStorageKey() != null)
                .orElse(null);
        if (candidate == null || !canAccessCandidate(employer, candidateId)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = cvStorageService.load(candidate.getCvStorageKey());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(candidate.getCvFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(candidate.getCvContentType()))
                .body(resource);
    }

    private boolean canAccessCandidate(User employer, Long candidateId) {
        List<Long> vacancyIds = vacancyRepository.findByEmployerId(employer.getId()).stream()
                .map(JobVacancy::getId)
                .toList();
        if (vacancyIds.isEmpty()) return false;
        return applicationRepository.findByVacancyIdIn(vacancyIds).stream()
                .anyMatch(app -> candidateId.equals(app.getCandidateId()));
    }
}
