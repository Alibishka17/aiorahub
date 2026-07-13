package com.truehire;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jdbc-session;DB_CLOSE_DELAY=-1",
        "app.upload-dir=target/test-session-uploads",
        "spring.session.store-type=jdbc",
        "spring.session.jdbc.initialize-schema=always"
})
@AutoConfigureMockMvc
class JdbcSessionWorkflowTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void authenticatedCookiePersistsForOneDayAndSessionIsStoredInDatabase() throws Exception {
        String email = "persistent-" + UUID.randomUUID() + "@example.com";
        MvcResult registration = mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "Persistent")
                        .param("lastName", "Candidate")
                        .param("phone", "+77005550303")
                        .param("email", email)
                        .param("password", "securePass123")
                        .param("role", "CANDIDATE"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Cookie sessionCookie = registration.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.getMaxAge()).isEqualTo(24 * 60 * 60);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Long.class))
                .isGreaterThan(0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT MAX(MAX_INACTIVE_INTERVAL) FROM SPRING_SESSION", Integer.class))
                .isEqualTo(24 * 60 * 60);

        mockMvc.perform(get("/").cookie(sessionCookie).header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/candidate\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("My account")));
    }
}
