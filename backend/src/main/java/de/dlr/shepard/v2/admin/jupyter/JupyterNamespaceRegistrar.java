package de.dlr.shepard.v2.admin.jupyter;

import de.dlr.shepard.plugin.RestNamespaceRegistry;
import de.dlr.shepard.v2.admin.jupyter.services.JupyterConfigService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import java.util.Set;

/**
 * V2CONV-A5 — registers the core-owned {@code /v2/jupyter} namespace with the
 * {@link RestNamespaceRegistry}, gated by the Jupyter feature's own runtime flag
 * ({@code :JupyterConfig.enabled}, deploy-time default {@code shepard.jupyter.enabled}).
 *
 * <p>Jupyter is on the V2CONV-A5 allowlist (sidecar integration/config/proxy surface, not
 * a payload kind) but its REST lives in core — so it has no {@link de.dlr.shepard.plugin.PluginManifest}
 * to carry a {@link de.dlr.shepard.plugin.RestNamespaceContributor} marker. This registrar is
 * the core-side equivalent: it declares the owned prefix and binds enabled-state to
 * {@link JupyterConfigService#effectiveEnabled()} (which itself is fail-soft, returning
 * {@code false} on a storage error — so an unreachable {@code :JupyterConfig} simply gates
 * the namespace off rather than 500-ing).
 *
 * <p>Priority APPLICATION+200 so it fires after the plugin-contributor pass
 * ({@code RestNamespaceRegistry.onStart}, APPLICATION+100) — ordering is not strictly
 * required (entries are keyed by distinct owner id) but keeps the registration log tidy.
 */
@ApplicationScoped
public class JupyterNamespaceRegistrar {

  static final String OWNER_ID = "jupyter";
  static final String OWNED_PREFIX = "/v2/jupyter";

  @Inject
  RestNamespaceRegistry namespaceRegistry;

  @Inject
  JupyterConfigService jupyterConfigService;

  void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION + 200) StartupEvent event) {
    namespaceRegistry.registerCoreContributor(
      OWNER_ID,
      Set.of(OWNED_PREFIX),
      jupyterConfigService::effectiveEnabled
    );
    Log.infof("V2CONV-A5: registered core namespace '%s' → %s (gated by :JupyterConfig.enabled)", OWNER_ID, OWNED_PREFIX);
  }
}
