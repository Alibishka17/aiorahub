package com.truehire;

import com.truehire.model.JobVacancy;
import com.truehire.model.Role;
import com.truehire.model.User;
import com.truehire.model.VacancyStatus;
import com.truehire.repository.JobVacancyRepository;
import com.truehire.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-workflow;DB_CLOSE_DELAY=-1",
        "app.upload-dir=target/test-uploads"
})
@AutoConfigureMockMvc
class AccountWorkflowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobVacancyRepository vacancyRepository;

    @Test
    void registersCandidateWithContactPreferencesAndHashedPassword() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "Айжан")
                        .param("lastName", "Серикова")
                        .param("phone", "+77001234567")
                        .param("telegramEnabled", "true")
                        .param("email", "aizhan@example.com")
                        .param("password", "securePass123")
                        .param("role", "CANDIDATE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/candidate"));

        User user = userRepository.findByEmailIgnoreCase("aizhan@example.com").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.CANDIDATE);
        assertThat(user.getPhone()).isEqualTo("+77001234567");
        assertThat(user.isTelegramEnabled()).isTrue();
        assertThat(user.isWhatsappEnabled()).isFalse();
        assertThat(user.getPassword()).startsWith("$2").isNotEqualTo("securePass123");
    }

    @Test
    void rejectsPhoneOutsideInternationalFormat() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "Test")
                        .param("lastName", "User")
                        .param("phone", "87001234567")
                        .param("email", "invalid-phone@example.com")
                        .param("password", "securePass123")
                        .param("role", "CANDIDATE"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("международном формате")));

        assertThat(userRepository.existsByEmailIgnoreCase("invalid-phone@example.com")).isFalse();
    }

    @Test
    void rejectsRegistrationWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/register")
                        .param("firstName", "No")
                        .param("lastName", "Token")
                        .param("phone", "+77001112233")
                        .param("email", "no-token@example.com")
                        .param("password", "securePass123")
                        .param("role", "CANDIDATE"))
                .andExpect(status().isForbidden());

        assertThat(userRepository.existsByEmailIgnoreCase("no-token@example.com")).isFalse();
    }

    @Test
    void candidateCanUploadCvToProtectedStorage() throws Exception {
        MvcResult registration = mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "CV")
                        .param("lastName", "Owner")
                        .param("phone", "+77005554433")
                        .param("email", "cv-owner@example.com")
                        .param("password", "securePass123")
                        .param("role", "CANDIDATE"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);
        MockMultipartFile cv = new MockMultipartFile(
                "cv", "resume.pdf", "application/pdf", "%PDF-1.4 test".getBytes());

        mockMvc.perform(multipart("/candidate/cv").file(cv).with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/candidate#profile"));

        User candidate = userRepository.findByEmailIgnoreCase("cv-owner@example.com").orElseThrow();
        assertThat(candidate.getCvFileName()).isEqualTo("resume.pdf");
        assertThat(candidate.getCvStorageKey()).endsWith(".pdf");
    }

    @Test
    void employerCanCreateAndHideOwnVacancyWhileCandidateCannotOpenEmployerCabinet() throws Exception {
        MvcResult registration = mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "Recruiter")
                        .param("lastName", "One")
                        .param("phone", "+971501234567")
                        .param("whatsappEnabled", "true")
                        .param("email", "recruiter-one@example.com")
                        .param("password", "securePass123")
                        .param("role", "EMPLOYER"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession employerSession = (MockHttpSession) registration.getRequest().getSession(false);

        mockMvc.perform(post("/employer/vacancies").with(csrf())
                        .session(employerSession)
                        .param("title", "QA Engineer")
                        .param("description", "Test product workflows")
                        .param("conditions", "Remote")
                        .param("candidateRequirements", "Three years of experience"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employer"));

        User employer = userRepository.findByEmailIgnoreCase("recruiter-one@example.com").orElseThrow();
        JobVacancy vacancy = vacancyRepository.findByEmployerId(employer.getId()).stream()
                .filter(v -> v.getTitle().equals("QA Engineer"))
                .findFirst()
                .orElseThrow();
        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.PUBLISHED);

        mockMvc.perform(post("/employer/vacancies/{id}/hide", vacancy.getId()).with(csrf()).session(employerSession))
                .andExpect(status().is3xxRedirection());
        assertThat(vacancyRepository.findById(vacancy.getId()).orElseThrow().getStatus())
                .isEqualTo(VacancyStatus.HIDDEN);

        MvcResult candidateRegistration = mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "Candidate")
                        .param("lastName", "Two")
                        .param("phone", "+77007654321")
                        .param("email", "candidate-two@example.com")
                        .param("password", "securePass123")
                        .param("role", "CANDIDATE"))
                .andReturn();
        MockHttpSession candidateSession = (MockHttpSession) candidateRegistration.getRequest().getSession(false);

        mockMvc.perform(get("/employer").session(candidateSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?role=EMPLOYER"));
    }
}
