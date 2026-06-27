package de.dlr.shepard.v2.scenegraph.entities;

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
 * DT1-PHASE-0 — {@code :DigitalTwinScene} entity scaffold.
 *
 * <p>A digital-twin scene groups one root {@link CoordinateFrame} (and its
 * tree of descendant frames + attached {@link Joint}s) into a single
 * addressable handle. Future phases ({@code RDK-PARSE-2},
 * {@code URDF-WEBVIEW phase 2}, {@code ISAAC-USD-EXPORT-1}) consume this
 * entity as the import target / export source / scene-graph root.
 *
 * <p><b>Scope of this PR (scaffold only).</b> The entity, its DAO, and
 * the {@code V95} migration with uniqueness constraints. <em>No REST
 * surface, no IO classes, no MCP tools, no Activity wiring, no frontend
 * UI</em> — those land in {@code SCENEGRAPH-REST-1} (gated on this row).
 *
 * <p><b>Relationship shape.</b> The graph edges
 * {@code (:DigitalTwinScene)-[:HAS_FRAME]->(:CoordinateFrame)} and
 * {@code (:DigitalTwinScene)-[:HAS_JOINT]->(:Joint)} are documented in
 * the {@code V95} migration comment as the planned shape; the edges
 * themselves are written by the service layer in {@code SCENEGRAPH-REST-1}
 * when scenes are populated. This entity carries the scalar
 * {@link #rootFrameAppId} as the explicit root pointer per the design in
 * {@code aidocs/data/85 §5} ({@code DigitalTwinScene.worldFrameAppId} in
 * that doc; renamed to {@code rootFrameAppId} per the task spec to
 * decouple from the "world" frame-type terminology).
 *
 * <p><b>Cross-cutting rules.</b>
 * <ul>
 *   <li>{@code HasAppId} — UUID v7 minted by
 *       {@code GenericDAO#createOrUpdate} when {@code appId} is null
 *       (per the L2a write seam).</li>
 *   <li>Every field is nullable except the OGM primary key — per the
 *       "Always: schema changes are additive and nullable" rule.</li>
 *   <li>Constraint shipped by
 *       {@code V95__DT1_Phase0_DigitalTwinScene_scaffold.cypher}:
 *       {@code REQUIRE n.appId IS UNIQUE}. Rollback by
 *       {@code V95_R__DT1_Phase0_rollback.cypher}.</li>
 * </ul>
 *
 * @see CoordinateFrame
 * @see Joint
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class DigitalTwinScene implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO#createOrUpdate}.
   */
  @Property("appId")
  private String appId;

  /** Short human-readable name. Nullable per the additive-schema rule. */
  @Property("name")
  private String name;

  /** Free-form prose describing the scene. Nullable. */
  @Property("description")
  private String description;

  /**
   * {@code appId} of the source file this scene was parsed from (e.g.
   * a {@code .rdk} or {@code .urdf}). Nullable: a scene can be
   * hand-built without a source file. The reference is by app-level id
   * rather than an OGM graph edge so this scaffold does not couple to
   * the {@code FileReference} entity at compile time.
   */
  @Property("sourceFileAppId")
  private String sourceFileAppId;

  /**
   * {@code appId} of the root {@link CoordinateFrame} of this scene's
   * frame tree. Nullable for empty / freshly-created scenes. The actual
   * {@code (:DigitalTwinScene)-[:HAS_FRAME]->(:CoordinateFrame)} graph
   * edges are written by the service layer in {@code SCENEGRAPH-REST-1};
   * this property provides the explicit root pointer per
   * {@code aidocs/data/85 §5}.
   */
  @Property("rootFrameAppId")
  private String rootFrameAppId;

  /**
   * SCENEGRAPH-LIST-1 — epoch-millis timestamp set on first save.
   * Nullable per the additive-schema rule: pre-SCENEGRAPH-LIST-1 rows
   * carry no value and the list endpoint returns {@code null} for them.
   * Written by {@code SceneGraphService.saveScene} only when currently
   * unset, so subsequent updates do not clobber the creation stamp.
   */
  @Property("createdAt")
  private Long createdAt;

  /**
   * SCENEGRAPH-LIST-1 — epoch-millis timestamp set on every save.
   * Nullable per the additive-schema rule. Refreshed by
   * {@code SceneGraphService.saveScene} on each persist so the list
   * endpoint can report "most recently touched" without an N+1 walk
   * over the {@code :Activity} chain.
   */
  @Property("updatedAt")
  private Long updatedAt;

  /** For testing purposes only. */
  public DigitalTwinScene(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof DigitalTwinScene other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
