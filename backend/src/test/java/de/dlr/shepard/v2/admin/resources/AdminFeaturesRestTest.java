package de.dlr.shepard.v2.admin.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.configuration.feature.runtime.FeatureToggleRegistry;
import de.dlr.shepard.v2.admin.io.FeatureToggleIO;
import de.dlr.shepard.v2.admin.io.PatchFeatureToggleIO;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class AdminFeaturesRestTest {

  @Mock
  FeatureToggleRegistry registry;

  AdminFeaturesRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new AdminFeaturesRest();
    resource.registry = registry;
  }

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

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = AdminFeaturesRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "AdminFeaturesRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals("instance-admin", gate.value()[0]);
  }

  @Test
  void listReturnsAllToggles() {
    Mockito.when(registry.list()).thenReturn(List.of(
      entry("versioning", true),
      entry("spatial-data", false)
    ));

    var r = resource.list();

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<FeatureToggleIO> body = (List<FeatureToggleIO>) r.getEntity();
    assertEquals(2, body.size());
    assertEquals("versioning", body.get(0).getName());
    assertTrue(body.get(0).isEnabled());
    assertEquals("spatial-data", body.get(1).getName());
    assertTrue(!body.get(1).isEnabled());
  }

  @Test
  void patchKnownToggleReturnsUpdatedIO() {
    var entry = mutableEntry("versioning", true);
    Mockito.when(registry.get("versioning")).thenReturn(Optional.of(entry));

    var body = new PatchFeatureToggleIO();
    body.setEnabled(false);

    var r = resource.patch("versioning", body);

    assertEquals(200, r.getStatus());
    FeatureToggleIO io = (FeatureToggleIO) r.getEntity();
    assertEquals("versioning", io.getName());
    assertTrue(!io.isEnabled());
  }

  @Test
  void patchUnknownNameReturns404() {
    Mockito.when(registry.get("nonexistent")).thenReturn(Optional.empty());

    var body = new PatchFeatureToggleIO();
    body.setEnabled(true);

    var r = resource.patch("nonexistent", body);

    assertEquals(404, r.getStatus());
  }
}
