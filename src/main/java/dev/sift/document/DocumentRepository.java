package dev.sift.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Document queries. Ownership is part of every query, never checked afterwards.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    Page<DocumentSummary> findSummariesByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    Page<DocumentSummary> findSummariesByUserIdAndDeletedAtIsNullAndTitleContainingIgnoreCase(
            Long userId, String keyword, Pageable pageable);
}
