package de.dlr.shepard.v2.notifications.transport.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.notifications.transport.io.NotificationTransportListIO;
import de.dlr.shepard.v2.notifications.transport.io.NotificationTransportReadIO;
import de.dlr.shepard.v2.notifications.transport.services.NotificationTransportService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
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

  @Inject
  NotificationTransportService service;

  @GET
  @Operation(
    summary = "List all configured notification transports.",
    description = "Returns every :NotificationTransport row in the instance, ordered " +
      "by name ascending. CREDENTIAL FIELDS ARE OMITTED — smtpPassword + " +
      "matrixAccessToken never appear on this response (compile-time guarantee via " +
      "NotificationTransportReadIO). Gated on the instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current list of transports (may be empty).",
    content = @Content(schema = @Schema(implementation = NotificationTransportListIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response list() {
    List<NotificationTransportReadIO> items = service.listAll()
        .stream()
        .map(NotificationTransportReadIO::from)
        .toList();
    return Response.ok(new NotificationTransportListIO(items)).build();
  }
}
