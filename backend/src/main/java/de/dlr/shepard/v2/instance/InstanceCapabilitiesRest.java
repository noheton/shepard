package de.dlr.shepard.v2.instance;

import de.dlr.shepard.plugin.PluginEntry;
import de.dlr.shepard.plugin.PluginRegistry;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * INST2 — public read of per-instance capability flags (which plugins are
 * currently ENABLED).
 *
 * <p>Exposes a non-admin read surface so the frontend can gate
 * plugin-specific UI surfaces (e.g. the Helmholtz Unhide "Publishing"
 * panel on the Collection page) without requiring the caller to hold the
 * {@code instance-admin} role. The full admin surface — including
 * DISABLED + FAILED plugins and the PATCH toggle — stays on
 * {@code /v2/admin/plugins}.
 *
 * <p>Returns only the IDs of plugins whose effective runtime state is
 * {@code ENABLED} (runtime override wins; falls through to the
 * {@code shepard.plugins.<id>.enabled} deploy-time default). Plugins
 * that are DISABLED, FAILED, or DEGRADED are not listed.
 */
@Path("/v2/instance/capabilities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Instance identity (INST1)")
public class InstanceCapabilitiesRest {

  @Inject
  PluginRegistry registry;

  @GET
  @Operation(
    summary = "List the IDs of plugins that are currently ENABLED on this instance.",
    description = "Returns only ENABLED plugin IDs — a lightweight capability probe for the " +
    "frontend so it can gate plugin-specific UI surfaces (e.g. the Unhide Publishing panel). " +
    "Does not require the instance-admin role — any authenticated user may call it. " +
    "The full admin surface (all plugin states, PATCH toggle) lives at GET/PATCH /v2/admin/plugins."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current set of enabled plugin IDs.",
    content = @Content(schema = @Schema(implementation = InstanceCapabilitiesIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response getCapabilities() {
    List<PluginEntry> entries = registry.list();
    List<InstanceCapabilitiesIO.PluginInfo> plugins = new ArrayList<>(entries.size());
    for (PluginEntry entry : entries) {
      if (registry.isEnabled(entry.id())) {
        String title = (entry.title() == null || entry.title().isBlank()) ? entry.id() : entry.title();
        plugins.add(new InstanceCapabilitiesIO.PluginInfo(entry.id(), entry.version(), title));
      }
    }
    return Response.ok(new InstanceCapabilitiesIO(plugins)).build();
  }
}
