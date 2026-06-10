package de.dlr.shepard.v2.shapes.mffd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.spi.view.RenderException;
import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.RenderResponse.ChannelBindingProjection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MFFD-RENDER-MATERIAL-BATCH-TRACE (slice 2) — unit tests for
 * {@link MaterialBatchTraceViewRenderer}.
 *
 * <p>No CDI, no Neo4j — {@link SemanticAnnotationDAO} is mocked via Mockito.
 * The full CDI-lookup path ({@link MaterialBatchTraceViewRenderer#render}) is
 * intentionally not covered here; that path requires a running container and
 * is exercised by the backend integration tests in CI. The coverage target here
 * is the pure rendering logic exposed via the package-private
 * {@link MaterialBatchTraceViewRenderer#renderWithDao} method.
 */
class MaterialBatchTraceViewRendererTest {

  private final MaterialBatchTraceViewRenderer renderer = new MaterialBatchTraceViewRenderer();

  private SemanticAnnotationDAO mockDao(List<SemanticAnnotation> result) {
    SemanticAnnotationDAO dao = mock(SemanticAnnotationDAO.class);
    when(dao.findByPredicateAndValue(
      MffdMaterialBatchKind.PRED_MATERIAL_BATCH,
      "batch-001",
      "DataObject",
      0,
      500
    )).thenReturn(result);
    return dao;
  }

  private static SemanticAnnotation annotationWithSubjectAppId(String subjectAppId) {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setSubjectAppId(subjectAppId);
    return a;
  }

  private static RenderRequest requestFor(String templateAppId, String focusId) {
    return new RenderRequest(
      templateAppId,
      focusId,
      MffdMaterialBatchKind.TRACE_SHAPE_IRI,
      "{}"
    );
  }

  // ── 1. Shape IRI contract ─────────────────────────────────────────────────

  @Test
  void supportedShapeIris_containsTraceIri() {
    assertThat(renderer.supportedShapeIris())
      .contains(MffdMaterialBatchKind.TRACE_SHAPE_IRI);
  }

  // ── 2. Stable name ────────────────────────────────────────────────────────

  @Test
  void name_isExpected() {
    assertThat(renderer.name()).isEqualTo("MaterialBatchTraceViewRenderer");
  }

  // ── 3. No consumers → MISSING binding ────────────────────────────────────

  @Test
  void render_noConsumers_returnsMissingStatus() {
    SemanticAnnotationDAO dao = mockDao(List.of());
    RenderResponse response = renderer.renderWithDao(requestFor("t-1", "batch-001"), dao);

    assertThat(response.channelBindings()).hasSize(1);
    ChannelBindingProjection binding = response.channelBindings().get(0);
    assertThat(binding.status()).isEqualTo("MISSING");
    assertThat(binding.required()).isTrue();
    assertThat(binding.resolved()).isNull();
    assertThat(binding.role()).isEqualTo(MffdMaterialBatchKind.TRACE_ROLE_BATCH);
  }

  // ── 4. One consumer → one OK binding ─────────────────────────────────────

  @Test
  void render_oneConsumer_returnsOk() {
    SemanticAnnotation ann = annotationWithSubjectAppId("do-1");
    SemanticAnnotationDAO dao = mockDao(List.of(ann));
    RenderResponse response = renderer.renderWithDao(requestFor("t-1", "batch-001"), dao);

    assertThat(response.channelBindings()).hasSize(1);
    ChannelBindingProjection binding = response.channelBindings().get(0);
    assertThat(binding.status()).isEqualTo("OK");
    assertThat(binding.required()).isTrue();
    assertThat(binding.resolved()).isNotNull();
    assertThat(binding.resolved().channelRef()).isEqualTo("do-1");
    assertThat(binding.role()).isEqualTo(MffdMaterialBatchKind.TRACE_ROLE_BATCH);
    assertThat(binding.channelSelector()).isEqualTo(MffdMaterialBatchKind.PRED_MATERIAL_BATCH);
    assertThat(binding.unit()).isNull();
  }

  // ── 5. Multiple consumers → one binding each ─────────────────────────────

  @Test
  void render_multipleConsumers_oneBindingEach() {
    List<SemanticAnnotation> annotations = List.of(
      annotationWithSubjectAppId("do-1"),
      annotationWithSubjectAppId("do-2"),
      annotationWithSubjectAppId("do-3")
    );
    SemanticAnnotationDAO dao = mockDao(annotations);
    RenderResponse response = renderer.renderWithDao(requestFor("t-1", "batch-001"), dao);

    assertThat(response.channelBindings()).hasSize(3);
    assertThat(response.channelBindings())
      .allSatisfy(b -> assertThat(b.status()).isEqualTo("OK"));
    assertThat(response.channelBindings())
      .extracting(b -> b.resolved().channelRef())
      .containsExactlyInAnyOrder("do-1", "do-2", "do-3");
  }

  // ── 6. Null subjectAppId skipped ─────────────────────────────────────────

  @Test
  void render_nullSubjectAppIdSkipped() {
    // Annotation whose subjectAppId is null — should be skipped.
    // After skipping, there are no valid consumers → MISSING status.
    SemanticAnnotation ann = annotationWithSubjectAppId(null);
    SemanticAnnotationDAO dao = mockDao(List.of(ann));
    RenderResponse response = renderer.renderWithDao(requestFor("t-1", "batch-001"), dao);

    assertThat(response.channelBindings()).hasSize(1);
    assertThat(response.channelBindings().get(0).status()).isEqualTo("MISSING");
  }

  // ── 7. Renderer hint is "lineage" ─────────────────────────────────────────

  @Test
  void render_rendererHintIsLineage() {
    SemanticAnnotationDAO dao = mockDao(List.of());
    RenderResponse response = renderer.renderWithDao(requestFor("t-1", "batch-001"), dao);

    assertThat(response.renderer()).isEqualTo("lineage");
  }

  // ── 8. templateAppId and focusShepardId echoed ───────────────────────────

  @Test
  void render_templateAndFocusEchoed() {
    SemanticAnnotationDAO dao = mockDao(List.of());
    RenderResponse response = renderer.renderWithDao(
      requestFor("tmpl-xyz", "batch-001"),
      dao
    );

    assertThat(response.templateAppId()).isEqualTo("tmpl-xyz");
    assertThat(response.focusShepardId()).isEqualTo("batch-001");
  }

  // ── 9. Null / blank focusShepardId throws RenderException ────────────────

  @Test
  void render_nullFocusShepardId_throwsRenderException() {
    SemanticAnnotationDAO dao = mock(SemanticAnnotationDAO.class);
    RenderRequest req = new RenderRequest("t-1", null, MffdMaterialBatchKind.TRACE_SHAPE_IRI, "{}");
    assertThatThrownBy(() -> renderer.renderWithDao(req, dao))
      .isInstanceOf(RenderException.class)
      .hasMessageContaining("focusShepardId");
  }

  @Test
  void render_blankFocusShepardId_throwsRenderException() {
    SemanticAnnotationDAO dao = mock(SemanticAnnotationDAO.class);
    RenderRequest req = new RenderRequest("t-1", "  ", MffdMaterialBatchKind.TRACE_SHAPE_IRI, "{}");
    assertThatThrownBy(() -> renderer.renderWithDao(req, dao))
      .isInstanceOf(RenderException.class)
      .hasMessageContaining("focusShepardId");
  }
}
