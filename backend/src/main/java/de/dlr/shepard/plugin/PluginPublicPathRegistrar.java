package de.dlr.shepard.plugin;

import de.dlr.shepard.common.filters.PublicEndpointRegistry;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * PM1g — gathers public-path declarations from all active plugins at
 * startup and registers them with {@link PublicEndpointRegistry}.
 *
 * <p>Observes {@link StartupEvent} at priority 2100 (Application + 100)
 * so it fires <em>after</em> {@link PluginRegistry#onStart}, which
 * observes {@link StartupEvent} at the default Application priority (2000).
 * A lower numeric priority value fires first in CDI; we therefore use 2100
 * (higher number) to guarantee the registry has completed plugin discovery
 * before we iterate over the results.
 *
 * <p>Only plugins in {@link PluginState#ENABLED} state have their path
 * declarations honoured — disabled or failed plugins don't contribute to
 * the public surface (matching the posture that their REST beans are inert
 * until the operator enables them).
 */
@ApplicationScoped
public class PluginPublicPathRegistrar {

  @Inject
  PluginRegistry pluginRegistry;

  void onStart(
    @Observes @jakarta.annotation.Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 100)
    StartupEvent event
  ) {
    registerPluginPublicPaths();
  }

  /**
   * Iterate over all enabled plugins and register their declared public
   * paths / prefixes into {@link PublicEndpointRegistry}. Package-private
   * so tests can invoke it directly after staging a custom registry state.
   */
  void registerPluginPublicPaths() {
    int pathCount = 0;
    int prefixCount = 0;
    for (PluginEntry entry : pluginRegistry.list()) {
      if (entry.state() != PluginState.ENABLED) {
        continue;
      }
      PluginManifest manifest = entry.manifest();
      try {
        for (String path : manifest.publicPaths()) {
          PublicEndpointRegistry.registerPluginPath(path);
          pathCount++;
          Log.debugf("PM1g: plugin '%s' registered public path: %s", entry.id(), path);
        }
      } catch (RuntimeException ex) {
        Log.warnf(ex, "PM1g: plugin '%s' publicPaths() threw — skipping", entry.id());
      }
      try {
        for (String prefix : manifest.publicPathPrefixes()) {
          PublicEndpointRegistry.registerPluginPathPrefix(prefix);
          prefixCount++;
          Log.debugf("PM1g: plugin '%s' registered public path prefix: %s", entry.id(), prefix);
        }
      } catch (RuntimeException ex) {
        Log.warnf(ex, "PM1g: plugin '%s' publicPathPrefixes() threw — skipping", entry.id());
      }
    }
    Log.infof(
      "PM1g: plugin public-path registration complete — %d path(s), %d prefix(es) registered",
      pathCount,
      prefixCount
    );
  }
}
