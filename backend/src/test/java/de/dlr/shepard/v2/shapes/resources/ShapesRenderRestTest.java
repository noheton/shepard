package de.dlr.shepard.v2.shapes.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.shapes.io.ShapesRenderRequestIO;
import de.dlr.shepard.v2.shapes.io.ShapesRenderResponseIO;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShapesRenderRestTest {

  ShapesRenderRest rest;
  ShepardTemplateDAO templateDAO;

  @BeforeEach
  void setUp() {
    rest = new ShapesRenderRest();
    templateDAO = mock(ShepardTemplateDAO.class);
    rest.templateDAO = templateDAO;
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
    String bodyJson =
      """
      {
        "renderer": "tresjs",
        "channelBindings": [
          {"role":"x","channelSelector":"{\"measurement\":\"AFP\",\"device\":\"tcp\",\"location\":\"plant\",\"symbolicName\":\"tcp_x\",\"field\":\"value\"}","unit":"http://qudt.org/vocab/unit/MilliM","required":true},
          {"role":"y","channelSelector":"{\"measurement\":\"AFP\",\"device\":\"tcp\",\"location\":\"plant\",\"symbolicName\":\"tcp_y\",\"field\":\"value\"}","required":true}
        ]
      }
      """;
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
}
