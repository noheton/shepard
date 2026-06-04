package de.dlr.shepard.plugin;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * V2CONV-A5 — single source of truth for "which owned {@code /v2/<prefix>/...}
 * namespaces are currently <em>disabled</em>?".
 *
 * <p>Both consumers read from here and from here alone:
 * <ul>
 *   <li>{@link de.dlr.shepard.common.filters.DisabledNamespaceRequestFilter} —
 *       returns 404 for a request under a disabled prefix.</li>
 *   <li>{@link de.dlr.shepard.common.openapi.DisabledNamespaceOasFilter} —
 *       strips the disabled prefix's paths from the served OpenAPI v2 shelf.</li>
 * </ul>
 *
 * <p>The registry holds <em>entries</em>, each pairing an owner id with the prefixes
 * it owns and a {@link BooleanSupplier} that answers "is this owner enabled right
 * now?". Entries come from two sources, both gathered at startup:
 *
 * <ol>
 *   <li><b>Plugin manifests implementing {@link RestNamespaceContributor}.</b>
 *       Enabled-state reads {@link PluginRegistry#isEnabled(String)} keyed on the
 *       manifest id — so a runtime {@code PATCH /v2/admin/plugins/{id}} flip is
 *       reflected immediately. This is the AAS shape.</li>
 *   <li><b>Core-registered contributors</b> bound to a feature toggle's own runtime
 *       flag via {@link #registerCoreContributor(String, Set, BooleanSupplier)} — the
 *       Jupyter shape, where the REST lives in core and is gated by
 *       {@code :JupyterConfig}.</li>
 * </ol>
 *
 * <p><strong>Fail-soft</strong> (matches the repo's "registries are fail-soft" rule):
 * if an enabled-state supplier throws, the registry treats the namespace as ENABLED
 * (allow) and logs a WARN — a registry hiccup must never 404 a working endpoint.
 *
 * <p>The filter logic never hard-codes "aas"/"jupyter" — the allowlist is exactly the
 * set of registered contributors, which is determined by which modules implement the
 * marker / call {@link #registerCoreContributor}.
 */
@ApplicationScoped
public class RestNamespaceRegistry {

  /** The over-broad root a contributor must never claim — it would shadow all of v2. */
  static final String FORBIDDEN_ROOT = "/v2";

  /**
   * One owned-namespace declaration: an owner id, the prefixes it owns, and a way to
   * read whether the owner is enabled at call time. Package-private for tests.
   */
  static final class Entry {

    final String ownerId;
    final Set<String> prefixes;
    final BooleanSupplier enabled;

    Entry(String ownerId, Set<String> prefixes, BooleanSupplier enabled) {
      this.ownerId = ownerId;
      this.prefixes = prefixes;
      this.enabled = enabled;
    }

    /** Fail-soft read — any exception is treated as ENABLED (allow). */
    boolean isEnabled() {
      try {
        return enabled.getAsBoolean();
      } catch (RuntimeException ex) {
        Log.warnf(
          ex,
          "V2CONV-A5: enabled-state read for namespace owner '%s' threw — treating as ENABLED (fail-soft allow)",
          ownerId
        );
        return true;
      }
    }
  }

  @Inject
  PluginRegistry pluginRegistry;

  /** Keyed by owner id; insertion-ordered for stable iteration / logging. */
  private final Map<String, Entry> entries = new LinkedHashMap<>();

  /**
   * Gather plugin-manifest contributors at startup. Priority APPLICATION+100 so this
   * fires after {@link PluginRegistry#onStart} (default APPLICATION priority) has
   * completed discovery — same ordering trick as {@link PluginPublicPathRegistrar}.
   *
   * <p>Core-registered contributors (Jupyter) are added by their own startup hook
   * calling {@link #registerCoreContributor}; order between the two sources is
   * irrelevant since each entry is keyed by a distinct owner id.
   */
  void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION + 100) StartupEvent event) {
    registerPluginContributors();
  }

  /**
   * Discover every {@link PluginManifest} that also implements
   * {@link RestNamespaceContributor} and register an entry whose enabled-state reads
   * the plugin registry. Package-private so tests can drive it after staging a
   * registry state.
   */
  void registerPluginContributors() {
    for (PluginEntry entry : pluginRegistry.list()) {
      PluginManifest manifest = entry.manifest();
      if (!(manifest instanceof RestNamespaceContributor contributor)) {
        continue;
      }
      Set<String> prefixes;
      try {
        prefixes = sanitise(entry.id(), contributor.ownedRestPathPrefixes());
      } catch (RuntimeException ex) {
        Log.warnf(ex, "V2CONV-A5: plugin '%s' ownedRestPathPrefixes() threw — skipping", entry.id());
        continue;
      }
      if (prefixes.isEmpty()) {
        continue;
      }
      String id = entry.id();
      register(new Entry(id, prefixes, () -> pluginRegistry.isEnabled(id)));
      Log.infof("V2CONV-A5: plugin '%s' owns REST namespace(s) %s (gated by plugin enabled-state)", id, prefixes);
    }
  }

  /**
   * Register a core-owned namespace contributor whose enabled-state is read from a
   * feature toggle's own runtime flag. Used by in-tree surfaces (Jupyter) whose REST
   * lives in core and therefore has no {@link PluginManifest}.
   *
   * <p>Idempotent on owner id: a second registration for the same id replaces the
   * first (so a startup hook firing twice across hot-reload doesn't duplicate).
   *
   * @param ownerId stable identifier for the surface (e.g. {@code "jupyter"}).
   * @param ownedPrefixes the {@code /v2/...} prefixes it owns.
   * @param enabled reads whether the surface is enabled at call time (fail-soft).
   */
  public void registerCoreContributor(String ownerId, Set<String> ownedPrefixes, BooleanSupplier enabled) {
    if (ownerId == null || ownerId.isBlank() || enabled == null) {
      Log.warn("V2CONV-A5: registerCoreContributor called with blank id or null supplier — ignored");
      return;
    }
    Set<String> prefixes = sanitise(ownerId, ownedPrefixes);
    if (prefixes.isEmpty()) {
      Log.warnf("V2CONV-A5: core contributor '%s' declared no valid prefixes — ignored", ownerId);
      return;
    }
    register(new Entry(ownerId, prefixes, enabled));
    Log.infof("V2CONV-A5: core surface '%s' owns REST namespace(s) %s (gated by feature toggle)", ownerId, prefixes);
  }

  private void register(Entry entry) {
    entries.put(entry.ownerId, entry);
  }

  /**
   * Drop blanks, normalise to no-trailing-slash, and reject the over-broad
   * {@code /v2} root that would shadow the whole shelf.
   */
  private Set<String> sanitise(String ownerId, Set<String> raw) {
    Set<String> out = new java.util.LinkedHashSet<>();
    if (raw == null) {
      return out;
    }
    for (String p : raw) {
      if (p == null || p.isBlank()) {
        continue;
      }
      String prefix = p.trim();
      if (prefix.length() > 1 && prefix.endsWith("/")) {
        prefix = prefix.substring(0, prefix.length() - 1);
      }
      if (FORBIDDEN_ROOT.equals(prefix)) {
        Log.warnf("V2CONV-A5: owner '%s' tried to claim the over-broad '%s' root — ignored", ownerId, FORBIDDEN_ROOT);
        continue;
      }
      out.add(prefix);
    }
    return out;
  }

  // -----------------------------------------------------------------
  // Read API — consumed by the request filter + the OAS filter.
  // -----------------------------------------------------------------

  /**
   * The set of owned prefixes whose owner is currently <em>disabled</em>. Computed on
   * each call so a runtime toggle flip is reflected without a restart. Empty when every
   * owned namespace is enabled (the common case).
   */
  public List<String> disabledPrefixes() {
    List<String> out = new ArrayList<>();
    for (Entry e : entries.values()) {
      if (!e.isEnabled()) {
        out.addAll(e.prefixes);
      }
    }
    return out;
  }

  /**
   * Whether the given application-relative request path sits under a currently-disabled
   * owned namespace. {@code appPath} is the post-{@code /shepard/api}-strip path with a
   * leading slash (i.e. what {@code RequestPathHelper.applicationPath} returns).
   *
   * @return the matched disabled prefix, or empty when the path is not gated off.
   */
  public Optional<String> disabledPrefixFor(String appPath) {
    if (appPath == null || appPath.isEmpty()) {
      return Optional.empty();
    }
    for (Entry e : entries.values()) {
      if (e.isEnabled()) {
        continue;
      }
      for (String prefix : e.prefixes) {
        if (matchesPrefix(appPath, prefix)) {
          return Optional.of(prefix);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Structural prefix match: exact equality OR the prefix followed by {@code /}. Closes
   * the {@code prefix-foo} foot-gun — {@code /v2/aaszzz} does not match {@code /v2/aas}.
   * Public + static so the OAS filter (a different package) and tests can reuse it,
   * keeping the request-gate and OpenAPI-strip semantics identical.
   */
  public static boolean matchesPrefix(String path, String prefix) {
    if (path == null || prefix == null) {
      return false;
    }
    if (path.equals(prefix)) {
      return true;
    }
    if (!path.startsWith(prefix)) {
      return false;
    }
    return path.charAt(prefix.length()) == '/';
  }

  /** Test-only: clear all registrations. */
  void reset() {
    entries.clear();
  }
}
