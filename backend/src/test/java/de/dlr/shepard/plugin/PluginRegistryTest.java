package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PM1a — unit-level tests for {@link PluginRegistry}.
 *
 * <p>Bypasses CDI by reflectively wiring {@code pluginsDir} and
 * {@code beanManager} — keeps the test fast and free of
 * {@code @QuarkusTest} boot overhead. The smoke-level end-to-end
 * test (plugin JAR + Quarkus boot + UH1a endpoints respond) lives
 * in the integration-test job.
 */
class PluginRegistryTest {

  @TempDir
  Path tempDir;

  PluginRegistry registry;

  @BeforeEach
  void setUp() throws Exception {
    registry = new PluginRegistry();
    setField(registry, "pluginsDir", tempDir.toString());
    // beanManager left null — DefaultPluginContext tolerates this.
  }

  @AfterEach
  void tearDown() {
    PluginRegistryTestSupport.OnRegisterCalls.reset();
    PluginRegistryTestSupport.OnUnregisterCalls.reset();
  }

  @Test
  void discover_emptyDirectory_logsNothingAndStaysEmpty() {
    registry.discover();
    assertThat(registry.list()).isEmpty();
  }

  @Test
  void discover_missingDirectory_isFailSoft() throws Exception {
    setField(registry, "pluginsDir", tempDir.resolve("does-not-exist").toString());
    registry.discover();
    assertThat(registry.list()).isEmpty();
  }

  @Test
  void discover_nonDirectoryPath_isFailSoft() throws Exception {
    Path file = Files.createFile(tempDir.resolve("not-a-dir"));
    setField(registry, "pluginsDir", file.toString());
    registry.discover();
    assertThat(registry.list()).isEmpty();
  }

  @Test
  void discover_jarWithManifest_isDiscoveredAndEnabled() throws Exception {
    buildTestPluginJar(
      tempDir.resolve("test-plugin.jar"),
      "de.dlr.shepard.plugin.PluginRegistryTestSupport$RecordingManifest$test_plugin"
    );
    registry.discover();

    List<PluginEntry> plugins = registry.list();
    assertThat(plugins).hasSize(1);
    PluginEntry entry = plugins.get(0);
    assertThat(entry.id()).isEqualTo("test-plugin");
    assertThat(entry.version()).isEqualTo("0.0.1-test");
    assertThat(entry.shepardCompatibility()).isEqualTo(">=5.2.0,<6");
    assertThat(entry.state()).isEqualTo(PluginState.ENABLED);
    assertThat(entry.jarPath()).isNotNull();
    // onRegister was invoked: the recording counter ticked.
    assertThat(PluginRegistryTestSupport.OnRegisterCalls.get()).isEqualTo(1);
  }

  @Test
  void disabledToggle_skipsOnRegister() throws Exception {
    buildTestPluginJar(
      tempDir.resolve("test-plugin.jar"),
      "de.dlr.shepard.plugin.PluginRegistryTestSupport$RecordingManifest$test_plugin_disabled"
    );
    System.setProperty("shepard.plugins.test-plugin-disabled.enabled", "false");
    try {
      registry.discover();
      Optional<PluginEntry> entry = registry.get("test-plugin-disabled");
      assertThat(entry).isPresent();
      assertThat(entry.get().state()).isEqualTo(PluginState.DISABLED);
      // onRegister was NOT invoked.
      assertThat(PluginRegistryTestSupport.OnRegisterCalls.get()).isEqualTo(0);
    } finally {
      System.clearProperty("shepard.plugins.test-plugin-disabled.enabled");
    }
  }

  @Test
  void onRegisterThrowing_marksPluginFailedButContinues() throws Exception {
    buildTestPluginJar(
      tempDir.resolve("throwing-plugin.jar"),
      "de.dlr.shepard.plugin.PluginRegistryTestSupport$ThrowingManifest$throwing"
    );
    buildTestPluginJar(
      tempDir.resolve("recording-plugin.jar"),
      "de.dlr.shepard.plugin.PluginRegistryTestSupport$RecordingManifest$recording"
    );
    registry.discover();

    // Both entries present; throwing is FAILED, recording is ENABLED.
    Optional<PluginEntry> throwing = registry.get("throwing");
    Optional<PluginEntry> recording = registry.get("recording");
    assertThat(throwing).isPresent();
    assertThat(throwing.get().state()).isEqualTo(PluginState.FAILED);
    assertThat(throwing.get().failureMessage()).contains("kaboom");
    assertThat(recording).isPresent();
    assertThat(recording.get().state()).isEqualTo(PluginState.ENABLED);
  }

  @Test
  void duplicatePluginId_secondInstanceMarkedFailed() throws Exception {
    buildTestPluginJar(
      tempDir.resolve("first.jar"),
      "de.dlr.shepard.plugin.PluginRegistryTestSupport$RecordingManifest$dup"
    );
    buildTestPluginJar(
      tempDir.resolve("second.jar"),
      "de.dlr.shepard.plugin.PluginRegistryTestSupport$RecordingManifest$dup"
    );
    registry.discover();

    Optional<PluginEntry> first = registry.get("dup");
    assertThat(first).isPresent();
    assertThat(first.get().state()).isEqualTo(PluginState.ENABLED);
    // The second occurrence lands under a synthetic #duplicate key:
    long failedCount = registry.list().stream()
      .filter(e -> e.state() == PluginState.FAILED)
      .filter(e -> e.failureMessage() != null && e.failureMessage().contains("duplicate"))
      .count();
    assertThat(failedCount).isEqualTo(1);
  }

  @Test
  void setEnabled_unknownPlugin_throws() {
    assertThatThrownBy(() -> registry.setEnabled("missing", true))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("missing");
  }

  @Test
  void setEnabled_overridesIsEnabled() throws Exception {
    buildTestPluginJar(
      tempDir.resolve("flip.jar"),
      "de.dlr.shepard.plugin.PluginRegistryTestSupport$RecordingManifest$flip"
    );
    registry.discover();

    assertThat(registry.isEnabled("flip")).isTrue();
    registry.setEnabled("flip", false);
    assertThat(registry.isEnabled("flip")).isFalse();
    registry.setEnabled("flip", true);
    assertThat(registry.isEnabled("flip")).isTrue();
  }

  @Test
  void jarWithoutManifest_logsAndContinues() throws Exception {
    // A JAR with no META-INF/services entry — should be ignored.
    Path jar = tempDir.resolve("empty.jar");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), new Manifest())) {
      JarEntry e = new JarEntry("placeholder.txt");
      out.putNextEntry(e);
      out.write("hi".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    registry.discover();
    assertThat(registry.list()).isEmpty();
  }

  @Test
  void onShutdown_invokesOnUnregisterForEnabledPlugins() throws Exception {
    buildTestPluginJar(
      tempDir.resolve("shutdown-plugin.jar"),
      "de.dlr.shepard.plugin.PluginRegistryTestSupport$RecordingManifest$shutdownp"
    );
    registry.discover();
    PluginRegistryTestSupport.OnUnregisterCalls.reset();
    registry.onShutdown(null);
    assertThat(PluginRegistryTestSupport.OnUnregisterCalls.get()).isEqualTo(1);
  }

  // ----------------------------------------------------------------
  // Helpers.
  // ----------------------------------------------------------------

  /**
   * Builds a tiny JAR containing only a
   * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
   * entry pointing at an already-loaded class on the parent loader.
   *
   * <p>The trick: the test classpath already has the support classes
   * compiled in, so the child loader's parent (= the test classloader)
   * resolves the class without needing it inside the JAR. The JAR is
   * effectively a metadata-only artefact — the simplest possible
   * fixture proving the ServiceLoader path works.
   */
  private void buildTestPluginJar(Path jarPath, String implClass) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      JarEntry serviceFile = new JarEntry("META-INF/services/de.dlr.shepard.plugin.PluginManifest");
      out.putNextEntry(serviceFile);
      out.write(implClass.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    var field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
