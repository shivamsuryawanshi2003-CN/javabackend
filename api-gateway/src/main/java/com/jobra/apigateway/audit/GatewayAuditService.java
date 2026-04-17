package com.jobra.apigateway.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

@Service
public class GatewayAuditService {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuditService.class);

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;

    @Value("${gateway.audit.enabled:true}")
    private boolean auditEnabled;

    public GatewayAuditService(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void logAccessDenial(String principalName, String requestPath, String httpMethod, String reason) {
        if (!auditEnabled) {
            return;
        }
        JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            return;
        }
        try {
            jdbc.update(
                    "INSERT INTO audit_log (created_at, principal_name, request_path, http_method, reason) VALUES (?, ?, ?, ?, ?)",
                    Timestamp.from(Instant.now()),
                    principalName != null ? principalName : "anonymous",
                    requestPath,
                    httpMethod,
                    reason
            );
        } catch (Exception e) {
            log.warn("Failed to write gateway audit row: {}", e.getMessage());
        }
    }
}
