package de.dlr.shepard.v2.shapes.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.spi.view.RenderException;
import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.ViewRecipeRenderer;
import de.dlr.shepard.spi.view.ViewRecipeRendererRegistry;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.shapes.io.ShapesRenderRequestIO;
import de.dlr.shepard.v2.shapes.io.ShapesRenderResponseIO;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShapesRenderRestTest {

  ShapesRenderRest rest;
  ShepardTemplateDAO templateDAO;
  ViewRecipeRendererRegistry registry;

  @BeforeEach
  void setUp() {
    rest = new ShapesRenderRest();
    templateDAO = mock(ShepardTemplateDAO.class);
    rest.templateDAO = templateDAO;
    // Default: registry with zero registered renderers — every
    // existing TPL2b test exercises the in-tree DECLARED-projection
    // fallback path, so we wire an empty registry here.
    registry = new ViewRecipeRendererRegistry(List.<ViewRecipeRenderer>of());
    registry.discover();
    rest.rendererRegistry = registry;
  }

  // ─── VIS-S1 SPI dispatch — fixtures ──────────────────────────────────────

  /** Build a stub renderer claiming the given IRI + returning the given response. */
  private static ViewRecipeRenderer stubRenderer(String iri, RenderResponse response) {
    return new ViewRecipeRenderer() {
      @Override
      public Set<String> supportedShapeIris() {
        return Set.of(iri);
      }

      @Override
      public RenderResponse render(RenderRequest req) {
        return response;
      }

      @Override
      public String name() {
        return "StubRenderer";
      }
    };
  }

  /** Capture the request the renderer saw — used to assert dispatch wiring. */
  private static final class CapturingRenderer implements ViewRecipeRenderer {

    final String iri;
    final RenderResponse response;
    RenderRequest captured;

    CapturingRenderer(String iri, RenderResponse response) {
      this.iri = iri;
      this.response = response;
    }

    @Override
    public Set<String> supportedShapeIris() {
      return Set.of(iri);
    }

    @Override
    public RenderResponse render(RenderRequest req) {
      this.captured = req;
      return response;
    }

    @Override
    public String name() {
      return "CapturingRenderer";
    }
  }

  /** Renderer that throws {@link RenderException} — covers the 422 branch. */
  private static ViewRecipeRenderer throwingRenderer(String iri, RenderException ex) {
    return new ViewRecipeRenderer() {
      @Override
      public Set<String> supportedShapeIris() {
        return Set.of(iri);
      }

      @Override
      public RenderResponse render(RenderRequest req) {
        throw ex;
      }
    };
  }

  /** Renderer that throws an unexpected {@link RuntimeException} — covers the 500 branch. */
  private static ViewRecipeRenderer brokenRenderer(String iri) {
    return new ViewRecipeRenderer() {
      @Override
      public Set<String> supportedShapeIris() {
        return Set.of(iri);
      }

      @Override
      public RenderResponse render(RenderRequest req) {
        throw new IllegalStateException("synthetic unexpected failure");
      }

      @Override
      public String name() {
        return "BrokenRenderer";
      }
    };
  }

  private void wireRegistry(ViewRecipeRenderer... renderers) {
    registry = new ViewRecipeRendererRegistry(List.of(renderers));
    registry.discover();
    rest.rendererRegistry = registry;
  }

  // ─── 400 paths ───────────────────────────────────────────────────────────

  @Test
  void returns400OnNullBody() {
    assertThat(rest.render(null).getStatus()).isEqualTo(400);
  }

  @Test
  void returns400OnMissingTemplateAppId() {
    var body = new ShapesRenderRequestIO(null, "some-focus-id", null);
    assertThat(rest.render(body).getStatus()).isEqualTo(400);
  }

  @Test
  void returns400OnMissingFocusShepardId() {
    var body = new ShapesRenderRequestIO("some-tmpl-id", null, null);
    assertThat(rest.render(body).getStatus()).isEqualTo(400);
  }

  // ─── 404 path ────────────────────────────────────────────────────────────

  @Test
  void returns404WhenTemplateNotFound() {
    when(templateDAO.findByAppId("unknown-id")).thenReturn(Optional.empty());
    var body = new ShapesRenderRequestIO("unknown-id", "focus-id", null);
    assertThat(rest.render(body).getStatus()).isEqualTo(404);
  }

  // ─── 422 path ────────────────────────────────────────────────────────────

  @Test
  void returns422ForNonViewRecipeKind() {
    ShepardTemplate tmpl = new ShepardTemplate("My recipe", "COLLECTION_RECIPE", "{\"collection\":{}}");
    when(templateDAO.findByAppId("tmpl-1")).thenReturn(Optional.of(tmpl));

    var body = new ShapesRenderRequestIO("tmpl-1", "focus-id", null);
    Response r = rest.render(body);

    assertThat(r.getStatus()).isEqualTo(422);
  }

  // ─── 200 happy paths ─────────────────────────────────────────────────────

  @Test
  void returns200WithDeclaredBindingsForViewRecipe() {
    // Note: text blocks do NOT additionally unescape `\"` — but their
    // JSON parser still needs valid JSON. To embed a JSON-encoded
    // string-value-containing-JSON inside, double-escape `\\"`.
    String bodyJson =
      "{" +
      "\"renderer\":\"tresjs\"," +
      "\"channelBindings\":[" +
      "{\"role\":\"x\",\"channelSelector\":\"{\\\"measurement\\\":\\\"AFP\\\",\\\"device\\\":\\\"tcp\\\",\\\"location\\\":\\\"plant\\\",\\\"symbolicName\\\":\\\"tcp_x\\\",\\\"field\\\":\\\"value\\\"}\",\"unit\":\"http://qudt.org/vocab/unit/MilliM\",\"required\":true}," +
      "{\"role\":\"y\",\"channelSelector\":\"{\\\"measurement\\\":\\\"AFP\\\",\\\"device\\\":\\\"tcp\\\",\\\"location\\\":\\\"plant\\\",\\\"symbolicName\\\":\\\"tcp_y\\\",\\\"field\\\":\\\"value\\\"}\",\"required\":true}" +
      "]" +
      "}";
    ShepardTemplate tmpl = new ShepardTemplate("Trace3D recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("view-tmpl-1")).thenReturn(Optional.of(tmpl));

    var body = new ShapesRenderRequestIO("view-tmpl-1", "do-focus-1", null);
    Response r = rest.render(body);

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapesRenderResponseIO) r.getEntity();
    assertThat(io.templateAppId()).isEqualTo("view-tmpl-1");
    assertThat(io.focusShepardId()).isEqualTo("do-focus-1");
    assertThat(io.renderer()).isEqualTo("tresjs");
    assertThat(io.channelBindings()).hasSize(2);

    var xBinding = io.channelBindings().get(0);
    assertThat(xBinding.role()).isEqualTo("x");
    assertThat(xBinding.unit()).isEqualTo("http://qudt.org/vocab/unit/MilliM");
    assertThat(xBinding.required()).isTrue();
    assertThat(xBinding.status()).isEqualTo("DECLARED");
    assertThat(xBinding.resolved()).isNull();

    var yBinding = io.channelBindings().get(1);
    assertThat(yBinding.role()).isEqualTo("y");
    assertThat(yBinding.unit()).isNull();
  }

  @Test
  void returns200WithEmptyBindingsWhenBodyHasNoChannelBindings() {
    String bodyJson = "{\"renderer\":\"tresjs\"}";
    ShepardTemplate tmpl = new ShepardTemplate("Minimal recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("view-tmpl-2")).thenReturn(Optional.of(tmpl));

    var body = new ShapesRenderRequestIO("view-tmpl-2", "do-focus-2", null);
    Response r = rest.render(body);

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapesRenderResponseIO) r.getEntity();
    assertThat(io.renderer()).isEqualTo("tresjs");
    assertThat(io.channelBindings()).isEmpty();
  }

  @Test
  void returns200WithNullRendererWhenBodyLacksRendererKey() {
    String bodyJson = "{\"view\":\"my-view\"}";
    ShepardTemplate tmpl = new ShepardTemplate("View-only recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("view-tmpl-3")).thenReturn(Optional.of(tmpl));

    var body = new ShapesRenderRequestIO("view-tmpl-3", "do-focus-3", null);
    Response r = rest.render(body);

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapesRenderResponseIO) r.getEntity();
    assertThat(io.renderer()).isNull();
    assertThat(io.channelBindings()).isEmpty();
  }

  // ─── VIS-S1 SPI dispatch — happy paths ───────────────────────────────────

  @Test
  void dispatchesToRegisteredRendererWhenBodyDeclaresMatchingShape() {
    String iri = "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape";
    String bodyJson =
      "{\"viewRecipeShape\":\"" + iri + "\",\"renderer\":\"tresjs\",\"channelBindings\":[" +
      "{\"role\":\"x\",\"channelSelector\":\"sel-x\",\"unit\":\"http://qudt.org/vocab/unit/MilliM\",\"required\":true}" +
      "]}";
    ShepardTemplate tmpl = new ShepardTemplate("Trace3D recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("view-tmpl-spi-1")).thenReturn(Optional.of(tmpl));

    RenderResponse stubOut = new RenderResponse(
      "echoed-by-renderer",
      "echoed-by-renderer",
      "tresjs",
      List.of(
        new RenderResponse.ChannelBindingProjection(
          "x",
          "sel-x",
          "http://qudt.org/vocab/unit/MilliM",
          true,
          "OK",
          new RenderResponse.ResolvedChannel("ts-ref-appid-x")
        )
      )
    );
    CapturingRenderer captor = new CapturingRenderer(iri, stubOut);
    wireRegistry(captor);

    var body = new ShapesRenderRequestIO("view-tmpl-spi-1", "do-focus-spi-1", null);
    Response r = rest.render(body);

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapesRenderResponseIO) r.getEntity();
    // Dispatcher must echo the REQUEST's appIds, not whatever the
    // renderer happens to return — guards against a buggy renderer
    // returning the wrong identity.
    assertThat(io.templateAppId()).isEqualTo("view-tmpl-spi-1");
    assertThat(io.focusShepardId()).isEqualTo("do-focus-spi-1");
    assertThat(io.renderer()).isEqualTo("tresjs");
    assertThat(io.channelBindings()).hasSize(1);
    var binding = io.channelBindings().get(0);
    assertThat(binding.role()).isEqualTo("x");
    assertThat(binding.status()).isEqualTo("OK");
    assertThat(binding.resolved()).isNotNull();
    assertThat(binding.resolved().channelRef()).isEqualTo("ts-ref-appid-x");

    // Renderer saw the parsed shape IRI + the raw template body.
    assertThat(captor.captured).isNotNull();
    assertThat(captor.captured.templateAppId()).isEqualTo("view-tmpl-spi-1");
    assertThat(captor.captured.focusShepardId()).isEqualTo("do-focus-spi-1");
    assertThat(captor.captured.shapeIri()).isEqualTo(iri);
    assertThat(captor.captured.templateBodyJson()).isEqualTo(bodyJson);
  }

  @Test
  void preservesUnitMismatchStatusCodeFromRenderer() {
    String iri = "http://example.com/UnitTestShape";
    String bodyJson = "{\"viewRecipeShape\":\"" + iri + "\",\"renderer\":\"tresjs\"}";
    ShepardTemplate tmpl = new ShepardTemplate("Unit test recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("vt-um-1")).thenReturn(Optional.of(tmpl));

    RenderResponse out = new RenderResponse(
      "vt-um-1",
      "fo-1",
      "tresjs",
      List.of(
        new RenderResponse.ChannelBindingProjection(
          "x", "sel", "http://qudt.org/vocab/unit/MilliM", true, "UNIT_MISMATCH", null
        )
      )
    );
    wireRegistry(stubRenderer(iri, out));

    Response r = rest.render(new ShapesRenderRequestIO("vt-um-1", "fo-1", null));
    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapesRenderResponseIO) r.getEntity();
    assertThat(io.channelBindings()).hasSize(1);
    assertThat(io.channelBindings().get(0).status()).isEqualTo("UNIT_MISMATCH");
    assertThat(io.channelBindings().get(0).resolved()).isNull();
  }

  // ─── VIS-S1 SPI dispatch — fallback paths ─────────────────────────────────

  @Test
  void fallsBackToInTreeProjectionWhenNoRendererClaimsShape() {
    // Template DECLARES a shape IRI but no registered renderer claims it
    // → fall back to the TPL2b DECLARED-status projection. The wire
    // shape stays identical to the no-IRI case so external clients
    // see no breakage.
    String bodyJson =
      "{\"viewRecipeShape\":\"http://example.com/UnregisteredShape\"," +
      "\"renderer\":\"tresjs\"," +
      "\"channelBindings\":[{\"role\":\"x\",\"channelSelector\":\"sel-x\",\"required\":true}]}";
    ShepardTemplate tmpl = new ShepardTemplate("Orphan recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("orphan-1")).thenReturn(Optional.of(tmpl));

    Response r = rest.render(new ShapesRenderRequestIO("orphan-1", "f-1", null));
    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapesRenderResponseIO) r.getEntity();
    assertThat(io.channelBindings()).hasSize(1);
    assertThat(io.channelBindings().get(0).status()).isEqualTo("DECLARED");
  }

  @Test
  void fallsBackToInTreeProjectionWhenBodyHasNoShapeIri() {
    // No `viewRecipeShape` field at all → fall straight through to
    // the TPL2b in-tree path; registry is never consulted.
    wireRegistry(stubRenderer("http://example.com/NeverAsked", null));
    String bodyJson =
      "{\"renderer\":\"tresjs\",\"channelBindings\":[{\"role\":\"x\",\"channelSelector\":\"sel-x\",\"required\":true}]}";
    ShepardTemplate tmpl = new ShepardTemplate("No-iri recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("noiri-1")).thenReturn(Optional.of(tmpl));

    Response r = rest.render(new ShapesRenderRequestIO("noiri-1", "f-1", null));
    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapesRenderResponseIO) r.getEntity();
    assertThat(io.channelBindings().get(0).status()).isEqualTo("DECLARED");
  }

  @Test
  void fallsBackWhenViewRecipeShapeIsBlank() {
    String bodyJson = "{\"viewRecipeShape\":\"   \",\"renderer\":\"tresjs\"}";
    ShepardTemplate tmpl = new ShepardTemplate("Blank-iri recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("blank-iri-1")).thenReturn(Optional.of(tmpl));

    Response r = rest.render(new ShapesRenderRequestIO("blank-iri-1", "f-1", null));
    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapesRenderResponseIO) r.getEntity();
    assertThat(io.renderer()).isEqualTo("tresjs");
    assertThat(io.channelBindings()).isEmpty();
  }

  @Test
  void worksWhenRegistryIsNull() {
    // Defensive — if CDI injection ever returned null (test path or
    // a future where the registry is moved to a profile), the
    // endpoint must still serve TPL2b behaviour.
    rest.rendererRegistry = null;
    String bodyJson =
      "{\"viewRecipeShape\":\"http://example.com/X\",\"renderer\":\"tresjs\"," +
      "\"channelBindings\":[{\"role\":\"x\",\"channelSelector\":\"sel\",\"required\":true}]}";
    ShepardTemplate tmpl = new ShepardTemplate("No-reg recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("noreg-1")).thenReturn(Optional.of(tmpl));

    Response r = rest.render(new ShapesRenderRequestIO("noreg-1", "f-1", null));
    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapesRenderResponseIO) r.getEntity();
    assertThat(io.channelBindings()).hasSize(1);
    assertThat(io.channelBindings().get(0).status()).isEqualTo("DECLARED");
  }

  // ─── VIS-S1 SPI dispatch — failure paths ─────────────────────────────────

  @Test
  void rendererRenderExceptionMapsTo422WithErrorCode() {
    String iri = "http://example.com/Throwy";
    String bodyJson = "{\"viewRecipeShape\":\"" + iri + "\"}";
    ShepardTemplate tmpl = new ShepardTemplate("Throwy recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("throwy-1")).thenReturn(Optional.of(tmpl));

    wireRegistry(
      throwingRenderer(
        iri,
        new RenderException("render.body.invalid", "missing required `xChannel`")
      )
    );

    Response r = rest.render(new ShapesRenderRequestIO("throwy-1", "f-1", null));
    assertThat(r.getStatus()).isEqualTo(422);
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertThat(body.get("code")).isEqualTo("render.body.invalid");
    assertThat(body.get("error")).asString().contains("missing required");
  }

  @Test
  void rendererUnexpectedExceptionMapsTo500() {
    String iri = "http://example.com/Broken";
    String bodyJson = "{\"viewRecipeShape\":\"" + iri + "\"}";
    ShepardTemplate tmpl = new ShepardTemplate("Broken recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("broken-1")).thenReturn(Optional.of(tmpl));

    wireRegistry(brokenRenderer(iri));

    Response r = rest.render(new ShapesRenderRequestIO("broken-1", "f-1", null));
    assertThat(r.getStatus()).isEqualTo(500);
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertThat(body.get("type")).isEqualTo("render.internal-error");
    assertThat(body.get("error")).asString().contains("synthetic unexpected failure");
  }

  @Test
  void rendererReturningNullMapsTo500() {
    String iri = "http://example.com/NullReturn";
    String bodyJson = "{\"viewRecipeShape\":\"" + iri + "\"}";
    ShepardTemplate tmpl = new ShepardTemplate("Null recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("null-1")).thenReturn(Optional.of(tmpl));

    wireRegistry(stubRenderer(iri, null));

    Response r = rest.render(new ShapesRenderRequestIO("null-1", "f-1", null));
    assertThat(r.getStatus()).isEqualTo(500);
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertThat(body.get("type")).isEqualTo("render.internal-error");
  }

  @Test
  void renderExceptionWithNullCodeFallsBackToRenderUnknownError() {
    String iri = "http://example.com/NullCode";
    String bodyJson = "{\"viewRecipeShape\":\"" + iri + "\"}";
    ShepardTemplate tmpl = new ShepardTemplate("NullCode recipe", "VIEW_RECIPE", bodyJson);
    when(templateDAO.findByAppId("nc-1")).thenReturn(Optional.of(tmpl));
    wireRegistry(throwingRenderer(iri, new RenderException(null, "boom")));

    Response r = rest.render(new ShapesRenderRequestIO("nc-1", "f-1", null));
    assertThat(r.getStatus()).isEqualTo(422);
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertThat(body.get("code")).isEqualTo("render.unknown-error");
  }
}
