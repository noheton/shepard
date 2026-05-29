package de.dlr.shepard.v2.admin.jupyter.entities;

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
 * J1e — runtime-mutable JupyterHub config singleton.
 *
 * <p>Single-instance Neo4j node following the A3b / N1c2 / UH1a / ROR1
 * pattern (CLAUDE.md "Always: surface operator knobs in the admin
 * config"). One {@code :JupyterConfig} node is seeded on first startup
 * from the deploy-time defaults in {@code application.properties}
 * ({@code shepard.jupyter.enabled} default {@code false} and
 * {@code shepard.jupyter.hub-url} default {@code null}); subsequent
 * runtime PATCHes against {@code GET/PATCH /v2/admin/jupyter/config}
 * mutate this node in place.
 *
 * <p>Field set is the runtime-mutable subset of the feature's knobs:
 *
 * <ul>
 *   <li>{@link #enabled} — master switch for the "Open in JupyterHub"
 *       affordance. When {@code false}, the unified data-references row
 *       for {@code .ipynb} files still renders (with download + delete)
 *       but the JupyterHub launch button is hidden.</li>
 *   <li>{@link #hubUrl} — JupyterHub base URL the launch button targets
 *       (e.g. {@code https://hub.example.org}). The launch URL is built
 *       as {@code {hubUrl}/hub/spawn?file={downloadUrl}}; consult
 *       {@code docs/admin/runbooks/jupyterhub-config.md} for the
 *       JupyterHub-side convention map.</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Non-null runtime values win over deploy-time
 * defaults; setting {@code hubUrl} to {@code null} via PATCH reverts it
 * to the deploy-time default (RFC 7396 "clear" semantics handled at the
 * service layer). The {@code enabled} field is a non-null primitive
 * boolean and always carries the operator's last explicit choice.
 *
 * <p><b>Visibility gate.</b> The frontend launch button is visible only
 * when {@code enabled == true && hubUrl != null && !hubUrl.isBlank()} —
 * either knob being clear suppresses the affordance.
 *
 * <p><b>Constraint.</b> {@code V94__Add_appId_constraint_JupyterConfig.cypher}
 * adds {@code REQUIRE n.appId IS UNIQUE} on {@code :JupyterConfig}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class JupyterConfig implements HasAppId {

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
   * Master switch for the JupyterHub link-out affordance. When
   * {@code false}, the "Open in JupyterHub" button is hidden in the
   * unified data-references table even if {@link #hubUrl} is set.
   * Default: {@code false} (opt-in feature).
   */
  @Property("enabled")
  private boolean enabled;

  /**
   * JupyterHub base URL (e.g. {@code https://hub.example.org}). Used
   * by the frontend to build the launch URL
   * {@code {hubUrl}/hub/spawn?file={downloadUrl}}.
   * {@code null} → no URL configured; the launch button is hidden
   * regardless of {@link #enabled}.
   */
  @Property("hubUrl")
  private String hubUrl;

  /** For testing purposes only. */
  public JupyterConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof JupyterConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
