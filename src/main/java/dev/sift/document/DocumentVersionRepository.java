package dev.sift.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Version history lookups.
 */
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersionSummary> findSummariesByDocumentIdOrderByVersionNumberDesc(Long documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, Integer versionNumber);

    @Query("SELECT MAX(v.versionNumber) FROM DocumentVersion v WHERE v.documentId = :documentId")
    Integer findMaxVersionNumber(@Param("documentId") Long documentId);

    @Modifying
    @Query("DELETE FROM DocumentVersion v " +
           "WHERE v.documentId = :documentId AND v.versionNumber <= :maxVersionToDelete")
    int deleteOlderThan(@Param("documentId") Long documentId,
                        @Param("maxVersionToDelete") int maxVersionToDelete);
}
