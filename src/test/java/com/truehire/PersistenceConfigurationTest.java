package com.truehire;

import com.truehire.repository.InterviewResultRepository;
import com.truehire.repository.JobApplicationRepository;
import com.truehire.repository.JobVacancyRepository;
import com.truehire.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:persistence-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class PersistenceConfigurationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobVacancyRepository vacancyRepository;

    @Autowired
    private JobApplicationRepository applicationRepository;

    @Autowired
    private InterviewResultRepository resultRepository;

    @Test
    void flywaySchemaIsValidAndStartsWithoutMockData() {
        assertThat(userRepository.count()).isZero();
        assertThat(vacancyRepository.count()).isZero();
        assertThat(applicationRepository.count()).isZero();
        assertThat(resultRepository.count()).isZero();
    }
}
