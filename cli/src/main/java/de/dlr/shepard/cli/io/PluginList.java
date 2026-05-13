package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * Wire-shape mirror of the backend's
 * {@code de.dlr.shepard.v2.admin.plugins.io.PluginListIO}.
 *
 * <p>The backend wraps the {@code plugins} array under an envelope
 * so future fields (pagination cursors, server-side filter echoes,
 * …) can land without breaking clients — matching the
 * {@link OntologyBundleList} idiom.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PluginList {

  private final List<PluginInfo> plugins;

  public PluginList(@JsonProperty("plugins") List<PluginInfo> plugins) {
    this.plugins = plugins == null ? Collections.emptyList() : plugins;
  }

  public List<PluginInfo> getPlugins() {
    return plugins;
  }
}
