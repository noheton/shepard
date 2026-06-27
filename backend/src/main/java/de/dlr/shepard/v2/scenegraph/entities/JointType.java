package de.dlr.shepard.v2.scenegraph.entities;

/**
 * DT1-PHASE-0 — discriminator for {@link Joint} nodes.
 *
 * <p>Matches the URDF joint-type taxonomy so RDK-PARSE-2 / URDF importers
 * map directly without an intermediate translation table.
 */
public enum JointType {
  /** Rotates about an axis with finite limits. */
  REVOLUTE,
  /** Translates along an axis with finite limits. */
  PRISMATIC,
  /** Rigid connection (no motion). */
  FIXED,
  /** Rotates about an axis with no limit (e.g. wheel). */
  CONTINUOUS,
}
