package com.truehire.repository;

import com.truehire.model.VisaDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VisaDocumentRepository extends JpaRepository<VisaDocument, Long> {
    List<VisaDocument> findByApplicationId(Long applicationId);
}
