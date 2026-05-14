package de.dlr.shepard.plugins.references.dbpediadatabus.entities;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;

/**
 * REF1c — DBpedia Databus rich reference. Attaches to a
 * {@code :DataObject} via the standard {@code :HAS_REFERENCE} edge
 * (inherited from {@link BasicReference}), so the existing
 * reference-listing graph integration surfaces these alongside
 * {@code :GitReference} / {@code :URIReference} without bespoke
 * wiring.
 *
 * <p>Plugin-first per CLAUDE.md heuristic #2 — the integration
 * lives entirely in {@code plugins/reference-dbpedia-databus/}.
 * The rich CRUD lives on the sibling endpoint family at
 * {@code /v2/data-objects/{appId}/dbpedia-databus-references/...}
 * per the dispatcher direction (REF1a will eventually formalise an
 * SPI seam where each plugin contributes its IO subtype to the
 * polymorphic shape).
 */
@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class DbpediaDatabusReference extends BasicReference {

  public static final String STATUS_FRESH = "fresh";
  public static final String STATUS_STALE = "stale";
  public static final String STATUS_UNAVAILABLE = "unavailable";

  /** Canonical Databus artifact URI; required. */
  private String artifactUri;

  private String cachedTitle;
  private String cachedAbstract;
  private String cachedVersion;
  private String cachedLicence;

  /** Epoch millis of the artifact's own dct:modified timestamp. */
  private Long cachedModifiedAtMillis;

  /** Epoch millis when the cache was last successfully refreshed. */
  private Long cacheFetchedAtMillis;

  /** fresh / stale / unavailable. */
  private String cacheStatus;

  public DbpediaDatabusReference(long id) {
    super(id);
  }

  public DbpediaDatabusReference(String artifactUri) {
    this.artifactUri = artifactUri;
  }
}
