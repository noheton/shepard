package de.dlr.shepard.plugin;

import java.util.Objects;

/**
 * PM1c — a single declared dependency between two
 * {@link PluginManifest}s.
 *
 * <p>A plugin's {@link PluginManifest#dependencies()} returns a list
 * of these. At discovery time the {@link PluginRegistry} validates
 * each entry:
 *
 * <ol>
 *   <li>{@link #pluginId()} must match the {@code id()} of another
 *       discovered manifest — otherwise the dependent plugin is marked
 *       {@link PluginState#FAILED} with
 *       {@code plugin.dependency.missing}.</li>
 *   <li>{@link #versionConstraint()} must accept the dependency's
 *       declared {@code version()} string — otherwise the dependent
 *       plugin is marked {@link PluginState#FAILED} with
 *       {@code plugin.dependency.version-mismatch}.</li>
 * </ol>
 *
 * <p>The dependency graph is then topologically sorted; cycles fail
 * every plugin on the cycle with {@code plugin.dependency.cycle}.
 *
 * <p>{@link #versionConstraint()} is a Maven-style semver range:
 *
 * <ul>
 *   <li>{@code "1.0.0"} — exact match (legacy Maven shorthand).</li>
 *   <li>{@code "[1.0,2.0)"} — at least 1.0, strictly less than 2.0.</li>
 *   <li>{@code "[1.5,)"} — at least 1.5, no upper bound.</li>
 *   <li>{@code "(,2.0]"} — anything up to 2.0 inclusive.</li>
 *   <li>{@code ""} or {@code null} — accept any version (rarely
 *       desirable; surfaces as a WARN).</li>
 * </ul>
 *
 * <p>The parser lives in {@link VersionRange}; both an exact-version
 * comparator (matching {@code N1c2}'s ontology-bundle precedent of
 * not pulling in {@code org.apache.maven:maven-artifact} just for one
 * dependency) and the range syntax above are supported.
 */
public record PluginDependency(String pluginId, String versionConstraint) {
  public PluginDependency {
    Objects.requireNonNull(pluginId, "pluginId");
    if (pluginId.isBlank()) {
      throw new IllegalArgumentException("pluginId must be non-blank");
    }
    // versionConstraint is intentionally allowed to be null / empty —
    // the registry surfaces a WARN on parse but admits the dependency.
  }
}
