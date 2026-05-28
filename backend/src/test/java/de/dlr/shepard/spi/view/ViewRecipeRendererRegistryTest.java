package de.dlr.shepard.spi.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * VIS-S1 — unit tests for the {@link ViewRecipeRendererRegistry}.
 *
 * <p>The registry is constructed with a synthetic {@code Iterable}
 * source via the package-private constructor, so the tests don't
 * boot Quarkus or fiddle with {@code META-INF/services} files.
 */
class ViewRecipeRendererRegistryTest {

  // ─── Fixtures ──────────────────────────────────────────────────────────

  /** Minimal stub renderer claiming one or more shape IRIs. */
  private static ViewRecipeRenderer stub(String name, String... iris) {
    Set<String> claimed = Set.of(iris);
    return new ViewRecipeRenderer() {
      @Override
      public Set<String> supportedShapeIris() {
        return claimed;
      }

      @Override
      public RenderResponse render(RenderRequest req) {
        return new RenderResponse(
          req.templateAppId(),
          req.focusShepardId(),
          "stub",
          Collections.emptyList()
        );
      }

      @Override
      public String name() {
        return name;
      }
    };
  }

  /** Stub that throws from supportedShapeIris() — fail-soft branch. */
  private static ViewRecipeRenderer throwingClaims(String name) {
    return new ViewRecipeRenderer() {
      @Override
      public Set<String> supportedShapeIris() {
        throw new IllegalStateException("synthetic supportedShapeIris() failure");
      }

      @Override
      public RenderResponse render(RenderRequest req) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String name() {
        return name;
      }
    };
  }

  // ─── Empty registry ─────────────────────────────────────────────────────

  @Test
  void emptyClasspathProducesEmptyRegistry() {
    var registry = new ViewRecipeRendererRegistry(List.of());
    registry.discover();

    assertThat(registry.registeredShapes()).isEmpty();
    assertThat(registry.rendererCount()).isZero();
    assertThat(registry.resolve("http://example.com/Shape")).isEmpty();
  }

  // ─── Single renderer happy path ────────────────────────────────────────

  @Test
  void singleRendererClaimingOneShapeIsResolvable() {
    var trace3d = stub("Trace3DRenderer", "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape");
    var registry = new ViewRecipeRendererRegistry(List.of(trace3d));
    registry.discover();

    assertThat(registry.registeredShapes())
      .containsExactly("http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape");
    assertThat(registry.rendererCount()).isEqualTo(1);
    assertThat(registry.resolve("http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape"))
      .containsSame(trace3d);
  }

  @Test
  void rendererClaimingMultipleShapesIsResolvableUnderEach() {
    var visTrace3d = stub(
      "VisTrace3DRenderer",
      "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape",
      "http://semantics.dlr.de/shepard-ui/mesh#MeshViewShape"
    );
    var registry = new ViewRecipeRendererRegistry(List.of(visTrace3d));
    registry.discover();

    assertThat(registry.registeredShapes()).hasSize(2);
    assertThat(registry.resolve("http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape"))
      .containsSame(visTrace3d);
    assertThat(registry.resolve("http://semantics.dlr.de/shepard-ui/mesh#MeshViewShape"))
      .containsSame(visTrace3d);
    assertThat(registry.rendererCount()).isEqualTo(1);
  }

  // ─── Multiple renderers ────────────────────────────────────────────────

  @Test
  void twoRenderersClaimingDistinctShapesCoexist() {
    var trace3d = stub("Trace3DRenderer", "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape");
    var cad = stub("CadRenderer", "http://semantics.dlr.de/shepard-ui/cad#CadViewShape");
    var registry = new ViewRecipeRendererRegistry(List.of(trace3d, cad));
    registry.discover();

    assertThat(registry.registeredShapes())
      .containsExactly(
        "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape",
        "http://semantics.dlr.de/shepard-ui/cad#CadViewShape"
      );
    assertThat(registry.rendererCount()).isEqualTo(2);
  }

  // ─── Fail-fast on duplicate ────────────────────────────────────────────

  @Test
  void duplicateShapeIriFailsFastAtStartup() {
    String dupe = "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape";
    var first = stub("AlphaRenderer", dupe);
    var second = stub("BetaRenderer", dupe);
    var registry = new ViewRecipeRendererRegistry(List.of(first, second));

    assertThatThrownBy(registry::discover)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(dupe)
      .hasMessageContaining("AlphaRenderer")
      .hasMessageContaining("BetaRenderer");
  }

  @Test
  void duplicateAcrossOverlappingRendererClaimSetsFailsFast() {
    // Renderer B re-claims the SECOND of two IRIs A already owns —
    // we must still fail-fast and the message names both registrants.
    var rendererA = stub(
      "RendererA",
      "http://example.com/ShapeOne",
      "http://example.com/ShapeShared"
    );
    var rendererB = stub("RendererB", "http://example.com/ShapeShared");
    var registry = new ViewRecipeRendererRegistry(List.of(rendererA, rendererB));

    assertThatThrownBy(registry::discover)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("http://example.com/ShapeShared")
      .hasMessageContaining("RendererA")
      .hasMessageContaining("RendererB");
  }

  // ─── Fail-soft edge cases ──────────────────────────────────────────────

  @Test
  void rendererClaimingEmptyShapeSetIsSkippedSilently() {
    var dormant = stub("DormantRenderer"); // no IRIs
    var active = stub("ActiveRenderer", "http://example.com/ActiveShape");
    var registry = new ViewRecipeRendererRegistry(List.of(dormant, active));
    registry.discover();

    assertThat(registry.registeredShapes()).containsExactly("http://example.com/ActiveShape");
    assertThat(registry.resolve("http://example.com/ActiveShape")).containsSame(active);
  }

  @Test
  void rendererClaimingNullOrBlankIrisHasThemSkipped() {
    var withBlank = new ViewRecipeRenderer() {
      @Override
      public Set<String> supportedShapeIris() {
        // LinkedHashSet preserves insertion order; using a Set.of()
        // here would reject null entries — we want a mix to test the
        // skip-the-bad-one branch.
        java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>();
        s.add("");
        s.add("http://example.com/Good");
        s.add(null);
        return s;
      }

      @Override
      public RenderResponse render(RenderRequest req) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String name() {
        return "MixedIriRenderer";
      }
    };
    var registry = new ViewRecipeRendererRegistry(List.of(withBlank));
    registry.discover();

    assertThat(registry.registeredShapes()).containsExactly("http://example.com/Good");
    assertThat(registry.resolve("http://example.com/Good")).containsSame(withBlank);
  }

  @Test
  void rendererThrowingFromSupportedShapeIrisIsSkippedNotFatal() {
    var bad = throwingClaims("BadRenderer");
    var good = stub("GoodRenderer", "http://example.com/Good");
    var registry = new ViewRecipeRendererRegistry(List.of(bad, good));
    registry.discover();

    assertThat(registry.registeredShapes()).containsExactly("http://example.com/Good");
    assertThat(registry.resolve("http://example.com/Good")).containsSame(good);
  }

  @Test
  void nullRendererInIterableIsTolerated() {
    var good = stub("GoodRenderer", "http://example.com/Good");
    var withNull = new java.util.ArrayList<ViewRecipeRenderer>();
    withNull.add(good);
    withNull.add(null);
    var registry = new ViewRecipeRendererRegistry(withNull);
    registry.discover();

    assertThat(registry.registeredShapes()).containsExactly("http://example.com/Good");
  }

  // ─── resolve() input validation ─────────────────────────────────────────

  @Test
  void resolveReturnsEmptyOnNullShapeIri() {
    var registry = new ViewRecipeRendererRegistry(
      List.of(stub("X", "http://example.com/X"))
    );
    registry.discover();
    assertThat(registry.resolve(null)).isEmpty();
  }

  @Test
  void resolveReturnsEmptyOnBlankShapeIri() {
    var registry = new ViewRecipeRendererRegistry(
      List.of(stub("X", "http://example.com/X"))
    );
    registry.discover();
    assertThat(registry.resolve("")).isEmpty();
    assertThat(registry.resolve("   ")).isEmpty();
  }

  @Test
  void resolveReturnsEmptyForUnknownShapeIri() {
    var registry = new ViewRecipeRendererRegistry(
      List.of(stub("X", "http://example.com/X"))
    );
    registry.discover();
    assertThat(registry.resolve("http://example.com/never-registered")).isEmpty();
  }

  // ─── Empty-source production-path constructor smoke test ──────────────

  @Test
  void defaultConstructorDoesNotInvokeDiscovery() {
    // The CDI ctor must NOT auto-discover; discovery only fires from
    // the StartupEvent (or a direct call to discover()).
    var registry = new ViewRecipeRendererRegistry();
    assertThat(registry.registeredShapes()).isEmpty();
    assertThat(registry.rendererCount()).isZero();
    assertThat(registry.resolve("anything")).isEmpty();
  }
}
