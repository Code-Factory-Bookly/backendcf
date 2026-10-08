package com.bookly.backendcf.auth.infrastructure.persistence;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    boolean existsByEmail(String email);

    Optional<UserAccount> findByEmail(String email);

    List<UserAccount> findTop10ByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrderByFullNameAsc(
            String email, String fullName);

    List<UserAccount> findTop10ByOrderByFullNameAsc();
}
