package de.dlr.shepard.plugins.v1compat.services;

import de.dlr.shepard.plugins.v1compat.daos.LegacyV1ConfigDAO;
import de.dlr.shepard.plugins.v1compat.entities.LegacyV1Config;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * V1COMPAT.0 — service layer for the {@code :LegacyV1Config}
 * singleton (per {@code aidocs/platform/103a §2 rows 1 + 3}).
 *
 * <p>Responsibilities:
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes the Quarkus {@code StartupEvent}; if no
 *       {@code :LegacyV1Config} node exists yet, one is minted from
 *       the {@code shepard.legacy.v1.enabled} install-time default
 *       (default {@code true} — the v1 sunset philosophy is
 *       "default-on, operator decides when to flip"). The V63
 *       Cypher migration also seeds; the StartupEvent hook is the
 *       defence-in-depth path.</li>
 *   <li><b>Hot-path read.</b> {@link #isEnabled()} returns the cached
 *       enabled state without hitting Neo4j on the hot v1-request
 *       path. The cache TTL is 5 s — short enough that an admin's
 *       PATCH propagates "feels-instant" to callers, long enough
 *       that the {@code LegacyV1GateFilter} never blocks on a DB
 *       round-trip for the steady-state case.</li>
 *   <li><b>Get-or-seed read for admin REST.</b> {@link #current()}
 *       returns the singleton (seeding if absent — defence against
 *       a fresh DB that somehow missed the migration + startup
 *       hook).</li>
 *   <li><b>Merge-patch.</b> {@link #patch} applies the runtime-
 *       mutable subset of the config — Phase 1 minimal shape:
 *       just {@code enabled}. After a successful patch the in-
 *       process cache is invalidated so the next read picks up
 *       the new value immediately (regardless of TTL).</li>
 * </ol>
 *
 * <p>Precedence: runtime row value wins; deploy-time
 * {@code shepard.legacy.v1.enabled} is the install-time default
 * that seeds the singleton on first start. Per CLAUDE.md
 * "Always: surface operator knobs in the admin config".
 */
@ApplicationScoped
public class LegacyV1ConfigService {

  /**
   * Cache TTL (millis) for the hot-path {@link #isEnabled()} read.
   * 5 s is small enough that an admin's PATCH propagates within a
   * single ad-blink to in-flight callers, but large enough that
   * steady-state traffic never queues on a DB round-trip.
   */
  static final long CACHE_TTL_MILLIS = 5_000L;

  @Inject
  LegacyV1ConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.legacy.v1.enabled", defaultValue = "true")
  boolean installDefaultEnabled;

  /**
   * Tiny in-process cache for the hot-path enabled read. Tuple of
   * (enabled, expiresAtMillis). Replaced atomically on every
   * cache-miss read or after a PATCH.
   */
  private final AtomicReference<CachedState> cache = new AtomicReference<>(null);

  /** Lazy time-source — overridable in tests via the test-seam ctor. */
  private final ClockSource clock;

  /** Production no-arg ctor for CDI. */
  public LegacyV1ConfigService() {
    this.clock = System::currentTimeMillis;
  }

  /** Test-seam ctor — inject DAO + a fake clock + the deploy default. */
  public LegacyV1ConfigService(
    LegacyV1ConfigDAO dao,
    RequestContextController requestContextController,
    boolean installDefaultEnabled,
    ClockSource clock
  ) {
    this.dao = dao;
    this.requestContextController = requestContextController;
    this.installDefaultEnabled = installDefaultEnabled;
    this.clock = clock;
  }

  /**
   * Seed the singleton on first startup. Idempotent — re-running
   * sees the existing row and returns. Mirrors {@code UnhideConfigService}
   * exactly; same fail-soft posture (a seed failure logs a WARN but
   * does not block startup — the {@link #isEnabled()} read falls
   * back to the deploy-time default).
   */
  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      Log.warnf(
        e,
        "V1COMPAT.0: could not seed :LegacyV1Config on startup; admin actions will retry on first read"
      );
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  /**
   * Seed the singleton if it doesn't exist yet. {@code synchronized}
   * across the inspect+create so two startup hooks racing on a fresh
   * DB don't produce two rows (the V63 Cypher migration's MERGE is
   * the primary seed path; this is the JVM-layer fallback).
   *
   * @return the freshly-seeded or pre-existing {@link LegacyV1Config}.
   */
  public synchronized LegacyV1Config seedIfNeeded() {
    LegacyV1Config existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf(
        "V1COMPAT.0: :LegacyV1Config already present (appId=%s, enabled=%s)",
        existing.getAppId(),
        existing.isEnabled()
      );
      return existing;
    }
    LegacyV1Config seed = new LegacyV1Config();
    seed.setEnabled(installDefaultEnabled);
    long now = clock.nowMillis();
    seed.setCreatedAt(now);
    seed.setUpdatedAt(now);
    LegacyV1Config saved = dao.createOrUpdate(seed);
    Log.infof(
      "V1COMPAT.0: seeded :LegacyV1Config from deploy-time default (enabled=%b, appId=%s).",
      saved.isEnabled(),
      saved.getAppId()
    );
    invalidateCache();
    return saved;
  }

  /**
   * Hot-path read used by {@code LegacyV1GateFilter}. Reads through
   * the in-process cache (5 s TTL). On cache miss, loads the
   * singleton (seeding if absent); on persistent DB failure, returns
   * {@code true} (fail-open — the v1 surface stays available rather
   * than 410-storming legitimate callers because Neo4j is down).
   */
  public boolean isEnabled() {
    CachedState cached = cache.get();
    long now = clock.nowMillis();
    if (cached != null && cached.expiresAtMillis > now) {
      return cached.enabled;
    }
    return refreshCache(now);
  }

  /**
   * Force-refresh the cache. Mainly for tests + the post-PATCH
   * invalidation path; also called on cache miss by
   * {@link #isEnabled()}.
   */
  public boolean refreshCache() {
    return refreshCache(clock.nowMillis());
  }

  private boolean refreshCache(long now) {
    boolean activated = false;
    try {
      activated = requestContextController.activate();
    } catch (RuntimeException ex) {
      // RequestContextController unavailable (e.g. in unit-test
      // wiring) — fall through; the DAO call below will surface a
      // clearer failure if it actually needs the request scope.
      Log.debugf("V1COMPAT.0: RequestContextController.activate failed (%s); proceeding without scope.", ex.getMessage());
    }
    try {
      LegacyV1Config row = dao.findSingleton();
      boolean enabled = row == null ? installDefaultEnabled : row.isEnabled();
      cache.set(new CachedState(enabled, now + CACHE_TTL_MILLIS));
      return enabled;
    } catch (RuntimeException ex) {
      // Fail-open: v1 stays available when the DB is down — better a
      // few extra v1 hits than a 410 storm during a Neo4j hiccup.
      Log.warnf(ex, "V1COMPAT.0: failed to read :LegacyV1Config; falling back to deploy-time default %b", installDefaultEnabled);
      cache.set(new CachedState(installDefaultEnabled, now + CACHE_TTL_MILLIS));
      return installDefaultEnabled;
    } finally {
      if (activated) {
        try {
          requestContextController.deactivate();
        } catch (RuntimeException ignored) {
          // best-effort cleanup
        }
      }
    }
  }

  /**
   * Admin-facing read — bypasses the hot-path cache so the response
   * always reflects the database. Seeds on demand. Use this from
   * the admin REST GET / PATCH; use {@link #isEnabled()} from the
   * gate filter.
   */
  public LegacyV1Config current() {
    LegacyV1Config row = dao.findSingleton();
    if (row != null) return row;
    return seedIfNeeded();
  }

  /**
   * Apply a Phase 1 patch — currently just the {@code enabled} flag.
   * Returns the post-patch row. Invalidates the hot-path cache so
   * the next {@link #isEnabled()} call picks up the new value
   * immediately (regardless of TTL).
   *
   * @param enabled  new value for the master toggle
   * @param actor    username of the admin doing the patch (nullable)
   * @return the post-patch singleton
   */
  public synchronized LegacyV1Config setEnabled(boolean enabled, String actor) {
    LegacyV1Config row = current();
    if (row.isEnabled() != enabled) {
      row.setEnabled(enabled);
      row.setUpdatedAt(clock.nowMillis());
      row.setUpdatedBy(actor);
      row = dao.createOrUpdate(row);
      Log.infof(
        "V1COMPAT.0: :LegacyV1Config.enabled flipped to %b by %s (appId=%s).",
        enabled,
        actor == null ? "<unknown>" : actor,
        row.getAppId()
      );
      invalidateCache();
    } else {
      Log.debugf(
        "V1COMPAT.0: :LegacyV1Config.enabled already %b — no-op patch from %s.",
        enabled,
        actor == null ? "<unknown>" : actor
      );
    }
    return row;
  }

  /** Invalidate the hot-path cache; next read triggers a DB refresh. */
  public void invalidateCache() {
    cache.set(null);
  }

  // ──────────────────────────────────────────────────────────────────────
  //  Tiny test seams
  // ──────────────────────────────────────────────────────────────────────

  /** Trivial clock-source seam so tests can simulate cache-TTL expiry. */
  @FunctionalInterface
  public interface ClockSource {
    long nowMillis();
  }

  /** Cache tuple — enabled value + monotonic expiry. */
  private static final class CachedState {

    final boolean enabled;
    final long expiresAtMillis;

    CachedState(boolean enabled, long expiresAtMillis) {
      this.enabled = enabled;
      this.expiresAtMillis = expiresAtMillis;
    }
  }
}
