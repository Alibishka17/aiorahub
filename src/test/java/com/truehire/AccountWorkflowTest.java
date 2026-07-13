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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-workflow;DB_CLOSE_DELAY=-1",
        "app.upload-dir=target/test-uploads",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
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
                .andExpect(redirectedUrl("/candidate/documents"));

        User candidate = userRepository.findByEmailIgnoreCase("cv-owner@example.com").orElseThrow();
        assertThat(candidate.getCvFileName()).isEqualTo("resume.pdf");
        assertThat(candidate.getCvStorageKey()).endsWith(".pdf");
    }

    @Test
    void oversizedUploadRedirectRendersAVisibleCvError() throws Exception {
        User candidate = userRepository.save(new User(
                "large-cv-" + UUID.randomUUID() + "@example.com", "not-used", "Large CV", Role.CANDIDATE));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", candidate.getId());

        mockMvc.perform(get("/candidate/documents")
                        .param("uploadTooLarge", "true")
                        .session(session)
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "CV must not exceed 10 MB.")));

        assertThat(Files.readString(Path.of("deploy/nginx/aiorahub.conf")))
                .contains("client_max_body_size 11m;")
                .contains("error_page 413 =303 /candidate/documents?uploadTooLarge=true;");
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
                        .param("candidateRequirements", "Three years of experience")
                        .param("category", "Quality assurance")
                        .param("country", "United Arab Emirates")
                        .param("city", "Dubai")
                        .param("salaryMin", "18000")
                        .param("salaryMax", "24000")
                        .param("salaryCurrency", "AED")
                        .param("requiredDocuments", "Work permit and degree")
                        .param("additionalInfo", "Relocation package"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employer/vacancies"));

        User employer = userRepository.findByEmailIgnoreCase("recruiter-one@example.com").orElseThrow();
        JobVacancy vacancy = vacancyRepository.findByEmployerId(employer.getId()).stream()
                .filter(v -> v.getTitle().equals("QA Engineer"))
                .findFirst()
                .orElseThrow();
        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(vacancy.getCountry()).isEqualTo("United Arab Emirates");
        assertThat(vacancy.getCity()).isEqualTo("Dubai");
        assertThat(vacancy.getSalaryMin()).isEqualTo(18000L);
        assertThat(vacancy.getSalaryMax()).isEqualTo(24000L);
        assertThat(vacancy.getSalaryCurrency()).isEqualTo("AED");
        assertThat(vacancy.getRequiredDocuments()).isEqualTo("Work permit and degree");
        assertThat(vacancy.getAdditionalInfo()).isEqualTo("Relocation package");

        mockMvc.perform(get("/employer/vacancies").session(employerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id=\"new-vacancy\""))));
        mockMvc.perform(get("/employer/vacancies").param("create", "true")
                        .session(employerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"new-vacancy\"")));

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

        mockMvc.perform(get("/employer/candidates").session(employerSession))
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

        mockMvc.perform(get("/candidate/vacancies").session(session))
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
                .isEqualTo("/vacancies/" + vacancy.getId());

        mockMvc.perform(post("/candidate/apply/{id}", vacancy.getId()).with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vacancies/" + vacancy.getId()))
                .andExpect(flash().attribute("interviewError",
                        "The AI interview is temporarily unavailable. Please try again in a few minutes."));
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
        mockMvc.perform(get("/employer/candidates")
                        .session(employerSession).header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Open transcript and conclusion")));

        application.setStatus(ApplicationStatus.OFFER_GRANTED);
        applicationRepository.save(application);
        mockMvc.perform(get("/candidate/visa/{id}", application.getId())
                        .session(candidateSession).header("Accept-Language", "ru-RU"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Прогресс получения визы")));
    }

    @Test
    void candidateDashboardSeparatesVacancySearchAndFiltersStructuredFields() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User employer = userRepository.save(new User(
                "filters-employer-" + suffix + "@example.com", "not-used", "Filter Employer", Role.EMPLOYER));
        JobVacancy matching = new JobVacancy(
                "Platform Engineer " + suffix, "Cloud platform", "Hybrid", "Kubernetes", employer.getId());
        matching.setCategory("IT-" + suffix);
        matching.setCity("Berlin-" + suffix);
        matching.setCountry("Germany-" + suffix);
        matching.setSalaryMin(70000L);
        matching.setSalaryMax(90000L);
        matching.setSalaryCurrency("EUR");
        vacancyRepository.save(matching);
        JobVacancy other = new JobVacancy(
                "Clinic Nurse " + suffix, "Clinical care", "On-site", "Nursing", employer.getId());
        other.setCategory("Healthcare-" + suffix);
        other.setCity("Munich-" + suffix);
        other.setCountry("Germany-" + suffix);
        other.setSalaryMax(50000L);
        other.setSalaryCurrency("EUR");
        vacancyRepository.save(other);

        User candidate = userRepository.save(new User(
                "filters-candidate-" + suffix + "@example.com", "not-used", "Filter Candidate", Role.CANDIDATE));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", candidate.getId());

        mockMvc.perform(get("/candidate").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(matching.getTitle()))));

        mockMvc.perform(get("/candidate/vacancies").session(session)
                        .param("category", matching.getCategory())
                        .param("country", matching.getCountry())
                        .param("city", matching.getCity())
                        .param("salary", "80000"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(matching.getTitle())))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(other.getTitle()))));
    }

    @Test
    void candidateAndEmployerSectionsRenderAsDistinctPages() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User candidate = userRepository.save(new User(
                "routes-candidate-" + suffix + "@example.com", "not-used", "Route Candidate", Role.CANDIDATE));
        User employer = userRepository.save(new User(
                "routes-employer-" + suffix + "@example.com", "not-used", "Route Employer", Role.EMPLOYER));
        MockHttpSession candidateSession = new MockHttpSession();
        candidateSession.setAttribute("userId", candidate.getId());
        MockHttpSession employerSession = new MockHttpSession();
        employerSession.setAttribute("userId", employer.getId());

        for (Map.Entry<String, String> page : Map.of(
                "/candidate", "Candidate dashboard — AIOraHub",
                "/candidate/vacancies", "Vacancies — AIOraHub",
                "/candidate/applications", "My applications — AIOraHub",
                "/candidate/documents", "Documents — AIOraHub",
                "/candidate/settings", "Settings — AIOraHub").entrySet()) {
            mockMvc.perform(get(page.getKey()).session(candidateSession))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(page.getValue())));
        }

        for (Map.Entry<String, String> page : Map.of(
                "/employer", "Recruiter dashboard — AIOraHub",
                "/employer/vacancies", "Vacancies — AIOraHub",
                "/employer/candidates", "Candidates — AIOraHub",
                "/employer/settings", "Settings — AIOraHub").entrySet()) {
            mockMvc.perform(get(page.getKey()).session(employerSession))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(page.getValue())));
        }

        mockMvc.perform(get("/candidate").param("view", "documents").session(candidateSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Documents — AIOraHub")));
        mockMvc.perform(get("/employer").param("view", "candidates").session(employerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Candidates — AIOraHub")));
    }

    @Test
    void candidateCanUpdateProfileAndPasswordFromSettings() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String originalEmail = "settings-old-" + suffix + "@example.com";
        String newEmail = "settings-new-" + suffix + "@example.com";
        MvcResult registration = mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "Old")
                        .param("lastName", "Name")
                        .param("phone", "+77005550101")
                        .param("email", originalEmail)
                        .param("password", "securePass123")
                        .param("role", "CANDIDATE"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);

        mockMvc.perform(post("/candidate/settings").with(csrf()).session(session)
                        .param("firstName", "New")
                        .param("lastName", "Profile")
                        .param("phone", "+77005550202")
                        .param("email", newEmail)
                        .param("telegramEnabled", "true")
                        .param("currentPassword", "securePass123")
                        .param("newPassword", "newSecurePass456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/candidate/settings"));

        User updated = userRepository.findByEmailIgnoreCase(newEmail).orElseThrow();
        assertThat(updated.getName()).isEqualTo("New Profile");
        assertThat(updated.getPhone()).isEqualTo("+77005550202");
        assertThat(updated.isTelegramEnabled()).isTrue();

        mockMvc.perform(post("/login").with(csrf())
                        .param("email", newEmail)
                        .param("password", "newSecurePass456")
                        .param("role", "CANDIDATE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/candidate"));
    }

    @Test
    void publicVacancyPageTracksViewsButExcludesOwningEmployer() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User employer = userRepository.save(new User(
                "views-employer-" + suffix + "@example.com", "not-used", "View Employer", Role.EMPLOYER));
        JobVacancy vacancy = vacancyRepository.save(new JobVacancy(
                "Viewed role " + suffix, "Description", "Remote", "Experience", employer.getId()));

        mockMvc.perform(get("/vacancies/{id}", vacancy.getId())).andExpect(status().isOk());
        assertThat(vacancyRepository.findById(vacancy.getId()).orElseThrow().getViewCount()).isEqualTo(1);

        MockHttpSession ownerSession = new MockHttpSession();
        ownerSession.setAttribute("userId", employer.getId());
        mockMvc.perform(get("/vacancies/{id}", vacancy.getId()).session(ownerSession))
                .andExpect(status().isOk());
        assertThat(vacancyRepository.findById(vacancy.getId()).orElseThrow().getViewCount()).isEqualTo(1);
    }

    private JobVacancy createPublishedVacancy(String title) {
        String suffix = UUID.randomUUID().toString();
        User employer = userRepository.save(new User(
                "test-employer-" + suffix + "@example.com", "not-used", "Test Employer", Role.EMPLOYER));
        return vacancyRepository.save(new JobVacancy(
                title, "Description", "Remote", "Relevant experience", employer.getId()));
    }
}
