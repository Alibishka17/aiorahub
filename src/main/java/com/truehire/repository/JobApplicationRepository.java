package com.truehire.repository;

import com.truehire.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByCandidateId(Long candidateId);
    List<JobApplication> findByVacancyIdIn(List<Long> vacancyIds);
    Optional<JobApplication> findByVacancyIdAndCandidateId(Long vacancyId, Long candidateId);
    Optional<JobApplication> findByGuestAccessToken(String guestAccessToken);
    Optional<JobApplication> findByVacancyIdAndGuestEmailIgnoreCase(Long vacancyId, String guestEmail);
}
