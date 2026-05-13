package de.dlr.shepard.context.semantic.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * N1c2 — operator-uploaded ontology bundle (per {@code aidocs/65 §2.3}).
 *
 * <p>The catalogue entry for a TTL file the operator added at
 * runtime via {@code POST /v2/admin/semantic/ontologies}. The bytes
 * live on disk under
 * {@code shepard.semantic.internal.user-bundles-dir/<bundleId>.ttl};
 * this node carries the manifest-shape metadata so the seed loop
 * can pick the bundle up on the next startup the same way it picks
 * up the classpath-bundled ones.
 *
 * <p>Bundle-id uniqueness across the built-in + user namespace is
 * enforced at the service layer in {@code OntologyConfigService} —
 * uploading a bundle with id {@code prov-o} is refused (409). User
 * bundles can be removed via {@code DELETE
 * /v2/admin/semantic/ontologies/{id}}; built-in bundles cannot
 * (they're shipped in the JAR).
 *
 * <p>User bundles never carry {@code required: true} — the required
 * set is the conservative built-in baseline (prov-o + obo-relations
 * today, per {@code aidocs/65 §2.1}).
 */
@NodeEntity
@Data
@NoArgsConstructor
public class UserOntologyBundle implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO#createOrUpdate} per L2a's seam.
   */
  @Property("appId")
  private String appId;

  /**
   * Bundle id — the slug the operator picked when uploading; also
   * the on-disk filename stem
   * ({@code <user-bundles-dir>/<bundleId>.ttl}).
   * Unique across the manifest + user namespace. Must match
   * {@code ^[a-z0-9][a-z0-9_-]{0,63}$} (validated at service layer).
   */
  @Property("bundleId")
  private String bundleId;

  /** Human-readable name, for the list endpoint. Optional. */
  @Property("name")
  private String name;

  /** Canonical IRI prefix the ontology mints terms under. Required. */
  @Property("iriPrefix")
  private String iriPrefix;

  /**
   * Optional canonical URL — {@code null} for local-only bundles
   * that don't have a public canonical source. The N1c bulk-refresh
   * endpoint skips entries with no {@code canonicalUrl}.
   */
  @Property("canonicalUrl")
  private String canonicalUrl;

  /** SPDX-ish licence string. Required; carries the operator's
   * declaration that they have the right to redistribute the bundle. */
  @Property("license")
  private String license;

  /** Hex SHA-256 of the on-disk TTL bytes, computed at upload time. */
  @Property("sha256")
  private String sha256;

  /** Size of the on-disk TTL in bytes, captured at upload time. */
  @Property("byteSize")
  private Long byteSize;

  /** Format of the bundled RDF — defaults to "Turtle" for user uploads. */
  @Property("format")
  private String format;

  /** Username of the admin who uploaded the bundle. */
  @Property("addedBy")
  private String addedBy;

  /** Millis since epoch when the bundle was uploaded. */
  @Property("addedAt")
  private Long addedAt;

  @Override
  public String getUniqueId() {
    return id == null ? null : id.toString();
  }
}
