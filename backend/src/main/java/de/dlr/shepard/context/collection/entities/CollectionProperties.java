package de.dlr.shepard.context.collection.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * Per-Collection settings node — the {@code (:Collection)
 * -[:HAS_PROPERTIES]-> (:CollectionProperties)} sibling that holds
 * cross-cutting Collection-level config without polluting the
 * Collection entity itself.
 *
 * <p>Designed in {@code aidocs/58 §5} (CP1). The intent is to give a
 * stable place for owner-facing settings that grow over time —
 * starting with the WebDAV-visibility opt-out from {@code aidocs/61}
 * and the default-file-container strategy currently nailed to
 * {@code :HAS_DEFAULT_FILE_CONTAINER} on the Collection. Future
 * fields land here without changing the Collection wire shape.
 *
 * <p>v1 fields are intentionally narrow: {@code webdavVisible}
 * + {@code defaultOntologyUri} + {@code uiDefaultsJson} placeholder.
 * Cross-link to {@code :ShepardTemplate} (per {@code aidocs/54}) is
 * deferred to the slice that ships templates (T1).
 */
@NodeEntity
@Data
@NoArgsConstructor
public class CollectionProperties implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save.
   */
  @Property("appId")
  private String appId;

  /**
   * When {@code false}, the Collection is hidden from the
   * {@code /v2/webdav/...} mount surface (per {@code aidocs/61 §13}).
   * Default {@code true} — opt-out rather than opt-in.
   */
  @Property("webdavVisible")
  private boolean webdavVisible = true;

  /**
   * When {@code false}, this Collection is excluded from the
   * Helmholtz Unhide feed ({@code /v2/unhide/feed.jsonld}). Default
   * {@code true} — opt-out, not opt-in, so existing Collections
   * continue to appear in the feed without an explicit owner action.
   *
   * <p>No Neo4j migration is needed: the property is additive and
   * schema-less; existing nodes with no {@code publishToHelmholtzKG}
   * property are treated as {@code true} by the feed filter in
   * {@code UnhideFeedService} (UH1d).
   */
  @Property("publishToHelmholtzKG")
  private boolean publishToHelmholtzKG = true;

  /**
   * Optional Collection-default ontology IRI (PROV-O / QUDT / …) per
   * {@code aidocs/48}. {@code null} means the instance-level default.
   */
  @Property("defaultOntologyUri")
  private String defaultOntologyUri;

  /**
   * Free-form JSON blob carrying UI-only defaults — initial open
   * tab, sort order, column widths. Opaque to the backend; the
   * frontend owns the schema.
   */
  @Property("uiDefaultsJson")
  private String uiDefaultsJson;

  public CollectionProperties(String appId) {
    this.appId = appId;
  }

  /** For testing purposes only. */
  public CollectionProperties(long id) {
    this.id = id;
  }

  @Override
  public String getUniqueId() {
    return appId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof CollectionProperties other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
