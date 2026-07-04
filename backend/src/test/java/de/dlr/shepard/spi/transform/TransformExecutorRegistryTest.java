package de.dlr.shepard.spi.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-B3 — unit tests for {@link TransformExecutorRegistry}. Mirrors
 * {@code ViewRecipeRendererRegistryTest}: synthetic {@code Iterable} source via
 * the package-public test constructor, so no Quarkus boot / META-INF fiddling.
 */
class TransformExecutorRegistryTest {

  private static TransformExecutor stub(String name, String... iris) {
    Set<String> claimed = Set.of(iris);
    return new TransformExecutor() {
      @Override
      public Set<String> supportedShapeIris() {
        return claimed;
      }

      @Override
      public TransformResult materialize(TransformRequest req) {
        return TransformResult.reference("derived", name);
      }

      @Override
      public String name() {
        return name;
      }
    };
  }

  private static TransformExecutor throwingClaims(String name) {
    return new TransformExecutor() {
      @Override
      public Set<String> supportedShapeIris() {
        throw new IllegalStateException("synthetic supportedShapeIris() failure");
      }

      @Override
      public TransformResult materialize(TransformRequest req) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String name() {
        return name;
      }
    };
  }

  @Test
  void emptyClasspathProducesEmptyRegistry() {
    var registry = new TransformExecutorRegistry(List.of());
    registry.discover();
    assertThat(registry.registeredShapes()).isEmpty();
    assertThat(registry.executorCount()).isZero();
    assertThat(registry.resolve("http://example.com/Shape")).isEmpty();
  }

  @Test
  void singleExecutorClaimingOneShapeIsResolvable() {
    var ex = stub("KrlExecutor", "http://semantics.dlr.de/shepard/transform#KrlTransformShape");
    var registry = new TransformExecutorRegistry(List.of(ex));
    registry.discover();
    assertThat(registry.registeredShapes())
      .containsExactly("http://semantics.dlr.de/shepard/transform#KrlTransformShape");
    assertThat(registry.executorCount()).isEqualTo(1);
    assertThat(registry.resolve("http://semantics.dlr.de/shepard/transform#KrlTransformShape")).containsSame(ex);
  }

  @Test
  void executorClaimingMultipleShapesIsResolvableUnderEach() {
    var ex = stub("Multi", "http://example.com/A", "http://example.com/B");
    var registry = new TransformExecutorRegistry(List.of(ex));
    registry.discover();
    assertThat(registry.registeredShapes()).hasSize(2);
    assertThat(registry.resolve("http://example.com/A")).containsSame(ex);
    assertThat(registry.resolve("http://example.com/B")).containsSame(ex);
    assertThat(registry.executorCount()).isEqualTo(1);
  }

  @Test
  void twoExecutorsClaimingDistinctShapesCoexist() {
    var a = stub("A", "http://example.com/A");
    var b = stub("B", "http://example.com/B");
    var registry = new TransformExecutorRegistry(List.of(a, b));
    registry.discover();
    assertThat(registry.registeredShapes()).containsExactly("http://example.com/A", "http://example.com/B");
    assertThat(registry.executorCount()).isEqualTo(2);
  }

  @Test
  void duplicateShapeIriFailsFastAtStartup() {
    String dupe = "http://example.com/Dupe";
    var registry = new TransformExecutorRegistry(List.of(stub("Alpha", dupe), stub("Beta", dupe)));
    assertThatThrownBy(registry::discover)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(dupe)
      .hasMessageContaining("Alpha")
      .hasMessageContaining("Beta");
  }

  @Test
  void duplicateAcrossOverlappingClaimSetsFailsFast() {
    var a = stub("A", "http://example.com/One", "http://example.com/Shared");
    var b = stub("B", "http://example.com/Shared");
    var registry = new TransformExecutorRegistry(List.of(a, b));
    assertThatThrownBy(registry::discover)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("http://example.com/Shared")
      .hasMessageContaining("A")
      .hasMessageContaining("B");
  }

  @Test
  void executorClaimingEmptyShapeSetIsSkippedSilently() {
    var dormant = stub("Dormant");
    var active = stub("Active", "http://example.com/Active");
    var registry = new TransformExecutorRegistry(List.of(dormant, active));
    registry.discover();
    assertThat(registry.registeredShapes()).containsExactly("http://example.com/Active");
    assertThat(registry.resolve("http://example.com/Active")).containsSame(active);
  }

  @Test
  void executorClaimingNullOrBlankIrisHasThemSkipped() {
    var withBlank = new TransformExecutor() {
      @Override
      public Set<String> supportedShapeIris() {
        var s = new LinkedHashSet<String>();
        s.add("");
        s.add("http://example.com/Good");
        s.add(null);
        return s;
      }

      @Override
      public TransformResult materialize(TransformRequest req) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String name() {
        return "MixedIri";
      }
    };
    var registry = new TransformExecutorRegistry(List.of(withBlank));
    registry.discover();
    assertThat(registry.registeredShapes()).containsExactly("http://example.com/Good");
    assertThat(registry.resolve("http://example.com/Good")).containsSame(withBlank);
  }

  @Test
  void executorThrowingFromSupportedShapeIrisIsSkippedNotFatal() {
    var bad = throwingClaims("Bad");
    var good = stub("Good", "http://example.com/Good");
    var registry = new TransformExecutorRegistry(List.of(bad, good));
    registry.discover();
    assertThat(registry.registeredShapes()).containsExactly("http://example.com/Good");
    assertThat(registry.resolve("http://example.com/Good")).containsSame(good);
  }

  @Test
  void nullExecutorInIterableIsTolerated() {
    var good = stub("Good", "http://example.com/Good");
    var withNull = new ArrayList<TransformExecutor>();
    withNull.add(good);
    withNull.add(null);
    var registry = new TransformExecutorRegistry(withNull);
    registry.discover();
    assertThat(registry.registeredShapes()).containsExactly("http://example.com/Good");
  }

  @Test
  void resolveReturnsEmptyOnNullOrBlankOrUnknown() {
    var registry = new TransformExecutorRegistry(List.of(stub("X", "http://example.com/X")));
    registry.discover();
    assertThat(registry.resolve(null)).isEmpty();
    assertThat(registry.resolve("")).isEmpty();
    assertThat(registry.resolve("   ")).isEmpty();
    assertThat(registry.resolve("http://example.com/never")).isEmpty();
  }

  @Test
  void defaultConstructorDoesNotInvokeDiscovery() {
    var registry = new TransformExecutorRegistry();
    assertThat(registry.registeredShapes()).isEmpty();
    assertThat(registry.executorCount()).isZero();
    assertThat(registry.resolve("anything")).isEmpty();
  }

  // ─── The built-in default is discovered through real ServiceLoader ─────────

  @Test
  void serviceLoaderDiscoversBuiltInNoOpExecutor() {
    // Production path (no test iterable) → the META-INF/services entry must
    // register the NoOpTransformExecutor under its identity shape IRI.
    var registry = new TransformExecutorRegistry();
    registry.discover();
    assertThat(registry.resolve(NoOpTransformExecutor.IDENTITY_SHAPE_IRI))
      .isPresent()
      .get()
      .isInstanceOf(NoOpTransformExecutor.class);
  }
}
