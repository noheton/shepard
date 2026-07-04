package de.dlr.shepard.v2.transform.urscript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * URSCRIPT-TRAJECTORY-1 — unit tests for {@link UrScriptTrajectoryTransformExecutor}.
 *
 * <p>The full {@code materialize} path resolves the {@code UrScriptTrajectoryService}
 * via {@code CDI.current()} + calls the sidecar, which is not available in a
 * plain JUnit context — so the end-to-end interpret is exercised by the backend
 * integration tests (CI). Here we cover the pure machinery: shape-IRI claim,
 * stable name, body parsing, and the typed input-validation failure paths that
 * fire BEFORE any CDI/sidecar interaction.
 */
class UrScriptTrajectoryTransformExecutorTest {

  private final UrScriptTrajectoryTransformExecutor executor = new UrScriptTrajectoryTransformExecutor();

  @Test
  void claimsTheUrScriptTrajectoryShapeIri() {
    assertThat(executor.supportedShapeIris())
      .containsExactly(UrScriptTrajectoryTransformExecutor.URSCRIPT_TRAJECTORY_SHAPE_IRI);
  }

  @Test
  void nameIsStable() {
    assertThat(executor.name()).isEqualTo("UrScriptTrajectoryTransformExecutor");
  }

  @Test
  void rejectsNullRequest() {
    assertThatThrownBy(() -> executor.materialize(null))
      .isInstanceOf(TransformException.class);
  }

  @Test
  void failsWhenNoUrscriptAppIdBoundOrInBody() {
    var req = new TransformRequest(
      "tmpl-1",
      UrScriptTrajectoryTransformExecutor.URSCRIPT_TRAJECTORY_SHAPE_IRI,
      Map.of(),
      "alice",
      "{\"mappingRecipeShape\":\"x\",\"urdfFileReferenceAppId\":\"u-1\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining(".urscript/.script FileReference appId");
  }

  @Test
  void failsWhenNoUrdfAppIdBoundOrInBody() {
    var req = new TransformRequest(
      "tmpl-1",
      UrScriptTrajectoryTransformExecutor.URSCRIPT_TRAJECTORY_SHAPE_IRI,
      Map.of("urscriptFileAppId", "us-1"),
      "alice",
      "{\"mappingRecipeShape\":\"x\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("URDF FileReference appId");
  }

  @Test
  void failsWhenNoTargetDataObjectInBody() {
    // urscript + urdf resolved from bindings, but body omits targetDataObjectAppId.
    var req = new TransformRequest(
      "tmpl-1",
      UrScriptTrajectoryTransformExecutor.URSCRIPT_TRAJECTORY_SHAPE_IRI,
      Map.of("urscriptFileAppId", "us-1", "urdfFileAppId", "urdf-1"),
      "alice",
      "{\"mappingRecipeShape\":\"x\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("targetDataObjectAppId");
  }

  @Test
  void failsWhenNoContainerInBody() {
    var req = new TransformRequest(
      "tmpl-1",
      UrScriptTrajectoryTransformExecutor.URSCRIPT_TRAJECTORY_SHAPE_IRI,
      Map.of("urscriptFileAppId", "us-1", "urdfFileAppId", "urdf-1"),
      "alice",
      "{\"mappingRecipeShape\":\"x\",\"targetDataObjectAppId\":\"do-1\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("timeseriesContainerAppId");
  }

  @Test
  void urscriptAndUrdfFallBackToBodyFieldsWhenBindingsAbsent() {
    // No bindings; urscript + urdf taken from the body, then targetDataObject is
    // the first missing field — proving the body fallback resolved both.
    var req = new TransformRequest(
      "tmpl-1",
      UrScriptTrajectoryTransformExecutor.URSCRIPT_TRAJECTORY_SHAPE_IRI,
      Map.of(),
      "alice",
      "{\"urscriptFileReferenceAppId\":\"us-1\",\"urdfFileReferenceAppId\":\"u-1\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("targetDataObjectAppId");
  }

  @Test
  void shapeIriIsDistinctFromKrl() {
    assertThat(UrScriptTrajectoryTransformExecutor.URSCRIPT_TRAJECTORY_SHAPE_IRI)
      .isNotEqualTo("http://semantics.dlr.de/shepard/transform#KrlTrajectoryShape")
      .isEqualTo("http://semantics.dlr.de/shepard/transform#UrScriptTrajectoryShape");
  }
}
