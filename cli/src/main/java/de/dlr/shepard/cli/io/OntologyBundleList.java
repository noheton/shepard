package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Wire-shape mirror of the backend's {@code GET /v2/admin/semantic/ontologies}
 * response, which returns a {@code PagedResponseIO<OntologyBundleIO>} envelope.
 * The {@code items} array carries the bundles; {@code total}, {@code page},
 * and {@code pageSize} are ignored by the CLI (the list is never paginated in
 * practice — bundle counts are O(5–20)).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class OntologyBundleList {

  private final List<OntologyBundle> items;

  public OntologyBundleList(@JsonProperty("items") List<OntologyBundle> items) {
    this.items = items == null ? List.of() : List.copyOf(items);
  }

  public List<OntologyBundle> getItems() {
    return items;
  }
}
