package com.truehire.controller;

import com.truehire.model.*;
import com.truehire.repository.*;
import com.truehire.service.AdminAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final String ADMIN_SESSION = "adminAuthenticated";
    private final AdminAuthenticationService authentication;
    private final SiteVisitRepository visitRepository;
    private final UserRepository userRepository;
    private final JobVacancyRepository vacancyRepository;
    private final JobApplicationRepository applicationRepository;
    private final MessageSource messages;

    public AdminController(AdminAuthenticationService authentication,
                           SiteVisitRepository visitRepository,
                           UserRepository userRepository,
                           JobVacancyRepository vacancyRepository,
                           JobApplicationRepository applicationRepository,
                           MessageSource messages) {
        this.authentication = authentication;
        this.visitRepository = visitRepository;
        this.userRepository = userRepository;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.messages = messages;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        return isAdmin(session) ? "redirect:/admin" : "admin-login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password,
                        HttpServletRequest request, Model model, Locale locale) {
        if (!authentication.isConfigured()) {
            model.addAttribute("error", messages.getMessage("admin.login.unconfigured", null, locale));
            return "admin-login";
        }
        if (!authentication.authenticate(username, password)) {
            model.addAttribute("error", messages.getMessage("admin.login.invalid", null, locale));
            model.addAttribute("username", username);
            return "admin-login";
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            session = request.getSession(true);
        } else {
            request.changeSessionId();
        }
        session.setAttribute(ADMIN_SESSION, true);
        return "redirect:/admin";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/admin/login";
    }

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime today = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        LocalDateTime month = today.withDayOfMonth(1);
        model.addAttribute("viewsToday", visitRepository.countByVisitedAtAfter(today));
        model.addAttribute("viewsMonth", visitRepository.countByVisitedAtAfter(month));
        model.addAttribute("uniqueToday", visitRepository.countUniqueSessionsSince(today));
        model.addAttribute("onlineNow", visitRepository.countUniqueSessionsSince(now.minusMinutes(5)));
        model.addAttribute("candidates", userRepository.countByRole(Role.CANDIDATE));
        model.addAttribute("employers", userRepository.countByRole(Role.EMPLOYER));
        model.addAttribute("applications", applicationRepository.count());
        model.addAttribute("completedInterviews", applicationRepository.countByStatus(ApplicationStatus.INTERVIEW_COMPLETED));
        model.addAttribute("vacancies", vacancyRepository.findAll());
        model.addAttribute("employerUsers", userRepository.findByRole(Role.EMPLOYER));
        model.addAttribute("recentVisits", visitRepository.findTop30ByOrderByVisitedAtDesc());
        model.addAttribute("popularPaths", visitRepository.popularPathsSince(month));
        return "admin";
    }

    @PostMapping("/vacancies")
    public String createVacancy(@RequestParam Long employerId,
                                @RequestParam String title,
                                @RequestParam String description,
                                @RequestParam String conditions,
                                @RequestParam String candidateRequirements,
                                @RequestParam(defaultValue = "hrme-warsaw") String interviewTemplateId,
                                HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        if (userRepository.findById(employerId).filter(user -> user.getRole() == Role.EMPLOYER).isEmpty()) {
            return "redirect:/admin#vacancies";
        }
        JobVacancy vacancy = new JobVacancy(title.trim(), description.trim(), conditions.trim(),
                candidateRequirements.trim(), employerId);
        vacancy.setInterviewTemplateId(templateId(interviewTemplateId));
        vacancyRepository.save(vacancy);
        return "redirect:/admin#vacancies";
    }

    @PostMapping("/vacancies/{id}")
    public String updateVacancy(@PathVariable Long id,
                                @RequestParam String title,
                                @RequestParam String description,
                                @RequestParam String conditions,
                                @RequestParam String candidateRequirements,
                                @RequestParam String interviewTemplateId,
                                @RequestParam VacancyStatus status,
                                HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        vacancyRepository.findById(id).ifPresent(vacancy -> {
            vacancy.setTitle(title.trim());
            vacancy.setDescription(description.trim());
            vacancy.setConditions(conditions.trim());
            vacancy.setCandidateRequirements(candidateRequirements.trim());
            vacancy.setInterviewTemplateId(templateId(interviewTemplateId));
            vacancy.setStatus(status);
            vacancyRepository.save(vacancy);
        });
        return "redirect:/admin#vacancies";
    }

    private boolean isAdmin(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(ADMIN_SESSION));
    }

    private String templateId(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.matches("[a-z0-9-]{1,100}") ? normalized : "hrme-warsaw";
    }
}
