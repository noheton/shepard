package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PM1b2 — covers the {@link PluginState#DEGRADED} intermediate state
 * introduced for runtime-only plugin JARs that the Quarkus
 * build-time CDI scanner couldn't index.
 *
 * <p>Today (Option B path; see {@code aidocs/69-runtime-plugin-cdi.md})
 * the registry doesn't auto-detect runtime-only JARs — the PM1
 * shipped today requires every plugin to also be declared in the
 * {@code with-plugins} Maven profile so Quarkus's build-time CDI
 * scan picks it up. PM1b2 introduces the state + the lifecycle hook
 * ({@link PluginEntry#markDegraded(String)}) + the
 * {@code GET /v2/admin/plugins} visibility so PM1b3 can flip a
 * plugin's state from DEGRADED → ENABLED in-place once the runtime
 * CDI integration ships.
 *
 * <p>These tests cover the state machine + reporting plumbing —
 * detection of runtime-only JARs lives in PM1b3.
 */
class PluginRegistryDegradedStateTest {

  PluginRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new PluginRegistry();
  }

  @Test
  void markDegraded_transitionsState() {
    PluginEntry e = stageEntry("late-loader", "1.0.0");

    e.markDegraded("plugin.runtime.no-cdi-scan: declare in with-plugins profile");

    assertThat(e.state()).isEqualTo(PluginState.DEGRADED);
    assertThat(e.failureMessage()).contains("plugin.runtime.no-cdi-scan");
  }

  @Test
  void degradedEntry_visibleInList() {
    PluginEntry e = stageEntry("late-loader", "1.0.0");
    e.markDegraded("test");

    List<PluginEntry> all = registry.list();

    assertThat(all).contains(e);
    assertThat(all).extracting(PluginEntry::state).contains(PluginState.DEGRADED);
  }

  @Test
  void pluginStateEnum_carriesDegraded() {
    // Defensive — the admin REST IO record encodes the enum's
    // name() verbatim; a rename would break the wire shape.
    assertThat(PluginState.valueOf("DEGRADED")).isEqualTo(PluginState.DEGRADED);
    assertThat(PluginState.DEGRADED.name()).isEqualTo("DEGRADED");
  }

  @Test
  void markEnabled_clearsDegradedFailureMessage() {
    PluginEntry e = stageEntry("late-loader", "1.0.0");
    e.markDegraded("simulated");

    e.markEnabled();

    assertThat(e.state()).isEqualTo(PluginState.ENABLED);
    assertThat(e.failureMessage()).isNull();
  }

  private PluginEntry stageEntry(String id, String version) {
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
    };
    PluginEntry entry = new PluginEntry(manifest, null);
    registry.entries.put(id, entry);
    return entry;
  }
}
