package de.dlr.shepard.plugin;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PM1a — registry entry for a single discovered plugin.
 *
 * <p>One {@code PluginEntry} per {@link PluginManifest} the
 * {@link PluginRegistry} loads. Carries the live manifest reference
 * (used to invoke lifecycle hooks), the JAR path it came from (for
 * operator-readable {@code GET /v2/admin/plugins} output), and the
 * mutable lifecycle {@link PluginState} + failure cause if any.
 *
 * <p>PM1b adds {@code registeredAt} — the wall-clock instant the
 * registry first observed the plugin. Used by
 * {@code GET /v2/admin/plugins} so an operator can see plugin
 * discovery order chronologically (matching the startup log line).
 *
 * <p>Mutability: {@code state} / {@code failureMessage} flip as the
 * registry transitions the plugin through its lifecycle; everything
 * else is immutable post-construction.
 */
public final class PluginEntry {

  private final PluginManifest manifest;
  private final Path jarPath;
  private final Instant registeredAt;
  private volatile PluginState state;
  private volatile String failureMessage;

  /**
   * @param manifest the loaded {@link PluginManifest} — non-null.
   * @param jarPath  the JAR the manifest was loaded from. May be
   *                 {@code null} for build-classpath plugins (the
   *                 ADR-0024 Phase 1 shape) so a plugin discovered
   *                 without a backing JAR still gets a registry row.
   */
  public PluginEntry(PluginManifest manifest, Path jarPath) {
    this(manifest, jarPath, Instant.now());
  }

  /**
   * Test-friendly constructor that lets the caller pin
   * {@code registeredAt} — used by the admin-REST unit tests to keep
   * timestamps deterministic across assertions.
   */
  public PluginEntry(PluginManifest manifest, Path jarPath, Instant registeredAt) {
    this.manifest = Objects.requireNonNull(manifest, "manifest");
    this.jarPath = jarPath;
    this.registeredAt = registeredAt == null ? Instant.now() : registeredAt;
    this.state = PluginState.DISCOVERED;
    this.failureMessage = null;
  }

  public PluginManifest manifest() {
    return manifest;
  }

  public String id() {
    return manifest.id();
  }

  public String version() {
    return manifest.version();
  }

  public String shepardCompatibility() {
    return manifest.shepardCompatibility();
  }

  /**
   * PM1c — human-readable display name, projected from
   * {@link PluginManifest#title()}. May be empty / equal to
   * {@link #id()} if the plugin didn't override the default.
   */
  public String title() {
    return manifest.title();
  }

  /**
   * PM1c — operator-facing description, projected from
   * {@link PluginManifest#description()}. May be empty.
   */
  public String description() {
    return manifest.description();
  }

  /**
   * PM1c — homepage URL, projected from
   * {@link PluginManifest#homepageUrl()}. May be empty.
   */
  public Optional<URI> homepageUrl() {
    return manifest.homepageUrl();
  }

  /**
   * PM1c — repository URL, projected from
   * {@link PluginManifest#repositoryUrl()}. May be empty.
   */
  public Optional<URI> repositoryUrl() {
    return manifest.repositoryUrl();
  }

  /**
   * PM1c — SPDX licence id, projected from
   * {@link PluginManifest#licence()}. May be empty.
   */
  public String licence() {
    return manifest.licence();
  }

  /**
   * PM1c — declared plugin dependencies, projected from
   * {@link PluginManifest#dependencies()}. Empty list if the plugin
   * declares none.
   */
  public List<PluginDependency> dependencies() {
    return manifest.dependencies();
  }

  public Path jarPath() {
    return jarPath;
  }

  /**
   * Wall-clock instant when this entry was constructed — i.e. when
   * the registry first observed the plugin. Surfaced in the admin
   * REST so an operator sees discovery order chronologically.
   */
  public Instant registeredAt() {
    return registeredAt;
  }

  public PluginState state() {
    return state;
  }

  public String failureMessage() {
    return failureMessage;
  }

  /** Used by {@link PluginRegistry} to transition the lifecycle. */
  void markEnabled() {
    this.state = PluginState.ENABLED;
    this.failureMessage = null;
  }

  /** Used by {@link PluginRegistry} to transition the lifecycle. */
  void markDisabled() {
    this.state = PluginState.DISABLED;
    this.failureMessage = null;
  }

  /** Used by {@link PluginRegistry} when {@code onRegister} throws. */
  void markFailed(String message) {
    this.state = PluginState.FAILED;
    this.failureMessage = message;
  }

  /**
   * PM1b2 — used by {@link PluginRegistry} when a JAR-walk plugin's
   * lifecycle hooks ran but its CDI beans weren't picked up by
   * Quarkus's build-time scanner (no {@code with-plugins} Maven
   * dependency declaration). The plugin is loaded but its REST
   * resources / beans are inert. Operators see the row with this
   * state in {@code GET /v2/admin/plugins} alongside a message
   * pointing at the operator runbook.
   */
  void markDegraded(String message) {
    this.state = PluginState.DEGRADED;
    this.failureMessage = message;
  }
}
