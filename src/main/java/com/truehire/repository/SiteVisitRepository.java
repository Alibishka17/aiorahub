package com.truehire.repository;

import com.truehire.model.SiteVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SiteVisitRepository extends JpaRepository<SiteVisit, Long> {
    long countByVisitedAtAfter(LocalDateTime since);

    @Query("select count(distinct v.sessionKey) from SiteVisit v where v.visitedAt >= :since")
    long countUniqueSessionsSince(@Param("since") LocalDateTime since);

    @Query(value = "select path, count(*) as views from site_visit where visited_at >= :since group by path order by views desc limit 8", nativeQuery = true)
    List<Object[]> popularPathsSince(@Param("since") LocalDateTime since);

    List<SiteVisit> findTop30ByOrderByVisitedAtDesc();
}
