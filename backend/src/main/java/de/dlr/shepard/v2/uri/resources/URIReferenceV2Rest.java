package de.dlr.shepard.v2.uri.resources;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code /v2/uri-references/...} REST surface for URI reference edits.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code PATCH /v2/uri-references/{appId}} — RFC 7396 merge-patch on
 *       {@code name}, {@code uri}, and {@code relationship}.</li>
 * </ul>
 *
 * <p>Auth: Write permission on the owning DataObject (same chain as
 * {@code FileReferenceV2Rest}).
 *
 * <p>REF-EDIT-6 — see {@code aidocs/16-dispatcher-backlog.md}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/uri-references")
@RequestScoped
@Tag(name = "URI references (v2)")
public class URIReferenceV2Rest {

  @Inject
  URIReferenceService uriReferenceService;

  @Inject
  PermissionsService permissionsService;

  // ─── patch ────────────────────────────────────────────────────────────────

  @PATCH
  @Path("/{appId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    summary = "RFC 7396 merge-patch on a URIReference.",
    description =
      "Applies a partial update to the `:URIReference` identified by `appId` (UUID v7).\n\n" +
      "Mutable fields: `name` (human-readable label), `uri` (the link target), " +
      "`relationship` (optional free-text relationship label, nullable).\n\n" +
      "Absent fields are left unchanged per RFC 7396 semantics. " +
      "Setting `name` or `uri` to `null` or blank returns 400. " +
      "`relationship` may be set to `null` to clear it.\n\n" +
      "Content-Type: prefer `application/merge-patch+json`; `application/json` is also accepted.\n\n" +
      "Auth: Write permission on the parent DataObject (inherited from its Collection).\n\n" +
      "Side effects: `ProvenanceCaptureFilter` records an `UPDATE` Activity."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated URIReferenceIO with the patched fields applied.",
    content = @Content(schema = @Schema(implementation = URIReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "Patch body is not a JSON object, or `name`/`uri` is null or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No URIReference with that appId.")
  public Response patchReference(
    @PathParam("appId") String appId,
    @RequestBody(required = true, content = @Content(mediaType = "application/merge-patch+json")) JsonNode body,
    @Context SecurityContext securityContext
  ) {
    if (body == null || !body.isObject()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("PATCH body must be a JSON object").build();
    }

    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    URIReference ref = uriReferenceService.findByAppId(appId);
    if (ref == null) return Response.status(Response.Status.NOT_FOUND).build();

    Response gate = checkAccess(ref, AccessType.Write, caller);
    if (gate != null) return gate;

    try {
      Map<String, Object> patch = jsonNodeToMap(body);
      URIReference updated = uriReferenceService.patchReferenceByAppId(appId, patch);
      return Response.ok(new URIReferenceIO(updated)).build();
    } catch (IllegalArgumentException iae) {
      return Response.status(Response.Status.BAD_REQUEST).entity(iae.getMessage()).build();
    } catch (InvalidPathException ipe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  /**
   * Returns the caller's principal name, or {@code null} when no
   * principal is present (unauthenticated request).
   */
  private String callerOrNull(SecurityContext securityContext) {
    return securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
  }

  /**
   * Access gate. Returns {@code null} when access is allowed;
   * otherwise returns the short-circuit Response (403 / 404).
   */
  private Response checkAccess(URIReference ref, AccessType accessType, String caller) {
    if (ref.getDataObject() == null) {
      // Graph inconsistency — treat as 404.
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    String doAppId = ref.getDataObject().getAppId();
    if (doAppId == null) {
      // Pre-L2a DataObject with no appId — fall back to OGM id check.
      long doOgmId = ref.getDataObject().getId();
      if (!permissionsService.isAccessTypeAllowedForUser(doOgmId, accessType, caller)) {
        return Response.status(Response.Status.FORBIDDEN).build();
      }
      return null;
    }
    if (!permissionsService.isAccessAllowedForDataObjectAppId(doAppId, accessType, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }

  /**
   * Convert a Jackson {@link JsonNode} object into a plain
   * {@code Map<String, Object>} preserving null values (so
   * RFC 7396 explicit-null clears flow through to the patch service).
   */
  Map<String, Object> jsonNodeToMap(JsonNode node) {
    Map<String, Object> out = new LinkedHashMap<>();
    var fields = node.fields();
    while (fields.hasNext()) {
      var e = fields.next();
      JsonNode v = e.getValue();
      if (v == null || v.isNull()) {
        out.put(e.getKey(), null);
      } else if (v.isTextual()) {
        out.put(e.getKey(), v.asText());
      } else if (v.isInt() || v.isLong()) {
        out.put(e.getKey(), v.asLong());
      } else if (v.isNumber()) {
        out.put(e.getKey(), v.asDouble());
      } else if (v.isBoolean()) {
        out.put(e.getKey(), v.asBoolean());
      } else if (v.isObject()) {
        Map<String, Object> sub = new HashMap<>();
        var inner = v.fields();
        while (inner.hasNext()) {
          var ie = inner.next();
          sub.put(ie.getKey(), ie.getValue() == null || ie.getValue().isNull() ? null : ie.getValue().asText());
        }
        out.put(e.getKey(), sub);
      } else {
        out.put(e.getKey(), v.toString());
      }
    }
    return out;
  }
}
