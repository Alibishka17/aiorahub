package com.truehire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truehire.model.JobApplication;
import com.truehire.model.JobVacancy;
import com.truehire.service.InterviewConfigurationService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewConfigurationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InterviewConfigurationService service =
            new InterviewConfigurationService(objectMapper);

    @Test
    void snapshotsVacancyConfigurationForEachApplication() throws Exception {
        JobVacancy vacancy = new JobVacancy(
                "Account manager", "Manage enterprise clients", "Remote",
                "Three years of sales experience", 7L);
        Map<String, String[]> parameters = validParameters();
        service.apply(vacancy, parameters);

        JobApplication first = new JobApplication(10L, 20L, com.truehire.model.ApplicationStatus.APPLIED);
        service.snapshot(first, vacancy, Locale.forLanguageTag("kk"));
        JsonNode firstSnapshot = objectMapper.readTree(first.getInterviewConfigSnapshot());

        parameters.put("interviewQuestions", new String[]{"What changed in your last role?"});
        service.apply(vacancy, parameters);
        JobApplication second = new JobApplication(10L, 21L, com.truehire.model.ApplicationStatus.APPLIED);
        service.snapshot(second, vacancy, Locale.forLanguageTag("ru"));

        assertThat(firstSnapshot.path("questions").get(0).asText())
                .isEqualTo("Tell me about your enterprise sales experience.");
        assertThat(objectMapper.readTree(second.getInterviewConfigSnapshot())
                .path("questions").get(0).asText()).isEqualTo("What changed in your last role?");
        assertThat(first.getSummaryLanguage()).isEqualTo("kk");
        assertThat(second.getSummaryLanguage()).isEqualTo("ru");
    }

    @Test
    void rejectsUnassessableCustomCriterionAndMissingQuestions() {
        JobVacancy vacancy = new JobVacancy("Role", "Description", "Conditions", "Requirements", 1L);
        Map<String, String[]> unsupported = validParameters();
        unsupported.put("customCriterionNames", new String[]{"Candidate age"});
        unsupported.put("customCriterionLevels", new String[]{"advanced"});
        assertThatThrownBy(() -> service.apply(vacancy, unsupported))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("interview.custom_criterion");

        Map<String, String[]> noQuestions = validParameters();
        noQuestions.put("interviewQuestions", new String[]{"", "  "});
        assertThatThrownBy(() -> service.apply(vacancy, noQuestions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("interview.questions");
    }

    private Map<String, String[]> validParameters() {
        Map<String, String[]> parameters = new LinkedHashMap<>();
        parameters.put("interviewMode", new String[]{"full"});
        parameters.put("interviewLanguage", new String[]{"en"});
        parameters.put("interviewCustomPrompt", new String[]{"Keep a professional tone."});
        parameters.put("interviewQuestions",
                new String[]{"Tell me about your enterprise sales experience."});
        parameters.put("criteria", new String[]{"relevant_experience"});
        parameters.put("criterionLevel_relevant_experience", new String[]{"advanced"});
        parameters.put("allowFollowUpQuestions", new String[]{"true"});
        return parameters;
    }
}
