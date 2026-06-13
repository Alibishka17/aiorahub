package com.truehire.controller;

import com.truehire.model.*;
import com.truehire.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employer")
public class EmployerController {

    private final UserRepository userRepository;
    private final JobVacancyRepository vacancyRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewResultRepository resultRepository;

    public EmployerController(UserRepository userRepository,
                              JobVacancyRepository vacancyRepository,
                              JobApplicationRepository applicationRepository,
                              InterviewResultRepository resultRepository) {
        this.userRepository = userRepository;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.resultRepository = resultRepository;
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
        if (employer == null) return "redirect:/";

        List<JobVacancy> vacancies = vacancyRepository.findByEmployerId(employer.getId());
        Map<Long, JobVacancy> vacancyById = vacancies.stream()
                .collect(Collectors.toMap(JobVacancy::getId, Function.identity()));

        List<Map<String, Object>> responses = new ArrayList<>();
        if (!vacancies.isEmpty()) {
            List<Long> ids = new ArrayList<>(vacancyById.keySet());
            for (JobApplication app : applicationRepository.findByVacancyIdIn(ids)) {
                Map<String, Object> row = new HashMap<>();
                row.put("app", app);
                row.put("vacancy", vacancyById.get(app.getVacancyId()));
                row.put("candidate", userRepository.findById(app.getCandidateId()).orElse(null));
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
        if (employer == null) return "redirect:/";

        vacancyRepository.save(new JobVacancy(
                title, description, conditions, candidateRequirements, employer.getId()));
        return "redirect:/employer";
    }

    /** Работодатель выдаёт Job Offer кандидату, прошедшему AI-интервью. */
    @PostMapping("/applications/{id}/offer")
    public String grantOffer(@PathVariable Long id, HttpSession session) {
        User employer = currentEmployer(session);
        if (employer == null) return "redirect:/";

        applicationRepository.findById(id).ifPresent(app -> {
            JobVacancy vacancy = vacancyRepository.findById(app.getVacancyId()).orElse(null);
            boolean ownVacancy = vacancy != null && vacancy.getEmployerId().equals(employer.getId());
            if (ownVacancy && app.getStatus() == ApplicationStatus.INTERVIEW_COMPLETED) {
                app.setStatus(ApplicationStatus.OFFER_GRANTED);
                applicationRepository.save(app);
            }
        });
        return "redirect:/employer";
    }
}
