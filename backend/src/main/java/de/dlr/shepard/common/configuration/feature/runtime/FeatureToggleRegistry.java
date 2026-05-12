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

@ApplicationScoped
public class FeatureToggleRegistry {

  public static class FeatureToggleEntry {

    private final String name;
    private final String description;
    private final Supplier<Boolean> reader;
    private final Consumer<Boolean> writer;

    public FeatureToggleEntry(String name, String description, Supplier<Boolean> reader, Consumer<Boolean> writer) {
      this.name = name;
      this.description = description;
      this.reader = reader;
      this.writer = writer;
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
      v -> { spatialOverride = v; spatialOverrideSet = true; }
    ));

    register(new FeatureToggleEntry(
      "versioning",
      "Enables collection versioning (shepard.features.versioning.enabled).",
      () -> versioningOverrideSet ? versioningOverride : VersioningFeatureToggle.isEnabled(),
      v -> { versioningOverride = v; versioningOverrideSet = true; }
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
