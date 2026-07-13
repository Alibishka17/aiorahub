package com.truehire;

import com.truehire.model.JobVacancy;
import com.truehire.model.ApplicationStatus;
import com.truehire.model.JobApplication;
import com.truehire.model.InterviewResult;
import com.truehire.model.Role;
import com.truehire.model.User;
import com.truehire.model.VacancyStatus;
import com.truehire.repository.JobVacancyRepository;
import com.truehire.repository.JobApplicationRepository;
import com.truehire.repository.InterviewResultRepository;
import com.truehire.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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

    @Autowired
    private JobApplicationRepository applicationRepository;

    @Autowired
    private InterviewResultRepository resultRepository;

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("international phone format")));

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

    @Test
    void guestCanApplyWithoutCreatingAccount() throws Exception {
        MvcResult employerRegistration = mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "Guest")
                        .param("lastName", "Recruiter")
                        .param("phone", "+971509876543")
                        .param("email", "guest-recruiter@example.com")
                        .param("password", "securePass123")
                        .param("role", "EMPLOYER"))
                .andReturn();
        MockHttpSession employerSession = (MockHttpSession) employerRegistration.getRequest().getSession(false);
        User employer = userRepository.findByEmailIgnoreCase("guest-recruiter@example.com").orElseThrow();
        JobVacancy vacancy = vacancyRepository.save(new JobVacancy(
                "Public Product Designer",
                "Design recruiting workflows",
                "Remote",
                "Product design experience",
                employer.getId()));
        long usersBefore = userRepository.count();

        mockMvc.perform(get("/vacancies"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Public Product Designer")));

        MvcResult applicationResult = mockMvc.perform(post("/vacancies/{id}/apply", vacancy.getId()).with(csrf())
                        .param("firstName", "Amina")
                        .param("lastName", "Khan")
                        .param("phone", "+77001239876")
                        .param("telegramEnabled", "true")
                        .param("whatsappEnabled", "true")
                        .param("email", "guest-candidate@example.com"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        JobApplication application = applicationRepository
                .findByVacancyIdAndGuestEmailIgnoreCase(vacancy.getId(), "guest-candidate@example.com")
                .orElseThrow();
        assertThat(application.getCandidateId()).isNull();
        assertThat(application.getGuestAccessToken()).hasSize(32);
        assertThat(application.isGuestTelegramEnabled()).isTrue();
        assertThat(application.isGuestWhatsappEnabled()).isTrue();
        assertThat(applicationResult.getResponse().getRedirectedUrl())
                .isEqualTo("/guest/application/" + application.getGuestAccessToken());

        mockMvc.perform(get("/guest/application/{token}", application.getGuestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Application sent")));
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);

        mockMvc.perform(get("/employer").session(employerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Not registered")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("guest-candidate@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Write to candidate")));
    }

    @Test
    void guestApplicationRejectsPhoneOutsideInternationalFormat() throws Exception {
        JobVacancy vacancy = createPublishedVacancy("Phone validation");

        mockMvc.perform(post("/vacancies/{id}/apply", vacancy.getId()).with(csrf())
                        .param("firstName", "Invalid")
                        .param("lastName", "Phone")
                        .param("phone", "87001234567")
                        .param("email", "guest-invalid-phone@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("international phone format")));

        assertThat(applicationRepository
                .findByVacancyIdAndGuestEmailIgnoreCase(vacancy.getId(), "guest-invalid-phone@example.com"))
                .isEmpty();
    }

    @Test
    void registeredCandidateOpensVacancyAndStartsInterviewWithoutGuestForm() throws Exception {
        MvcResult registration = mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "Direct")
                        .param("lastName", "Candidate")
                        .param("phone", "+77002223344")
                        .param("email", "direct-candidate@example.com")
                        .param("password", "securePass123")
                        .param("role", "CANDIDATE"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);
        User candidate = userRepository.findByEmailIgnoreCase("direct-candidate@example.com").orElseThrow();
        JobVacancy vacancy = createPublishedVacancy("Direct application");

        mockMvc.perform(get("/candidate").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Open vacancy")));

        mockMvc.perform(get("/vacancies/{id}", vacancy.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Apply")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your profile is complete")));

        mockMvc.perform(get("/vacancies/{id}/apply", vacancy.getId()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vacancies/" + vacancy.getId()));

        MvcResult start = mockMvc.perform(post("/candidate/apply/{id}", vacancy.getId())
                        .with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        JobApplication application = applicationRepository
                .findByVacancyIdAndCandidateId(vacancy.getId(), candidate.getId())
                .orElseThrow();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(start.getResponse().getRedirectedUrl())
                .isEqualTo("/candidate#history");

        mockMvc.perform(post("/candidate/apply/{id}", vacancy.getId()).with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/candidate#history"));
        assertThat(applicationRepository.findByCandidateId(candidate.getId()))
                .filteredOn(app -> app.getVacancyId().equals(vacancy.getId()))
                .hasSize(1);

        mockMvc.perform(get("/vacancies/{id}", vacancy.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Application sent")));
    }

    @Test
    void selectsSupportedBrowserLanguageAndPersistsManualChoice() throws Exception {
        mockMvc.perform(get("/").header("Accept-Language", "ru-RU,ru;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Таланты находят работу")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"ru\"")));

        mockMvc.perform(get("/").header("Accept-Language", "kk-KZ,kk;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Мамандар жұмыс табады")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"kk\"")));

        MvcResult languageChange = mockMvc.perform(get("/login?lang=kk").header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Кабинетке кіру")))
                .andExpect(cookie().value("AIORAHUB_LANG", "kk"))
                .andReturn();
        Cookie languageCookie = languageChange.getResponse().getCookie("AIORAHUB_LANG");

        mockMvc.perform(get("/register").cookie(languageCookie).header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Жеке кабинет ашу")));
    }

    @Test
    void rendersLocalizedCandidateEmployerAndVisaWorkspaces() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User employer = userRepository.save(new User(
                "workspace-employer-" + suffix + "@example.com", "not-used", "Workspace Employer", Role.EMPLOYER));
        User candidate = userRepository.save(new User(
                "workspace-candidate-" + suffix + "@example.com", "not-used", "Workspace Candidate", Role.CANDIDATE));
        JobVacancy vacancy = vacancyRepository.save(new JobVacancy(
                "Backend Engineer", "Build services", "Remote", "Java experience", employer.getId()));
        JobApplication application = applicationRepository.save(new JobApplication(
                vacancy.getId(), candidate.getId(), ApplicationStatus.INTERVIEW_COMPLETED));
        InterviewResult result = new InterviewResult(application.getId(), "B2", "Clear communication", null, true);
        result.setSummary("Relevant experience");
        result.setTranscript("Interview transcript");
        result.setConclusion("Proceed to next stage");
        resultRepository.save(result);

        MockHttpSession candidateSession = new MockHttpSession();
        candidateSession.setAttribute("userId", candidate.getId());
        mockMvc.perform(get("/candidate").session(candidateSession).header("Accept-Language", "kk-KZ"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Менің өтінімдерім")));

        MockHttpSession employerSession = new MockHttpSession();
        employerSession.setAttribute("userId", employer.getId());
        mockMvc.perform(get("/employer").session(employerSession).header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Open transcript and conclusion")));

        application.setStatus(ApplicationStatus.OFFER_GRANTED);
        applicationRepository.save(application);
        mockMvc.perform(get("/candidate/visa/{id}", application.getId())
                        .session(candidateSession).header("Accept-Language", "ru-RU"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Прогресс получения визы")));
    }

    private JobVacancy createPublishedVacancy(String title) {
        String suffix = UUID.randomUUID().toString();
        User employer = userRepository.save(new User(
                "test-employer-" + suffix + "@example.com", "not-used", "Test Employer", Role.EMPLOYER));
        return vacancyRepository.save(new JobVacancy(
                title, "Description", "Remote", "Relevant experience", employer.getId()));
    }
}
