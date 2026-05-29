package de.dlr.shepard.v2.scenegraph.entities;

/**
 * DT1-PHASE-0 — discriminator for {@link CoordinateFrame} nodes.
 *
 * <p>The enum set is the smallest one that gates SCENEGRAPH-REST-1; the
 * richer {@code frameType} taxonomy from {@code aidocs/data/85 §2}
 * (WORLD/FIXED/ROBOT_LINK/SENSOR/PART/TOOL/CUSTOM) is deferred to a
 * later phase to keep the scaffold scope minimal. New values added later
 * are additive per the "Always: evolve in a new namespace" rule.
 */
public enum FrameKind {
  /** Generic coordinate frame (no special semantics). */
  FRAME,
  /** Frame attached to a kinematic joint (transform driven by joint angle). */
  JOINT,
  /** Tool / tool-centre-point intermediate frame. */
  TOOL,
  /** Base / world-anchored frame for a robot or fixture. */
  BASE,
  /** Tool-centre-point frame (end-effector reference). */
  TCP,
}
