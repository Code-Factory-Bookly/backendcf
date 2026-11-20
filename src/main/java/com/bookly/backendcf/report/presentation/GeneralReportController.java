package com.bookly.backendcf.report.presentation;

import com.bookly.backendcf.audit.application.AuditRecorder;
import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import com.bookly.backendcf.report.application.GeneralReportService;
import com.bookly.backendcf.report.application.ReportSessionActivity;
import com.bookly.backendcf.report.domain.model.ReportPeriod;
import com.bookly.backendcf.report.presentation.dto.GeneralReportResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportes")
public class GeneralReportController {

    private static final UUID REPORT_RESOURCE_ID = UUID.nameUUIDFromBytes(
            "bookly:hu-16:general-report".getBytes(StandardCharsets.UTF_8));

    private final GeneralReportService generalReportService;
    private final ReportSessionActivity sessionActivity;
    private final AuditRecorder auditRecorder;

    public GeneralReportController(
            GeneralReportService generalReportService,
            ReportSessionActivity sessionActivity,
            AuditRecorder auditRecorder) {
        this.generalReportService = generalReportService;
        this.sessionActivity = sessionActivity;
        this.auditRecorder = auditRecorder;
    }

    @GetMapping("/general")
    public GeneralReportResponse general(
            @RequestParam(name = "periodo", required = false) ReportPeriod period,
            Authentication authentication,
            HttpServletRequest request) {
        UUID actorId = UUID.fromString(authentication.getName());
        String sessionKey = authentication.getDetails() instanceof String value ? value : actorId.toString();
        sessionActivity.touch(actorId, sessionKey);
        GeneralReportResponse response = generalReportService.generate(period);

        auditRecorder.record(new AuditAction(
                actorId,
                AuditActionType.REPORT_VIEWED,
                AuditResourceType.REPORT,
                REPORT_RESOURCE_ID,
                OffsetDateTime.now(),
                resolveClientIp(request),
                Map.of(
                        "report", "HU-16",
                        "period", response.period().name(),
                        "from", response.from().toString(),
                        "to", response.to().toString())));
        return response;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
