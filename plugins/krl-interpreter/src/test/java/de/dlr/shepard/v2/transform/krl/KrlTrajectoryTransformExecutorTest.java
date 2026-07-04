package de.dlr.shepard.v2.transform.krl;

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
 * V2CONV-B5 — unit tests for {@link KrlTrajectoryTransformExecutor}.
 *
 * <p>The full {@code materialize} path resolves the {@code KrlTrajectoryService}
 * via {@code CDI.current()} + calls the sidecar, which is not available in a
 * plain JUnit context — so the end-to-end interpret is exercised by the backend
 * integration tests (CI). Here we cover the pure machinery: shape-IRI claim,
 * stable name, body parsing, and the typed input-validation failure paths that
 * fire BEFORE any CDI/sidecar interaction.
 */
class KrlTrajectoryTransformExecutorTest {

  private final KrlTrajectoryTransformExecutor executor = new KrlTrajectoryTransformExecutor();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void claimsTheKrlTrajectoryShapeIri() {
    assertThat(executor.supportedShapeIris())
      .containsExactly(KrlTrajectoryTransformExecutor.KRL_TRAJECTORY_SHAPE_IRI);
  }

  @Test
  void nameIsStable() {
    assertThat(executor.name()).isEqualTo("KrlTrajectoryTransformExecutor");
  }

  @Test
  void rejectsNullRequest() {
    assertThatThrownBy(() -> executor.materialize(null))
      .isInstanceOf(TransformException.class);
  }

  @Test
  void failsWhenNoSrcAppIdBoundOrInBody() {
    var req = new TransformRequest(
      "tmpl-1",
      KrlTrajectoryTransformExecutor.KRL_TRAJECTORY_SHAPE_IRI,
      Map.of(),
      "alice",
      "{\"mappingRecipeShape\":\"x\",\"urdfFileReferenceAppId\":\"u-1\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining(".src/.krl FileReference appId");
  }

  @Test
  void failsWhenNoUrdfAppIdBoundOrInBody() {
    var req = new TransformRequest(
      "tmpl-1",
      KrlTrajectoryTransformExecutor.KRL_TRAJECTORY_SHAPE_IRI,
      Map.of("srcFileAppId", "src-1"),
      "alice",
      "{\"mappingRecipeShape\":\"x\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("URDF FileReference appId");
  }

  @Test
  void failsWhenNoTargetDataObjectInBody() {
    // src + urdf resolved from bindings, but the body omits targetDataObjectAppId.
    var req = new TransformRequest(
      "tmpl-1",
      KrlTrajectoryTransformExecutor.KRL_TRAJECTORY_SHAPE_IRI,
      Map.of("srcFileAppId", "src-1", "urdfFileAppId", "urdf-1"),
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
      KrlTrajectoryTransformExecutor.KRL_TRAJECTORY_SHAPE_IRI,
      Map.of("srcFileAppId", "src-1", "urdfFileAppId", "urdf-1"),
      "alice",
      "{\"mappingRecipeShape\":\"x\",\"targetDataObjectAppId\":\"do-1\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("timeseriesContainerAppId");
  }

  @Test
  void srcAndUrdfFallBackToBodyFieldsWhenBindingsAbsent() throws Exception {
    // No bindings; src + urdf taken from the body, then targetDataObject is the
    // first missing field — proving the body fallback resolved src + urdf.
    var req = new TransformRequest(
      "tmpl-1",
      KrlTrajectoryTransformExecutor.KRL_TRAJECTORY_SHAPE_IRI,
      Map.of(),
      "alice",
      "{\"srcFileReferenceAppId\":\"s-1\",\"urdfFileReferenceAppId\":\"u-1\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("targetDataObjectAppId");
  }

  @Test
  void parsesDatAppIdsFromArray() throws Exception {
    JsonNode body = MAPPER.readTree("{\"datFileReferenceAppIds\":[\"d-1\",\"d-2\"]}");
    List<String> dat = executor.parseDatAppIds(body);
    assertThat(dat).containsExactly("d-1", "d-2");
  }

  @Test
  void parsesDatAppIdsFromStringifiedArray() throws Exception {
    JsonNode body = MAPPER.readTree("{\"datFileReferenceAppIds\":\"[\\\"d-3\\\"]\"}");
    assertThat(executor.parseDatAppIds(body)).containsExactly("d-3");
  }

  @Test
  void emptyDatAppIdsWhenFieldAbsent() throws Exception {
    JsonNode body = MAPPER.readTree("{}");
    assertThat(executor.parseDatAppIds(body)).isEmpty();
  }
}
