package com.jobra.authservice.service;

import com.jobra.authservice.entity.AuditLog;
import com.jobra.authservice.repository.AuditLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    public void logAccessDenied(String principalName, String requestPath, String httpMethod, String reason) {
        AuditLog row = new AuditLog();
        row.setPrincipalName(principalName != null ? principalName : "anonymous");
        row.setRequestPath(requestPath);
        row.setHttpMethod(httpMethod);
        row.setReason(reason);
        auditLogRepository.save(row);
    }
}
