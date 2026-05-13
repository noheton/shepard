package de.dlr.shepard.v2.admin.plugins;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugin.PluginEntry;
import de.dlr.shepard.plugin.PluginRegistry;
import de.dlr.shepard.v2.admin.plugins.io.PluginEntryIO;
import de.dlr.shepard.v2.admin.plugins.io.PluginListIO;
import de.dlr.shepard.v2.admin.plugins.io.PluginPatchIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * PM1b — admin REST surface for the PM1a {@link PluginRegistry}.
 *
 * <p>Lives in core (not in a plugin) because it <em>is</em> the
 * runtime SPI-registry surface — one of the CLAUDE.md
 * "plugin-first" exceptions ("the runtime SPI registry itself").
 *
 * <p>Path: {@code /v2/admin/plugins} — the fork's development
 * surface per the API-version policy ({@code /shepard/api/...} is
 * frozen at upstream 5.2.0; everything additive lives under
 * {@code /v2/}). Endpoints:
 *
 * <ul>
 *   <li>{@code GET /v2/admin/plugins} — list every discovered plugin
 *       (including {@code DISABLED} + {@code FAILED}) so an operator
 *       sees the full state space, not just live ones.</li>
 *   <li>{@code PATCH /v2/admin/plugins/{id}} — RFC 7396 merge-patch
 *       with body {@code {"enabled": true|false}}. Returns the
 *       updated entry. 404 {@code plugin.not-found} for unknown id.
 *       400 {@code plugin.config.read-only-field} for any field
 *       other than {@code enabled} (defensive against future field
 *       accretion).</li>
 * </ul>
 *
 * <p>PROV1a hook — {@code ProvenanceCaptureFilter} captures admin
 * mutations automatically, so each PATCH lands an {@code :Activity}
 * row (targetKind=PluginEntry, action=patch). No extra wiring here.
 *
 * <p>Precedence: the runtime PATCH wins for the lifetime of the JVM
 * (the {@code PluginRegistry}'s in-memory {@code runtimeOverrides}
 * map). The deploy-time {@code shepard.plugins.<id>.enabled} key in
 * {@code application.properties} stays valid as the install default —
 * it seeds the toggle on startup but the runtime override always wins
 * until restart, matching the A3b / N1c2 / UH1a "admin-configurable"
 * pattern.
 *
 * @see PluginRegistry
 * @see de.dlr.shepard.plugin.PluginEntry
 */
@Path("/v2/admin/plugins")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class PluginsAdminRest {

  /** RFC 7807 type URI for "no plugin with this id". */
  static final String PROBLEM_TYPE_NOT_FOUND = "/problems/plugin.not-found";

  /** RFC 7807 type URI for "PATCH mentions a non-patchable field". */
  static final String PROBLEM_TYPE_READ_ONLY_FIELD = "/problems/plugin.config.read-only-field";

  @Inject
  PluginRegistry registry;

  @GET
  @Operation(
    summary = "List every discovered plugin.",
    description = "Returns one row per plugin observed by the PM1a PluginRegistry — including " +
    "DISABLED + FAILED rows so the operator sees the full state space. Order matches the " +
    "registry's insertion order (classpath scan first, then drop-in JAR walk). The `enabled` " +
    "column reflects the effective runtime toggle (PATCH override wins; falls through to " +
    "shepard.plugins.<id>.enabled from application.properties)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current plugin registry snapshot.",
    content = @Content(schema = @Schema(implementation = PluginListIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response list() {
    List<PluginEntry> entries = registry.list();
    List<PluginEntryIO> rows = new ArrayList<>(entries.size());
    for (PluginEntry entry : entries) {
      rows.add(PluginEntryIO.from(entry, registry.isEnabled(entry.id())));
    }
    return Response.ok(new PluginListIO(rows)).build();
  }

  @PATCH
  @jakarta.ws.rs.Path("/{id}")
  @Operation(
    summary = "RFC 7396 merge-patch a plugin's enabled toggle.",
    description = "Patchable fields: `enabled` (Boolean). The flip is in-memory only — the " +
    "runtime override stays in effect for the lifetime of the JVM; surviving across restart " +
    "requires editing shepard.plugins.<id>.enabled in application.properties (PM1a's drop-in " +
    "model — runtime mutation wins until restart). Returns the updated entry. " +
    "PROV1a's ProvenanceCaptureFilter captures this PATCH as an :Activity row " +
    "(targetKind=PluginEntry) so the audit trail records who flipped which plugin when."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated plugin entry.",
    content = @Content(schema = @Schema(implementation = PluginEntryIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Caller named a field other than `enabled` in the patch body (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(
    responseCode = "404",
    description = "No plugin with that id (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  public Response patch(@PathParam("id") String id, PluginPatchIO body) {
    final PluginPatchIO patch = body == null ? new PluginPatchIO() : body;

    Set<String> unknown = patch.unknownFields();
    if (!unknown.isEmpty()) {
      String firstUnknown = unknown.iterator().next();
      Log.warnf("PM1b: rejected PATCH /v2/admin/plugins/%s — read-only field '%s' mentioned", id, firstUnknown);
      return problem(
        PROBLEM_TYPE_READ_ONLY_FIELD,
        "Field is not patchable",
        Status.BAD_REQUEST,
        "Field '" + firstUnknown + "' cannot be set via PATCH. Only 'enabled' is patchable today."
      );
    }

    Optional<PluginEntry> existing = registry.get(id);
    if (existing.isEmpty()) {
      return problem(
        PROBLEM_TYPE_NOT_FOUND,
        "Unknown plugin id",
        Status.NOT_FOUND,
        "No plugin registered with id '" + id + "'."
      );
    }

    if (patch.getEnabled() != null) {
      boolean targetState = patch.getEnabled();
      boolean currentState = registry.isEnabled(id);
      if (targetState != currentState) {
        registry.setEnabled(id, targetState);
        Log.infof("PM1b: plugin '%s' enabled toggle flipped to %s via admin REST", id, targetState);
      } else {
        Log.debugf("PM1b: plugin '%s' already enabled=%s — PATCH is a no-op", id, targetState);
      }
    }
    // No body / no fields → return the current entry unchanged (200).

    PluginEntry refreshed = registry.get(id).orElse(existing.get());
    return Response.ok(PluginEntryIO.from(refreshed, registry.isEnabled(id))).build();
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson envelope = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(envelope).build();
  }
}
