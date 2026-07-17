package de.dlr.shepard.v2.admin.config.spi;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * V2CONV-A4 — registry of {@link ConfigDescriptor} beans backing the generic
 * admin-config surface {@code /v2/admin/config/{feature}}.
 *
 * <p>Mirrors the A3b {@code FeatureToggleRegistry} shape: an
 * {@code @ApplicationScoped} CDI bean that builds an immutable index keyed by
 * {@link ConfigDescriptor#featureName()} on {@code @Observes StartupEvent}.
 * Every {@code ConfigDescriptor} CDI bean on the classpath is discovered via
 * CDI {@link Instance} — adding a new configurable feature is just adding a new
 * descriptor bean; no edit to this registry or to any REST class is needed.
 *
 * <p><b>Fail-soft</b> (per the repo's "registries are fail-soft" rule):
 * {@link #resolve(String)} returns {@link Optional#empty()} for an unknown
 * feature (the resource renders 404 problem-JSON) and never throws. A descriptor
 * with a duplicate or blank {@code featureName()} is skipped with a WARN rather
 * than aborting startup.
 */
@ApplicationScoped
public class ConfigRegistry {

  @Inject
  Instance<ConfigDescriptor<?>> descriptors;

  /** Insertion-ordered for a stable listing. */
  private final Map<String, ConfigDescriptor<?>> byFeature = new LinkedHashMap<>();

  void onStart(@Observes StartupEvent event) {
    register();
  }

  /** Build the index. Package-visible so tests can drive it without a real StartupEvent. */
  void register() {
    byFeature.clear();
    if (descriptors == null) {
      Log.warn("V2CONV-A4: no ConfigDescriptor beans injected — /v2/admin/config will be empty");
      return;
    }
    for (ConfigDescriptor<?> d : descriptors) {
      String name = d.featureName();
      if (name == null || name.isBlank()) {
        Log.warnf("V2CONV-A4: skipping ConfigDescriptor %s with null/blank featureName()", d.getClass().getName());
        continue;
      }
      ConfigDescriptor<?> prev = byFeature.putIfAbsent(name, d);
      if (prev != null) {
        Log.warnf(
          "V2CONV-A4: duplicate config feature '%s' — keeping %s, ignoring %s",
          name, prev.getClass().getName(), d.getClass().getName()
        );
        continue;
      }
      Log.debugf("V2CONV-A4: registered config feature '%s' (%s)", name, d.getClass().getSimpleName());
    }
    Log.infof("V2CONV-A4: ConfigRegistry holds %d config feature(s): %s", byFeature.size(), byFeature.keySet());
  }

  /**
   * Resolve a descriptor by its {@code {feature}} path segment.
   *
   * @param feature the feature key (e.g. {@code "semantic"})
   * @return the descriptor, or empty when none is registered under that key
   */
  public Optional<ConfigDescriptor<?>> resolve(String feature) {
    if (feature == null) return Optional.empty();
    return Optional.ofNullable(byFeature.get(feature));
  }

  /** All registered feature keys, in registration order. */
  public List<String> featureNames() {
    return List.copyOf(byFeature.keySet());
  }

  /** All registered descriptors, in registration order. */
  public List<ConfigDescriptor<?>> all() {
    return Collections.unmodifiableList(List.copyOf(byFeature.values()));
  }

  /** Count of registered features. O(1). */
  public int count() {
    return byFeature.size();
  }

  /**
   * Slice of registered descriptors, in registration order.
   * Allocates only the requested slice, not the full list.
   */
  public List<ConfigDescriptor<?>> list(long skip, int limit) {
    return byFeature.values().stream().skip(skip).limit(limit).toList();
  }
}
