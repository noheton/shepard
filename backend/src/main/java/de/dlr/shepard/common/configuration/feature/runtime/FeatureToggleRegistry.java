package de.dlr.shepard.common.configuration.feature.runtime;

import de.dlr.shepard.common.configuration.feature.toggles.SpatialDataFeatureToggle;
import de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class FeatureToggleRegistry {

  public static class FeatureToggleEntry {

    private final String name;
    private final String description;
    private final Supplier<Boolean> reader;
    private final Consumer<Boolean> writer;

    /**
     * DX7 — the MicroProfile Config property key that seeds the initial
     * value of this toggle (e.g.
     * {@code "shepard.infrastructure.spatial.enabled"}). Used by
     * {@link #getSource()} to distinguish {@code "config"} (operator
     * provided a value for this key) from {@code "default"} (the key is
     * absent; the toggle runs on its hardcoded default).
     */
    private final String propertyKey;

    /**
     * DX7 — set to {@code true} the first time {@link #setEnabled(boolean)}
     * is called after startup seeding, marking that the in-process value
     * was overridden via the PATCH admin endpoint rather than read from
     * config / the hardcoded default.
     */
    private volatile boolean overriddenAtRuntime = false;

    public FeatureToggleEntry(String name, String description, Supplier<Boolean> reader, Consumer<Boolean> writer) {
      this(name, description, reader, writer, null);
    }

    public FeatureToggleEntry(
      String name,
      String description,
      Supplier<Boolean> reader,
      Consumer<Boolean> writer,
      String propertyKey
    ) {
      this.name = name;
      this.description = description;
      this.reader = reader;
      this.writer = writer;
      this.propertyKey = propertyKey;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public boolean isEnabled() {
      return reader.get();
    }

    public void setEnabled(boolean value) {
      writer.accept(value);
      overriddenAtRuntime = true;
    }

    /**
     * DX7 — returns the origin of the toggle's current value.
     *
     * <ul>
     *   <li>{@code "runtime"} — value was set via the PATCH admin endpoint
     *       during this JVM process's lifetime; will revert on restart.
     *   <li>{@code "config"} — no runtime override; value comes from the
     *       MicroProfile Config property ({@code application.properties} /
     *       env-var / system property) for {@link #propertyKey}.
     *   <li>{@code "default"} — no runtime override and the config property
     *       is absent; the toggle runs on its hardcoded default value.
     * </ul>
     */
    public String getSource() {
      if (overriddenAtRuntime) return "runtime";
      if (propertyKey != null) {
        boolean configPresent = ConfigProvider.getConfig()
          .getOptionalValue(propertyKey, Boolean.class)
          .isPresent();
        return configPresent ? "config" : "default";
      }
      return "default";
    }
  }

  private final Map<String, FeatureToggleEntry> entries = new LinkedHashMap<>();

  private volatile boolean spatialOverride;
  private boolean spatialOverrideSet = false;
  private volatile boolean versioningOverride;
  private boolean versioningOverrideSet = false;

  void onStart(@Observes StartupEvent event) {
    spatialOverride = SpatialDataFeatureToggle.isActive();
    spatialOverrideSet = true;
    versioningOverride = VersioningFeatureToggle.isEnabled();
    versioningOverrideSet = true;

    register(new FeatureToggleEntry(
      "spatial-data",
      "Enables PostGIS spatial data containers and references (shepard.infrastructure.spatial.enabled).",
      () -> spatialOverrideSet ? spatialOverride : SpatialDataFeatureToggle.isActive(),
      v -> { spatialOverride = v; spatialOverrideSet = true; },
      "shepard.infrastructure.spatial.enabled"
    ));

    register(new FeatureToggleEntry(
      "versioning",
      "Enables collection versioning (shepard.features.versioning.enabled).",
      () -> versioningOverrideSet ? versioningOverride : VersioningFeatureToggle.isEnabled(),
      v -> { versioningOverride = v; versioningOverrideSet = true; },
      "shepard.features.versioning.enabled"
    ));
  }

  private void register(FeatureToggleEntry entry) {
    entries.put(entry.getName(), entry);
  }

  public List<FeatureToggleEntry> list() {
    return Collections.unmodifiableList(List.copyOf(entries.values()));
  }

  public Optional<FeatureToggleEntry> get(String name) {
    return Optional.ofNullable(entries.get(name));
  }

  public boolean set(String name, boolean value) {
    FeatureToggleEntry entry = entries.get(name);
    if (entry == null) {
      return false;
    }
    entry.setEnabled(value);
    return true;
  }
}
