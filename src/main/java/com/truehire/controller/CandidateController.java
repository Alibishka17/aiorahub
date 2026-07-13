package com.truehire.controller;

import com.truehire.model.*;
import com.truehire.repository.*;
import com.truehire.service.AccountSettingsService;
import com.truehire.service.CvStorageService;
import com.truehire.service.InterviewLaunchService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/candidate")
public class CandidateController {

    private static final Logger log = LoggerFactory.getLogger(CandidateController.class);
    private static final Set<String> VIEWS = Set.of("dashboard", "vacancies", "applications", "documents", "settings");

    private final UserRepository userRepository;
    private final JobVacancyRepository vacancyRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewResultRepository resultRepository;
    private final VisaDocumentRepository documentRepository;
    private final CvStorageService cvStorageService;
    private final InterviewLaunchService interviewLaunchService;
    private final AccountSettingsService accountSettingsService;
    private final MessageSource messages;

    public CandidateController(UserRepository userRepository,
                               JobVacancyRepository vacancyRepository,
                               JobApplicationRepository applicationRepository,
                               InterviewResultRepository resultRepository,
                               VisaDocumentRepository documentRepository,
                               CvStorageService cvStorageService,
                               InterviewLaunchService interviewLaunchService,
                               AccountSettingsService accountSettingsService,
                               MessageSource messages) {
        this.userRepository = userRepository;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.resultRepository = resultRepository;
        this.documentRepository = documentRepository;
        this.cvStorageService = cvStorageService;
        this.interviewLaunchService = interviewLaunchService;
        this.accountSettingsService = accountSettingsService;
        this.messages = messages;
    }

    private User currentCandidate(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) return null;
        return userRepository.findById((Long) userId)
                .filter(u -> u.getRole() == Role.CANDIDATE)
                .orElse(null);
    }

    private JobApplication ownApplication(Long appId, User candidate) {
        return applicationRepository.findById(appId)
                .filter(a -> candidate.getId().equals(a.getCandidateId()))
                .orElse(null);
    }

    @GetMapping
    public String dashboard(@RequestParam(defaultValue = "dashboard") String view,
                            @RequestParam(defaultValue = "") String query,
                            @RequestParam(defaultValue = "") String category,
                            @RequestParam(defaultValue = "") String city,
                            @RequestParam(defaultValue = "") String country,
                            @RequestParam(required = false) Long salary,
                            HttpSession session,
                            Model model) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";

        String selectedView = VIEWS.contains(view) ? view : "dashboard";
        List<JobApplication> applications = new ArrayList<>(applicationRepository.findByCandidateId(candidate.getId()));
        applications.sort(Comparator.comparing(JobApplication::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        Map<Long, JobVacancy> vacancyMap = new LinkedHashMap<>();
        List<JobVacancy> publishedVacancies = vacancyRepository.findByStatus(VacancyStatus.PUBLISHED);
        publishedVacancies.forEach(v -> vacancyMap.put(v.getId(), v));
        applications.forEach(app -> vacancyRepository.findById(app.getVacancyId())
                .ifPresent(v -> vacancyMap.putIfAbsent(v.getId(), v)));

        Map<Long, JobApplication> myApplications = new HashMap<>();
        Map<Long, InterviewResult> interviewResults = new HashMap<>();
        for (JobApplication app : applications) {
            myApplications.put(app.getVacancyId(), app);
            resultRepository.findByApplicationId(app.getId())
                    .ifPresent(result -> interviewResults.put(app.getId(), result));
        }

        List<JobVacancy> filteredVacancies = publishedVacancies.stream()
                .filter(v -> contains(v.getTitle(), query) || contains(v.getDescription(), query))
                .filter(v -> category.isBlank() || equalsIgnoreCase(v.getCategory(), category))
                .filter(v -> city.isBlank() || equalsIgnoreCase(v.getCity(), city))
                .filter(v -> country.isBlank() || equalsIgnoreCase(v.getCountry(), country))
                .filter(v -> matchesSalary(v, salary))
                .toList();

        long completedInterviews = applications.stream()
                .filter(app -> app.getStatus() == ApplicationStatus.INTERVIEW_COMPLETED
                        || app.getStatus() == ApplicationStatus.OFFER_GRANTED
                        || app.getStatus() == ApplicationStatus.VISA_PROCESSING)
                .count();
        long activeApplications = applications.stream()
                .filter(app -> app.getStatus() == ApplicationStatus.APPLIED
                        || app.getStatus() == ApplicationStatus.INTERVIEW_PENDING)
                .count();

        model.addAttribute("user", candidate);
        model.addAttribute("view", selectedView);
        model.addAttribute("applications", applications);
        model.addAttribute("recentApplications", applications.stream().limit(4).toList());
        model.addAttribute("vacancyById", vacancyMap);
        model.addAttribute("myApps", myApplications);
        model.addAttribute("interviewResults", interviewResults);
        model.addAttribute("vacancies", filteredVacancies);
        model.addAttribute("categories", distinctValues(publishedVacancies, true));
        model.addAttribute("cities", distinctValues(publishedVacancies, false));
        model.addAttribute("countries", publishedVacancies.stream().map(JobVacancy::getCountry)
                .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        model.addAttribute("filterQuery", query.trim());
        model.addAttribute("filterCategory", category.trim());
        model.addAttribute("filterCity", city.trim());
        model.addAttribute("filterCountry", country.trim());
        model.addAttribute("filterSalary", salary);
        model.addAttribute("completedInterviews", completedInterviews);
        model.addAttribute("activeApplications", activeApplications);
        return "candidate";
    }

    @PostMapping("/cv")
    public String uploadCv(@RequestParam("cv") MultipartFile cv,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";
        try {
            String previousStorageKey = candidate.getCvStorageKey();
            CvStorageService.StoredCv stored = cvStorageService.store(candidate.getId(), cv);
            candidate.setCvFileName(stored.fileName());
            candidate.setCvContentType(stored.contentType());
            candidate.setCvStorageKey(stored.storageKey());
            userRepository.save(candidate);
            cvStorageService.delete(previousStorageKey);
            redirectAttributes.addFlashAttribute("cvSaved", true);
        } catch (IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("cvError", ex.getMessage());
        }
        return "redirect:/candidate?view=documents";
    }

    @GetMapping("/cv")
    public ResponseEntity<Resource> downloadOwnCv(HttpSession session) throws IOException {
        User candidate = currentCandidate(session);
        if (candidate == null) return ResponseEntity.status(401).build();
        if (candidate.getCvStorageKey() == null) return ResponseEntity.notFound().build();
        Resource resource = cvStorageService.load(candidate.getCvStorageKey());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(candidate.getCvFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(candidate.getCvContentType()))
                .body(resource);
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
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";
        String error = accountSettingsService.update(candidate, firstName, lastName, phone, email,
                telegramEnabled, whatsappEnabled, currentPassword, newPassword);
        if (error == null) {
            redirectAttributes.addFlashAttribute("settingsSaved", true);
        } else {
            redirectAttributes.addFlashAttribute("settingsError", messages.getMessage(error, null, locale));
        }
        return "redirect:/candidate?view=settings";
    }

    @PostMapping("/apply/{vacancyId}")
    public String apply(@PathVariable Long vacancyId,
                        HttpSession session,
                        Locale locale,
                        RedirectAttributes redirectAttributes) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";
        JobVacancy vacancy = vacancyRepository.findById(vacancyId).orElse(null);
        if (vacancy == null) return "redirect:/candidate?view=vacancies";

        JobApplication application = applicationRepository
                .findByVacancyIdAndCandidateId(vacancyId, candidate.getId()).orElse(null);
        if (application == null) {
            if (vacancy.getStatus() != VacancyStatus.PUBLISHED) return "redirect:/candidate?view=vacancies";
            application = applicationRepository.save(new JobApplication(
                    vacancyId, candidate.getId(), ApplicationStatus.APPLIED));
        }
        try {
            return "redirect:" + interviewLaunchService.launch(application, vacancy, candidate.getEmail());
        } catch (IllegalStateException ex) {
            return interviewLaunchFailure(application, vacancy, locale, redirectAttributes, ex);
        }
    }

    @PostMapping("/applications/{appId}/interview")
    public String startInterview(@PathVariable Long appId,
                                 HttpSession session,
                                 Locale locale,
                                 RedirectAttributes redirectAttributes) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";
        JobApplication application = ownApplication(appId, candidate);
        if (application == null) return "redirect:/candidate?view=applications";
        JobVacancy vacancy = vacancyRepository.findById(application.getVacancyId()).orElse(null);
        if (vacancy == null) return "redirect:/candidate?view=applications";
        try {
            return "redirect:" + interviewLaunchService.launch(application, vacancy, candidate.getEmail());
        } catch (IllegalStateException ex) {
            return interviewLaunchFailure(application, vacancy, locale, redirectAttributes, ex);
        }
    }

    private String interviewLaunchFailure(JobApplication application,
                                          JobVacancy vacancy,
                                          Locale locale,
                                          RedirectAttributes redirectAttributes,
                                          IllegalStateException error) {
        log.error("Unable to launch HRme interview for application {} and vacancy {}",
                application.getId(), vacancy.getId(), error);
        redirectAttributes.addFlashAttribute("interviewError",
                messages.getMessage("error.interview_unavailable", null, locale));
        return "redirect:/vacancies/" + vacancy.getId();
    }

    @GetMapping("/visa/{appId}")
    public String visa(@PathVariable Long appId, HttpSession session, Model model) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";
        JobApplication app = ownApplication(appId, candidate);
        if (app == null || (app.getStatus() != ApplicationStatus.OFFER_GRANTED
                && app.getStatus() != ApplicationStatus.VISA_PROCESSING)) {
            return "redirect:/candidate?view=applications";
        }
        List<VisaDocument> documents = documentRepository.findByApplicationId(app.getId());
        int step = documents.isEmpty() ? 0 : documents.get(0).getStatus().ordinal() + 1;
        model.addAttribute("user", candidate);
        model.addAttribute("app", app);
        model.addAttribute("vacancy", vacancyRepository.findById(app.getVacancyId()).orElse(null));
        model.addAttribute("documents", documents);
        model.addAttribute("step", step);
        return "visa";
    }

    @PostMapping("/visa/{appId}/documents")
    public String uploadDocuments(@PathVariable Long appId,
                                  @RequestParam MultipartFile passport,
                                  @RequestParam MultipartFile diploma,
                                  @RequestParam MultipartFile contract,
                                  HttpSession session) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";
        JobApplication app = ownApplication(appId, candidate);
        if (app == null || app.getStatus() != ApplicationStatus.OFFER_GRANTED) {
            return "redirect:/candidate?view=applications";
        }
        saveDocument(app.getId(), "PASSPORT", passport);
        saveDocument(app.getId(), "DIPLOMA", diploma);
        saveDocument(app.getId(), "CONTRACT", contract);
        app.setStatus(ApplicationStatus.VISA_PROCESSING);
        applicationRepository.save(app);
        return "redirect:/candidate/visa/" + app.getId();
    }

    private void saveDocument(Long applicationId, String type, MultipartFile file) {
        String fileName = (file != null && file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
                ? file.getOriginalFilename() : type.toLowerCase() + ".pdf";
        documentRepository.save(new VisaDocument(
                applicationId, type, "/uploads/" + applicationId + "/" + fileName, VisaStatus.UPLOADED));
    }

    private List<String> distinctValues(List<JobVacancy> vacancies, boolean categories) {
        return vacancies.stream()
                .map(v -> categories ? v.getCategory() : v.getCity())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private boolean contains(String value, String query) {
        return query == null || query.isBlank()
                || (value != null && value.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT)));
    }

    private boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.equalsIgnoreCase(expected.trim());
    }

    private boolean matchesSalary(JobVacancy vacancy, Long requestedSalary) {
        if (requestedSalary == null || requestedSalary <= 0) return true;
        Long upperBound = vacancy.getSalaryMax() != null ? vacancy.getSalaryMax() : vacancy.getSalaryMin();
        return upperBound != null && upperBound >= requestedSalary;
    }
}
