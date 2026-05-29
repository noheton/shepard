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
 * DT1-PHASE-0 — {@code :Joint} entity scaffold.
 *
 * <p>A kinematic joint connecting two {@link CoordinateFrame}s in a
 * {@link DigitalTwinScene}. Joints carry the URDF/RoboDK axis +
 * limit triple ({@code axisX/axisY/axisZ}, {@code limitMin},
 * {@code limitMax}, {@code homeAngle}) so RDK-PARSE-2 and URDF
 * importers can hydrate them without an intermediate translation
 * step.
 *
 * <p>The graph edges
 * {@code (:Joint)-[:JOINT_PARENT]->(:CoordinateFrame)} +
 * {@code (:Joint)-[:JOINT_CHILD]->(:CoordinateFrame)} +
 * {@code (:DigitalTwinScene)-[:HAS_JOINT]->(:Joint)} are documented
 * in the {@code V95} migration comment; the edges are written by the
 * service layer in {@code SCENEGRAPH-REST-1}. This entity references
 * its endpoints via scalar appIds rather than OGM
 * {@code @Relationship}-mapped objects to keep the scaffold pure-data.
 *
 * <p><b>Cross-cutting rules.</b>
 * <ul>
 *   <li>{@code HasAppId} — UUID v7 minted on save (L2a write seam).</li>
 *   <li>All non-primary fields nullable. Primitive doubles default to
 *       {@code 0.0d}.</li>
 *   <li>Constraint shipped by
 *       {@code V95__DT1_Phase0_DigitalTwinScene_scaffold.cypher}:
 *       {@code REQUIRE n.appId IS UNIQUE}.</li>
 * </ul>
 *
 * @see CoordinateFrame
 * @see DigitalTwinScene
 * @see JointType
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Joint implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /** Application-level identifier (UUID v7). Minted on save. */
  @Property("appId")
  private String appId;

  /** Short human-readable joint label (e.g. {@code "joint_1"}). */
  @Property("name")
  private String name;

  /** {@code appId} of the parent {@link CoordinateFrame}. */
  @Property("parentFrameAppId")
  private String parentFrameAppId;

  /** {@code appId} of the child {@link CoordinateFrame} this joint drives. */
  @Property("childFrameAppId")
  private String childFrameAppId;

  /** x-component of the unit axis vector in the parent frame. */
  @Property("axisX")
  private double axisX;

  /** y-component of the unit axis vector. */
  @Property("axisY")
  private double axisY;

  /** z-component of the unit axis vector. */
  @Property("axisZ")
  private double axisZ;

  /**
   * Minimum joint position. Units depend on {@link #type}: radians for
   * {@link JointType#REVOLUTE} / {@link JointType#CONTINUOUS}, metres
   * for {@link JointType#PRISMATIC}. Meaningless for
   * {@link JointType#FIXED} (carry {@code 0.0d}).
   */
  @Property("limitMin")
  private double limitMin;

  /** Maximum joint position (units per {@link #limitMin}). */
  @Property("limitMax")
  private double limitMax;

  /**
   * Joint discriminator. Nullable in OGM; service-layer code treats
   * {@code null} as {@link JointType#FIXED}.
   */
  @Property("type")
  private JointType type;

  /**
   * "Home" position the joint snaps to on scene load (units per
   * {@link #limitMin}).
   */
  @Property("homeAngle")
  private double homeAngle;

  /** For testing purposes only. */
  public Joint(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Joint other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
