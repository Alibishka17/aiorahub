package com.truehire.controller;

import com.truehire.model.*;
import com.truehire.repository.*;
import com.truehire.service.AdminAuthenticationService;
import com.truehire.service.InterviewConfigurationService;
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
    private final InterviewConfigurationService interviewConfigurationService;

    public AdminController(AdminAuthenticationService authentication,
                           SiteVisitRepository visitRepository,
                           UserRepository userRepository,
                           JobVacancyRepository vacancyRepository,
                           JobApplicationRepository applicationRepository,
                           MessageSource messages,
                           InterviewConfigurationService interviewConfigurationService) {
        this.authentication = authentication;
        this.visitRepository = visitRepository;
        this.userRepository = userRepository;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.messages = messages;
        this.interviewConfigurationService = interviewConfigurationService;
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
        model.addAttribute("interviewCriteria", interviewConfigurationService.criterionOptions());
        return "admin";
    }

    @PostMapping("/vacancies")
    public String createVacancy(@RequestParam Long employerId,
                                @RequestParam String title,
                                @RequestParam String description,
                                @RequestParam String conditions,
                                @RequestParam String candidateRequirements,
                                @RequestParam(defaultValue = "") String category,
                                @RequestParam(defaultValue = "") String city,
                                @RequestParam(defaultValue = "") String country,
                                @RequestParam(required = false) Long salaryMin,
                                @RequestParam(required = false) Long salaryMax,
                                @RequestParam(defaultValue = "EUR") String salaryCurrency,
                                @RequestParam(defaultValue = "") String requiredDocuments,
                                @RequestParam(defaultValue = "") String additionalInfo,
                                HttpServletRequest request,
                                HttpSession session,
                                Locale locale,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        if (userRepository.findById(employerId).filter(user -> user.getRole() == Role.EMPLOYER).isEmpty()) {
            return "redirect:/admin#vacancies";
        }
        JobVacancy vacancy = new JobVacancy(title.trim(), description.trim(), conditions.trim(),
                candidateRequirements.trim(), employerId);
        setVacancyFields(vacancy, category, city, country, salaryMin, salaryMax, salaryCurrency,
                requiredDocuments, additionalInfo);
        try {
            interviewConfigurationService.apply(vacancy, request.getParameterMap());
        } catch (IllegalArgumentException error) {
            redirectAttributes.addFlashAttribute("vacancyError",
                    messages.getMessage(error.getMessage(), null, locale));
            return "redirect:/admin#vacancies";
        }
        vacancyRepository.save(vacancy);
        return "redirect:/admin#vacancies";
    }

    @PostMapping("/vacancies/{id}")
    public String updateVacancy(@PathVariable Long id,
                                @RequestParam String title,
                                @RequestParam String description,
                                @RequestParam String conditions,
                                @RequestParam String candidateRequirements,
                                @RequestParam(defaultValue = "") String category,
                                @RequestParam(defaultValue = "") String city,
                                @RequestParam(defaultValue = "") String country,
                                @RequestParam(required = false) Long salaryMin,
                                @RequestParam(required = false) Long salaryMax,
                                @RequestParam(defaultValue = "EUR") String salaryCurrency,
                                @RequestParam(defaultValue = "") String requiredDocuments,
                                @RequestParam(defaultValue = "") String additionalInfo,
                                @RequestParam VacancyStatus status,
                                HttpServletRequest request,
                                HttpSession session,
                                Locale locale,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        JobVacancy vacancy = vacancyRepository.findById(id).orElse(null);
        if (vacancy != null) {
            vacancy.setTitle(title.trim());
            vacancy.setDescription(description.trim());
            vacancy.setConditions(conditions.trim());
            vacancy.setCandidateRequirements(candidateRequirements.trim());
            setVacancyFields(vacancy, category, city, country, salaryMin, salaryMax, salaryCurrency,
                    requiredDocuments, additionalInfo);
            if (request.getParameter("interviewMode") != null) {
                try {
                    interviewConfigurationService.apply(vacancy, request.getParameterMap());
                } catch (IllegalArgumentException error) {
                    redirectAttributes.addFlashAttribute("vacancyError",
                            messages.getMessage(error.getMessage(), null, locale));
                    return "redirect:/admin#vacancies";
                }
            }
            vacancy.setStatus(status);
            vacancyRepository.save(vacancy);
        }
        return "redirect:/admin#vacancies";
    }

    @GetMapping("/vacancies/{id}/interview-settings")
    public String interviewSettings(@PathVariable Long id, HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        JobVacancy vacancy = vacancyRepository.findById(id).orElse(null);
        if (vacancy == null || !vacancy.hasCustomInterviewConfiguration()) {
            return "redirect:/admin#vacancies";
        }
        model.addAttribute("vacancy", vacancy);
        model.addAttribute("editingVacancy", vacancy);
        model.addAttribute("interviewCriteria", interviewConfigurationService.criterionOptions());
        model.addAttribute("pageTitle", vacancy.getTitle());
        model.addAttribute("adminEditing", true);
        return "vacancy-interview-edit";
    }

    @PostMapping("/vacancies/{id}/interview-settings")
    public String updateInterviewSettings(@PathVariable Long id,
                                          HttpServletRequest request,
                                          HttpSession session,
                                          Locale locale,
                                          org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        JobVacancy vacancy = vacancyRepository.findById(id).orElse(null);
        if (vacancy == null || !vacancy.hasCustomInterviewConfiguration()) {
            return "redirect:/admin#vacancies";
        }
        try {
            interviewConfigurationService.apply(vacancy, request.getParameterMap());
            vacancyRepository.save(vacancy);
            redirectAttributes.addFlashAttribute("vacancyNotice",
                    messages.getMessage("interview.saved", null, locale));
        } catch (IllegalArgumentException error) {
            redirectAttributes.addFlashAttribute("vacancyError",
                    messages.getMessage(error.getMessage(), null, locale));
        }
        return "redirect:/admin/vacancies/" + id + "/interview-settings";
    }

    private boolean isAdmin(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(ADMIN_SESSION));
    }

    private void setVacancyFields(JobVacancy vacancy, String category, String city, String country,
                                  Long salaryMin, Long salaryMax, String currency,
                                  String requiredDocuments, String additionalInfo) {
        vacancy.setCategory(blankToNull(category));
        vacancy.setCity(blankToNull(city));
        vacancy.setCountry(blankToNull(country));
        vacancy.setRequiredDocuments(blankToNull(requiredDocuments));
        vacancy.setAdditionalInfo(blankToNull(additionalInfo));
        Long minimum = salaryMin == null || salaryMin < 0 ? null : salaryMin;
        Long maximum = salaryMax == null || salaryMax < 0 ? null : salaryMax;
        if (minimum != null && maximum != null && minimum > maximum) {
            Long swap = minimum;
            minimum = maximum;
            maximum = swap;
        }
        vacancy.setSalaryMin(minimum);
        vacancy.setSalaryMax(maximum);
        String normalized = currency == null ? "EUR" : currency.trim().toUpperCase(Locale.ROOT);
        vacancy.setSalaryCurrency(normalized.matches("[A-Z]{3}") ? normalized : "EUR");
    }

    private String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
