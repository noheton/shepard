package de.dlr.shepard.v2.scenegraph.export;

import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.entities.JointType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * SCENEGRAPH-REST-1 — emit URDF XML from a {@link DigitalTwinScene}.
 *
 * <h2>What's exported</h2>
 * <ul>
 *   <li>One {@code <link name="…"/>} per {@link CoordinateFrame} in the scene.
 *       Link name = the frame's {@code name} (falls back to {@code link_<appId>}
 *       when null).</li>
 *   <li>One {@code <joint name="…" type="…">} per {@link Joint} in the scene with
 *       {@code <parent link="…"/>}, {@code <child link="…"/>}, {@code <origin>}
 *       (from the child frame's local transform), {@code <axis>} (from the joint's
 *       axis triple), and {@code <limit lower="…" upper="…"/>} when the type
 *       supports limits.</li>
 * </ul>
 *
 * <h2>What's NOT yet exported</h2>
 * <ul>
 *   <li>{@code <visual>} / {@code <collision>} blocks — meshes live on
 *       {@code FileReference} payloads attached to the source DataObject; the
 *       exporter does not resolve them inline. Operators wanting visuals must
 *       hand-stitch URDFs or call this exporter twice and merge.</li>
 *   <li>{@code <inertial>} blocks — mass / inertia tensors are not yet captured
 *       on the scaffold entities (CAD1b / SB1d substrate).</li>
 *   <li>{@code <transmission>} blocks — ROS-control transmissions are out of
 *       scope until URDF-WEBVIEW-1 phase 2 wires the trajectory playback.</li>
 * </ul>
 *
 * <p>This is a pure-functional helper — no Neo4j calls, no I/O. Producers pass
 * the already-loaded scene + its frames + joints; testability is trivial.
 */
@ApplicationScoped
public class UrdfExporter {

  private static final String INDENT = "  ";

  /**
   * Render the URDF XML for a given scene.
   *
   * @param scene  scene metadata (used for {@code <robot name="…">}); never null
   * @param frames frames belonging to the scene (one {@code <link>} per frame)
   * @param joints joints belonging to the scene (one {@code <joint>} per joint)
   * @return the URDF XML document as a UTF-8 string with a single trailing newline
   */
  public String export(DigitalTwinScene scene, List<CoordinateFrame> frames, List<Joint> joints) {
    StringBuilder out = new StringBuilder();
    out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    String robotName = safeName(scene.getName(), "scene_" + safeAppId(scene.getAppId()));
    out.append("<robot name=\"").append(escape(robotName)).append("\">\n");

    // Frames → <link>s. Empty <link> is legal URDF.
    for (CoordinateFrame f : frames) {
      String linkName = linkNameFor(f);
      out.append(INDENT).append("<link name=\"").append(escape(linkName)).append("\"/>\n");
    }

    // Joints → <joint> blocks.
    for (Joint j : joints) {
      String jointName = safeName(j.getName(), "joint_" + safeAppId(j.getAppId()));
      String typeStr = urdfTypeOf(j.getType());
      out.append(INDENT).append("<joint name=\"").append(escape(jointName))
        .append("\" type=\"").append(typeStr).append("\">\n");

      CoordinateFrame parent = findFrame(frames, j.getParentFrameAppId());
      CoordinateFrame child = findFrame(frames, j.getChildFrameAppId());

      if (parent != null) {
        out.append(INDENT).append(INDENT)
          .append("<parent link=\"").append(escape(linkNameFor(parent))).append("\"/>\n");
      }
      if (child != null) {
        out.append(INDENT).append(INDENT)
          .append("<child link=\"").append(escape(linkNameFor(child))).append("\"/>\n");

        // <origin> uses the child frame's local transform — the URDF convention
        // pins joint origin to the child link's frame relative to the parent.
        out.append(INDENT).append(INDENT)
          .append("<origin xyz=\"")
          .append(fmt(child.getX())).append(' ')
          .append(fmt(child.getY())).append(' ')
          .append(fmt(child.getZ())).append("\" rpy=\"")
          .append(fmt(child.getRx())).append(' ')
          .append(fmt(child.getRy())).append(' ')
          .append(fmt(child.getRz())).append("\"/>\n");
      }

      out.append(INDENT).append(INDENT)
        .append("<axis xyz=\"")
        .append(fmt(j.getAxisX())).append(' ')
        .append(fmt(j.getAxisY())).append(' ')
        .append(fmt(j.getAxisZ())).append("\"/>\n");

      if (hasLimits(j.getType())) {
        out.append(INDENT).append(INDENT)
          .append("<limit lower=\"").append(fmt(j.getLimitMin()))
          .append("\" upper=\"").append(fmt(j.getLimitMax()))
          .append("\" effort=\"0\" velocity=\"0\"/>\n");
      }

      out.append(INDENT).append("</joint>\n");
    }

    out.append("</robot>\n");
    return out.toString();
  }

  private static CoordinateFrame findFrame(List<CoordinateFrame> frames, String appId) {
    if (appId == null) return null;
    for (CoordinateFrame f : frames) {
      if (appId.equals(f.getAppId())) return f;
    }
    return null;
  }

  private static String linkNameFor(CoordinateFrame f) {
    return safeName(f.getName(), "link_" + safeAppId(f.getAppId()));
  }

  private static String safeName(String preferred, String fallback) {
    if (preferred == null || preferred.isBlank()) return fallback;
    // URDF link/joint names must be XML NAME tokens — no whitespace; normalise
    // any whitespace to underscore but otherwise pass-through.
    return preferred.trim().replaceAll("\\s+", "_");
  }

  private static String safeAppId(String appId) {
    return appId == null ? "anonymous" : appId.replace("-", "");
  }

  private static String urdfTypeOf(JointType t) {
    if (t == null) return "fixed";
    return switch (t) {
      case REVOLUTE -> "revolute";
      case PRISMATIC -> "prismatic";
      case CONTINUOUS -> "continuous";
      case FIXED -> "fixed";
    };
  }

  private static boolean hasLimits(JointType t) {
    return t == JointType.REVOLUTE || t == JointType.PRISMATIC;
  }

  private static String fmt(double d) {
    // Strip trailing zeros from the canonical decimal representation. Java's
    // %s for double does the right thing for integral values (1.0 not 1.0E0).
    if (d == (long) d) return Long.toString((long) d);
    return Double.toString(d);
  }

  private static String escape(String s) {
    if (s == null) return "";
    return s
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;");
  }
}
