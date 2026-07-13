package com.truehire.repository;

import com.truehire.model.JobVacancy;
import com.truehire.model.VacancyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobVacancyRepository extends JpaRepository<JobVacancy, Long> {
    List<JobVacancy> findByEmployerId(Long employerId);
    List<JobVacancy> findByEmployerIdAndStatusNot(Long employerId, VacancyStatus status);
    List<JobVacancy> findByStatus(VacancyStatus status);
}
