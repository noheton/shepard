package de.dlr.shepard.v2.admin.ledger.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response body for anchor-job status endpoints.
 *
 * <p>Returned by {@code POST /v2/admin/ledger/anchor} (202 + initial state)
 * and {@code GET /v2/admin/ledger/anchor/{jobId}} (current state).
 * Designed in {@code aidocs/integrations/111} §5.1–5.2.
 *
 * <p>Status values: {@code queued} | {@code running} | {@code complete} | {@code failed}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LedgerAnchorJob")
public class LedgerAnchorJobIO {

  @Schema(required = true, description = "Stable UUID v7 identifier for this anchor job.")
  private String jobId;

  @Schema(
    required = true,
    description = "Current state of the job.",
    enumeration = { "queued", "running", "complete", "failed" }
  )
  private String status;

  @Schema(
    required = true,
    description = "Human-readable summary, e.g. '3/3 activities anchored via bloxberg.'."
  )
  private String message;
}
