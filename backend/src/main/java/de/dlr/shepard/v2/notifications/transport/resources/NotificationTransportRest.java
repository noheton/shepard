package de.dlr.shepard.v2.notifications.transport.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;
import de.dlr.shepard.v2.notifications.transport.io.NotificationTransportReadIO;
import de.dlr.shepard.v2.notifications.transport.io.NotificationTransportWriteIO;
import de.dlr.shepard.v2.notifications.transport.services.NotificationTransportService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * NTF1-BACKEND-LIST + NTF1-BACKEND-CRUD — admin REST for the
 * {@code :NotificationTransport} list-shaped entity.
 *
 * <p>This class hosts the LIST endpoint; CRUD writes live alongside
 * (added in the NTF1-BACKEND-CRUD commit).
 *
 * <p><b>Credential safety.</b> All read paths return
 * {@link NotificationTransportReadIO} which omits
 * {@code smtpPassword} and {@code matrixAccessToken} at the type
 * level — there is no way to accidentally leak them via this REST
 * surface (compile-time guarantee).
 */
@Path("/v2/admin/notifications/transports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class NotificationTransportRest {

  /** RFC 7807 type URI for an invalid {@code kind} on POST. */
  static final String PROBLEM_TYPE_INVALID_KIND = "/problems/notifications.transport.invalid-kind";

  /** RFC 7807 type URI for a missing required field on POST. */
  static final String PROBLEM_TYPE_MISSING_FIELD = "/problems/notifications.transport.missing-field";

  /** RFC 7807 type URI for an unknown transport appId. */
  static final String PROBLEM_TYPE_NOT_FOUND = "/problems/notifications.transport.not-found";

  @Inject
  NotificationTransportService service;

  @GET
  @Operation(
    operationId = "listNotificationTransports",
    summary = "List all configured notification transports.",
    description = "Returns every :NotificationTransport row in the instance, ordered " +
      "by name ascending. CREDENTIAL FIELDS ARE OMITTED — smtpPassword + " +
      "matrixAccessToken never appear on this response (compile-time guarantee via " +
      "NotificationTransportReadIO). Gated on the instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged list of transports (may be empty).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response list() {
    List<NotificationTransportReadIO> items = service.listAll()
        .stream()
        .map(NotificationTransportReadIO::from)
        .toList();
    return Response.ok(new PagedResponseIO<>(items, items.size(), 0, items.size()))
        .build();
  }

  // ─── NTF1-BACKEND-CRUD: POST / PATCH / DELETE ───────────────────────────

  @POST
  @Operation(
    operationId = "createNotificationTransport",
    summary = "Create a new notification transport.",
    description = "Creates a :NotificationTransport row. Required fields: `kind` " +
      "(must be a valid TransportKind), `name`. All other fields are optional and " +
      "kind-specific. Credentials (smtpPassword, matrixAccessToken) are accepted on " +
      "write and stored — they will NOT appear in the GET response (the read-side " +
      "IO omits them by type). Returns 201 + the newly-minted appId in the body."
  )
  @APIResponse(
    responseCode = "201",
    description = "Transport created.",
    content = @Content(schema = @Schema(implementation = NotificationTransportReadIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "kind missing/invalid or name missing (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response create(NotificationTransportWriteIO body) {
    NotificationTransportWriteIO patch = body == null ? new NotificationTransportWriteIO() : body;

    // Required: kind + name.
    if (patch.getKind() == null || patch.getKind().isBlank()) {
      return problem(PROBLEM_TYPE_MISSING_FIELD, "Missing required field 'kind'",
          Status.BAD_REQUEST,
          "POST /v2/admin/notifications/transports requires a non-empty 'kind' field. " +
          "Valid kinds: " + java.util.Arrays.toString(TransportKind.values()) + ".");
    }
    if (!isValidKind(patch.getKind())) {
      return problem(PROBLEM_TYPE_INVALID_KIND, "Invalid kind",
          Status.BAD_REQUEST,
          "kind '" + patch.getKind() + "' is not a recognised TransportKind. " +
          "Valid: " + java.util.Arrays.toString(TransportKind.values()) + ".");
    }
    if (patch.getName() == null || patch.getName().isBlank()) {
      return problem(PROBLEM_TYPE_MISSING_FIELD, "Missing required field 'name'",
          Status.BAD_REQUEST,
          "POST /v2/admin/notifications/transports requires a non-empty 'name' field.");
    }

    NotificationTransport t = new NotificationTransport();
    applyPatch(t, patch, /* isCreate */ true);
    NotificationTransport saved = service.save(t);

    return Response.status(Status.CREATED).entity(NotificationTransportReadIO.from(saved)).build();
  }

  @PATCH
  @Path("/{appId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "patchNotificationTransport",
    summary = "RFC 7396 merge-patch an existing notification transport.",
    description = "Patches the :NotificationTransport identified by appId. RFC 7396 " +
      "semantics: absent = leave alone, explicit null = clear, value = replace. " +
      "Touched flags on the request body distinguish absent from null. The PATCH " +
      "is captured as an :Activity row by PROV1a's ProvenanceCaptureFilter."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated transport.",
    content = @Content(schema = @Schema(implementation = NotificationTransportReadIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No transport with that appId.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patch(@PathParam("appId") String appId, NotificationTransportWriteIO body) {
    Optional<NotificationTransport> found = service.findByAppId(appId);
    if (found.isEmpty()) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Transport not found",
          Status.NOT_FOUND, "No notification transport with appId '" + appId + "'.");
    }
    NotificationTransportWriteIO patch = body == null ? new NotificationTransportWriteIO() : body;

    // Validate kind ONLY if the caller touched it AND provided a value.
    if (patch.isKindTouched() && patch.getKind() != null && !isValidKind(patch.getKind())) {
      return problem(PROBLEM_TYPE_INVALID_KIND, "Invalid kind",
          Status.BAD_REQUEST,
          "kind '" + patch.getKind() + "' is not a recognised TransportKind. " +
          "Valid: " + java.util.Arrays.toString(TransportKind.values()) + ".");
    }

    NotificationTransport t = found.get();
    applyPatch(t, patch, /* isCreate */ false);
    NotificationTransport saved = service.save(t);

    return Response.ok(NotificationTransportReadIO.from(saved)).build();
  }

  @DELETE
  @Path("/{appId}")
  @Operation(
    operationId = "deleteNotificationTransport",
    summary = "Delete a notification transport.",
    description = "Removes the :NotificationTransport row identified by appId. " +
      "No cascade — historical :Activity rows referencing the deleted transport's " +
      "appId are preserved per the CLAUDE.md \"audit trail is a graph\" rule. " +
      "Returns 204 on success, 404 when the appId is unknown."
  )
  @APIResponse(responseCode = "204", description = "Transport deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No transport with that appId.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response delete(@PathParam("appId") String appId) {
    boolean deleted = service.deleteByAppId(appId);
    if (!deleted) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Transport not found",
          Status.NOT_FOUND, "No notification transport with appId '" + appId + "'.");
    }
    Log.infof("NTF1: DELETE /v2/admin/notifications/transports/%s", appId);
    return Response.noContent().build();
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  /**
   * Apply RFC 7396 merge-patch semantics — touched fields overwrite,
   * untouched fields preserve the entity's current value. For
   * {@code isCreate=true}, the entity starts empty so all touched
   * fields take effect (and non-touched fields stay null).
   */
  private static void applyPatch(NotificationTransport t, NotificationTransportWriteIO p, boolean isCreate) {
    if (p.isKindTouched()) t.setKind(p.getKind());
    if (p.isNameTouched()) t.setName(p.getName());
    if (p.isEnabledTouched() && p.getEnabled() != null) t.setEnabled(p.getEnabled());

    if (p.isSmtpHostTouched()) t.setSmtpHost(p.getSmtpHost());
    if (p.isSmtpPortTouched()) t.setSmtpPort(p.getSmtpPort());
    if (p.isSmtpUsernameTouched()) t.setSmtpUsername(p.getSmtpUsername());
    if (p.isSmtpPasswordTouched()) t.setSmtpPassword(p.getSmtpPassword());
    if (p.isSmtpFromTouched()) t.setSmtpFrom(p.getSmtpFrom());
    if (p.isSmtpTlsTouched()) t.setSmtpTls(p.getSmtpTls());

    if (p.isMatrixHomeserverTouched()) t.setMatrixHomeserver(p.getMatrixHomeserver());
    if (p.isMatrixAccessTokenTouched()) t.setMatrixAccessToken(p.getMatrixAccessToken());
    if (p.isMatrixDefaultRoomTouched()) t.setMatrixDefaultRoom(p.getMatrixDefaultRoom());
  }

  static boolean isValidKind(String kind) {
    if (kind == null) return false;
    try {
      TransportKind.valueOf(kind);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

}
