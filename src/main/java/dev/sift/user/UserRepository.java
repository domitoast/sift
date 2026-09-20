package dev.sift.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User lookups. Queries exclude soft-deleted accounts so they cannot log in.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByEmailAndDeletedAtIsNull(String email);
}
