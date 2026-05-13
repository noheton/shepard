package de.dlr.shepard.plugin;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * PM1e — persistent runtime override for a plugin's enabled flag.
 *
 * <p>One row per plugin id (V32 uniqueness constraint). A row exists
 * IFF an admin has runtime-flipped the plugin away from its
 * deploy-time {@code shepard.plugins.<id>.enabled} install default;
 * when an admin flips back to the default, the row is DELETEd so the
 * table stays sparse (one row per non-default plugin, not one row
 * per registered plugin).
 *
 * <p>This is the persistent half of the operator knob — the
 * deploy-time {@code shepard.plugins.<id>.enabled} key in
 * {@code application.properties} stays the install default; a row
 * here wins forever after. Pattern matches the A3b / N1c2 / UH1a
 * "{@code :*Config}" idiom (CLAUDE.md "operator knobs"), except this
 * is a per-plugin row rather than a global singleton — the natural
 * shape for "one override per plugin".
 *
 * <p>Lives in core (not in a plugin) because it is part of the
 * runtime SPI-registry itself — one of CLAUDE.md's plugin-first
 * exceptions ("the runtime SPI registry itself").
 *
 * <p>Mutations land in {@code :Activity} via {@code ProvenanceCaptureFilter}
 * (PROV1a, automatic — the admin endpoints capture by default). The
 * {@code updatedBy} field here is intentionally redundant with the
 * Activity row's actor; we keep it on the entity for fast
 * "who flipped this most recently" queries without scanning the
 * Activity log.
 */
@NodeEntity
@Data
@NoArgsConstructor
public class PluginRuntimeOverride implements HasId, HasAppId {

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
   * The plugin id this override applies to — matches
   * {@code PluginManifest#id()}. Unique across the label (V32
   * constraint).
   */
  @Property("pluginId")
  private String pluginId;

  /**
   * The persisted enabled value. The runtime override always wins
   * over the deploy-time default while this row exists; when an admin
   * resets the plugin to its deploy-time default, the row is deleted
   * (rather than mutated to match) so the table stays sparse.
   */
  @Property("enabled")
  private boolean enabled;

  /**
   * Wall-clock instant of the most recent PATCH. Stored as ISO-8601
   * via Neo4j OGM's built-in {@link Instant} converter — readable
   * directly from {@code cypher-shell} for ops.
   */
  @Property("updatedAt")
  private Instant updatedAt;

  /**
   * The subject identifier of the admin who last flipped this
   * override (the {@code sub} claim from their JWT, or the
   * sentinel {@code "anonymous"} when the actor can't be resolved).
   * Redundant with the {@code :Activity} audit trail but kept here
   * for fast lookups.
   */
  @Property("updatedBy")
  private String updatedBy;

  public PluginRuntimeOverride(String pluginId, boolean enabled, String updatedBy) {
    this.pluginId = pluginId;
    this.enabled = enabled;
    this.updatedBy = updatedBy;
    this.updatedAt = Instant.now();
  }

  @Override
  public String getUniqueId() {
    return id == null ? null : id.toString();
  }
}
