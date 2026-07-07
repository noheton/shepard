package de.dlr.shepard.v2.admin.config.descriptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.configuration.feature.runtime.FeatureToggleRegistry;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.io.FeatureTogglesConfigIO;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

// APISIMP-FEATURE-TOGGLE-CONFIG-UNIFY — unit tests for FeatureTogglesConfigDescriptor.

class FeatureTogglesConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private FeatureToggleRegistry registry;
  private FeatureTogglesConfigDescriptor descriptor;

  private FeatureToggleRegistry.FeatureToggleEntry entry(String name, boolean enabled) {
    Supplier<Boolean> reader = () -> enabled;
    Consumer<Boolean> writer = v -> {};
    return new FeatureToggleRegistry.FeatureToggleEntry(name, name + " description", reader, writer);
  }

  private FeatureToggleRegistry.FeatureToggleEntry mutableEntry(String name, boolean initial) {
    boolean[] state = { initial };
    Supplier<Boolean> reader = () -> state[0];
    Consumer<Boolean> writer = v -> state[0] = v;
    return new FeatureToggleRegistry.FeatureToggleEntry(name, name + " description", reader, writer);
  }

  @BeforeEach
  void setUp() {
    registry = Mockito.mock(FeatureToggleRegistry.class);
    descriptor = new FeatureTogglesConfigDescriptor();
    descriptor.registry = registry;
  }

  @Test
  void featureNameIsFeatureToggles() {
    assertEquals("feature-toggles", descriptor.featureName());
  }

  @Test
  void currentShapeWrapsAllRegistryEntries() {
    Mockito.when(registry.list()).thenReturn(List.of(
      entry("versioning", true),
      entry("spatial-data", false)
    ));

    FeatureTogglesConfigIO shape = descriptor.currentShape();

    assertNotNull(shape.toggles());
    assertEquals(2, shape.toggles().size());
    assertEquals("versioning", shape.toggles().get(0).getName());
    assertTrue(shape.toggles().get(0).isEnabled());
    assertEquals("spatial-data", shape.toggles().get(1).getName());
    assertTrue(!shape.toggles().get(1).isEnabled());
  }

  @Test
  void patchHappyPathMutatesAndReturnsUpdatedShape() throws Exception {
    var e = mutableEntry("versioning", true);
    Mockito.when(registry.list()).thenReturn(List.of(e));
    Mockito.when(registry.get("versioning")).thenReturn(Optional.of(e));
    Mockito.when(registry.set("versioning", false)).thenAnswer(inv -> {
      e.setEnabled(false);
      return true;
    });

    FeatureTogglesConfigIO result =
      descriptor.applyMergePatch(MAPPER.readTree("{\"versioning\": false}"));

    assertNotNull(result);
    Mockito.verify(registry).set("versioning", false);
  }

  @Test
  void emptyPatchBodyLeavesStateUnchanged() throws Exception {
    Mockito.when(registry.list()).thenReturn(List.of(entry("versioning", true)));

    FeatureTogglesConfigIO result = descriptor.applyMergePatch(MAPPER.readTree("{}"));

    assertNotNull(result);
    Mockito.verify(registry, Mockito.never()).set(Mockito.anyString(), Mockito.anyBoolean());
  }

  @Test
  void unknownToggleNameThrowsBadRequest() {
    Mockito.when(registry.get("nonexistent")).thenReturn(Optional.empty());

    ConfigPatchException ex = assertThrows(ConfigPatchException.class, () ->
      descriptor.applyMergePatch(MAPPER.readTree("{\"nonexistent\": true}"))
    );

    assertEquals(400, ex.getStatus().getStatusCode());
  }

  @Test
  void nonBooleanValueThrowsBadRequest() {
    var e = entry("versioning", true);
    Mockito.when(registry.get("versioning")).thenReturn(Optional.of(e));

    ConfigPatchException ex = assertThrows(ConfigPatchException.class, () ->
      descriptor.applyMergePatch(MAPPER.readTree("{\"versioning\": \"yes\"}"))
    );

    assertEquals(400, ex.getStatus().getStatusCode());
  }

  @Test
  void nullValueThrowsBadRequest() {
    var e = entry("versioning", true);
    Mockito.when(registry.get("versioning")).thenReturn(Optional.of(e));

    ConfigPatchException ex = assertThrows(ConfigPatchException.class, () ->
      descriptor.applyMergePatch(MAPPER.readTree("{\"versioning\": null}"))
    );

    assertEquals(400, ex.getStatus().getStatusCode());
  }
}
