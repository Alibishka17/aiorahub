package com.truehire;

import com.truehire.model.*;
import com.truehire.repository.*;
import com.truehire.service.HrmeInterviewClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-integration;DB_CLOSE_DELAY=-1",
        "app.upload-dir=target/test-admin-uploads",
        "app.admin.password-hash=$2y$10$JuGQiSzjVSGWLmTUNL47Te3uwlBZG6VgqbAeYkCO3QtTXVWGDT4Du",
        "app.hrme.service-token=test-integration-token"
})
@AutoConfigureMockMvc
class AdminAndIntegrationWorkflowTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JobVacancyRepository vacancyRepository;
    @Autowired JobApplicationRepository applicationRepository;
    @Autowired InterviewResultRepository resultRepository;
    @Autowired SiteVisitRepository visitRepository;

    @MockBean HrmeInterviewClient hrmeClient;

    @Test
    void adminLoginProtectsDashboardAndCanCreateVacancy() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        mockMvc.perform(post("/admin/login").with(csrf())
                        .param("username", "Admin")
                        .param("password", "wrong"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid username")));

        var login = mockMvc.perform(post("/admin/login").with(csrf())
                        .param("username", "Admin")
                        .param("password", "test-admin-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        User employer = userRepository.save(new User(
                "admin-owner@example.com", "unused", "Admin Owner", Role.EMPLOYER));

        mockMvc.perform(post("/admin/vacancies").with(csrf()).session(session)
                        .param("employerId", employer.getId().toString())
                        .param("title", "Welder in Poland")
                        .param("description", "Production welding")
                        .param("conditions", "Relocation to Poland")
                        .param("candidateRequirements", "One year of experience")
                        .param("interviewTemplateId", "hrme-warsaw"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin#vacancies"));

        JobVacancy vacancy = vacancyRepository.findByEmployerId(employer.getId()).get(0);
        assertThat(vacancy.getInterviewTemplateId()).isEqualTo("hrme-warsaw");
        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Welder in Poland")));
    }

    @Test
    void launchesIdempotentHrmeInterviewAndAcceptsCompletionCallback() throws Exception {
        User employer = userRepository.save(new User(
                "integration-owner@example.com", "unused", "Integration Owner", Role.EMPLOYER));
        User candidate = userRepository.save(new User(
                "integration-candidate@example.com", "unused", "Integration Candidate", Role.CANDIDATE));
        JobVacancy vacancy = vacancyRepository.save(new JobVacancy(
                "Welder in Poland", "Welding", "Relocation", "Experience", employer.getId()));
        JobApplication application = applicationRepository.save(new JobApplication(
                vacancy.getId(), candidate.getId(), ApplicationStatus.APPLIED));
        when(hrmeClient.createInterview(anyLong(), anyLong(), any(), any()))
                .thenReturn(new HrmeInterviewClient.InterviewSession(
                        901L, "access-token", "https://hrme.ai/interview/access-token"));
        MockHttpSession candidateSession = new MockHttpSession();
        candidateSession.setAttribute("userId", candidate.getId());

        mockMvc.perform(post("/candidate/applications/{id}/interview", application.getId())
                        .with(csrf()).session(candidateSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://hrme.ai/interview/access-token"));

        JobApplication launched = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(launched.getExternalInterviewId()).isEqualTo(901L);
        assertThat(launched.getStatus()).isEqualTo(ApplicationStatus.INTERVIEW_PENDING);

        mockMvc.perform(get("/api/integrations/hrme/catalog"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/integrations/hrme/catalog")
                        .header("Authorization", "Bearer test-integration-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"interview_id\":901")));

        mockMvc.perform(post("/api/integrations/hrme/interviews/{id}/complete", application.getId())
                        .header("Authorization", "Bearer test-integration-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interview_id\":901,\"summary\":\"Strong welding experience\",\"transcript\":\"Candidate: five years\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        JobApplication completed = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ApplicationStatus.INTERVIEW_COMPLETED);
        assertThat(resultRepository.findByApplicationId(application.getId()).orElseThrow().getSummary())
                .isEqualTo("Strong welding experience");
    }

    @Test
    void tracksFirstPartyVisitsWithoutTrackingAdminOrApiPages() throws Exception {
        long before = visitRepository.count();
        mockMvc.perform(get("/vacancies").header("User-Agent", "Mozilla/5.0"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/login").header("User-Agent", "Mozilla/5.0"))
                .andExpect(status().isOk());
        assertThat(visitRepository.count()).isEqualTo(before + 1);
    }
}
