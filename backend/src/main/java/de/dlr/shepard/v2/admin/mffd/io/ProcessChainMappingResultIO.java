package de.dlr.shepard.v2.admin.mffd.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * MFFD-MAPPING-REST-1 — response body shape for
 * {@code POST /v2/admin/mffd/process-chain-mapping}.
 *
 * <p>Counters and an unresolved checklist (per-entry, with the original
 * YAML line number when available). The Activity row records the same
 * counters in its summary; the body returns them so the admin UI can
 * render the checklist without a follow-up activity query.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Result of applying a MFFD process-chain mapping YAML payload.")
public class ProcessChainMappingResultIO {

  @Schema(description = "YAML schema version applied (currently 1).")
  private int schemaVersion;

  @Schema(description = "Total mapping entries in the YAML payload.")
  private int entries;

  @Schema(description = "Sum of (source × target) DataObject pairs matched across all entries.")
  private int matched;

  @Schema(description = "Number of entries where either side resolved zero DataObjects.")
  private int unmatched;

  @Schema(description = "Number of has_successor edges MERGEd (created or refreshed) by this run.")
  private int edgesCreated;

  @Schema(description = "Per-entry checklist of unresolved selectors, with YAML line numbers.")
  private List<UnresolvedEntryIO> unresolved = new ArrayList<>();

  @Schema(description = "Non-fatal warnings (e.g. unknown transitionKind values).")
  private List<String> warnings = new ArrayList<>();

  /** A YAML-source unresolved-selector row for operator follow-up. */
  @Data
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "One unresolved selector in the YAML — operator follow-up checklist row.")
  public static class UnresolvedEntryIO {

    @Schema(description = "1-based line number in the YAML payload (best-effort).")
    private int line;

    @Schema(description = "\"source\" or \"target\" — which side of the entry did not resolve.")
    private String side;

    @Schema(description = "Why the selector did not match — typically zero DataObjects.")
    private String reason;

    public UnresolvedEntryIO(int line, String side, String reason) {
      this.line = line;
      this.side = side;
      this.reason = reason;
    }
  }
}
