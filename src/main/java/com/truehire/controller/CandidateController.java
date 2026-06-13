package com.truehire.controller;

import com.truehire.model.*;
import com.truehire.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

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
    private final Random random = new Random();

    public CandidateController(UserRepository userRepository,
                               JobVacancyRepository vacancyRepository,
                               JobApplicationRepository applicationRepository,
                               InterviewResultRepository resultRepository,
                               VisaDocumentRepository documentRepository) {
        this.userRepository = userRepository;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.resultRepository = resultRepository;
        this.documentRepository = documentRepository;
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
        if (candidate == null) return "redirect:/";

        List<JobVacancy> vacancies = vacancyRepository.findAll();

        Map<Long, JobApplication> myApplications = new HashMap<>();
        for (JobApplication app : applicationRepository.findByCandidateId(candidate.getId())) {
            myApplications.put(app.getVacancyId(), app);
        }

        model.addAttribute("user", candidate);
        model.addAttribute("vacancies", vacancies);
        model.addAttribute("myApps", myApplications);
        return "candidate";
    }

    @PostMapping("/apply/{vacancyId}")
    public String apply(@PathVariable Long vacancyId, HttpSession session) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/";

        boolean alreadyApplied = applicationRepository
                .findByVacancyIdAndCandidateId(vacancyId, candidate.getId()).isPresent();
        if (!alreadyApplied && vacancyRepository.existsById(vacancyId)) {
            applicationRepository.save(
                    new JobApplication(vacancyId, candidate.getId(), ApplicationStatus.APPLIED));
        }
        return "redirect:/candidate";
    }

    // ---------- Симуляция AI-интервью ----------

    @GetMapping("/interview/{appId}")
    public String interview(@PathVariable Long appId, HttpSession session, Model model) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/";

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

    /**
     * «Завершить интервью»: система генерирует фейковый отчёт AI-агента
     * и сразу делает его доступным работодателю.
     */
    @PostMapping("/interview/{appId}/complete")
    public String completeInterview(@PathVariable Long appId, HttpSession session) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/";

        JobApplication app = ownApplication(appId, candidate);
        if (app == null) return "redirect:/candidate";

        if (resultRepository.findByApplicationId(app.getId()).isEmpty()) {
            String languageScore = LANGUAGE_LEVELS[random.nextInt(LANGUAGE_LEVELS.length)];
            String comment = AI_COMMENTS[random.nextInt(AI_COMMENTS.length)];
            String recordUrl = "https://meet.google.com/rec/truehire-" + app.getId();

            resultRepository.save(
                    new InterviewResult(app.getId(), languageScore, comment, recordUrl, true));

            app.setStatus(ApplicationStatus.INTERVIEW_COMPLETED);
            applicationRepository.save(app);
        }
        return "redirect:/candidate";
    }

    // ---------- Визовое сопровождение ----------

    @GetMapping("/visa/{appId}")
    public String visa(@PathVariable Long appId, HttpSession session, Model model) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/";

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
        if (candidate == null) return "redirect:/";

        JobApplication app = ownApplication(appId, candidate);
        if (app == null || app.getStatus() != ApplicationStatus.OFFER_GRANTED) {
            return "redirect:/candidate";
        }

        // В прототипе файлы не сохраняются на диск — фиксируем только метаданные
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

    /** Демо-кнопка: продвигает визовый процесс на следующий этап. */
    @PostMapping("/visa/{appId}/advance")
    public String advanceVisa(@PathVariable Long appId, HttpSession session) {
        User candidate = currentCandidate(session);
        if (candidate == null) return "redirect:/";

        JobApplication app = ownApplication(appId, candidate);
        if (app == null) return "redirect:/candidate";

        VisaStatus[] statuses = VisaStatus.values();
        for (VisaDocument doc : documentRepository.findByApplicationId(app.getId())) {
            int next = doc.getStatus().ordinal() + 1;
            if (next < statuses.length) {
                doc.setStatus(statuses[next]);
                documentRepository.save(doc);
            }
        }
        return "redirect:/candidate/visa/" + app.getId();
    }
}
