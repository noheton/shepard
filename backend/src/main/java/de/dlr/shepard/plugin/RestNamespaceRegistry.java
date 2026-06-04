package de.dlr.shepard.plugin;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
 * <p>The registry has two namespace sources, both consulted at every call to
 * {@link #disabledPrefixes()} / {@link #disabledPrefixFor(String)}:
 *
 * <ol>
 *   <li><b>Plugin manifests implementing {@link RestNamespaceContributor}.</b>
 *       Queried live from {@link PluginRegistry#list()} on every call — no startup
 *       caching — so there is no dependency on CDI observer priority ordering.
 *       Enabled-state is read via {@link PluginRegistry#isEnabled(String)}.</li>
 *   <li><b>Core-registered contributors</b> bound to a feature toggle's own runtime
 *       flag via {@link #registerCoreContributor(String, Set, BooleanSupplier)} — the
 *       Jupyter shape, where the REST lives in core and is gated by
 *       {@code :JupyterConfig.enabled}.</li>
 * </ol>
 *
 * <p><strong>Fail-soft</strong> (matches the repo's "registries are fail-soft" rule):
 * if an enabled-state supplier or prefix declaration throws, the registry treats the
 * namespace as ENABLED (allow) and logs a WARN — a registry hiccup must never 404 a
 * working endpoint.
 *
 * <p>The filter logic never hard-codes "aas"/"jupyter" — the allowlist is entirely
 * data-driven: plugin contributors discovered via the {@link PluginRegistry} plus core
 * contributors registered at startup via {@link #registerCoreContributor}.
 */
@ApplicationScoped
public class RestNamespaceRegistry {

  /** The over-broad root a contributor must never claim — it would shadow all of v2. */
  static final String FORBIDDEN_ROOT = "/v2";

  /**
   * One core-owned-namespace declaration: an owner id, the prefixes it owns, and a way
   * to read whether the owner is enabled at call time. Package-private for tests.
   *
   * <p>Used only for core contributors (Jupyter etc. registered via
   * {@link #registerCoreContributor}). Plugin contributors are queried live from the
   * {@link PluginRegistry} instead of being pre-registered in this map.
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

  /** Core contributors only — keyed by owner id; insertion-ordered for stable iteration. */
  private final Map<String, Entry> coreEntries = new LinkedHashMap<>();

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
    coreEntries.put(ownerId, new Entry(ownerId, prefixes, enabled));
    Log.infof("V2CONV-A5: core surface '%s' owns REST namespace(s) %s (gated by feature toggle)", ownerId, prefixes);
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
   *
   * <p>Consults both core contributors (registered via {@link #registerCoreContributor})
   * and plugin contributors (queried live from the {@link PluginRegistry}) on every call.
   */
  public List<String> disabledPrefixes() {
    List<String> out = new ArrayList<>();
    // Core contributors
    for (Entry e : coreEntries.values()) {
      if (!e.isEnabled()) {
        out.addAll(e.prefixes);
      }
    }
    // Plugin contributors — queried live to avoid startup-ordering issues
    for (PluginEntry pe : pluginRegistry.list()) {
      if (!(pe.manifest() instanceof RestNamespaceContributor c)) {
        continue;
      }
      boolean enabled;
      try {
        enabled = pluginRegistry.isEnabled(pe.id());
      } catch (RuntimeException ex) {
        Log.warnf(ex, "V2CONV-A5: isEnabled('%s') threw — treating as ENABLED (fail-soft allow)", pe.id());
        continue;
      }
      if (enabled) {
        continue;
      }
      try {
        out.addAll(sanitise(pe.id(), c.ownedRestPathPrefixes()));
      } catch (RuntimeException ex) {
        Log.warnf(ex, "V2CONV-A5: plugin '%s' ownedRestPathPrefixes() threw — treating as enabled (fail-soft)", pe.id());
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
    // Core contributors
    for (Entry e : coreEntries.values()) {
      if (e.isEnabled()) {
        continue;
      }
      for (String prefix : e.prefixes) {
        if (matchesPrefix(appPath, prefix)) {
          return Optional.of(prefix);
        }
      }
    }
    // Plugin contributors — queried live
    for (PluginEntry pe : pluginRegistry.list()) {
      if (!(pe.manifest() instanceof RestNamespaceContributor c)) {
        continue;
      }
      boolean enabled;
      try {
        enabled = pluginRegistry.isEnabled(pe.id());
      } catch (RuntimeException ex) {
        Log.warnf(ex, "V2CONV-A5: isEnabled('%s') threw — treating as ENABLED (fail-soft allow)", pe.id());
        continue;
      }
      if (enabled) {
        continue;
      }
      Set<String> prefixes;
      try {
        prefixes = sanitise(pe.id(), c.ownedRestPathPrefixes());
      } catch (RuntimeException ex) {
        Log.warnf(ex, "V2CONV-A5: plugin '%s' ownedRestPathPrefixes() threw — treating as enabled (fail-soft)", pe.id());
        continue;
      }
      for (String prefix : prefixes) {
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

  /** Test-only: clear all core registrations. Plugin registrations are always live from PluginRegistry. */
  void reset() {
    coreEntries.clear();
  }
}
