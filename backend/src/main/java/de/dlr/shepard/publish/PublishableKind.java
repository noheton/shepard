package de.dlr.shepard.publish;

/**
 * KIP1a publishable entity-kinds — the URL segments accepted by
 * {@code POST /v2/{kind}/{appId}/publish}.
 *
 * <p>KIP1a baseline supports two kinds: {@link #DATA_OBJECTS} and
 * {@link #COLLECTIONS}. Future KIP slices add bundles, files, and
 * lab-journal entries (per {@code aidocs/66 §4.1}); registering a
 * new kind is one row in {@link PublishableKindRegistry} —
 * deliberately the URL shape doesn't change.
 *
 * <p>The {@link #urlSegment} carries the kebab-cased URL form
 * (e.g. {@code "data-objects"}); the {@link #neo4jLabel} carries
 * the Neo4j label of the underlying entity (e.g. {@code "DataObject"});
 * the {@link #digitalObjectType} carries the KIP {@code digitalObjectType}
 * IRI per {@code aidocs/66 §3}'s table.
 */
public enum PublishableKind {
  /**
   * The {@code :DataObject} primitive — a logical thing inside a
   * Collection, freely nestable, with attributes. Published at
   * {@code POST /v2/data-objects/{appId}/publish}.
   */
  DATA_OBJECTS("data-objects", "DataObject", "http://shepard.dlr.de/types/dlr:DataObject"),

  /**
   * The {@code :Collection} primitive — top-level grouping of a
   * campaign / project / topic. Published at
   * {@code POST /v2/collections/{appId}/publish}.
   */
  COLLECTIONS("collections", "Collection", "http://shepard.dlr.de/types/dlr:Collection");

  private final String urlSegment;
  private final String neo4jLabel;
  private final String digitalObjectType;

  PublishableKind(String urlSegment, String neo4jLabel, String digitalObjectType) {
    this.urlSegment = urlSegment;
    this.neo4jLabel = neo4jLabel;
    this.digitalObjectType = digitalObjectType;
  }

  /** @return the URL segment used at {@code /v2/{kind}/...}. */
  public String urlSegment() {
    return urlSegment;
  }

  /** @return the Neo4j node label backing this kind. */
  public String neo4jLabel() {
    return neo4jLabel;
  }

  /** @return the KIP {@code digitalObjectType} IRI per {@code aidocs/66 §3}. */
  public String digitalObjectType() {
    return digitalObjectType;
  }
}
