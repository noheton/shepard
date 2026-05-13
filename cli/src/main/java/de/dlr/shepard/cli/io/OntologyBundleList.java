package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Wire-shape mirror of the backend's {@code OntologyBundleListIO}.
 * The {@code GET /v2/admin/semantic/ontologies} response carries one
 * {@code bundles} array — kept as a typed wrapper so the CLI's
 * {@code postJson}/{@code getJson} type tokens stay clean.
 */
public final class OntologyBundleList {

  private final List<OntologyBundle> bundles;

  @JsonCreator
  public OntologyBundleList(@JsonProperty("bundles") List<OntologyBundle> bundles) {
    this.bundles = bundles == null ? List.of() : List.copyOf(bundles);
  }

  public List<OntologyBundle> getBundles() {
    return bundles;
  }
}
