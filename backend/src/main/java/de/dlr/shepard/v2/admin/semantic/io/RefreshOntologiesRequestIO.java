package de.dlr.shepard.v2.admin.semantic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * N1c — JSON request body of {@code POST /v2/admin/semantic/refresh-ontologies}.
 *
 * <p>Both fields are optional. With an empty body ({@code {}}) the
 * refresh walks every bundle in the manifest with {@code force=false}.
 * With {@code bundles=["prov-o","qudt"]} only those bundles are
 * considered. {@code force=true} re-imports even when the canonical
 * Turtle hash matches what is already on disk — useful after an n10s
 * datastore reset.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RefreshOntologiesRequest")
public class RefreshOntologiesRequestIO {

  @Schema(
    description = "Optional list of bundle ids to refresh (e.g. [\"prov-o\",\"qudt\"]). " +
    "Default: all bundles in the manifest.",
    example = "[\"prov-o\",\"qudt\"]"
  )
  private List<String> bundles = new ArrayList<>();

  @Schema(
    description = "When true, re-imports even when the canonical Turtle hash matches the bundled stub.",
    defaultValue = "false"
  )
  private boolean force;

  public RefreshOntologiesRequestIO() {}

  public RefreshOntologiesRequestIO(List<String> bundles, boolean force) {
    this.bundles = bundles == null ? new ArrayList<>() : new ArrayList<>(bundles);
    this.force = force;
  }

  public List<String> getBundles() {
    return bundles == null ? Collections.emptyList() : bundles;
  }

  public void setBundles(List<String> bundles) {
    this.bundles = bundles == null ? new ArrayList<>() : new ArrayList<>(bundles);
  }

  public boolean isForce() {
    return force;
  }

  public void setForce(boolean force) {
    this.force = force;
  }
}
