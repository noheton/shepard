package de.dlr.shepard.plugins.vistrace3d.scenegraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-B4 — unit tests for {@link SceneGraphPlayTransformExecutor}.
 *
 * <p>The full {@code materialize} path resolves the URDF via the
 * request-scoped {@code SingletonFileReferenceService} through
 * {@code CDI.current()}, which is not available in a plain JUnit context — so
 * the CDI-coupled URDF resolution is exercised by the backend integration
 * tests. Here we cover the pure machinery: shape-IRI claim, body parsing, the
 * input-missing failure path, and the play-envelope construction from an
 * already-parsed model.
 */
class SceneGraphPlayTransformExecutorTest {

  private final SceneGraphPlayTransformExecutor executor = new SceneGraphPlayTransformExecutor();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void claimsTheSceneGraphPlayShapeIri() {
    assertThat(executor.supportedShapeIris())
      .containsExactly(SceneGraphPlayTransformExecutor.SCENE_GRAPH_PLAY_SHAPE_IRI);
  }

  @Test
  void nameIsStable() {
    assertThat(executor.name()).isEqualTo("SceneGraphPlayTransformExecutor");
  }

  @Test
  void failsWithTypedCodeWhenNoUrdfAppIdBoundOrInBody() {
    var req = new TransformRequest(
      "tmpl-1",
      SceneGraphPlayTransformExecutor.SCENE_GRAPH_PLAY_SHAPE_IRI,
      Map.of(),
      "alice",
      "{\"mappingRecipeShape\":\"x\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("URDF FileReference appId");
  }

  @Test
  void rejectsNullRequest() {
    assertThatThrownBy(() -> executor.materialize(null))
      .isInstanceOf(TransformException.class);
  }

  @Test
  void buildsPlayEnvelopeWithFrameTreeAndJoints() {
    UrdfKinematics.UrdfModel model = new UrdfKinematics.UrdfModel(
      "kr210",
      List.of(new UrdfKinematics.UrdfLink("base_link"), new UrdfKinematics.UrdfLink("link_1")),
      List.of(new UrdfKinematics.UrdfJoint(
        "joint_1", "revolute", "base_link", "link_1",
        0, 0, 0.675, 0, 0, 0, 0, 0, 1, -3.14, 3.14
      ))
    );

    Map<String, Object> env = executor.buildPlayEnvelope("urdf-app-1", null, model, List.of());

    assertThat(env.get("kind")).isEqualTo("scene-graph-play");
    assertThat(env.get("renderer")).isEqualTo("urdf");
    assertThat(env.get("robotName")).isEqualTo("kr210");
    assertThat(env.get("urdfFileReferenceAppId")).isEqualTo("urdf-app-1");
    assertThat(env.get("rootLink")).isEqualTo("base_link");
    assertThat(env.get("playbackStatus")).isEqualTo("STATIC_POSE");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> frames = (List<Map<String, Object>>) env.get("frames");
    assertThat(frames).hasSize(2);
    assertThat(frames.get(0)).containsEntry("name", "base_link").containsEntry("parent", null);
    assertThat(frames.get(1)).containsEntry("name", "link_1").containsEntry("parent", "base_link");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> joints = (List<Map<String, Object>>) env.get("joints");
    assertThat(joints).hasSize(1);
    assertThat(joints.get(0))
      .containsEntry("name", "joint_1")
      .containsEntry("type", "revolute")
      .containsEntry("limitLower", -3.14);
  }

  @Test
  void playbackStatusIsDeclaredWhenJointTimeseriesBound() {
    UrdfKinematics.UrdfModel model = new UrdfKinematics.UrdfModel(
      "r", List.of(new UrdfKinematics.UrdfLink("a")), List.of()
    );
    Map<String, Object> env = executor.buildPlayEnvelope("urdf-1", "ts-1", model, List.of());
    assertThat(env.get("playbackStatus")).isEqualTo("DECLARED");
    assertThat(env.get("jointTimeseriesReferenceAppId")).isEqualTo("ts-1");
  }

  @Test
  void parsesJointChannelBindingsFromArray() throws Exception {
    JsonNode body = MAPPER.readTree(
      "{\"jointChannelBindings\":[{\"joint\":\"joint_1\",\"channelSelector\":\"sel-1\"}]}"
    );
    List<Map<String, Object>> bindings = executor.parseJointChannelBindings(body);
    assertThat(bindings).hasSize(1);
    assertThat(bindings.get(0)).containsEntry("joint", "joint_1").containsEntry("channelSelector", "sel-1");
  }

  @Test
  void parsesJointChannelBindingsFromStringifiedArray() throws Exception {
    JsonNode body = MAPPER.readTree(
      "{\"jointChannelBindings\":\"[{\\\"joint\\\":\\\"j2\\\"}]\"}"
    );
    List<Map<String, Object>> bindings = executor.parseJointChannelBindings(body);
    assertThat(bindings).hasSize(1);
    assertThat(bindings.get(0)).containsEntry("joint", "j2");
  }

  @Test
  void emptyBindingsWhenFieldAbsent() throws Exception {
    JsonNode body = MAPPER.readTree("{}");
    assertThat(executor.parseJointChannelBindings(body)).isEmpty();
  }
}
