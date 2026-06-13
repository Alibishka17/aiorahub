package com.truehire.repository;

import com.truehire.model.JobVacancy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobVacancyRepository extends JpaRepository<JobVacancy, Long> {
    List<JobVacancy> findByEmployerId(Long employerId);
}
