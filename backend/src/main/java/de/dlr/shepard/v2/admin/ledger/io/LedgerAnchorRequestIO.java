package de.dlr.shepard.v2.admin.ledger.io;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code POST /v2/admin/ledger/anchor}.
 *
 * <p>Identifies the {@link de.dlr.shepard.provenance.entities.Activity} nodes to
 * anchor on a distributed ledger and the provider to use.  Designed in
 * {@code aidocs/integrations/111} §5.1.
 */
@Data
@NoArgsConstructor
@Schema(name = "LedgerAnchorRequest")
public class LedgerAnchorRequestIO {

  @NotEmpty
  @Size(min = 1, max = 100)
  @Schema(
    required = true,
    description = "One to 100 Activity appId values (UUID v7) whose JSON-LD digests will be anchored.",
    minItems = 1,
    maxItems = 100
  )
  private List<String> activityAppIds;

  @Schema(
    description = "Ledger provider to use: 'bloxberg' or 'opentimestamps'. " +
    "Omit to use the instance default (shepard.ledger.default-provider).",
    enumeration = { "bloxberg", "opentimestamps" }
  )
  private String provider;
}
