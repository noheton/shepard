package de.dlr.shepard.plugins.importer;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * IMP1a — smoke tests for the PluginManifest scaffold. We test:
 *
 * <ul>
 *   <li>the manifest declares the right id / version / compat
 *       range — these strings are part of the contract surface
 *       PluginRegistry uses to drive the admin REST + CLI list;
 *   <li>the ServiceLoader SPI file resolves to this class — guards
 *       against a typo in
 *       {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest};
 *   <li>{@code onRegister} / {@code onUnregister} are no-op
 *       safe — when PR-2 grows the bean wiring this test catches
 *       a regression where a thrown exception would land the
 *       plugin in FAILED state at startup;
 *   <li>the metadata fields (title, description, urls, licence)
 *       are non-null and non-empty — these are what an operator
 *       sees in {@code shepard-admin plugins list}.
 * </ul>
 */
final class ImporterPluginManifestTest {

  @Test
  void declares_expected_plugin_id_and_version() {
    var m = new ImporterPluginManifest();
    assertThat(m.id()).isEqualTo("importer");
    assertThat(m.version()).isEqualTo("1.0.0-SNAPSHOT");
    assertThat(m.shepardCompatibility()).isEqualTo(">=6.0.0-SNAPSHOT,<7");
  }

  @Test
  void surfaces_operator_metadata() {
    var m = new ImporterPluginManifest();
    assertThat(m.title()).contains("Importer");
    assertThat(m.description()).isNotBlank();
    assertThat(m.homepageUrl()).isPresent();
    assertThat(m.repositoryUrl()).isPresent();
    assertThat(m.licence()).isEqualTo("Apache-2.0");
    assertThat(m.dependencies()).isEmpty();
  }

  @Test
  void on_register_and_unregister_are_no_op_safe() {
    var m = new ImporterPluginManifest();
    var ctx = Mockito.mock(PluginContext.class);
    // Must not throw: a thrown exception flips the plugin to
    // FAILED in PluginRegistry.
    m.onRegister(ctx);
    m.onUnregister(ctx);
  }

  @Test
  void service_loader_resolves_to_this_manifest() {
    // Guards against a typo in
    // META-INF/services/de.dlr.shepard.plugin.PluginManifest.
    boolean found = false;
    for (PluginManifest pm : ServiceLoader.load(PluginManifest.class)) {
      if (pm instanceof ImporterPluginManifest) {
        found = true;
        break;
      }
    }
    assertThat(found)
      .as("ImporterPluginManifest must be discoverable via ServiceLoader")
      .isTrue();
  }
}
