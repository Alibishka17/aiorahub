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
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/candidate")
public class CandidateController {

    private static final String[] LANGUAGE_LEVELS = {
            "Немецкий: B1 (Intermediate)",
            "Немецкий: B2 (Upper-Intermediate)",
            "Немецкий: C1 (Advanced)"
    };

    private static final String[] AI_COMMENTS = {
            "Кандидат адекватен, отвечает по существу, опыт работы подтверждён в ходе беседы. Рекомендован к найму.",
            "Кандидат спокоен и мотивирован, релокация в Германию осознанная. Опыт соответствует заявленному в резюме.",
            "Коммуникация уверенная, без противоречий в ответах. Soft skills на хорошем уровне, готов приступить в течение 2 месяцев."
    };

    private final UserRepository userRepository;
    private final JobVacancyRepository vacancyRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewResultRepository resultRepository;
    private final VisaDocumentRepository documentRepository;
    private final CvStorageService cvStorageService;
    private final Random random = new Random();

    public CandidateController(UserRepository userRepository,
                               JobVacancyRepository vacancyRepository,
                               JobApplicationRepository applicationRepository,
                               InterviewResultRepository resultRepository,
                               VisaDocumentRepository documentRepository,
                               CvStorageService cvStorageService) {
        this.userRepository = userRepository;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.resultRepository = resultRepository;
        this.documentRepository = documentRepository;
        this.cvStorageService = cvStorageService;
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
                .filter(a -> a.getCandidateId().equals(candidate.getId()))
                .orElse(null);
    }

    // ---------- Дашборд соискателя ----------

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";

        List<JobApplication> applications = applicationRepository.findByCandidateId(candidate.getId());
        Map<Long, JobVacancy> vacancyMap = new LinkedHashMap<>();
        vacancyRepository.findByStatus(VacancyStatus.PUBLISHED)
                .forEach(v -> vacancyMap.put(v.getId(), v));
        applications.forEach(app -> vacancyRepository.findById(app.getVacancyId())
                .ifPresent(v -> vacancyMap.putIfAbsent(v.getId(), v)));

        Map<Long, JobApplication> myApplications = new HashMap<>();
        Map<Long, InterviewResult> interviewResults = new HashMap<>();
        for (JobApplication app : applications) {
            myApplications.put(app.getVacancyId(), app);
            resultRepository.findByApplicationId(app.getId())
                    .ifPresent(result -> interviewResults.put(app.getId(), result));
        }

        model.addAttribute("user", candidate);
        model.addAttribute("vacancies", new ArrayList<>(vacancyMap.values()));
        model.addAttribute("myApps", myApplications);
        model.addAttribute("interviewResults", interviewResults);
        return "candidate";
    }

    @PostMapping("/cv")
    public String uploadCv(@RequestParam("cv") MultipartFile cv,
                           HttpSession session,
                           Model model) {
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
            return "redirect:/candidate#profile";
        } catch (IllegalArgumentException | IOException ex) {
            model.addAttribute("cvError", ex.getMessage());
            return dashboard(session, model);
        }
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

    @PostMapping("/apply/{vacancyId}")
    public String apply(@PathVariable Long vacancyId, HttpSession session) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";

        boolean alreadyApplied = applicationRepository
                .findByVacancyIdAndCandidateId(vacancyId, candidate.getId()).isPresent();
        JobVacancy vacancy = vacancyRepository.findById(vacancyId).orElse(null);
        if (!alreadyApplied && vacancy != null && vacancy.getStatus() == VacancyStatus.PUBLISHED) {
            applicationRepository.save(
                    new JobApplication(vacancyId, candidate.getId(), ApplicationStatus.APPLIED));
        }
        return "redirect:/candidate";
    }

    // ---------- Симуляция AI-интервью ----------

    @GetMapping("/interview/{appId}")
    public String interview(@PathVariable Long appId, HttpSession session, Model model) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";

        JobApplication app = ownApplication(appId, candidate);
        if (app == null) return "redirect:/candidate";

        if (app.getStatus() == ApplicationStatus.APPLIED) {
            app.setStatus(ApplicationStatus.INTERVIEW_PENDING);
            applicationRepository.save(app);
        }

        model.addAttribute("user", candidate);
        model.addAttribute("app", app);
        model.addAttribute("vacancy", vacancyRepository.findById(app.getVacancyId()).orElse(null));
        return "interview";
    }

    /** Завершает интервью и делает предварительный отчёт доступным работодателю. */
    @PostMapping("/interview/{appId}/complete")
    public String completeInterview(@PathVariable Long appId, HttpSession session) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";

        JobApplication app = ownApplication(appId, candidate);
        if (app == null) return "redirect:/candidate";

        if (resultRepository.findByApplicationId(app.getId()).isEmpty()) {
            String languageScore = LANGUAGE_LEVELS[random.nextInt(LANGUAGE_LEVELS.length)];
            String comment = AI_COMMENTS[random.nextInt(AI_COMMENTS.length)];
            String recordUrl = "https://meet.google.com/rec/truehire-" + app.getId();

            InterviewResult result = new InterviewResult(app.getId(), languageScore, comment, recordUrl, true);
            result.setSummary("Кандидат подтвердил опыт, мотивацию и готовность к следующему этапу отбора.");
            result.setTranscript("AI-интервьюер: Расскажите о вашем профессиональном опыте.\n"
                    + candidate.getName() + ": Я описал ключевые проекты и свою роль в них.\n"
                    + "AI-интервьюер: Почему вам интересна эта вакансия?\n"
                    + candidate.getName() + ": Мой опыт соответствует требованиям, и я готов развиваться в международной команде.");
            result.setConclusion(comment);
            resultRepository.save(result);

            app.setStatus(ApplicationStatus.INTERVIEW_COMPLETED);
            applicationRepository.save(app);
        }
        return "redirect:/candidate";
    }

    // ---------- Визовое сопровождение ----------

    @GetMapping("/visa/{appId}")
    public String visa(@PathVariable Long appId, HttpSession session, Model model) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/login?role=CANDIDATE";

        JobApplication app = ownApplication(appId, candidate);
        if (app == null) return "redirect:/candidate";
        if (app.getStatus() != ApplicationStatus.OFFER_GRANTED
                && app.getStatus() != ApplicationStatus.VISA_PROCESSING) {
            return "redirect:/candidate";
        }

        List<VisaDocument> documents = documentRepository.findByApplicationId(app.getId());
        // Шаг прогресса: 0 — документы ещё не загружены, 1..4 — по статусу документов
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
            return "redirect:/candidate";
        }

        // Файлы визового процесса подключаются отдельным защищённым хранилищем; здесь фиксируются метаданные.
        saveDocument(app.getId(), "Паспорт", passport);
        saveDocument(app.getId(), "Диплом", diploma);
        saveDocument(app.getId(), "Контракт", contract);

        app.setStatus(ApplicationStatus.VISA_PROCESSING);
        applicationRepository.save(app);
        return "redirect:/candidate/visa/" + app.getId();
    }

    private void saveDocument(Long applicationId, String type, MultipartFile file) {
        String fileName = (file != null && file.getOriginalFilename() != null
                && !file.getOriginalFilename().isBlank())
                ? file.getOriginalFilename()
                : type.toLowerCase() + ".pdf";
        documentRepository.save(new VisaDocument(
                applicationId, type, "/uploads/" + applicationId + "/" + fileName, VisaStatus.UPLOADED));
    }

}
