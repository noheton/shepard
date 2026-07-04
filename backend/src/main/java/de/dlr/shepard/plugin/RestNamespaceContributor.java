package de.dlr.shepard.plugin;

import java.util.Set;

/**
 * V2CONV-A5 — marker an allowlisted host module additionally implements to declare
 * that it <em>owns</em> a top-level {@code /v2/<prefix>/...} REST path namespace.
 *
 * <p>Background. The {@code aidocs/platform/191-v2-surface-convergence.md §2}
 * convergence collapses most plugin surfaces into the payload-kind / view-shape
 * model so a plugin rarely needs a path prefix of its own. A small, deliberate
 * <strong>allowlist</strong> of modules legitimately cannot fit that model and so
 * own a namespace:
 *
 * <ul>
 *   <li><b>AAS</b> — {@code /v2/aas/shells/...} mirrors the external IDTA
 *       <em>Asset Administration Shell REST</em> standard, whose path shape is
 *       fixed by spec and cannot be re-expressed as a shepard payload kind.</li>
 *   <li><b>Jupyter</b> — {@code /v2/jupyter/*} is a sidecar
 *       integration/config/proxy surface, not a payload kind.</li>
 * </ul>
 *
 * <p>The allowlist is structural, not a string list: a module is on the allowlist
 * <em>iff</em> it implements this marker. The {@link RestNamespaceRegistry} gathers
 * every contributor and, crucially, knows how to ask "is the owner of this prefix
 * currently enabled?" — so the gate (404 + OpenAPI strip) is entirely data-driven.
 *
 * <h2>Two ways to contribute</h2>
 * <ol>
 *   <li><b>A {@link PluginManifest} implements this marker.</b> The registry resolves
 *       the owner's enabled-state via {@link PluginRegistry#isEnabled(String)} keyed
 *       on the manifest's {@link PluginManifest#id()}. This is the AAS shape.</li>
 *   <li><b>Core code registers a contributor bound to a feature toggle.</b> For an
 *       in-tree surface whose REST lives in core (so it has no {@code PluginManifest})
 *       but is still gated — e.g. Jupyter, gated by {@code :JupyterConfig} — core wires
 *       a {@link RestNamespaceRegistry#registerCoreContributor} entry whose enabled-state
 *       reads the feature's own runtime flag.</li>
 * </ol>
 *
 * <p>This composes with the PM1g {@code publicPaths()} / {@code publicPathPrefixes()}
 * marker family on {@link PluginManifest} — both are declarative path metadata a host
 * module ships, gathered at startup, never hard-coded in the consuming filter.
 *
 * @see RestNamespaceRegistry
 * @see de.dlr.shepard.common.filters.DisabledNamespaceRequestFilter
 * @see de.dlr.shepard.common.openapi.DisabledNamespaceOasFilter
 */
public interface RestNamespaceContributor {
  /**
   * The {@code /v2/...}-rooted path prefixes this module owns, each as an
   * application-relative path with a leading slash and <strong>no</strong> trailing
   * slash (e.g. {@code "/v2/aas"}, {@code "/v2/jupyter"}). Matching against an incoming
   * request path is structural: a prefix matches the bare path or the prefix followed
   * by {@code /} — so {@code /v2/aas} matches {@code /v2/aas/shells} but not
   * {@code /v2/aaszzz}.
   *
   * <p>Keep each prefix as narrow as the owned namespace — never return a prefix broad
   * enough to shadow core or another plugin's endpoints (e.g. {@code "/v2"}). The
   * registry logs and ignores blank entries and the over-broad {@code "/v2"} root.
   *
   * @return the owned prefixes; an empty set means "owns no namespace" (the marker is
   *     then a no-op, which is fine for a manifest that conditionally owns a prefix).
   */
  Set<String> ownedRestPathPrefixes();
}
