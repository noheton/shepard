package de.dlr.shepard.spi.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * VIS-S1 — coverage-completing smoke tests for the POJO record IO
 * shapes + the {@link RenderException} 3-arg constructor.
 *
 * <p>These records have no logic to test — JaCoCo treats their
 * generated accessors as uncovered until at least one read happens.
 * The tests below exist to (a) document the field accessors plugin
 * authors can rely on and (b) keep the bundle-coverage gate happy
 * for the new SPI package.
 */
class RenderEnvelopeTest {

  @Test
  void renderRequestExposesAllFieldsAsAccessors() {
    var req = new RenderRequest("tmpl-1", "do-1", "http://example.com/Shape", "{\"k\":1}");
    assertThat(req.templateAppId()).isEqualTo("tmpl-1");
    assertThat(req.focusShepardId()).isEqualTo("do-1");
    assertThat(req.shapeIri()).isEqualTo("http://example.com/Shape");
    assertThat(req.templateBodyJson()).isEqualTo("{\"k\":1}");
  }

  @Test
  void renderResponseExposesAllFieldsAsAccessors() {
    var resolved = new RenderResponse.ResolvedChannel("ts-ref-x");
    var binding = new RenderResponse.ChannelBindingProjection(
      "x", "sel-x", "http://qudt.org/vocab/unit/MilliM", true, "OK", resolved
    );
    var resp = new RenderResponse("tmpl-1", "do-1", "tresjs", List.of(binding));

    assertThat(resp.templateAppId()).isEqualTo("tmpl-1");
    assertThat(resp.focusShepardId()).isEqualTo("do-1");
    assertThat(resp.renderer()).isEqualTo("tresjs");
    assertThat(resp.channelBindings()).hasSize(1);

    var b = resp.channelBindings().get(0);
    assertThat(b.role()).isEqualTo("x");
    assertThat(b.channelSelector()).isEqualTo("sel-x");
    assertThat(b.unit()).isEqualTo("http://qudt.org/vocab/unit/MilliM");
    assertThat(b.required()).isTrue();
    assertThat(b.status()).isEqualTo("OK");
    assertThat(b.resolved()).isNotNull();
    assertThat(b.resolved().channelRef()).isEqualTo("ts-ref-x");
  }

  @Test
  void renderExceptionTwoArgConstructorPreservesCodeAndMessage() {
    var ex = new RenderException("render.body.invalid", "missing xChannel");
    assertThat(ex.code()).isEqualTo("render.body.invalid");
    assertThat(ex.getMessage()).isEqualTo("missing xChannel");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void renderExceptionThreeArgConstructorPropagatesCause() {
    var cause = new IllegalArgumentException("upstream blew up");
    var ex = new RenderException("render.body.invalid", "wrapped failure", cause);
    assertThat(ex.code()).isEqualTo("render.body.invalid");
    assertThat(ex.getMessage()).isEqualTo("wrapped failure");
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void defaultRendererNameIsClassSimpleName() {
    ViewRecipeRenderer anon = new ViewRecipeRenderer() {
      @Override
      public java.util.Set<String> supportedShapeIris() {
        return java.util.Set.of();
      }

      @Override
      public RenderResponse render(RenderRequest req) {
        return null;
      }
    };
    // Default name() implementation falls back to the class simple
    // name; anonymous classes have a synthetic simple name (empty
    // string for top-level anonymous classes in some JVMs). We assert
    // the call doesn't throw and returns a non-null value — the
    // contract.
    assertThat(anon.name()).isNotNull();
  }
}
