package de.dlr.shepard.v2.admin.config.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.enterprise.inject.Instance;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * V2CONV-A4 — unit tests for {@link ConfigRegistry}. No Quarkus boot: the CDI
 * {@link Instance} of descriptors is mocked, and {@link ConfigRegistry#register()}
 * is driven directly (the {@code @Observes StartupEvent} hook just calls it).
 */
class ConfigRegistryTest {

  /** Minimal fake descriptor for indexing tests. */
  private static final class FakeDescriptor implements ConfigDescriptor<String> {

    private final String name;

    FakeDescriptor(String name) {
      this.name = name;
    }

    @Override
    public String featureName() {
      return name;
    }

    @Override
    public String description() {
      return "desc-" + name;
    }

    @Override
    public String currentShape() {
      return "shape-" + name;
    }

    @Override
    public String applyMergePatch(JsonNode patch) {
      return "patched-" + name;
    }
  }

  @SuppressWarnings("unchecked")
  private static ConfigRegistry registryWith(ConfigDescriptor<?>... descriptors) {
    ConfigRegistry registry = new ConfigRegistry();
    Instance<ConfigDescriptor<?>> instance = Mockito.mock(Instance.class);
    Mockito.when(instance.iterator()).thenAnswer(inv -> List.of(descriptors).iterator());
    registry.descriptors = instance;
    registry.register();
    return registry;
  }

  @Test
  void indexesDescriptorsByFeatureName() {
    FakeDescriptor a = new FakeDescriptor("semantic");
    FakeDescriptor b = new FakeDescriptor("jupyter");
    ConfigRegistry registry = registryWith(a, b);

    assertEquals(List.of("semantic", "jupyter"), registry.featureNames());
    assertSame(a, registry.resolve("semantic").orElseThrow());
    assertSame(b, registry.resolve("jupyter").orElseThrow());
  }

  @Test
  void resolveUnknownFeatureIsEmptyNotThrow() {
    ConfigRegistry registry = registryWith(new FakeDescriptor("ror"));
    assertTrue(registry.resolve("does-not-exist").isEmpty());
    assertTrue(registry.resolve(null).isEmpty());
  }

  @Test
  void skipsBlankFeatureNameWithoutThrowing() {
    ConfigRegistry registry = registryWith(new FakeDescriptor("  "), new FakeDescriptor("ok"));
    assertEquals(List.of("ok"), registry.featureNames());
  }

  @Test
  void firstWinsOnDuplicateFeatureName() {
    FakeDescriptor first = new FakeDescriptor("dup");
    FakeDescriptor second = new FakeDescriptor("dup");
    ConfigRegistry registry = registryWith(first, second);

    assertEquals(1, registry.featureNames().size());
    assertSame(first, registry.resolve("dup").orElseThrow());
  }

  @Test
  void emptyWhenNoDescriptorsInjected() {
    ConfigRegistry registry = new ConfigRegistry();
    registry.descriptors = null;
    registry.register();
    assertTrue(registry.featureNames().isEmpty());
    assertTrue(registry.all().isEmpty());
  }

  @Test
  void fakeDescriptorRoundTrips() throws Exception {
    FakeDescriptor d = new FakeDescriptor("x");
    JsonNode empty = JsonNodeFactory.instance.objectNode();
    assertEquals("shape-x", d.currentShape());
    assertEquals("patched-x", d.applyMergePatch(empty));
  }

  @Test
  void iteratorBackedInstanceDrains() {
    Iterator<ConfigDescriptor<?>> it = List.<ConfigDescriptor<?>>of(new FakeDescriptor("a")).iterator();
    assertTrue(it.hasNext());
    it.next();
    assertFalse(it.hasNext());
  }
}
