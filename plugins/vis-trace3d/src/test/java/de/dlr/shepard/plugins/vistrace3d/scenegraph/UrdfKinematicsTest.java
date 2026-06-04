package de.dlr.shepard.plugins.vistrace3d.scenegraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** V2CONV-B4 — unit tests for the self-contained URDF kinematic-tree parser. */
class UrdfKinematicsTest {

  private static final String TWO_JOINT_URDF =
    "<?xml version=\"1.0\"?>"
    + "<robot name=\"kr210\">"
    + "  <link name=\"base_link\"/>"
    + "  <link name=\"link_1\"/>"
    + "  <link name=\"link_2\"/>"
    + "  <joint name=\"joint_1\" type=\"revolute\">"
    + "    <parent link=\"base_link\"/>"
    + "    <child link=\"link_1\"/>"
    + "    <origin xyz=\"0 0 0.675\" rpy=\"0 0 0\"/>"
    + "    <axis xyz=\"0 0 1\"/>"
    + "    <limit lower=\"-3.14\" upper=\"3.14\"/>"
    + "  </joint>"
    + "  <joint name=\"joint_2\" type=\"prismatic\">"
    + "    <parent link=\"link_1\"/>"
    + "    <child link=\"link_2\"/>"
    + "    <origin xyz=\"0.35 0 0\" rpy=\"0 1.5708 0\"/>"
    + "    <axis xyz=\"1 0 0\"/>"
    + "  </joint>"
    + "</robot>";

  private static UrdfKinematics.UrdfModel parse(String xml) {
    return UrdfKinematics.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void parsesRobotNameLinksAndJoints() {
    UrdfKinematics.UrdfModel model = parse(TWO_JOINT_URDF);
    assertThat(model.robotName()).isEqualTo("kr210");
    assertThat(model.links()).extracting(UrdfKinematics.UrdfLink::name)
      .containsExactly("base_link", "link_1", "link_2");
    assertThat(model.joints()).extracting(UrdfKinematics.UrdfJoint::name)
      .containsExactly("joint_1", "joint_2");
  }

  @Test
  void resolvesRootAsLinkWithNoIncomingJoint() {
    assertThat(parse(TWO_JOINT_URDF).rootLink()).isEqualTo("base_link");
  }

  @Test
  void capturesOriginAxisAndLimits() {
    UrdfKinematics.UrdfModel model = parse(TWO_JOINT_URDF);
    UrdfKinematics.UrdfJoint j1 = model.joints().get(0);
    assertThat(j1.type()).isEqualTo("revolute");
    assertThat(j1.originZ()).isEqualTo(0.675);
    assertThat(j1.axisZ()).isEqualTo(1.0);
    assertThat(j1.limitLower()).isEqualTo(-3.14);
    assertThat(j1.limitUpper()).isEqualTo(3.14);
    assertThat(j1.parentLink()).isEqualTo("base_link");
    assertThat(j1.childLink()).isEqualTo("link_1");

    UrdfKinematics.UrdfJoint j2 = model.joints().get(1);
    assertThat(j2.originPitch()).isEqualTo(1.5708);
    assertThat(j2.axisX()).isEqualTo(1.0);
    assertThat(j2.limitLower()).isNull();
  }

  @Test
  void defaultsJointTypeToFixedAndAxisToZ() {
    UrdfKinematics.UrdfModel model = parse(
      "<robot name=\"r\"><link name=\"a\"/><link name=\"b\"/>"
      + "<joint name=\"j\"><parent link=\"a\"/><child link=\"b\"/></joint></robot>"
    );
    UrdfKinematics.UrdfJoint j = model.joints().get(0);
    assertThat(j.type()).isEqualTo("fixed");
    assertThat(j.axisZ()).isEqualTo(1.0);
  }

  @Test
  void rejectsNonRobotRoot() {
    assertThatThrownBy(() -> parse("<notrobot/>"))
      .isInstanceOf(UrdfKinematics.UrdfParseException.class)
      .hasMessageContaining("robot");
  }

  @Test
  void rejectsRobotWithNoLinks() {
    assertThatThrownBy(() -> parse("<robot name=\"empty\"/>"))
      .isInstanceOf(UrdfKinematics.UrdfParseException.class)
      .hasMessageContaining("link");
  }

  @Test
  void rejectsMalformedXml() {
    assertThatThrownBy(() -> parse("<robot><link name=\"a\""))
      .isInstanceOf(UrdfKinematics.UrdfParseException.class);
  }
}
