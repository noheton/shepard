package de.dlr.shepard.auth.permission.services;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class PermissionsCacheWarmer {

  @Inject
  PermissionsService permissionsService;

  @Inject
  MostUsedEntityProvider provider;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.permissions.cache.warm.enabled", defaultValue = "false")
  boolean enabled;

  @ConfigProperty(name = "shepard.permissions.cache.warm.max-entries", defaultValue = "500")
  int maxEntries;

  private final AtomicBoolean started = new AtomicBoolean(false);
  private volatile CompletableFuture<Void> warmingFuture = CompletableFuture.completedFuture(null);

  void onStart(@Observes StartupEvent event) {
    if (!enabled) {
      Log.debug("Permissions cache warming disabled");
      return;
    }
    if (!started.compareAndSet(false, true)) {
      return;
    }
    // Off the startup thread: warming is best-effort and must never block readiness.
    var future = new CompletableFuture<Void>();
    warmingFuture = future;
    Thread.ofVirtual()
      .name("permissions-cache-warmer")
      .start(() -> {
        try {
          warm();
        } finally {
          future.complete(null);
        }
      });
  }

  CompletableFuture<Void> warmingFuture() {
    return warmingFuture;
  }

  void warm() {
    int cap = Math.max(0, maxEntries);
    Log.infof("Warming permissions cache (max %d entries)", cap);
    List<MostUsedEntityProvider.EntityAccessTriple> triples;
    try {
      triples = provider.findMostUsedEntities(cap);
    } catch (Exception e) {
      Log.warnf(e, "Permissions cache warming aborted: provider failed");
      return;
    }
    if (triples == null || triples.isEmpty()) {
      Log.info("Permissions cache warming: no entries to load");
      return;
    }
    boolean activated = false;
    try {
      activated = requestContextController.activate();
    } catch (Exception e) {
      Log.debugf(e, "Could not activate request context for cache warming");
    }
    int loaded = 0;
    int failed = 0;
    try {
      for (var t : triples) {
        try {
          permissionsService.isAccessTypeAllowedForUser(t.entityId(), t.accessType(), t.userSub());
          loaded++;
        } catch (Exception e) {
          failed++;
          Log.debugf(e, "Skipping cache warm for entity %d / %s / %s", t.entityId(), t.accessType(), t.userSub());
        }
      }
    } finally {
      if (activated) {
        try {
          requestContextController.deactivate();
        } catch (Exception e) {
          Log.debugf(e, "Failed to deactivate request context after cache warming");
        }
      }
    }
    Log.infof("Permissions cache warming finished: %d loaded, %d skipped", loaded, failed);
  }
}
