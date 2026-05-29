package de.dlr.shepard.plugins.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.RenderResponse.ChannelBindingProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BrushTraceViewRecipeRenderer} — SPATIAL-V6-004.
 */
class BrushTraceViewRecipeRendererTest {

  private BrushTraceViewRecipeRenderer renderer;

  @BeforeEach
  void setUp() {
    renderer = new BrushTraceViewRecipeRenderer();
  }

  // ── supportedShapeIris ────────────────────────────────────────────────────────

  @Test
  void supportedShapeIris_containsBrushTraceShapeIri() {
    assertThat(renderer.supportedShapeIris())
        .containsExactly(BrushTraceViewRecipeRenderer.BRUSH_TRACE_SHAPE_IRI);
  }

  // ── render: OK path ───────────────────────────────────────────────────────────

  @Test
  void render_withValidTraceSourceAppId_returnsOkBinding() {
    String templateBody = """
        {
          "traceSourceAppId": "0190adef-1111-7000-0000-000000000001",
          "brushMode": "ruled-surface"
        }
        """;
    var req = new RenderRequest(
        "tpl-app-id-001",
        "focus-app-id-002",
        BrushTraceViewRecipeRenderer.BRUSH_TRACE_SHAPE_IRI,
        templateBody
    );

    RenderResponse resp = renderer.render(req);

    assertThat(resp.templateAppId()).isEqualTo("tpl-app-id-001");
    assertThat(resp.focusShepardId()).isEqualTo("focus-app-id-002");
    assertThat(resp.renderer()).isEqualTo("brush-trace");
    assertThat(resp.channelBindings()).hasSize(1);

    ChannelBindingProjection binding = resp.channelBindings().get(0);
    assertThat(binding.role()).isEqualTo("traceSource");
    assertThat(binding.channelSelector()).isEqualTo("traceSourceAppId");
    assertThat(binding.status()).isEqualTo("OK");
    assertThat(binding.required()).isTrue();
    assertThat(binding.resolved()).isNotNull();
    assertThat(binding.resolved().channelRef())
        .isEqualTo("0190adef-1111-7000-0000-000000000001");
  }

  // ── render: MISSING path ─────────────────────────────────────────────────────

  @Test
  void render_withMissingTraceSourceAppId_returnsMissingBinding() {
    String templateBody = """
        {
          "brushMode": "tube"
        }
        """;
    var req = new RenderRequest(
        "tpl-app-id-003",
        "focus-app-id-004",
        BrushTraceViewRecipeRenderer.BRUSH_TRACE_SHAPE_IRI,
        templateBody
    );

    RenderResponse resp = renderer.render(req);

    assertThat(resp.renderer()).isEqualTo("brush-trace");
    assertThat(resp.channelBindings()).hasSize(1);

    ChannelBindingProjection binding = resp.channelBindings().get(0);
    assertThat(binding.status()).isEqualTo("MISSING");
    assertThat(binding.resolved()).isNull();
  }

  @Test
  void render_withNullBody_returnsMissingBinding() {
    var req = new RenderRequest(
        "tpl-app-id-005",
        "focus-app-id-006",
        BrushTraceViewRecipeRenderer.BRUSH_TRACE_SHAPE_IRI,
        null
    );

    RenderResponse resp = renderer.render(req);

    assertThat(resp.channelBindings().get(0).status()).isEqualTo("MISSING");
  }

  @Test
  void render_withEmptyBody_returnsMissingBinding() {
    var req = new RenderRequest(
        "tpl-app-id-007",
        "focus-app-id-008",
        BrushTraceViewRecipeRenderer.BRUSH_TRACE_SHAPE_IRI,
        ""
    );

    RenderResponse resp = renderer.render(req);

    assertThat(resp.channelBindings().get(0).status()).isEqualTo("MISSING");
  }

  @Test
  void render_withBlankTraceSourceAppId_returnsMissingBinding() {
    String templateBody = "{\"traceSourceAppId\": \"   \"}";
    var req = new RenderRequest(
        "tpl-app-id-009",
        "focus-app-id-010",
        BrushTraceViewRecipeRenderer.BRUSH_TRACE_SHAPE_IRI,
        templateBody
    );

    RenderResponse resp = renderer.render(req);

    // Blank string should be treated as missing.
    assertThat(resp.channelBindings().get(0).status()).isEqualTo("MISSING");
  }

  // ── extractTraceSourceAppId edge cases ───────────────────────────────────────

  @Test
  void extractTraceSourceAppId_malformedJson_returnsNull() {
    assertThat(renderer.extractTraceSourceAppId("{not valid json")).isNull();
  }

  @Test
  void extractTraceSourceAppId_nullField_returnsNull() {
    assertThat(renderer.extractTraceSourceAppId("{\"traceSourceAppId\": null}")).isNull();
  }

  @Test
  void extractTraceSourceAppId_validField_returnsValue() {
    assertThat(renderer.extractTraceSourceAppId("{\"traceSourceAppId\": \"abc-123\"}"))
        .isEqualTo("abc-123");
  }

  // ── name ─────────────────────────────────────────────────────────────────────

  @Test
  void name_isNonEmpty() {
    assertThat(renderer.name()).isNotBlank();
    assertThat(renderer.name()).contains("SPATIAL-V6-004");
  }
}
