package de.dlr.shepard.v2.admin.config;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V2CONV-A4 — central registry for admin {@link ConfigDescriptor}s.
 *
 * <p>Each descriptor calls {@link #register(ConfigDescriptor)} from its
 * {@code @Observes StartupEvent} handler. The generic
 * {@code GET|PATCH /v2/admin/config/{feature}} endpoint then resolves
 * descriptors here by feature name at request time.
 *
 * <p>Fail-soft: a null or blank-named descriptor is logged as a warning and
 * silently dropped — consistent with the "registries are fail-soft" rule.
 */
@ApplicationScoped
public class ConfigRegistry {

  private final Map<String, ConfigDescriptor> descriptors = new ConcurrentHashMap<>();

  /**
   * Register a config descriptor. Idempotent — re-registering the same feature
   * name replaces the prior entry (useful for test resets).
   */
  public void register(ConfigDescriptor descriptor) {
    if (descriptor == null) {
      Log.warn("V2CONV-A4: ConfigRegistry.register() called with null descriptor — skipped");
      return;
    }
    String name = descriptor.featureName();
    if (name == null || name.isBlank()) {
      Log.warn("V2CONV-A4: ConfigRegistry.register() called with null/blank featureName — skipped");
      return;
    }
    ConfigDescriptor prior = descriptors.put(name, descriptor);
    if (prior == null) {
      Log.infof("V2CONV-A4: registered ConfigDescriptor for feature '%s'", name);
    } else {
      Log.debugf("V2CONV-A4: replaced ConfigDescriptor for feature '%s'", name);
    }
  }

  /**
   * Look up a descriptor by feature name.
   *
   * @param featureName the path segment from {@code /v2/admin/config/{feature}}
   * @return the descriptor wrapped in Optional, or empty if not registered
   */
  public Optional<ConfigDescriptor> find(String featureName) {
    if (featureName == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(descriptors.get(featureName));
  }

  /**
   * Returns a sorted list of all registered feature names — used in 404 error messages.
   */
  public List<String> featureNames() {
    List<String> names = new ArrayList<>(descriptors.keySet());
    names.sort(String::compareToIgnoreCase);
    return names;
  }
}
