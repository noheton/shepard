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
 * DT1-PHASE-0 — {@code :CoordinateFrame} entity scaffold.
 *
 * <p>One node per coordinate frame in a digital-twin scene's frame tree.
 * Frames form a forest via the scalar {@link #parentFrameAppId} pointer;
 * the parallel graph edge
 * {@code (:CoordinateFrame)-[:HAS_PARENT_FRAME]->(:CoordinateFrame)}
 * (per {@code aidocs/data/85 §3}) is written by the service layer in
 * {@code SCENEGRAPH-REST-1}. The scaffold ships only the entity + the
 * appId uniqueness constraint; relationship-edge mutation belongs to the
 * consumer row.
 *
 * <p><b>Transform shape.</b> The local transform of the child frame
 * relative to its parent is stored as six scalar doubles — translation
 * {@code (x, y, z)} and Euler angles {@code (rx, ry, rz)} in radians —
 * not as a nested {@code @NodeEntity} or a 16-element matrix array.
 * Scalars are the OGM-friendly choice for a scaffold-only PR: they
 * round-trip cleanly through {@code session.save} / {@code session.load}
 * without custom converters or relationship-property gymnastics. A
 * 4×4 matrix shape (per the design doc's preferred form) is a later
 * additive layer that can wrap these scalars without an OGM migration.
 *
 * <p><b>Cross-cutting rules.</b>
 * <ul>
 *   <li>{@code HasAppId} — UUID v7 minted on save (L2a write seam).</li>
 *   <li>All non-primary fields nullable. Translation/rotation doubles
 *       default to {@code 0.0d} per Java primitive defaults — i.e. the
 *       identity transform on construction.</li>
 *   <li>Constraint shipped by
 *       {@code V95__DT1_Phase0_DigitalTwinScene_scaffold.cypher}:
 *       {@code REQUIRE n.appId IS UNIQUE}.</li>
 * </ul>
 *
 * @see DigitalTwinScene
 * @see FrameKind
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CoordinateFrame implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /** Application-level identifier (UUID v7). Minted on save. */
  @Property("appId")
  private String appId;

  /** Short human-readable frame label (e.g. {@code "tool0"}, {@code "base_link"}). */
  @Property("name")
  private String name;

  /**
   * {@code appId} of the parent {@link CoordinateFrame}. {@code null}
   * marks this frame as a root (typically referenced by
   * {@link DigitalTwinScene#getRootFrameAppId()}).
   */
  @Property("parentFrameAppId")
  private String parentFrameAppId;

  /** Translation x relative to parent, metres. */
  @Property("x")
  private double x;

  /** Translation y relative to parent, metres. */
  @Property("y")
  private double y;

  /** Translation z relative to parent, metres. */
  @Property("z")
  private double z;

  /** Rotation about local x-axis (roll), radians. */
  @Property("rx")
  private double rx;

  /** Rotation about local y-axis (pitch), radians. */
  @Property("ry")
  private double ry;

  /** Rotation about local z-axis (yaw), radians. */
  @Property("rz")
  private double rz;

  /**
   * Frame discriminator. Nullable in OGM serialisation; service-layer
   * code treats {@code null} as {@link FrameKind#FRAME}.
   */
  @Property("kind")
  private FrameKind kind;

  /** For testing purposes only. */
  public CoordinateFrame(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof CoordinateFrame other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
