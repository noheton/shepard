package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PM1b2 — covers {@link PluginRegistry#enforceCompatibility()}.
 *
 * <p>Stages {@link PluginEntry} instances directly into the registry's
 * package-private {@code entries} map and exercises the semver-range
 * enforcement of {@link PluginManifest#shepardCompatibility()}
 * without booting Quarkus or walking a JAR directory.
 *
 * <p>Failure-mode taxonomy (matches {@code aidocs/16 §PM1b2}):
 * <ul>
 *   <li>{@code plugin.compatibility.failed} — running shepard
 *       doesn't satisfy the declared range.</li>
 *   <li>{@code plugin.compatibility.unparseable} — the declared
 *       range string isn't valid syntax.</li>
 * </ul>
 *
 * <p>Precedence rule: compat enforcement runs BEFORE dependency
 * resolution, so a dependent of a compat-failed plugin fails for
 * {@code plugin.dependency.missing} rather than masking the more
 * accurate root cause.
 */
class PluginRegistryCompatibilityTest {

  PluginRegistry registry;

  @BeforeEach
  void setUp() throws Exception {
    registry = new PluginRegistry();
    // Pin the running version explicitly — the @ConfigProperty
    // defaultValue doesn't apply on the reflective test path.
    setField("shepardVersion", "5.2.0");
    setField("compatibilityStrict", true);
  }

  // ─── happy path ─────────────────────────────────────────────────

  @Test
  void compatibleVersion_passesUntouched() {
    // Running 5.2.0; plugin requires >=5.2.0,<6 → fine.
    stage("alpha", "1.0.0", ">=5.2.0,<6");

    registry.enforceCompatibility();

    PluginEntry e = registry.get("alpha").orElseThrow();
    assertThat(e.state()).isEqualTo(PluginState.DISCOVERED);
    assertThat(e.failureMessage()).isNull();
  }

  @Test
  void emptyConstraint_acceptsAnyVersion() {
    stage("any", "1.0.0", "");

    registry.enforceCompatibility();

    assertThat(registry.get("any").orElseThrow().state())
      .isEqualTo(PluginState.DISCOVERED);
  }

  @Test
  void nullConstraint_acceptsAnyVersion() {
    stage("nullc", "1.0.0", null);

    registry.enforceCompatibility();

    assertThat(registry.get("nullc").orElseThrow().state())
      .isEqualTo(PluginState.DISCOVERED);
  }

  // ─── failure: incompatible version ──────────────────────────────

  @Test
  void incompatibleTooOld_marksFailedWithDiagnostic() throws Exception {
    setField("shepardVersion", "4.9.0");
    stage("oldfork", "1.0.0", ">=5.2.0,<6");

    registry.enforceCompatibility();

    PluginEntry e = registry.get("oldfork").orElseThrow();
    assertThat(e.state()).isEqualTo(PluginState.FAILED);
    assertThat(e.failureMessage()).contains("plugin.compatibility.failed");
    assertThat(e.failureMessage()).contains(">=5.2.0,<6");
    assertThat(e.failureMessage()).contains("4.9.0");
  }

  @Test
  void incompatibleTooNew_marksFailedWithDiagnostic() throws Exception {
    setField("shepardVersion", "6.0.0");
    stage("newfork", "1.0.0", ">=5.2.0,<6");

    registry.enforceCompatibility();

    PluginEntry e = registry.get("newfork").orElseThrow();
    assertThat(e.state()).isEqualTo(PluginState.FAILED);
    assertThat(e.failureMessage()).contains("plugin.compatibility.failed");
    assertThat(e.failureMessage()).contains("6.0.0");
  }

  // ─── failure: malformed constraint ──────────────────────────────

  @Test
  void unparseableConstraint_marksFailed() {
    stage("malformed", "1.0.0", "[1.0"); // unbalanced bracket

    registry.enforceCompatibility();

    PluginEntry e = registry.get("malformed").orElseThrow();
    assertThat(e.state()).isEqualTo(PluginState.FAILED);
    assertThat(e.failureMessage()).contains("plugin.compatibility.unparseable");
  }

  // ─── strict=false override ──────────────────────────────────────

  @Test
  void strictFalse_incompatiblePluginRegistersWithWarn() throws Exception {
    setField("shepardVersion", "4.0.0");
    setField("compatibilityStrict", false);
    stage("override", "1.0.0", ">=5.2.0,<6");

    registry.enforceCompatibility();

    // strict=false → no FAILED state even though the version doesn't fit.
    PluginEntry e = registry.get("override").orElseThrow();
    assertThat(e.state()).isEqualTo(PluginState.DISCOVERED);
  }

  @Test
  void strictFalse_unparseableConstraintAlsoRegisters() throws Exception {
    setField("compatibilityStrict", false);
    stage("broken", "1.0.0", "[abc");

    registry.enforceCompatibility();

    PluginEntry e = registry.get("broken").orElseThrow();
    assertThat(e.state()).isEqualTo(PluginState.DISCOVERED);
  }

  // ─── precedence vs dependency resolution ────────────────────────

  @Test
  void compatFailure_precedesDependencyResolution_dependentFailsForMissing()
    throws Exception {
    // Running 4.0.0; plugin A depends on plugin B; B is incompatible
    // (>=5.2.0,<6). Expected: B fails for compat, A fails for missing
    // dep (because B was already FAILED before dep-resolution ran).
    setField("shepardVersion", "4.0.0");
    stage("a", "1.0.0", ">=4.0,<5", dep("b", "[1.0,)"));
    stage("b", "1.0.0", ">=5.2.0,<6");

    registry.enforceCompatibility();
    registry.validateAndOrderDependencies();

    PluginEntry a = registry.get("a").orElseThrow();
    PluginEntry b = registry.get("b").orElseThrow();
    assertThat(b.state()).isEqualTo(PluginState.FAILED);
    assertThat(b.failureMessage()).contains("plugin.compatibility.failed");
    // A is also FAILED, with a missing-dep reason (B was excluded).
    assertThat(a.state()).isEqualTo(PluginState.FAILED);
    assertThat(a.failureMessage()).contains("plugin.dependency.missing");
  }

  // ─── multi-plugin ───────────────────────────────────────────────

  @Test
  void mixedCompatibility_onlyTheIncompatibleOneFails() {
    stage("good", "1.0.0", ">=5.0,<6");
    stage("bad", "1.0.0", ">=10.0,<11");
    stage("any", "1.0.0", "");

    registry.enforceCompatibility();

    assertThat(registry.get("good").orElseThrow().state())
      .isEqualTo(PluginState.DISCOVERED);
    assertThat(registry.get("bad").orElseThrow().state())
      .isEqualTo(PluginState.FAILED);
    assertThat(registry.get("any").orElseThrow().state())
      .isEqualTo(PluginState.DISCOVERED);
  }

  @Test
  void alreadyFailedEntry_isNotReprocessed() {
    // If a duplicate-id detection already failed the entry, the
    // compat pass shouldn't change its failureMessage.
    PluginEntry e = makeEntry("dup", "1.0.0", ">=10,<11");
    e.markFailed("preexisting: duplicate plugin id");
    registry.entries.put("dup", e);

    registry.enforceCompatibility();

    assertThat(e.failureMessage()).isEqualTo("preexisting: duplicate plugin id");
  }

  // ─── helpers ────────────────────────────────────────────────────

  private void stage(String id, String version, String compat, PluginDependency... deps) {
    registry.entries.put(id, makeEntry(id, version, compat, deps));
  }

  private PluginEntry makeEntry(
    String id,
    String version,
    String compat,
    PluginDependency... deps
  ) {
    final List<PluginDependency> depList = new ArrayList<>(List.of(deps));
    PluginManifest manifest = new PluginManifest() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public String version() {
        return version;
      }

      @Override
      public String shepardCompatibility() {
        return compat;
      }

      @Override
      public List<PluginDependency> dependencies() {
        return depList;
      }
    };
    return new PluginEntry(manifest, null);
  }

  private static PluginDependency dep(String pluginId, String versionConstraint) {
    return new PluginDependency(pluginId, versionConstraint);
  }

  private void setField(String name, Object value) throws Exception {
    var field = registry.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(registry, value);
  }
}
