package com.truehire.controller;

import com.truehire.model.ApplicationStatus;
import com.truehire.model.InterviewResult;
import com.truehire.model.JobApplication;
import com.truehire.model.JobVacancy;
import com.truehire.model.VacancyStatus;
import com.truehire.repository.InterviewResultRepository;
import com.truehire.repository.JobApplicationRepository;
import com.truehire.repository.JobVacancyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Controller
public class PublicVacancyController {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private final JobVacancyRepository vacancyRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewResultRepository resultRepository;

    public PublicVacancyController(JobVacancyRepository vacancyRepository,
                                   JobApplicationRepository applicationRepository,
                                   InterviewResultRepository resultRepository) {
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.resultRepository = resultRepository;
    }

    @GetMapping("/vacancies")
    public String vacancies(Model model) {
        model.addAttribute("vacancies", vacancyRepository.findByStatus(VacancyStatus.PUBLISHED));
        return "vacancies";
    }

    @GetMapping("/vacancies/{id}")
    public String vacancy(@PathVariable Long id, Model model) {
        model.addAttribute("vacancy", publishedVacancy(id));
        return "vacancy";
    }

    @GetMapping("/vacancies/{id}/apply")
    public String applicationForm(@PathVariable Long id, Model model) {
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
                        Model model) {
        JobVacancy vacancy = publishedVacancy(id);
        String normalizedEmail = normalizeEmail(email);
        String error = validateContact(firstName, lastName, phone, normalizedEmail);
        if (error == null && applicationRepository
                .findByVacancyIdAndGuestEmailIgnoreCase(id, normalizedEmail).isPresent()) {
            error = "Отклик с этой почтой уже отправлен. Используйте ссылку на интервью из первого отклика.";
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
        applicationRepository.save(application);
        return "redirect:/guest/interview/" + token;
    }

    @GetMapping("/guest/interview/{token}")
    public String interview(@PathVariable String token, Model model) {
        JobApplication application = guestApplication(token);
        if (application.getStatus() == ApplicationStatus.INTERVIEW_COMPLETED) {
            model.addAttribute("guestApplication", application);
            model.addAttribute("vacancy", vacancyRepository.findById(application.getVacancyId()).orElse(null));
            return "guest-complete";
        }
        model.addAttribute("guestApplication", application);
        model.addAttribute("vacancy", vacancyRepository.findById(application.getVacancyId()).orElse(null));
        return "guest-interview";
    }

    @PostMapping("/guest/interview/{token}/complete")
    public String completeInterview(@PathVariable String token) {
        JobApplication application = guestApplication(token);
        if (application.getStatus() == ApplicationStatus.INTERVIEW_PENDING
                && resultRepository.findByApplicationId(application.getId()).isEmpty()) {
            InterviewResult result = new InterviewResult(
                    application.getId(),
                    "Оценка сформирована",
                    "Ответы кандидата переданы работодателю для рассмотрения.",
                    null,
                    true);
            result.setSummary("Кандидат завершил структурированное AI-интервью по вакансии.");
            result.setTranscript("Транскрибация интервью будет добавлена подключённой системой интервью.");
            result.setConclusion("Отклик и контактные данные доступны рекрутеру для принятия решения.");
            resultRepository.save(result);

            application.setStatus(ApplicationStatus.INTERVIEW_COMPLETED);
            applicationRepository.save(application);
        }
        return "redirect:/guest/interview/" + token;
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

    private String validateContact(String firstName, String lastName, String phone, String email) {
        if (clean(firstName).isEmpty() || clean(lastName).isEmpty()) {
            return "Укажите имя и фамилию.";
        }
        if (clean(firstName).length() > 100 || clean(lastName).length() > 100) {
            return "Имя и фамилия не должны превышать 100 символов.";
        }
        if (!INTERNATIONAL_PHONE.matcher(clean(phone)).matches()) {
            return "Телефон должен быть в международном формате, например +77001234567.";
        }
        if (!EMAIL.matcher(email).matches()) {
            return "Укажите корректную электронную почту.";
        }
        if (email.length() > 255) {
            return "Электронная почта не должна превышать 255 символов.";
        }
        return null;
    }

    private String normalizeEmail(String email) {
        return clean(email).toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
