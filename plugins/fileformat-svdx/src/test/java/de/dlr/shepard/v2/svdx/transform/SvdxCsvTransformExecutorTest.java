package de.dlr.shepard.v2.svdx.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-A7 — unit tests for {@link SvdxCsvTransformExecutor}.
 *
 * <p>The full {@code materialize} path resolves the {@code SvdxCsvIngestionService}
 * via {@code CDI.current()} + parses CSV bytes + writes to TimescaleDB, none of
 * which is available in a plain JUnit context — so the end-to-end ingest is
 * exercised by the backend integration tests (CI). Here we cover the pure
 * machinery: shape-IRI claim, stable name, body parsing, and the typed
 * input-validation failure paths that fire BEFORE any CDI/persistence interaction.
 * Mirrors {@code KrlTrajectoryTransformExecutorTest} exactly.
 */
class SvdxCsvTransformExecutorTest {

  private final SvdxCsvTransformExecutor executor = new SvdxCsvTransformExecutor();

  @Test
  void claimsTheSvdxCsvIngestShapeIri() {
    assertThat(executor.supportedShapeIris())
      .containsExactly(SvdxCsvTransformExecutor.SVDX_CSV_INGEST_SHAPE_IRI);
  }

  @Test
  void nameIsStable() {
    assertThat(executor.name()).isEqualTo("SvdxCsvTransformExecutor");
  }

  @Test
  void rejectsNullRequest() {
    assertThatThrownBy(() -> executor.materialize(null))
      .isInstanceOf(TransformException.class);
  }

  @Test
  void failsWhenNoSvdxAppIdBoundOrInBody() {
    var req = new TransformRequest(
      "tmpl-1",
      SvdxCsvTransformExecutor.SVDX_CSV_INGEST_SHAPE_IRI,
      Map.of(),
      "alice",
      "{\"mappingRecipeShape\":\"x\",\"csvFileReferenceAppId\":\"c-1\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining(".svdx FileReference appId");
  }

  @Test
  void failsWhenNoCsvAppIdBoundOrInBody() {
    var req = new TransformRequest(
      "tmpl-1",
      SvdxCsvTransformExecutor.SVDX_CSV_INGEST_SHAPE_IRI,
      Map.of("svdxFileAppId", "svdx-1"),
      "alice",
      "{\"mappingRecipeShape\":\"x\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining(".csv FileReference appId");
  }

  @Test
  void failsWhenNoTargetDataObjectInBody() {
    // svdx + csv resolved from bindings, but the body omits targetDataObjectAppId.
    var req = new TransformRequest(
      "tmpl-1",
      SvdxCsvTransformExecutor.SVDX_CSV_INGEST_SHAPE_IRI,
      Map.of("svdxFileAppId", "svdx-1", "csvFileAppId", "csv-1"),
      "alice",
      "{\"mappingRecipeShape\":\"x\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("targetDataObjectAppId");
  }

  @Test
  void svdxAndCsvFallBackToBodyFieldsWhenBindingsAbsent() {
    // No bindings; svdx + csv taken from the body, then targetDataObject is the
    // first missing field — proving the body fallback resolved svdx + csv.
    var req = new TransformRequest(
      "tmpl-1",
      SvdxCsvTransformExecutor.SVDX_CSV_INGEST_SHAPE_IRI,
      Map.of(),
      "alice",
      "{\"svdxFileReferenceAppId\":\"s-1\",\"csvFileReferenceAppId\":\"c-1\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("targetDataObjectAppId");
  }

  @Test
  void bindingRoleTakesPrecedenceOverBodyField() {
    // Both binding role and body field present; binding wins. We can't observe
    // the resolved value directly without CDI, but supplying only svdx via
    // binding + csv via body and omitting targetDataObject proves both resolved
    // (otherwise the failure would name the .csv, not targetDataObjectAppId).
    var req = new TransformRequest(
      "tmpl-1",
      SvdxCsvTransformExecutor.SVDX_CSV_INGEST_SHAPE_IRI,
      Map.of("svdxFileAppId", "svdx-binding"),
      "alice",
      "{\"svdxFileReferenceAppId\":\"svdx-body\",\"csvFileReferenceAppId\":\"c-1\"}"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("targetDataObjectAppId");
  }

  @Test
  void toleratesNullBody() {
    // A null body still produces a typed missing-input failure (not an NPE).
    var req = new TransformRequest(
      "tmpl-1",
      SvdxCsvTransformExecutor.SVDX_CSV_INGEST_SHAPE_IRI,
      Map.of(),
      "alice",
      null
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining(".svdx FileReference appId");
  }

  @Test
  void rejectsMalformedJsonBody() {
    var req = new TransformRequest(
      "tmpl-1",
      SvdxCsvTransformExecutor.SVDX_CSV_INGEST_SHAPE_IRI,
      Map.of(),
      "alice",
      "{ not valid json"
    );
    assertThatThrownBy(() -> executor.materialize(req))
      .isInstanceOf(TransformException.class)
      .hasMessageContaining("not valid JSON");
  }
}
