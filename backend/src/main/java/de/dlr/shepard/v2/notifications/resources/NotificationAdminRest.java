package de.dlr.shepard.v2.notifications.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.notifications.io.NotificationIO;
import de.dlr.shepard.v2.notifications.io.TestNotificationIO;
import de.dlr.shepard.v2.notifications.services.NotificationService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/admin/notifications")
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class NotificationAdminRest {

  @Inject
  NotificationService service;

  @POST
  @Path("/test")
  @Operation(
    summary = "Send a test in-app notification.",
    description = "Publishes a test notification to validate that the notification system is working. " +
    "The notification appears in the target audience's bell panel within one poll cycle (~30 seconds). " +
    "Use audience=INSTANCE_ADMIN to send only to admins, or audience=USER with targetUsername to target " +
    "a specific user. This endpoint is the acceptance test for NTF1a."
  )
  @APIResponse(
    responseCode = "201",
    description = "Test notification published.",
    content = @Content(schema = @Schema(implementation = NotificationIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks instance-admin role.")
  public Response sendTest(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TestNotificationIO.class))
    ) TestNotificationIO body,
    @Context SecurityContext sc
  ) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : "admin";
    String title = body.getTitle() != null ? body.getTitle() : "Test notification";
    String notifBody = body.getBody() != null ? body.getBody() : "Test notification sent by " + caller + ".";
    String audience = body.getAudience() != null ? body.getAudience() : NotificationService.AUDIENCE_INSTANCE_ADMIN;
    String category = body.getCategory() != null ? body.getCategory() : NotificationService.CATEGORY_INFO;

    var n = service.publish(
      audience,
      body.getTargetUsername(),
      category,
      "admin:test",
      title,
      notifBody,
      body.getActionUrl()
    );
    return Response.status(Response.Status.CREATED).entity(NotificationIO.from(n)).build();
  }
}
