package de.dlr.shepard.spi.transcode;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * VID-FFMPEG-TRANSCODE-2026-06-29 — CDI registry for
 * {@link TranscodingProvider} implementations.
 *
 * <p>Mirrors the {@link de.dlr.shepard.spi.analytics.AnalyticsRegistry}
 * idiom: an {@code @ApplicationScoped} bean, a {@code StartupEvent} observer
 * that resolves once, duplicate-id detection that keeps the first
 * registration and logs a WARN.
 *
 * <p>Per the {@code CLAUDE.md} "Always: registries are fail-soft" rule, a
 * caller resolving an unknown / unconfigured provider id gets
 * {@link Optional#empty()} back — never an exception. The transcode is
 * a secondary effect; "no provider configured" must degrade to "skip
 * transcoding, leave the original bytes addressable" rather than fail
 * the upload.
 *
 * <p>Per the {@code CLAUDE.md} "Always: resolve capabilities through
 * slots, not class names" rule, callers ask {@link #active()} for the
 * currently-selected provider rather than injecting a specific
 * implementation. The {@code shepard.plugins.video.transcoder} config
 * key picks which slot is active; the deploy-time default is
 * {@code "ffmpeg-local"}.
 */
@ApplicationScoped
public class TranscodingProviderRegistry {

  /** Default provider id — in-container ffmpeg shelled out via ProcessBuilder. */
  public static final String DEFAULT_PROVIDER_ID = "ffmpeg-local";

  @Inject
  Instance<TranscodingProvider> providers;

  @ConfigProperty(name = "shepard.plugins.video.transcoder", defaultValue = DEFAULT_PROVIDER_ID)
  String activeProviderId;

  private volatile Map<String, TranscodingProvider> byId = Map.of();

  /** Constructor for CDI. */
  public TranscodingProviderRegistry() {}

  /** Visible for testing. */
  TranscodingProviderRegistry(Instance<TranscodingProvider> providers, String activeProviderId) {
    this.providers = providers;
    this.activeProviderId = activeProviderId;
    resolve();
  }

  /** Quarkus startup hook — indexes the discovered beans once. */
  void onStartup(@Observes StartupEvent ev) {
    resolve();
  }

  /** Idempotent resolve — visible for tests. */
  void resolve() {
    Map<String, TranscodingProvider> map = new LinkedHashMap<>();
    if (providers != null) {
      for (TranscodingProvider p : providers) {
        if (p == null) continue;
        String id = p.id();
        if (id == null || id.isBlank()) {
          Log.warnf(
            "TranscodingProviderRegistry: skipping %s — id() returned null/blank",
            p.getClass().getName()
          );
          continue;
        }
        TranscodingProvider prior = map.putIfAbsent(id, p);
        if (prior != null) {
          Log.warnf(
            "TranscodingProviderRegistry: duplicate provider id '%s' — keeping %s, ignoring %s",
            id,
            prior.getClass().getName(),
            p.getClass().getName()
          );
        }
      }
    }
    this.byId = Map.copyOf(map);

    String available = byId.isEmpty() ? "<none>" : String.join(", ", byId.keySet());
    Log.infof(
      "TranscodingProviderRegistry: discovered %d provider(s): [%s]; active='%s'",
      byId.size(),
      available,
      activeProviderId
    );
  }

  /**
   * @param id provider id; null or blank resolves to
   *           {@link #DEFAULT_PROVIDER_ID}
   * @return the matching provider, or {@link Optional#empty()} when no
   *         provider with that id is registered (fail-soft per
   *         CLAUDE.md)
   */
  public Optional<TranscodingProvider> get(String id) {
    String key = (id == null || id.isBlank()) ? DEFAULT_PROVIDER_ID : id;
    return Optional.ofNullable(byId.get(key));
  }

  /**
   * Return the currently-active provider (selected by the
   * {@code shepard.plugins.video.transcoder} config key).
   *
   * @return the active provider, or {@link Optional#empty()} when the
   *         configured slot has no registered provider — the caller
   *         logs a WARN and skips transcoding (fail-soft)
   */
  public Optional<TranscodingProvider> active() {
    return get(activeProviderId);
  }

  /** @return the id of the currently-active provider slot (never null/blank). */
  public String activeId() {
    return activeProviderId;
  }

  /**
   * @return immutable view of registered providers, keyed by
   *         {@link TranscodingProvider#id()}. Diagnostic surface for
   *         admin REST; the dispatcher's path is {@link #active()}.
   */
  public Map<String, TranscodingProvider> all() {
    return byId;
  }
}
