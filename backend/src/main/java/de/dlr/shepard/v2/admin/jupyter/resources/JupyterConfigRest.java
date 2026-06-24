package de.dlr.shepard.v2.admin.jupyter.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.jupyter.entities.JupyterConfig;
import de.dlr.shepard.v2.admin.jupyter.io.JupyterConfigIO;
import de.dlr.shepard.v2.admin.jupyter.io.JupyterConfigPatchIO;
import de.dlr.shepard.v2.admin.jupyter.services.JupyterConfigService;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * J1e-PR-07 — <b>DEPRECATED wire-compat shim</b> retained at
 * {@code /v2/admin/jupyter/config} during a deprecation window.
 *
 * <p>The canonical path is now
 * {@code /v2/admin/plugins/jupyter/config} (per plugin SPI
 * conventions, introduced in J1e-PR-07). This class delegates to the
 * same {@link JupyterConfigService} and is wire-identical in every
 * respect. It will be removed once the deprecation window closes
 * (tracked as J1e-PR-07 in {@code aidocs/16-dispatcher-backlog.md}).
 *
 * <p>Operators should migrate all callers from this path to
 * {@code /v2/admin/plugins/jupyter/config}. A WARN-level deprecation
 * log is emitted on every call to assist discovery.
 *
 * @deprecated Use {@link JupyterConfigPluginRest} at
 *             {@code /v2/admin/plugins/jupyter/config} instead.
 * @see JupyterConfigPluginRest canonical resource at the new path
 * @see JupyterConfigService
 * @see JupyterConfig
 */
@Deprecated(since = "J1e-PR-07", forRemoval = true)
@Path("/v2/admin/jupyter/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class JupyterConfigRest {

  /** RFC 7807 type URI for an invalid hub URL. */
  static final String PROBLEM_TYPE_INVALID_HUB_URL = "/problems/jupyter.config.invalid-hub-url";

  @Inject
  JupyterConfigService service;

  @GET
  @Operation(
    summary = "Read the current :JupyterConfig singleton.",
    description = "Returns the runtime-mutable JupyterHub config — `enabled` (boolean) and " +
    "`hubUrl` (string, nullable). When the singleton has no `hubUrl`, the deploy-time " +
    "default (`shepard.jupyter.hub-url`) is returned in its place. The frontend reveals " +
    "the per-notebook 'Open in JupyterHub' action only when `enabled === true` AND " +
    "`hubUrl !== null`. Gated on the instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current JupyterHub config (singleton).",
    content = @Content(schema = @Schema(implementation = JupyterConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    Log.warn("J1e-PR-07: GET /v2/admin/jupyter/config is deprecated — migrate to /v2/admin/plugins/jupyter/config");
    JupyterConfig cfg = service.current();
    return Response.ok(toIO(cfg)).build();
  }

  @PATCH
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    summary = "RFC 7396 merge-patch the :JupyterConfig singleton.",
    description = "Patchable fields: `enabled` (boolean), `hubUrl` (string). " +
    "RFC 7396 semantics — absent = leave alone, null on `hubUrl` = clear (revert to " +
    "deploy-time default), value = replace. An explicit null on `enabled` is treated " +
    "as 'leave alone' since the entity field is a primitive boolean. " +
    "When `hubUrl` is non-null, it must parse as a syntactically valid absolute URL " +
    "(http or https). PROV1a's ProvenanceCaptureFilter captures this PATCH as an " +
    ":Activity row (targetKind=JupyterConfig)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = JupyterConfigIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "hubUrl is not a syntactically valid absolute http(s) URL (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(JupyterConfigPatchIO body) {
    Log.warn("J1e-PR-07: PATCH /v2/admin/jupyter/config is deprecated — migrate to /v2/admin/plugins/jupyter/config");
    JupyterConfigPatchIO patch = body == null ? new JupyterConfigPatchIO() : body;

    // Validate hubUrl when it was explicitly provided and is non-null.
    if (patch.isHubUrlTouched() && patch.getHubUrl() != null && !patch.getHubUrl().isBlank()) {
      String candidate = patch.getHubUrl();
      if (!isValidAbsoluteHttpUrl(candidate)) {
        Log.warnf("J1e: rejected PATCH — invalid hubUrl '%s' (must be absolute http(s) URL)", candidate);
        return problem(
          PROBLEM_TYPE_INVALID_HUB_URL,
          "Invalid hubUrl",
          Status.BAD_REQUEST,
          "hubUrl must be a syntactically valid absolute http(s) URL " +
          "(e.g. 'https://hub.example.org'). Set to null to revert to the deploy-time " +
          "default (shepard.jupyter.hub-url)."
        );
      }
    }

    // Resolve effective values per RFC 7396: absent = use current value.
    JupyterConfig current = service.current();
    boolean effectiveEnabled;
    if (patch.isEnabledTouched() && patch.getEnabled() != null) {
      effectiveEnabled = patch.getEnabled();
    } else {
      // Either absent (RFC 7396 "leave alone") or explicit null on a
      // primitive boolean (treated as "leave alone" — operator must
      // pass a concrete true/false to flip the master switch).
      effectiveEnabled = current.isEnabled();
    }
    String effectiveHubUrl;
    if (patch.isHubUrlTouched()) {
      // Touched: take whatever was on the wire (null = clear to default).
      effectiveHubUrl = patch.getHubUrl() != null && patch.getHubUrl().isBlank() ? null : patch.getHubUrl();
    } else {
      effectiveHubUrl = current.getHubUrl();
    }

    JupyterConfig saved = service.patch(effectiveEnabled, effectiveHubUrl);
    return Response.ok(toIO(saved)).build();
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private JupyterConfigIO toIO(JupyterConfig cfg) {
    return JupyterConfigIO.from(cfg, service.getDefaultHubUrl());
  }

  /**
   * Lightweight URL validation — accepts only absolute {@code http} or
   * {@code https} URLs with a non-empty host. Path/query/fragment are
   * tolerated so installs can put the hub behind a virtual-host prefix.
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

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
