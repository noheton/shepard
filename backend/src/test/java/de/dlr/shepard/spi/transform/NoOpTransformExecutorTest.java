package de.dlr.shepard.spi.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** V2CONV-B3 — unit tests for the built-in identity {@link NoOpTransformExecutor}. */
class NoOpTransformExecutorTest {

  private final NoOpTransformExecutor executor = new NoOpTransformExecutor();

  @Test
  void claimsTheIdentityShapeIri() {
    assertThat(executor.supportedShapeIris()).containsExactly(NoOpTransformExecutor.IDENTITY_SHAPE_IRI);
  }

  @Test
  void echoesSingleInputReferenceAsDerivedReference() {
    var req = new TransformRequest(
      "tmpl-1",
      NoOpTransformExecutor.IDENTITY_SHAPE_IRI,
      Map.of("srcFileAppId", "ref-appid-1"),
      "alice",
      "{}"
    );

    TransformResult result = executor.materialize(req);

    assertThat(result.kind()).isEqualTo(TransformResult.Kind.REFERENCE);
    assertThat(result.derivedReferenceAppId()).isEqualTo("ref-appid-1");
    assertThat(result.viewModel()).isNull();
    assertThat(result.executorName()).isEqualTo("NoOpTransformExecutor");
  }

  @Test
  void echoesOneOfTheInputReferencesWhenMultipleBound() {
    // The identity transform's "first" is unspecified across multiple inputs
    // (TransformRequest copies into an unordered map); assert it picks one.
    Map<String, String> inputs = new LinkedHashMap<>();
    inputs.put("srcFileAppId", "ref-appid-1");
    inputs.put("urdfFileAppId", "ref-appid-2");
    var req = new TransformRequest("tmpl-1", NoOpTransformExecutor.IDENTITY_SHAPE_IRI, inputs, "alice", "{}");

    TransformResult result = executor.materialize(req);

    assertThat(result.kind()).isEqualTo(TransformResult.Kind.REFERENCE);
    assertThat(result.derivedReferenceAppId()).isIn("ref-appid-1", "ref-appid-2");
  }

  @Test
  void throwsInputMissingWhenNoInputs() {
    var req = new TransformRequest("tmpl-1", NoOpTransformExecutor.IDENTITY_SHAPE_IRI, Map.of(), "alice", "{}");
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("at least one input reference");
    assertThat(((TransformException) catchIt(req)).code()).isEqualTo("transform.input.missing");
  }

  @Test
  void throwsBodyInvalidOnNullRequest() {
    assertThatThrownBy(() -> executor.materialize(null))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("must not be null");
  }

  private Throwable catchIt(TransformRequest req) {
    try {
      executor.materialize(req);
      throw new AssertionError("expected throw");
    } catch (TransformException ex) {
      return ex;
    }
  }
}
