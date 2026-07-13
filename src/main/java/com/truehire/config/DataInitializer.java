package com.truehire.config;

import com.truehire.controller.AuthController;
import com.truehire.model.*;
import com.truehire.repository.*;
import com.truehire.service.PasswordService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Mock-данные при старте, чтобы демо выглядело «живым»:
 * работодатель с тремя вакансиями и кандидат Anna,
 * которая уже прошла AI-интервью (видна в откликах работодателя).
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final JobVacancyRepository vacancyRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewResultRepository resultRepository;
    private final PasswordService passwordService;

    public DataInitializer(UserRepository userRepository,
                           JobVacancyRepository vacancyRepository,
                           JobApplicationRepository applicationRepository,
                           InterviewResultRepository resultRepository,
                           PasswordService passwordService) {
        this.userRepository = userRepository;
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.resultRepository = resultRepository;
        this.passwordService = passwordService;
    }

    @Override
    public void run(String... args) {
        userRepository.findAll().stream()
                .filter(user -> passwordService.needsUpgrade(user.getPassword()))
                .forEach(user -> {
                    user.setPassword(passwordService.encode(user.getPassword()));
                    userRepository.save(user);
                });

        if (userRepository.count() != 0
                || vacancyRepository.count() != 0
                || applicationRepository.count() != 0
                || resultRepository.count() != 0) {
            return;
        }

        User employer = userRepository.save(new User(
                AuthController.DEMO_EMPLOYER_EMAIL, passwordService.encode("demo123"), "Hans Weber (Berlin Tech GmbH)", Role.EMPLOYER));

        userRepository.save(new User(
                AuthController.DEMO_CANDIDATE_EMAIL, passwordService.encode("demo123"), "Алексей Петров", Role.CANDIDATE));

        User anna = userRepository.save(new User(
                "anna.k@example.com", passwordService.encode("demo123"), "Anna Kowalska", Role.CANDIDATE));

        JobVacancy javaDev = vacancyRepository.save(new JobVacancy(
                "Java-разработчик (Берлин)",
                "Разработка backend-сервисов логистической платформы на Java 17 и Spring Boot. "
                        + "Команда из 8 инженеров, международное окружение.",
                "Зарплата 65 000–80 000 € в год, релокационный пакет, помощь с жильём первые 3 месяца, "
                        + "Blue Card, гибридный формат.",
                "Опыт с Java от 3 лет, Spring Boot, SQL. Немецкий от B1 или английский B2.",
                employer.getId()));

        vacancyRepository.save(new JobVacancy(
                "Медсестра в клинику (Мюнхен)",
                "Работа в отделении терапии частной клиники. Сменный график, современное оборудование, "
                        + "программа адаптации для иностранных специалистов.",
                "Зарплата от 3 200 € в месяц, оплата признания диплома (Anerkennung), "
                        + "помощь с визой и жильём.",
                "Медицинское образование, опыт от 2 лет, немецкий от B1 (поможем дотянуть до B2).",
                employer.getId()));

        vacancyRepository.save(new JobVacancy(
                "Инженер-электрик (Гамбург)",
                "Монтаж и обслуживание промышленного оборудования на верфи. "
                        + "Обучение и сертификация за счёт работодателя.",
                "Зарплата от 4 000 € в месяц, оплачиваемая переподготовка, служебное жильё на первый год.",
                "Профильное образование или опыт от 4 лет. Немецкий A2+, готовность учить язык.",
                employer.getId()));

        // Anna уже откликнулась и прошла AI-интервью — работодатель сразу видит отклик с отчётом
        JobApplication annaApp = applicationRepository.save(new JobApplication(
                javaDev.getId(), anna.getId(), ApplicationStatus.INTERVIEW_COMPLETED));

        InterviewResult result = new InterviewResult(
                annaApp.getId(),
                "Немецкий: B2 (Upper-Intermediate)",
                "Кандидат адекватен, отвечает уверенно и по существу. Опыт работы с Java и Spring "
                        + "подтверждён в ходе технической части беседы. Мотивация к релокации высокая. "
                        + "Рекомендован к найму.",
                "https://meet.google.com/rec/truehire-" + annaApp.getId(),
                true);
        result.setSummary("Anna подтвердила опыт Java-разработки, готовность к релокации и знание немецкого языка на уровне B2.");
        result.setTranscript("AI-интервьюер: Расскажите о вашем опыте со Spring Boot.\n"
                + "Anna: Последние четыре года я разрабатываю backend-сервисы на Java и Spring Boot.\n"
                + "AI-интервьюер: Когда вы готовы к релокации?\n"
                + "Anna: После оффера смогу переехать в течение шести-восьми недель.");
        result.setConclusion("Рекомендована к следующему этапу с техническим интервью команды.");
        resultRepository.save(result);
    }
}
