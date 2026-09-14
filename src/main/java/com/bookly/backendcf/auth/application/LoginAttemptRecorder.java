package com.bookly.backendcf.auth.application;

import com.bookly.backendcf.auth.domain.model.UserAccount;
import com.bookly.backendcf.auth.infrastructure.persistence.UserAccountRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptRecorder {
    private final UserAccountRepository repository;

    public LoginAttemptRecorder(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserAccount recordFailure(String email, int maxAttempts, long lockMinutes) {
        UserAccount account = repository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        account.registerFailedLogin(OffsetDateTime.now(), maxAttempts, lockMinutes);
        return repository.save(account);
    }
}
