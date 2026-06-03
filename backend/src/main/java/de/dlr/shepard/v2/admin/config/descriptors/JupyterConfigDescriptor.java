package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.v2.admin.config.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.ConfigRegistry;
import de.dlr.shepard.v2.admin.config.ConfigValidationException;
import de.dlr.shepard.v2.admin.jupyter.entities.JupyterConfig;
import de.dlr.shepard.v2.admin.jupyter.io.JupyterConfigIO;
import de.dlr.shepard.v2.admin.jupyter.services.JupyterConfigService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * V2CONV-A4 — {@link ConfigDescriptor} for the Jupyter config singleton.
 *
 * <p>Registers under feature name {@code "jupyter"} so
 * {@code GET|PATCH /v2/admin/config/jupyter} dispatches here.
 * Mirrors the logic in {@code JupyterConfigPluginRest}.
 */
@ApplicationScoped
public class JupyterConfigDescriptor implements ConfigDescriptor {

  static final String FEATURE = "jupyter";

  @Inject
  JupyterConfigService service;

  void onStart(@Observes StartupEvent event, ConfigRegistry registry) {
    registry.register(this);
  }

  @Override
  public String featureName() {
    return FEATURE;
  }

  @Override
  public Object read() {
    return JupyterConfigIO.from(service.current(), service.getDefaultHubUrl());
  }

  @Override
  public Object patch(JsonNode node) throws ConfigValidationException {
    JupyterConfig current = service.current();
    boolean effectiveEnabled = current.isEnabled();
    String effectiveHubUrl = current.getHubUrl();

    if (node != null && node.has("enabled")) {
      JsonNode v = node.get("enabled");
      if (!v.isNull()) {
        effectiveEnabled = v.asBoolean();
      }
      // null for a boolean = leave alone (no sensible "clear" for a required bool flag)
    }

    if (node != null && node.has("hubUrl")) {
      JsonNode v = node.get("hubUrl");
      effectiveHubUrl = v.isNull() ? null : v.asText();
    }

    JupyterConfig saved = service.patch(effectiveEnabled, effectiveHubUrl);
    return JupyterConfigIO.from(saved, service.getDefaultHubUrl());
  }
}
