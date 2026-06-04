package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * V2CONV-A5 — unit tests for {@link RestNamespaceRegistry}.
 *
 * <p>Mocks {@link PluginRegistry} so the test controls both the discovered manifest set
 * and each plugin's enabled-state, then asserts the registry's disabled-prefix resolution.
 *
 * <p>Plugin contributors are now queried live from the PluginRegistry at call time (no
 * startup registration step needed). Core contributors use
 * {@link RestNamespaceRegistry#registerCoreContributor}.
 */
class RestNamespaceRegistryTest {

  private PluginRegistry pluginRegistry;
  private RestNamespaceRegistry registry;

  @BeforeEach
  void setUp() {
    pluginRegistry = Mockito.mock(PluginRegistry.class);
    registry = new RestNamespaceRegistry();
    registry.pluginRegistry = pluginRegistry;
    // Default: no plugins discovered
    when(pluginRegistry.list()).thenReturn(List.of());
  }

  /** A manifest that also owns a REST namespace. */
  private static PluginManifest contributorManifest(String id, Set<String> prefixes) {
    class M implements PluginManifest, RestNamespaceContributor {
      @Override
      public String id() {
        return id;
      }

      @Override
      public String version() {
        return "1.0.0";
      }

      @Override
      public String shepardCompatibility() {
        return ">=6.0.0-SNAPSHOT,<7";
      }

      @Override
      public Set<String> ownedRestPathPrefixes() {
        return prefixes;
      }
    }
    return new M();
  }

  private static PluginEntry entry(PluginManifest manifest) {
    return new PluginEntry(manifest, null);
  }

  @Test
  void enabledPlugin_isNotGated() {
    PluginManifest aas = contributorManifest("aas", Set.of("/v2/aas"));
    when(pluginRegistry.list()).thenReturn(List.of(entry(aas)));
    when(pluginRegistry.isEnabled("aas")).thenReturn(true);

    assertThat(registry.disabledPrefixes()).isEmpty();
    assertThat(registry.disabledPrefixFor("/v2/aas/shells")).isEmpty();
  }

  @Test
  void disabledPlugin_gatesItsPrefix() {
    PluginManifest aas = contributorManifest("aas", Set.of("/v2/aas"));
    when(pluginRegistry.list()).thenReturn(List.of(entry(aas)));
    when(pluginRegistry.isEnabled("aas")).thenReturn(false);

    assertThat(registry.disabledPrefixes()).containsExactly("/v2/aas");
    assertThat(registry.disabledPrefixFor("/v2/aas")).contains("/v2/aas");
    assertThat(registry.disabledPrefixFor("/v2/aas/shells/abc")).contains("/v2/aas");
    assertThat(registry.disabledPrefixFor("/v2/aas/.well-known/aas-server")).contains("/v2/aas");
  }

  @Test
  void prefixMatchIsStructuralNotSubstring() {
    PluginManifest aas = contributorManifest("aas", Set.of("/v2/aas"));
    when(pluginRegistry.list()).thenReturn(List.of(entry(aas)));
    when(pluginRegistry.isEnabled("aas")).thenReturn(false);

    // A sibling resource whose name merely starts with the prefix string must NOT be gated.
    assertThat(registry.disabledPrefixFor("/v2/aaszzz")).isEmpty();
    assertThat(registry.disabledPrefixFor("/v2/collections")).isEmpty();
    // The frozen v1 surface is never touched.
    assertThat(registry.disabledPrefixFor("/collections")).isEmpty();
  }

  @Test
  void manifestWithoutMarker_isIgnored() {
    PluginManifest plain = new PluginManifest() {
      @Override
      public String id() {
        return "plain";
      }

      @Override
      public String version() {
        return "1.0.0";
      }

      @Override
      public String shepardCompatibility() {
        return ">=6.0.0-SNAPSHOT,<7";
      }
    };
    when(pluginRegistry.list()).thenReturn(List.of(entry(plain)));

    assertThat(registry.disabledPrefixes()).isEmpty();
  }

  @Test
  void overBroadV2Root_isRejected() {
    PluginManifest greedy = contributorManifest("greedy", Set.of("/v2"));
    when(pluginRegistry.list()).thenReturn(List.of(entry(greedy)));
    when(pluginRegistry.isEnabled("greedy")).thenReturn(false);

    // The greedy "/v2" claim is dropped — it never gates anything.
    assertThat(registry.disabledPrefixes()).isEmpty();
  }

  @Test
  void coreContributor_gatesWhenToggleOff() {
    AtomicBoolean enabled = new AtomicBoolean(true);
    registry.registerCoreContributor("jupyter", Set.of("/v2/jupyter"), enabled::get);

    assertThat(registry.disabledPrefixFor("/v2/jupyter/config")).isEmpty();

    enabled.set(false);
    assertThat(registry.disabledPrefixFor("/v2/jupyter/config")).contains("/v2/jupyter");
    assertThat(registry.disabledPrefixes()).containsExactly("/v2/jupyter");
  }

  @Test
  void failSoft_enabledSupplierThrows_treatedAsEnabled() {
    registry.registerCoreContributor("boom", Set.of("/v2/boom"), () -> {
      throw new IllegalStateException("registry hiccup");
    });

    // Must NOT gate the prefix — a registry hiccup defaults to ALLOW.
    assertThat(registry.disabledPrefixFor("/v2/boom/x")).isEmpty();
    assertThat(registry.disabledPrefixes()).isEmpty();
  }

  @Test
  void runtimeFlip_reflectedWithoutReRegistration() {
    PluginManifest aas = contributorManifest("aas", Set.of("/v2/aas"));
    when(pluginRegistry.list()).thenReturn(List.of(entry(aas)));
    // Start enabled, then flip — disabledPrefixFor() reads isEnabled() live each call.
    when(pluginRegistry.isEnabled("aas")).thenReturn(true, false);

    assertThat(registry.disabledPrefixFor("/v2/aas/shells")).isEmpty();
    assertThat(registry.disabledPrefixFor("/v2/aas/shells")).contains("/v2/aas");
  }

  @Test
  void blankAndTrailingSlashPrefixes_areSanitised() {
    PluginManifest m = contributorManifest("x", Set.of("/v2/x/", "", "   "));
    when(pluginRegistry.list()).thenReturn(List.of(entry(m)));
    when(pluginRegistry.isEnabled("x")).thenReturn(false);

    // Trailing slash normalised away; blanks dropped.
    assertThat(registry.disabledPrefixes()).containsExactly("/v2/x");
    assertThat(registry.disabledPrefixFor("/v2/x/y")).contains("/v2/x");
  }

  @Test
  void pluginContributors_queriedLiveNotAtStartup() {
    // Verify that plugin contributors are discovered live without any explicit registration step.
    // This guards against regressions where startup-ordering breaks the registry.
    PluginManifest aas = contributorManifest("aas", Set.of("/v2/aas", "/v2/admin/aas"));
    when(pluginRegistry.list()).thenReturn(List.of(entry(aas)));
    when(pluginRegistry.isEnabled("aas")).thenReturn(false);

    // No explicit registerPluginContributors() call — should still work.
    assertThat(registry.disabledPrefixes()).containsExactlyInAnyOrder("/v2/aas", "/v2/admin/aas");
    assertThat(registry.disabledPrefixFor("/v2/aas/shells")).isPresent();
    assertThat(registry.disabledPrefixFor("/v2/admin/aas/config")).isPresent();
  }
}
