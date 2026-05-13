package de.dlr.shepard.v2.admin.semantic.io;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * N1c2 — response body for
 * {@code GET /v2/admin/semantic/ontologies}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Merged view of pre-seeded + operator-uploaded ontology bundles.")
public class OntologyBundleListIO {

  @Schema(description = "Built-ins first (manifest order), then user uploads (id ASC).")
  private List<OntologyBundleIO> bundles = new ArrayList<>();
}
