package com.bookly.backendcf.audit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Persisted append-only representation of a critical audit action. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private AuditActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private AuditResourceType resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> metadata;

    protected AuditLog() {
    }

    private AuditLog(AuditAction action) {
        this.actorUserId = action.actorUserId();
        this.actionType = action.actionType();
        this.resourceType = action.resourceType();
        this.resourceId = action.resourceId();
        this.occurredAt = action.occurredAt();
        this.sourceIp = action.sourceIp();
        this.metadata = Map.copyOf(action.metadata());
    }

    public static AuditLog from(AuditAction action) {
        return new AuditLog(action);
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public AuditActionType getActionType() {
        return actionType;
    }

    public AuditResourceType getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
