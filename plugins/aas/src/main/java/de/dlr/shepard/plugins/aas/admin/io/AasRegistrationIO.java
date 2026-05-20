package de.dlr.shepard.plugins.aas.admin.io;

import de.dlr.shepard.plugins.aas.entities.AasRegistration;
import de.dlr.shepard.plugins.aas.entities.AasRegistration.Status;

/**
 * AAS1-reg Commit 3 — wire shape for a single {@link AasRegistration} outbox row,
 * returned by {@code GET /v2/admin/aas/registrations}.
 */
public record AasRegistrationIO(
  String appId,
  String shellAppId,
  String registryUrl,
  Status status,
  Long lastAttemptAt,
  String errorMessage,
  Long createdAt,
  Long updatedAt
) {

  public static AasRegistrationIO from(AasRegistration reg) {
    return new AasRegistrationIO(
      reg.getAppId(),
      reg.getShellAppId(),
      reg.getRegistryUrl(),
      reg.getStatus(),
      reg.getLastAttemptAt(),
      reg.getErrorMessage(),
      reg.getCreatedAt(),
      reg.getUpdatedAt()
    );
  }
}
