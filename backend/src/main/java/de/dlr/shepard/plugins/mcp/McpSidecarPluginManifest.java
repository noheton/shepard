package de.dlr.shepard.plugins.mcp;

import de.dlr.shepard.plugin.PluginManifest;

/**
 * MCP-1 — sidecar manifest stub for {@code shepard-plugin-mcp}.
 *
 * <p>Registers {@code id=mcp} in the {@link de.dlr.shepard.plugin.PluginRegistry}
 * so it appears in {@code GET /v2/admin/plugins} and can be toggled at
 * runtime by an instance-admin, exactly like any other plugin.
 *
 * <p>Unlike in-JVM plugins the sidecar runs as a separate Docker container
 * ({@code shepard-mcp:8811}). This stub carries no CDI beans —
 * {@code onRegister} is the default no-op. The sidecar itself polls
 * {@code GET /v2/instance/capabilities} on startup and every 30 s to
 * learn its own enabled state and returns 503 to all tool calls when
 * disabled.
 *
 * <p>Default enable posture: {@code shepard.plugins.mcp.enabled=true}
 * (set in {@code application.properties}).  An admin can flip it
 * off at runtime via {@code PATCH /v2/admin/plugins/mcp} without
 * restarting the sidecar container.
 */
public class McpSidecarPluginManifest implements PluginManifest {

  @Override
  public String id() {
    return "mcp";
  }

  @Override
  public String version() {
    return "1.0.0-SNAPSHOT";
  }

  @Override
  public String shepardCompatibility() {
    return ">=5.2.0,<6";
  }

  @Override
  public String title() {
    return "Shepard MCP Server";
  }

  @Override
  public String description() {
    return "Model Context Protocol (SSE) sidecar — exposes Shepard to AI agents " +
      "via 15 MCP tools covering collections, DataObjects, provenance chains, " +
      "timeseries channels, files, and structured data. " +
      "Runs as the shepard-mcp Docker container; Caddy routes /mcp/* to it.";
  }

  @Override
  public String licence() {
    return "Apache-2.0";
  }
}
