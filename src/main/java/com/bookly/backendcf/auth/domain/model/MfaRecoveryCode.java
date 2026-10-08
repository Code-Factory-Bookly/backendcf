package com.bookly.backendcf.auth.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mfa_recovery_code")
public class MfaRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MfaRecoveryCode() {
    }

    public MfaRecoveryCode(UUID userId, String codeHash, OffsetDateTime createdAt) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(OffsetDateTime now) {
        this.usedAt = now;
    }
}
