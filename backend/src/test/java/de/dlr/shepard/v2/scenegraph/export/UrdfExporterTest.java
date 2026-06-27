package de.dlr.shepard.v2.scenegraph.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.entities.JointType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SCENEGRAPH-REST-1 — unit tests for {@link UrdfExporter}.
 *
 * <p>Pure-functional: no Quarkus context, no Neo4j. Verifies the
 * URDF XML shape for the four important paths — empty scene, single
 * link, revolute-joint pair with limits, and a fixed joint (no limit
 * block).
 */
public class UrdfExporterTest {

  private final UrdfExporter exporter = new UrdfExporter();

  @Test
  public void export_emptyScene_emitsRobotShell() {
    var scene = new DigitalTwinScene();
    scene.setAppId("019e7243-f995-7914-be80-aaa000000001");
    scene.setName("empty");

    String urdf = exporter.export(scene, List.of(), List.of());

    assertTrue(urdf.startsWith("<?xml version=\"1.0\""));
    assertTrue(urdf.contains("<robot name=\"empty\">"));
    assertTrue(urdf.contains("</robot>"));
    assertFalse(urdf.contains("<link"));
    assertFalse(urdf.contains("<joint"));
  }

  @Test
  public void export_singleFrame_emitsOneLink() {
    var scene = new DigitalTwinScene();
    scene.setAppId("019e7243-f995-7914-be80-aaa000000002");
    scene.setName("one-link");
    var frame = new CoordinateFrame();
    frame.setAppId("019e7243-f995-7914-be80-bbb000000001");
    frame.setName("base_link");
    frame.setKind(FrameKind.BASE);

    String urdf = exporter.export(scene, List.of(frame), List.of());

    assertTrue(urdf.contains("<link name=\"base_link\"/>"), "expected base_link, got: " + urdf);
  }

  @Test
  public void export_revoluteJoint_emitsAllBlocks() {
    var scene = new DigitalTwinScene();
    scene.setAppId("019e7243-f995-7914-be80-aaa000000003");
    scene.setName("arm");

    var parent = new CoordinateFrame();
    parent.setAppId("019e7243-f995-7914-be80-bbb000000010");
    parent.setName("base_link");

    var child = new CoordinateFrame();
    child.setAppId("019e7243-f995-7914-be80-bbb000000011");
    child.setName("upper_arm");
    child.setX(0.0); child.setY(0.0); child.setZ(0.25);
    child.setRx(0.0); child.setRy(0.0); child.setRz(0.0);

    var joint = new Joint();
    joint.setAppId("019e7243-f995-7914-be80-ccc000000001");
    joint.setName("shoulder");
    joint.setParentFrameAppId(parent.getAppId());
    joint.setChildFrameAppId(child.getAppId());
    joint.setAxisX(0); joint.setAxisY(0); joint.setAxisZ(1);
    joint.setLimitMin(-Math.PI); joint.setLimitMax(Math.PI);
    joint.setType(JointType.REVOLUTE);

    String urdf = exporter.export(scene, List.of(parent, child), List.of(joint));

    assertTrue(urdf.contains("<joint name=\"shoulder\" type=\"revolute\">"));
    assertTrue(urdf.contains("<parent link=\"base_link\"/>"));
    assertTrue(urdf.contains("<child link=\"upper_arm\"/>"));
    assertTrue(urdf.contains("<origin xyz=\"0 0 0.25\""), "expected origin xyz, got: " + urdf);
    assertTrue(urdf.contains("<axis xyz=\"0 0 1\"/>"));
    assertTrue(urdf.contains("<limit lower="), "REVOLUTE must emit a <limit> block");
  }

  @Test
  public void export_fixedJoint_omitsLimits() {
    var scene = new DigitalTwinScene();
    scene.setAppId("019e7243-f995-7914-be80-aaa000000004");
    scene.setName("rigid");

    var parent = new CoordinateFrame();
    parent.setAppId("p1"); parent.setName("p");
    var child = new CoordinateFrame();
    child.setAppId("c1"); child.setName("c");
    var joint = new Joint();
    joint.setAppId("j1"); joint.setName("fix1");
    joint.setParentFrameAppId("p1"); joint.setChildFrameAppId("c1");
    joint.setType(JointType.FIXED);

    String urdf = exporter.export(scene, List.of(parent, child), List.of(joint));

    assertTrue(urdf.contains("<joint name=\"fix1\" type=\"fixed\">"));
    assertFalse(urdf.contains("<limit "), "FIXED must NOT emit a <limit> block");
  }

  @Test
  public void export_xmlEscapesUnsafeNames() {
    var scene = new DigitalTwinScene();
    scene.setName("rocket & fire");

    String urdf = exporter.export(scene, List.of(), List.of());

    assertTrue(urdf.contains("&amp;"), "ampersand must be escaped, got: " + urdf);
    assertFalse(urdf.contains("\"rocket & fire\""), "raw ampersand must not appear in attribute");
  }

  @Test
  public void export_continuousJoint_emitsContinuousType() {
    var scene = new DigitalTwinScene();
    scene.setName("wheel-bot");

    var parent = new CoordinateFrame(); parent.setAppId("p"); parent.setName("chassis");
    var child = new CoordinateFrame(); child.setAppId("c"); child.setName("wheel");
    var j = new Joint();
    j.setAppId("j"); j.setName("axle");
    j.setParentFrameAppId("p"); j.setChildFrameAppId("c");
    j.setType(JointType.CONTINUOUS);
    j.setAxisX(0); j.setAxisY(1); j.setAxisZ(0);

    String urdf = exporter.export(scene, List.of(parent, child), List.of(j));

    assertTrue(urdf.contains("type=\"continuous\""));
    assertFalse(urdf.contains("<limit "), "CONTINUOUS must NOT emit a <limit> block");
  }
}
