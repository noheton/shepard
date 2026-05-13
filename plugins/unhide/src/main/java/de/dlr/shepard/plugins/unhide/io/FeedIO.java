package de.dlr.shepard.plugins.unhide.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

/**
 * UH1a — top-level shape of {@code GET /v2/unhide/feed.jsonld}.
 *
 * <p>Maps to the JSON-LD frame in {@code aidocs/67 §4.1}:
 *
 * <pre>
 * {
 *   "@context": [...],
 *   "@graph": [...],
 *   "_meta": {...}
 * }
 * </pre>
 *
 * <p>The {@code @context} block carries both
 * {@code https://schema.org/} and the metadata4ing namespace —
 * Unhide's inward-mappings handle the schema.org core and m4i terms
 * out of the box. UH1b will extend per-entry bodies with the m4i
 * processing-step trail once PROV1h's content-negotiated render
 * endpoint exists.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "@context", "@graph", "_meta" })
public record FeedIO(
  @JsonProperty("@context") List<Object> context,
  @JsonProperty("@graph") List<FeedEntryIO> graph,
  @JsonProperty("_meta") Map<String, Object> meta
) {
  /** schema.org namespace URI (trailing slash per the canonical doc). */
  public static final String SCHEMA_ORG_CTX = "https://schema.org/";

  /** metadata4ing (NFDI4Ing) ontology base — the JSON-LD vocab Unhide consumes. */
  public static final String METADATA4ING_CTX = "https://w3id.org/nfdi4ing/metadata4ing/";

  /**
   * Default {@code @context} block — schema.org, metadata4ing, and a
   * small inline vocab map with the dcat / shepard local prefixes
   * used in entries.
   */
  public static List<Object> defaultContext() {
    return List.of(
      SCHEMA_ORG_CTX,
      METADATA4ING_CTX,
      Map.of(
        "shepard", "https://shepard.dlr.de/types/",
        "dcat", "http://www.w3.org/ns/dcat#",
        "m4i", "https://w3id.org/nfdi4ing/metadata4ing/"
      )
    );
  }
}
