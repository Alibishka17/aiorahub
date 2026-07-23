package com.truehire.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truehire.model.JobApplication;
import com.truehire.model.JobVacancy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class InterviewConfigurationService {

    public static final int MAX_QUESTIONS = 8;
    private static final Set<String> LANGUAGES = Set.of("ru", "en");
    private static final Set<String> MODES = Set.of("builder", "full");
    private static final Set<String> LEVELS = Set.of("basic", "intermediate", "advanced", "expert");
    private static final Set<String> LANGUAGE_LEVELS = Set.of("A1", "A2", "B1", "B2", "C1", "C2");
    private static final Pattern UNSUPPORTED_CRITERION = Pattern.compile(
            "(?iu).*(возраст|пол|раса|религи|беремен|здоров|инвалид|внешност|семейн|"
                    + "age|gender|race|religion|pregnan|health|disab|appearance|marital).*");

    private static final List<CriterionOption> CRITERIA = List.of(
            new CriterionOption("language", "Language proficiency", true),
            new CriterionOption("professional_knowledge", "Professional knowledge", false),
            new CriterionOption("relevant_experience", "Relevant experience", false),
            new CriterionOption("communication", "Communication", false),
            new CriterionOption("answer_structure", "Structured answers", false),
            new CriterionOption("motivation", "Motivation", false),
            new CriterionOption("customer_focus", "Customer focus", false),
            new CriterionOption("problem_solving", "Problem solving", false),
            new CriterionOption("stress_resilience", "Stress resilience", false),
            new CriterionOption("leadership", "Leadership", false)
    );
    private static final Map<String, CriterionOption> CRITERIA_BY_KEY = CRITERIA.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(CriterionOption::key, value -> value));

    private final ObjectMapper objectMapper;

    public InterviewConfigurationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<CriterionOption> criterionOptions() {
        return CRITERIA;
    }

    public void apply(JobVacancy vacancy, Map<String, String[]> parameters) {
        String mode = value(parameters, "interviewMode");
        String language = value(parameters, "interviewLanguage");
        if (!MODES.contains(mode)) throw new IllegalArgumentException("interview.mode");
        if (!LANGUAGES.contains(language)) throw new IllegalArgumentException("interview.language");

        List<String> questions = values(parameters, "interviewQuestions").stream()
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
        if (questions.isEmpty() || questions.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("interview.questions");
        }
        if (questions.stream().anyMatch(value -> value.length() > 1000)) {
            throw new IllegalArgumentException("interview.question_length");
        }

        String customPrompt = clean(value(parameters, "interviewCustomPrompt"), 10000);
        if ("full".equals(mode) && customPrompt == null) {
            throw new IllegalArgumentException("interview.full_prompt_required");
        }

        List<AssessmentCriterion> criteria = new ArrayList<>();
        for (String key : values(parameters, "criteria")) {
            CriterionOption option = CRITERIA_BY_KEY.get(key);
            if (option == null) continue;
            String level = value(parameters, "criterionLevel_" + key);
            validateLevel(option.language(), level);
            criteria.add(new AssessmentCriterion(key, option.label(), level, false));
        }
        List<String> customNames = values(parameters, "customCriterionNames");
        List<String> customLevels = values(parameters, "customCriterionLevels");
        for (int index = 0; index < customNames.size(); index++) {
            String label = customNames.get(index).trim();
            if (label.isBlank()) continue;
            if (!isAssessableCriterion(label)) {
                throw new IllegalArgumentException("interview.custom_criterion");
            }
            String level = index < customLevels.size() ? customLevels.get(index) : "";
            validateLevel(false, level);
            criteria.add(new AssessmentCriterion("custom_" + (index + 1), label, level, true));
        }
        if (criteria.isEmpty()) throw new IllegalArgumentException("interview.criteria");

        vacancy.setInterviewMode(mode);
        vacancy.setInterviewLanguage(language);
        vacancy.setInterviewIntro(clean(value(parameters, "interviewIntro"), 2000));
        vacancy.setInterviewOutro(clean(value(parameters, "interviewOutro"), 2000));
        vacancy.setInterviewCustomPrompt(customPrompt);
        vacancy.setInterviewQuestionsJson(write(questions));
        vacancy.setAssessmentCriteriaJson(write(criteria));
        vacancy.setAllowFollowUpQuestions(flag(parameters, "allowFollowUpQuestions"));
        vacancy.setAllowQuestionRephrasing(flag(parameters, "allowQuestionRephrasing"));
        vacancy.setAllowQuestionReordering(flag(parameters, "allowQuestionReordering"));
        vacancy.setAllowQuestionSkipping(flag(parameters, "allowQuestionSkipping"));
        vacancy.setAllowVacancyQuestions(flag(parameters, "allowVacancyQuestions"));
    }

    public void snapshot(JobApplication application, JobVacancy vacancy, Locale dashboardLocale) {
        application.setSummaryLanguage("kk".equals(dashboardLocale.getLanguage()) ? "kk" : "ru");
        if (!vacancy.hasCustomInterviewConfiguration()) return;
        List<String> questions = read(vacancy.getInterviewQuestionsJson(), new TypeReference<>() {});
        List<AssessmentCriterion> criteria = read(vacancy.getAssessmentCriteriaJson(), new TypeReference<>() {});
        InterviewSnapshot snapshot = new InterviewSnapshot(
                vacancy.getInterviewMode(), vacancy.getInterviewLanguage(),
                defaultIntro(vacancy), defaultOutro(vacancy), vacancy.getInterviewCustomPrompt(),
                questions, criteria,
                new FreedomSettings(vacancy.isAllowFollowUpQuestions(),
                        vacancy.isAllowQuestionRephrasing(), vacancy.isAllowQuestionReordering(),
                        vacancy.isAllowQuestionSkipping(), vacancy.isAllowVacancyQuestions()),
                new VacancyContext(vacancy.getTitle(), vacancy.getDescription(), vacancy.getConditions(),
                        vacancy.getCandidateRequirements(), vacancy.getCategory(), vacancy.getCity(),
                        vacancy.getCountry(), vacancy.getSalaryMin(), vacancy.getSalaryMax(),
                        vacancy.getSalaryCurrency(), vacancy.getRequiredDocuments(), vacancy.getAdditionalInfo()),
                application.getSummaryLanguage(), 300);
        application.setInterviewConfigSnapshot(write(snapshot));
    }

    public boolean isAssessableCriterion(String label) {
        String normalized = label == null ? "" : label.trim();
        return normalized.length() >= 3 && normalized.length() <= 120
                && !UNSUPPORTED_CRITERION.matcher(normalized).matches();
    }

    private String defaultIntro(JobVacancy vacancy) {
        if (vacancy.getInterviewIntro() != null && !vacancy.getInterviewIntro().isBlank()) {
            return vacancy.getInterviewIntro();
        }
        return "ru".equals(vacancy.getInterviewLanguage())
                ? "Здравствуйте! Я проведу короткое интервью по вакансии «" + vacancy.getTitle()
                    + "». Оно займёт не более пяти минут. Готовы начать?"
                : "Hello! I will conduct a short interview for the \"" + vacancy.getTitle()
                    + "\" vacancy. It will take no more than five minutes. Are you ready to begin?";
    }

    private String defaultOutro(JobVacancy vacancy) {
        if (vacancy.getInterviewOutro() != null && !vacancy.getInterviewOutro().isBlank()) {
            return vacancy.getInterviewOutro();
        }
        return "ru".equals(vacancy.getInterviewLanguage())
                ? "Спасибо за ваши ответы. Работодатель ознакомится с результатами интервью и свяжется с вами по дальнейшим шагам."
                : "Thank you for your answers. The employer will review the interview results and contact you about the next steps.";
    }

    private void validateLevel(boolean languageCriterion, String level) {
        if (!(languageCriterion ? LANGUAGE_LEVELS : LEVELS).contains(level)) {
            throw new IllegalArgumentException("interview.level");
        }
    }

    private boolean flag(Map<String, String[]> parameters, String name) {
        return parameters.containsKey(name);
    }

    private String value(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        return values == null || values.length == 0 ? "" : values[0].trim();
    }

    private List<String> values(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        return values == null ? List.of() : Arrays.asList(values);
    }

    private String clean(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) return null;
        if (normalized.length() > maxLength) throw new IllegalArgumentException("interview.field_length");
        return normalized;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize interview configuration", ex);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid interview configuration", ex);
        }
    }

    public record CriterionOption(String key, String label, boolean language) {}
    public record AssessmentCriterion(String key, String label, String level, boolean custom) {}
    public record FreedomSettings(boolean followUpQuestions, boolean rephraseQuestions,
                                  boolean reorderQuestions, boolean skipQuestions,
                                  boolean answerVacancyQuestions) {}
    public record VacancyContext(String title, String description, String conditions,
                                 String requirements, String category, String city, String country,
                                 Long salaryMin, Long salaryMax, String salaryCurrency,
                                 String requiredDocuments, String additionalInfo) {}
    public record InterviewSnapshot(String mode, String language, String intro, String outro,
                                    String customPrompt, List<String> questions,
                                    List<AssessmentCriterion> criteria, FreedomSettings freedoms,
                                    VacancyContext vacancy, String summaryLanguage,
                                    int maxDurationSeconds) {}
}
