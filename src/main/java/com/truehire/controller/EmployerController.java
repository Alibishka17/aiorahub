package com.truehire.controller;

import com.truehire.model.*;
import com.truehire.repository.*;
import com.truehire.service.AccountSettingsService;
import com.truehire.service.CvStorageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.MessageSource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employer")
public class EmployerController {

    private static final Set<String> VIEWS = Set.of("dashboard", "vacancies", "candidates", "settings");

    private final UserRepository userRepository;
    private final JobVacancyRepository vacancyRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewResultRepository resultRepository;
    private final CvStorageService cvStorageService;
    private final AccountSettingsService accountSettingsService;
    private final MessageSource messages;

    public EmployerController(UserRepository userRepository,
                              JobVacancyRepository vacancyRepository,
                              JobApplicationRepository applicationRepository,
                              InterviewResultRepository resultRepository,
                              CvStorageService cvStorageService,
                              AccountSettingsService accountSettingsService,
                              MessageSource messages) {
        this.userRepository = userRepository;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.resultRepository = resultRepository;
        this.cvStorageService = cvStorageService;
        this.accountSettingsService = accountSettingsService;
        this.messages = messages;
    }

    private User currentEmployer(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) return null;
        return userRepository.findById((Long) userId)
                .filter(u -> u.getRole() == Role.EMPLOYER)
                .orElse(null);
    }

    @GetMapping
    public String dashboard(@RequestParam(defaultValue = "dashboard") String view,
                            @RequestParam(defaultValue = "false") boolean create,
                            HttpSession session,
                            Locale locale,
                            Model model) {
        User employer = currentEmployer(session);
        if (employer == null) return "redirect:/login?role=EMPLOYER";

        List<JobVacancy> vacancies = vacancyRepository.findByEmployerIdAndStatusNot(
                employer.getId(), VacancyStatus.ARCHIVED);
        vacancies.sort(Comparator.comparing(JobVacancy::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        Map<Long, JobVacancy> vacancyById = vacancies.stream()
                .collect(Collectors.toMap(JobVacancy::getId, Function.identity()));

        List<Map<String, Object>> responses = new ArrayList<>();
        if (!vacancies.isEmpty()) {
            for (JobApplication app : applicationRepository.findByVacancyIdIn(new ArrayList<>(vacancyById.keySet()))) {
                User candidate = app.getCandidateId() == null
                        ? null : userRepository.findById(app.getCandidateId()).orElse(null);
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
        responses.sort(Comparator.comparing((Map<String, Object> row) ->
                        ((JobApplication) row.get("app")).getCreatedAt(),
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        long publishedVacancies = vacancies.stream().filter(v -> v.getStatus() == VacancyStatus.PUBLISHED).count();
        long completedInterviews = responses.stream().filter(row -> row.get("result") != null).count();
        long totalViews = vacancies.stream().mapToLong(JobVacancy::getViewCount).sum();
        long maxMetric = Math.max(1, Math.max(totalViews, Math.max(responses.size(), completedInterviews)));
        Map<Long, Long> applicationCounts = vacancies.stream().collect(Collectors.toMap(
                JobVacancy::getId, vacancy -> applicationRepository.countByVacancyId(vacancy.getId())));

        model.addAttribute("user", employer);
        model.addAttribute("view", VIEWS.contains(view) ? view : "dashboard");
        model.addAttribute("showCreateForm", create);
        model.addAttribute("vacancies", vacancies);
        model.addAttribute("responses", responses);
        model.addAttribute("recentResponses", responses.stream().limit(5).toList());
        model.addAttribute("applicationCounts", applicationCounts);
        model.addAttribute("publishedVacancies", publishedVacancies);
        model.addAttribute("completedInterviews", completedInterviews);
        model.addAttribute("totalViews", totalViews);
        model.addAttribute("viewsPercent", totalViews * 100 / maxMetric);
        model.addAttribute("applicationsPercent", responses.size() * 100 / maxMetric);
        model.addAttribute("interviewsPercent", completedInterviews * 100 / maxMetric);
        model.addAttribute("countryOptions", countryOptions(locale));
        model.addAttribute("cityOptions", cityOptions(vacancies));
        return "employer";
    }

    @PostMapping("/vacancies")
    public String createVacancy(@RequestParam String title,
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
                                HttpSession session) {
        User employer = currentEmployer(session);
        if (employer == null) return "redirect:/login?role=EMPLOYER";

        JobVacancy vacancy = new JobVacancy(
                title.trim(), description.trim(), conditions.trim(), candidateRequirements.trim(), employer.getId());
        vacancy.setCategory(blankToNull(category));
        vacancy.setCity(blankToNull(city));
        vacancy.setCountry(blankToNull(country));
        vacancy.setRequiredDocuments(blankToNull(requiredDocuments));
        vacancy.setAdditionalInfo(blankToNull(additionalInfo));
        setSalary(vacancy, salaryMin, salaryMax, salaryCurrency);
        vacancyRepository.save(vacancy);
        return "redirect:/employer?view=vacancies";
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
        return "redirect:/employer?view=vacancies";
    }

    @PostMapping("/settings")
    public String updateSettings(@RequestParam String firstName,
                                 @RequestParam String lastName,
                                 @RequestParam String phone,
                                 @RequestParam String email,
                                 @RequestParam(defaultValue = "false") boolean telegramEnabled,
                                 @RequestParam(defaultValue = "false") boolean whatsappEnabled,
                                 @RequestParam(defaultValue = "") String currentPassword,
                                 @RequestParam(defaultValue = "") String newPassword,
                                 HttpSession session,
                                 Locale locale,
                                 RedirectAttributes redirectAttributes) {
        User employer = currentEmployer(session);
        if (employer == null) return "redirect:/login?role=EMPLOYER";
        String error = accountSettingsService.update(employer, firstName, lastName, phone, email,
                telegramEnabled, whatsappEnabled, currentPassword, newPassword);
        if (error == null) {
            redirectAttributes.addFlashAttribute("settingsSaved", true);
        } else {
            redirectAttributes.addFlashAttribute("settingsError", messages.getMessage(error, null, locale));
        }
        return "redirect:/employer?view=settings";
    }

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
        return "redirect:/employer?view=candidates";
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
                .filename(candidate.getCvFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(candidate.getCvContentType()))
                .body(resource);
    }

    private boolean canAccessCandidate(User employer, Long candidateId) {
        List<Long> vacancyIds = vacancyRepository.findByEmployerId(employer.getId()).stream()
                .map(JobVacancy::getId).toList();
        return !vacancyIds.isEmpty() && applicationRepository.findByVacancyIdIn(vacancyIds).stream()
                .anyMatch(app -> candidateId.equals(app.getCandidateId()));
    }

    private void setSalary(JobVacancy vacancy, Long minimum, Long maximum, String currency) {
        Long safeMinimum = minimum == null || minimum < 0 ? null : minimum;
        Long safeMaximum = maximum == null || maximum < 0 ? null : maximum;
        if (safeMinimum != null && safeMaximum != null && safeMinimum > safeMaximum) {
            Long swap = safeMinimum;
            safeMinimum = safeMaximum;
            safeMaximum = swap;
        }
        vacancy.setSalaryMin(safeMinimum);
        vacancy.setSalaryMax(safeMaximum);
        String normalizedCurrency = currency == null ? "EUR" : currency.trim().toUpperCase(Locale.ROOT);
        vacancy.setSalaryCurrency(normalizedCurrency.matches("[A-Z]{3}") ? normalizedCurrency : "EUR");
    }

    private String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private List<CountryOption> countryOptions(Locale locale) {
        return Arrays.stream(Locale.getISOCountries())
                .map(code -> new CountryOption(code, new Locale("", code).getDisplayCountry(locale)))
                .filter(option -> !option.name().isBlank())
                .sorted(Comparator.comparing(CountryOption::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<String> cityOptions(List<JobVacancy> vacancies) {
        Set<String> cities = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        cities.addAll(List.of("Almaty", "Astana", "Berlin", "Dubai", "Frankfurt", "Hamburg",
                "Istanbul", "London", "Munich", "Prague", "Riyadh", "Vienna", "Warsaw"));
        vacancies.stream().map(JobVacancy::getCity).filter(Objects::nonNull)
                .map(String::trim).filter(value -> !value.isBlank()).forEach(cities::add);
        return new ArrayList<>(cities);
    }

    public record CountryOption(String code, String name) {}
}
