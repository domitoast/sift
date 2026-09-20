package dev.sift.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Source queries. Ownership and the soft-delete flag are part of every query.
 */
public interface SourceRepository extends JpaRepository<Source, Long> {
    Optional<Source> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    List<Source> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    boolean existsByUrlAndUserIdAndDeletedAtIsNull(String url, Long userId);

    List<Source> findAllByEnabledTrueAndDeletedAtIsNull();

    Optional<Source> findByIdAndDeletedAtIsNull(Long id);
}
