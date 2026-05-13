package de.dlr.shepard.plugin;

import jakarta.enterprise.inject.spi.BeanManager;

/**
 * PM1a — read-only context handed to a plugin's
 * {@link PluginManifest#onRegister} / {@link PluginManifest#onUnregister}
 * lifecycle hooks.
 *
 * <p>Phase 1 surface — kept deliberately small. The plugin can:
 * <ul>
 *   <li>Look up CDI beans the backend exposes
 *       (via {@link #beanManager()}).</li>
 *   <li>Read its own plugin id and version
 *       ({@link #pluginId()}, {@link #pluginVersion()}).</li>
 * </ul>
 *
 * <p>Future phases will extend this with explicit registries
 * (e.g. {@code PayloadKindRegistry}, a {@code MinterRegistry},
 * {@code AdapterRegistry}) per {@code aidocs/47 §2.5}. Until those
 * land, plugins can {@code beanManager().getBeans(...)} their way
 * to whatever core service they need.
 *
 * <p>Why a context object instead of static singletons: makes
 * unit-testing a plugin's lifecycle trivial — the test passes a
 * stub {@code PluginContext}, no @QuarkusTest required.
 */
public interface PluginContext {
  /**
   * The plugin's stable id (e.g. {@code "unhide"}). Same value the
   * manifest's {@link PluginManifest#id()} returns; passed through
   * for convenience so the manifest doesn't need to thread it
   * separately into its own helper methods.
   */
  String pluginId();

  /**
   * The plugin's version (e.g. {@code "1.0.0-SNAPSHOT"}). Same value
   * the manifest's {@link PluginManifest#version()} returns.
   */
  String pluginVersion();

  /**
   * The backend's CDI {@link BeanManager}. Plugins use this to look
   * up core services (e.g. {@code beanManager.getBeans(NeoConnector.class)}).
   *
   * <p>May be {@code null} in tests that don't boot Quarkus — plugins
   * should guard their lookups accordingly.
   */
  BeanManager beanManager();
}
