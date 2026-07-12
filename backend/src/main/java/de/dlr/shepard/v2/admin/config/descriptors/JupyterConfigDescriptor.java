package de.dlr.shepard.v2.admin.config.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.jupyter.entities.JupyterConfig;
import de.dlr.shepard.v2.admin.jupyter.io.JupyterConfigIO;
import de.dlr.shepard.v2.admin.jupyter.services.JupyterConfigService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * V2CONV-A4 — {@link ConfigDescriptor} for the JupyterHub config singleton,
 * exposed as {@code GET|PATCH /v2/admin/config/jupyter}. Replaces both the
 * bespoke {@code JupyterConfigPluginRest} (canonical
 * {@code /v2/admin/plugins/jupyter/config}) and its deprecated shim
 * {@code JupyterConfigRest} ({@code /v2/admin/jupyter/config}) — both deleted.
 * Delegates unchanged to {@link JupyterConfigService}.
 *
 * <p>Patchable fields: {@code enabled} (boolean), {@code hubUrl} (absolute
 * http(s) URL or null to clear to the deploy-time default). An explicit null on
 * the primitive {@code enabled} is treated as "leave alone".
 */
@ApplicationScoped
public class JupyterConfigDescriptor implements ConfigDescriptor<JupyterConfigIO> {

  /** RFC 7807 type URI for an invalid hub URL. */
  static final String PROBLEM_TYPE_INVALID_HUB_URL = "/problems/jupyter.config.invalid-hub-url";

  @Inject
  JupyterConfigService service;

  @Override
  public String featureName() {
    return "jupyter";
  }

  @Override
  public String description() {
    return "JupyterHub integration: master enable toggle and hub URL.";
  }

  @Override
  public boolean publicRead() {
    return true;
  }

  @Override
  public JupyterConfigIO currentShape() {
    return toIO(service.current());
  }

  @Override
  public JupyterConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    JupyterConfig current = service.current();

    boolean enabledTouched = patch.has("enabled");
    boolean hubUrlTouched = patch.has("hubUrl");

    // enabled: only a concrete true/false flips the master switch; absent or
    // explicit null leaves it alone (entity field is a primitive boolean).
    boolean effectiveEnabled;
    JsonNode enabledNode = patch.get("enabled");
    if (enabledTouched && enabledNode != null && !enabledNode.isNull()) {
      effectiveEnabled = enabledNode.asBoolean();
    } else {
      effectiveEnabled = current.isEnabled();
    }

    // hubUrl: touched with null/blank = clear (revert to default); value = replace.
    String effectiveHubUrl;
    if (hubUrlTouched) {
      String raw = textOrNull(patch.get("hubUrl"));
      effectiveHubUrl = raw != null && raw.isBlank() ? null : raw;
    } else {
      effectiveHubUrl = current.getHubUrl();
    }

    if (hubUrlTouched && effectiveHubUrl != null && !effectiveHubUrl.isBlank() && !isValidAbsoluteHttpUrl(effectiveHubUrl)) {
      Log.warnf("V2CONV-A4/jupyter: rejected PATCH — invalid hubUrl '%s' (must be absolute http(s) URL)", effectiveHubUrl);
      throw ConfigPatchException.badRequest(
        PROBLEM_TYPE_INVALID_HUB_URL,
        "Invalid hubUrl",
        "hubUrl must be a syntactically valid absolute http(s) URL " +
        "(e.g. 'https://hub.example.org'). Set to null to revert to the deploy-time " +
        "default (shepard.jupyter.hub-url)."
      );
    }

    JupyterConfig saved = service.patch(effectiveEnabled, effectiveHubUrl);
    return toIO(saved);
  }

  private JupyterConfigIO toIO(JupyterConfig cfg) {
    return JupyterConfigIO.from(cfg, service.getDefaultHubUrl());
  }

  /**
   * Accepts only absolute {@code http}/{@code https} URLs with a non-empty host.
   * Path/query/fragment tolerated for virtual-host-prefixed installs.
   */
  static boolean isValidAbsoluteHttpUrl(String candidate) {
    if (candidate == null || candidate.isBlank()) return false;
    try {
      URI uri = new URI(candidate);
      if (!uri.isAbsolute()) return false;
      String scheme = uri.getScheme();
      if (scheme == null) return false;
      String s = scheme.toLowerCase();
      if (!"http".equals(s) && !"https".equals(s)) return false;
      String host = uri.getHost();
      return host != null && !host.isBlank();
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static String textOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asText();
  }
}
