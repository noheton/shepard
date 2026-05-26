package de.dlr.shepard.plugins.aas.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * AAS1l — runtime-mutable AAS plugin config singleton.
 *
 * <p>Single-instance Neo4j node — mirrors the A3b feature-toggle,
 * UH1a {@code :UnhideConfig}, and N1c2 {@code :SemanticConfig} shapes
 * per the {@code CLAUDE.md} "Always: surface operator knobs in the
 * admin config" rule. One {@code :AasConfig} node is seeded on first
 * startup from the {@code shepard.aas.*} install-time defaults;
 * subsequent runtime PATCHes against
 * {@code /v2/admin/aas/config} mutate this node in place.
 *
 * <p>Field set is the runtime-mutable subset of the AAS feature's
 * knobs per {@code aidocs/16 AAS1l}:
 *
 * <ul>
 *   <li>{@link #registryUrl} — base URL of the external IDTA AAS
 *       Registry. When blank/null, registry sync is disabled.
 *       Previously only configurable via {@code shepard.aas.registry.url}
 *       deploy-time property.</li>
 *   <li>{@link #registryApiKey} — optional Bearer token for the
 *       registry. Stored as plaintext (not hashed, because it is an
 *       outbound credential — the operator controls the registry and
 *       can rotate on both sides). Admins should treat this as
 *       sensitive; it is never returned in GET responses (masked as
 *       a boolean presence flag).</li>
 *   <li>{@link #baseUrl} — public URL of this Shepard instance,
 *       embedded in AAS Shell descriptor endpoint hrefs sent to the
 *       registry. When blank/null, descriptors omit the
 *       {@code endpoints[]} list.</li>
 *   <li>{@link #enabled} — master toggle. When {@code false}, all
 *       AAS endpoints return 404 (same behaviour as the plugin-level
 *       {@code shepard.plugins.aas.enabled=false} deploy-time key,
 *       but flippable at runtime without restart).</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Runtime field values win for all fields;
 * deploy-time {@code shepard.aas.*} properties are install defaults
 * that seed the singleton on first start. See the {@code CLAUDE.md}
 * admin-config rule ("Runtime value wins").
 *
 * <p><b>Security note on {@link #registryApiKey}.</b> The key is
 * stored in Neo4j (not in-memory only). It is <em>not</em> returned
 * by {@code GET /v2/admin/aas/config} — only a boolean
 * {@code apiKeyPresent} flag is surfaced. Operators must use
 * {@code PATCH /v2/admin/aas/config} with {@code "registryApiKey"}
 * to set or rotate; {@code "registryApiKey": null} clears it.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class AasConfig implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO.createOrUpdate}.
   */
  @Property("appId")
  private String appId;

  /**
   * Master runtime toggle. When {@code false}, all AAS endpoints
   * return 404 regardless of other config values. Default
   * {@code false} so a fresh install requires an explicit enable
   * by the operator (safe-by-default posture per UH1a precedent).
   */
  @Property("enabled")
  private boolean enabled = false;

  /**
   * Base URL of the external IDTA AAS Registry (no trailing slash).
   * When blank/null, registry sync is skipped at startup and via
   * the on-demand sync endpoint. Mirrors the
   * {@code shepard.aas.registry.url} deploy-time property.
   */
  @Property("registryUrl")
  private String registryUrl;

  /**
   * Optional Bearer token for the external IDTA AAS Registry.
   * Sent as {@code Authorization: Bearer <key>} when registering
   * shell descriptors. Stored in Neo4j; never returned via the
   * GET endpoint (only {@code apiKeyPresent} boolean is surfaced).
   * Set to {@code null} to clear (open registries need no auth).
   * Mirrors the {@code shepard.aas.registry.api-key} deploy-time
   * property.
   */
  @Property("registryApiKey")
  private String registryApiKey;

  /**
   * Public URL of this Shepard instance, embedded in AAS Shell
   * descriptor endpoint hrefs sent to the registry. When
   * blank/null, the {@code endpoints[]} array in descriptors is
   * empty — the registry stores the shell ID but clients cannot
   * resolve it until a base URL is set. Mirrors the
   * {@code shepard.aas.base-url} deploy-time property.
   */
  @Property("baseUrl")
  private String baseUrl;

  /** For testing purposes only. */
  public AasConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof AasConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
