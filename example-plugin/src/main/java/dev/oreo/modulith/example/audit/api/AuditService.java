package dev.oreo.modulith.example.audit.api;

import java.util.UUID;

/** Named module API visible through audit::reports. */
public interface AuditService {
    long currentBalance(UUID player);
}
