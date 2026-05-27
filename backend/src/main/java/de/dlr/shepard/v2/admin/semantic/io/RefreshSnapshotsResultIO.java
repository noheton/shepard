package de.dlr.shepard.v2.admin.semantic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * N1l — JSON response body of
 * {@code POST /v2/admin/semantic/refresh-snapshots}.
 *
 * <p>Simple counter: number of {@link de.dlr.shepard.context.semantic.entities.SemanticAnnotation}
 * nodes whose denormalized {@code propertyName} / {@code valueName}
 * snapshot was updated against the live n10s labels during this run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RefreshSnapshotsResult")
public class RefreshSnapshotsResultIO {

  @Schema(description = "Number of SemanticAnnotation nodes whose propertyName or valueName snapshot was updated.")
  private int updated;

  public RefreshSnapshotsResultIO() {}

  public RefreshSnapshotsResultIO(int updated) {
    this.updated = updated;
  }

  public int getUpdated() {
    return updated;
  }

  public void setUpdated(int updated) {
    this.updated = updated;
  }
}
