package de.dlr.shepard.v2.admin.semantic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TPL5 — response body for
 * {@code POST /v2/admin/semantic/git-sources/{appId}/ingest}.
 *
 * <p>Mirrors the shape of {@link RefreshOntologiesResultIO}: a short
 * summary plus a structured error when the run failed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Result of a single-source ontology git ingest run.")
public class OntologyGitIngestResultIO {

  @Schema(description = "Whether the ingest run completed without errors.")
  private boolean ok;

  @Schema(description = "Number of ontology files ingested from the repo.")
  private int filesIngested;

  @Schema(description = "Error message when ok=false; null when ok=true.", nullable = true)
  private String error;

  /** Convenience factory for a successful result. */
  public static OntologyGitIngestResultIO ok(int filesIngested) {
    return new OntologyGitIngestResultIO(true, filesIngested, null);
  }

  /** Convenience factory for a failed result. */
  public static OntologyGitIngestResultIO error(String error, int filesIngested) {
    return new OntologyGitIngestResultIO(false, filesIngested, error);
  }
}
