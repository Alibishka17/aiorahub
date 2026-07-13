package com.truehire;

import com.truehire.config.DataInitializer;
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
    private DataInitializer dataInitializer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobVacancyRepository vacancyRepository;

    @Autowired
    private JobApplicationRepository applicationRepository;

    @Autowired
    private InterviewResultRepository resultRepository;

    @Test
    void flywaySchemaIsValidAndStarterSeedIsIdempotent() throws Exception {
        assertThat(userRepository.count()).isEqualTo(3);
        assertThat(vacancyRepository.count()).isEqualTo(3);
        assertThat(applicationRepository.count()).isEqualTo(1);
        assertThat(resultRepository.count()).isEqualTo(1);

        dataInitializer.run();

        assertThat(userRepository.count()).isEqualTo(3);
        assertThat(vacancyRepository.count()).isEqualTo(3);
        assertThat(applicationRepository.count()).isEqualTo(1);
        assertThat(resultRepository.count()).isEqualTo(1);
    }
}
