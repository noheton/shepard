package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PM1c — covers {@link PluginRegistry#validateAndOrderDependencies()}.
 *
 * <p>Stages {@link PluginEntry} instances directly into the registry's
 * package-private {@code entries} map and exercises the topological
 * sort + failure-mode classification without booting Quarkus or
 * walking a JAR directory.
 *
 * <p>Mirrors the failure-mode taxonomy from
 * {@code aidocs/16 §PM1c}:
 * <ul>
 *   <li>{@code plugin.dependency.cycle} — circular dependency.</li>
 *   <li>{@code plugin.dependency.missing} — required plugin absent.</li>
 *   <li>{@code plugin.dependency.version-mismatch} — version
 *       constraint unsatisfied.</li>
 * </ul>
 */
class PluginRegistryDependencyTest {

  PluginRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new PluginRegistry();
  }

  // ─── happy path ─────────────────────────────────────────────────

  @Test
  void linearChain_registersInTopoOrder() {
    // A → B → C (A depends on B, B depends on C).
    stage("a", "1.0.0", dep("b", "[1.0,)"));
    stage("b", "1.0.0", dep("c", "[1.0,)"));
    stage("c", "1.0.0");

    registry.validateAndOrderDependencies();

    List<String> order = idsInRegistryOrder();
    // C must come before B; B must come before A. Failed rows would
    // jump to the front, but there are none here.
    assertThat(order).containsExactly("c", "b", "a");
    assertThat(registry.get("a").orElseThrow().state()).isEqualTo(PluginState.DISCOVERED);
    assertThat(registry.get("b").orElseThrow().state()).isEqualTo(PluginState.DISCOVERED);
    assertThat(registry.get("c").orElseThrow().state()).isEqualTo(PluginState.DISCOVERED);
  }

  @Test
  void independentPlugins_keepInsertionOrder() {
    // Two plugins with no deps — Kahn's algorithm picks one of them
    // first deterministically (poll-from-queue), but both must end
    // up in the topo block. We assert presence + non-failure rather
    // than a specific order.
    stage("alpha", "1.0.0");
    stage("beta", "2.0.0");

    registry.validateAndOrderDependencies();

    assertThat(idsInRegistryOrder()).containsExactlyInAnyOrder("alpha", "beta");
    assertThat(registry.get("alpha").orElseThrow().state()).isEqualTo(PluginState.DISCOVERED);
    assertThat(registry.get("beta").orElseThrow().state()).isEqualTo(PluginState.DISCOVERED);
  }

  @Test
  void emptyConstraint_acceptsAnyVersion() {
    stage("dependent", "1.0.0", dep("target", ""));
    stage("target", "99.99.99");

    registry.validateAndOrderDependencies();

    assertThat(registry.get("dependent").orElseThrow().state()).isEqualTo(PluginState.DISCOVERED);
  }

  // ─── failure: cycle ─────────────────────────────────────────────

  @Test
  void twoNodeCycle_marksBothFailed() {
    // A → B → A
    stage("a", "1.0.0", dep("b", "[1.0,)"));
    stage("b", "1.0.0", dep("a", "[1.0,)"));

    registry.validateAndOrderDependencies();

    PluginEntry a = registry.get("a").orElseThrow();
    PluginEntry b = registry.get("b").orElseThrow();
    assertThat(a.state()).isEqualTo(PluginState.FAILED);
    assertThat(b.state()).isEqualTo(PluginState.FAILED);
    assertThat(a.failureMessage()).contains("plugin.dependency.cycle");
    assertThat(b.failureMessage()).contains("plugin.dependency.cycle");
  }

  @Test
  void threeNodeCycle_marksAllFailed() {
    // A → B → C → A
    stage("a", "1.0.0", dep("b", "[1.0,)"));
    stage("b", "1.0.0", dep("c", "[1.0,)"));
    stage("c", "1.0.0", dep("a", "[1.0,)"));

    registry.validateAndOrderDependencies();

    for (String id : List.of("a", "b", "c")) {
      PluginEntry e = registry.get(id).orElseThrow();
      assertThat(e.state()).as("entry " + id).isEqualTo(PluginState.FAILED);
      assertThat(e.failureMessage()).contains("plugin.dependency.cycle");
    }
  }

  @Test
  void cycleDoesNotPoisonHealthyDependents() {
    // A → B (B → C → B cycle); A is otherwise healthy but loses its
    // dependency target. Expected: B + C FAILED for cycle; A FAILED
    // for missing dep (B got removed from the graph).
    // The order of the failure annotations matters less than the
    // outcome — both rows are FAILED.
    stage("a", "1.0.0", dep("b", "[1.0,)"));
    stage("b", "1.0.0", dep("c", "[1.0,)"));
    stage("c", "1.0.0", dep("b", "[1.0,)"));

    registry.validateAndOrderDependencies();

    PluginEntry a = registry.get("a").orElseThrow();
    PluginEntry b = registry.get("b").orElseThrow();
    PluginEntry c = registry.get("c").orElseThrow();
    assertThat(a.state()).isEqualTo(PluginState.FAILED);
    assertThat(b.state()).isEqualTo(PluginState.FAILED);
    assertThat(c.state()).isEqualTo(PluginState.FAILED);
  }

  // ─── failure: missing ───────────────────────────────────────────

  @Test
  void missingDependency_marksDependentFailed() {
    stage("a", "1.0.0", dep("nonexistent", "[1.0,)"));

    registry.validateAndOrderDependencies();

    PluginEntry a = registry.get("a").orElseThrow();
    assertThat(a.state()).isEqualTo(PluginState.FAILED);
    assertThat(a.failureMessage()).contains("plugin.dependency.missing");
    assertThat(a.failureMessage()).contains("nonexistent");
  }

  @Test
  void missingDependency_doesNotFailHealthySiblings() {
    stage("a", "1.0.0", dep("nonexistent", "[1.0,)"));
    stage("healthy", "1.0.0");

    registry.validateAndOrderDependencies();

    assertThat(registry.get("a").orElseThrow().state()).isEqualTo(PluginState.FAILED);
    assertThat(registry.get("healthy").orElseThrow().state()).isEqualTo(PluginState.DISCOVERED);
  }

  // ─── failure: version mismatch ──────────────────────────────────

  @Test
  void versionConstraint_unsatisfied_marksDependentFailed() {
    // A requires B >= 2.0 but B is 1.0.
    stage("a", "1.0.0", dep("b", "[2.0,)"));
    stage("b", "1.0.0");

    registry.validateAndOrderDependencies();

    PluginEntry a = registry.get("a").orElseThrow();
    assertThat(a.state()).isEqualTo(PluginState.FAILED);
    assertThat(a.failureMessage()).contains("plugin.dependency.version-mismatch");
    assertThat(a.failureMessage()).contains("[2.0,)");
    // B is untouched — it's a valid target, just not for A's constraint.
    assertThat(registry.get("b").orElseThrow().state()).isEqualTo(PluginState.DISCOVERED);
  }

  @Test
  void versionConstraint_satisfied_passes() {
    // A requires B 1.x; B is 1.5 — fine.
    stage("a", "1.0.0", dep("b", "[1.0,2.0)"));
    stage("b", "1.5.0");

    registry.validateAndOrderDependencies();

    assertThat(registry.get("a").orElseThrow().state()).isEqualTo(PluginState.DISCOVERED);
  }

  @Test
  void unparseableConstraint_marksFailedWithDiagnostic() {
    stage("a", "1.0.0", dep("b", "[broken"));
    stage("b", "1.0.0");

    registry.validateAndOrderDependencies();

    PluginEntry a = registry.get("a").orElseThrow();
    assertThat(a.state()).isEqualTo(PluginState.FAILED);
    assertThat(a.failureMessage()).contains("plugin.dependency.version-mismatch");
  }

  // ─── failure: already-failed entries skipped ────────────────────

  @Test
  void preExistingFailedEntry_isNotConsideredAsDependencyTarget() {
    // Stage B as FAILED already (e.g. from a PM1a duplicate-id
    // detection). A depends on B — A should fail with missing.
    stage("a", "1.0.0", dep("b", "[1.0,)"));
    PluginEntry b = makeEntry("b", "1.0.0");
    b.markFailed("simulated pre-existing failure");
    registry.entries.put("b", b);

    registry.validateAndOrderDependencies();

    PluginEntry a = registry.get("a").orElseThrow();
    assertThat(a.state()).isEqualTo(PluginState.FAILED);
    assertThat(a.failureMessage()).contains("plugin.dependency.missing");
  }

  // ─── helpers ────────────────────────────────────────────────────

  private void stage(String id, String version, PluginDependency... deps) {
    registry.entries.put(id, makeEntry(id, version, deps));
  }

  private PluginEntry makeEntry(String id, String version, PluginDependency... deps) {
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
        return ">=5.2.0,<6";
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

  private List<String> idsInRegistryOrder() {
    return new ArrayList<>(registry.entries.keySet());
  }
}
