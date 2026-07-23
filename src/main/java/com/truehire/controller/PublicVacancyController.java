package com.truehire.controller;

import com.truehire.model.JobApplication;
import com.truehire.model.JobVacancy;
import com.truehire.model.Role;
import com.truehire.model.User;
import com.truehire.model.VacancyStatus;
import com.truehire.repository.JobApplicationRepository;
import com.truehire.repository.JobVacancyRepository;
import com.truehire.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import com.truehire.service.InterviewLaunchService;
import com.truehire.service.InterviewConfigurationService;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Controller
public class PublicVacancyController {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private final JobVacancyRepository vacancyRepository;
    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final MessageSource messages;
    private final InterviewLaunchService interviewLaunchService;
    private final InterviewConfigurationService interviewConfigurationService;

    public PublicVacancyController(JobVacancyRepository vacancyRepository,
                                   JobApplicationRepository applicationRepository,
                                   UserRepository userRepository,
                                   MessageSource messages,
                                   InterviewLaunchService interviewLaunchService,
                                   InterviewConfigurationService interviewConfigurationService) {
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.messages = messages;
        this.interviewLaunchService = interviewLaunchService;
        this.interviewConfigurationService = interviewConfigurationService;
    }

    @GetMapping("/vacancies")
    public String vacancies(Model model) {
        model.addAttribute("vacancies", vacancyRepository.findByStatus(VacancyStatus.PUBLISHED));
        return "vacancies";
    }

    @GetMapping("/vacancies/{id}")
    public String vacancy(@PathVariable Long id, HttpSession session, Model model) {
        JobVacancy vacancy = vacancyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        User currentUser = currentUser(session);
        JobApplication candidateApplication = null;
        if (currentUser != null && currentUser.getRole() == Role.CANDIDATE) {
            candidateApplication = applicationRepository
                    .findByVacancyIdAndCandidateId(id, currentUser.getId())
                    .orElse(null);
        }
        boolean canView = vacancy.getStatus() == VacancyStatus.PUBLISHED
                || candidateApplication != null
                || (currentUser != null && currentUser.getRole() == Role.EMPLOYER
                && currentUser.getId().equals(vacancy.getEmployerId()));
        if (!canView) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        boolean ownerViewing = currentUser != null && currentUser.getRole() == Role.EMPLOYER
                && currentUser.getId().equals(vacancy.getEmployerId());
        if (vacancy.getStatus() == VacancyStatus.PUBLISHED && !ownerViewing) {
            vacancy.setViewCount(vacancy.getViewCount() + 1);
            vacancyRepository.save(vacancy);
        }
        model.addAttribute("vacancy", vacancy);
        model.addAttribute("currentUser", currentUser);
        if (currentUser != null && currentUser.getRole() == Role.CANDIDATE) {
            model.addAttribute("candidate", currentUser);
            if (candidateApplication != null) {
                model.addAttribute("candidateApplication", candidateApplication);
            }
        }
        return "vacancy";
    }

    @GetMapping("/vacancies/{id}/apply")
    public String applicationForm(@PathVariable Long id, HttpSession session, Model model) {
        if (currentUser(session) != null) {
            return "redirect:/vacancies/" + id;
        }
        model.addAttribute("vacancy", publishedVacancy(id));
        return "guest-apply";
    }

    @PostMapping("/vacancies/{id}/apply")
    public String apply(@PathVariable Long id,
                        @RequestParam String firstName,
                        @RequestParam String lastName,
                        @RequestParam String phone,
                        @RequestParam(defaultValue = "false") boolean telegramEnabled,
                        @RequestParam(defaultValue = "false") boolean whatsappEnabled,
                        @RequestParam String email,
                        HttpSession session,
                        Model model,
                        Locale locale) {
        if (currentUser(session) != null) {
            return "redirect:/vacancies/" + id;
        }
        JobVacancy vacancy = publishedVacancy(id);
        String normalizedEmail = normalizeEmail(email);
        String error = validateContact(firstName, lastName, phone, normalizedEmail, locale);
        if (error == null && applicationRepository
                .findByVacancyIdAndGuestEmailIgnoreCase(id, normalizedEmail).isPresent()) {
            error = message("error.duplicate_application", locale);
        }
        if (error != null) {
            model.addAttribute("vacancy", vacancy);
            model.addAttribute("error", error);
            model.addAttribute("firstName", clean(firstName));
            model.addAttribute("lastName", clean(lastName));
            model.addAttribute("phone", clean(phone));
            model.addAttribute("email", normalizedEmail);
            model.addAttribute("telegramEnabled", telegramEnabled);
            model.addAttribute("whatsappEnabled", whatsappEnabled);
            return "guest-apply";
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        JobApplication application = JobApplication.guest(
                id,
                clean(firstName),
                clean(lastName),
                normalizedEmail,
                clean(phone),
                telegramEnabled,
                whatsappEnabled,
                token);
        interviewConfigurationService.snapshot(application, vacancy, locale);
        applicationRepository.save(application);
        try {
            return "redirect:" + interviewLaunchService.launch(application, vacancy, normalizedEmail);
        } catch (IllegalStateException ex) {
            return "redirect:/guest/application/" + token;
        }
    }

    @GetMapping("/guest/application/{token}")
    public String applicationConfirmation(@PathVariable String token, Model model) {
        JobApplication application = guestApplication(token);
        model.addAttribute("guestApplication", application);
        model.addAttribute("vacancy", vacancyRepository.findById(application.getVacancyId()).orElse(null));
        return "guest-complete";
    }

    @PostMapping("/guest/application/{token}/interview")
    public String startGuestInterview(@PathVariable String token) {
        JobApplication application = guestApplication(token);
        JobVacancy vacancy = publishedVacancy(application.getVacancyId());
        try {
            return "redirect:" + interviewLaunchService.launch(
                    application, vacancy, application.getGuestEmail());
        } catch (IllegalStateException ex) {
            return "redirect:/guest/application/" + token;
        }
    }

    private JobVacancy publishedVacancy(Long id) {
        return vacancyRepository.findById(id)
                .filter(vacancy -> vacancy.getStatus() == VacancyStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private JobApplication guestApplication(String token) {
        if (token == null || !token.matches("[a-f0-9]{32}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return applicationRepository.findByGuestAccessToken(token)
                .filter(JobApplication::isGuest)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String validateContact(String firstName, String lastName, String phone, String email, Locale locale) {
        if (clean(firstName).isEmpty() || clean(lastName).isEmpty()) {
            return message("error.name_required", locale);
        }
        if (clean(firstName).length() > 100 || clean(lastName).length() > 100) {
            return message("error.name_too_long", locale);
        }
        if (!INTERNATIONAL_PHONE.matcher(clean(phone)).matches()) {
            return message("error.phone", locale);
        }
        if (!EMAIL.matcher(email).matches()) {
            return message("error.email", locale);
        }
        if (email.length() > 255) {
            return message("error.email_too_long", locale);
        }
        return null;
    }

    private String normalizeEmail(String email) {
        return clean(email).toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String message(String code, Locale locale) {
        return messages.getMessage(code, null, locale);
    }

    private User currentUser(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (!(userId instanceof Long id)) {
            return null;
        }
        return userRepository.findById(id).orElse(null);
    }
}
