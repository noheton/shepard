package de.dlr.shepard.v2.notifications.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.notifications.io.NotificationCountIO;
import de.dlr.shepard.v2.notifications.io.NotificationIO;
import de.dlr.shepard.v2.notifications.services.NotificationService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/notifications")
@RequestScoped
@Tag(name = "Notifications")
public class NotificationRest {

  private static final String PROBLEM_TYPE_UNAUTHORIZED = "/problems/notifications.unauthorized";
  private static final String PROBLEM_TYPE_NOT_FOUND = "/problems/notifications.not-found";

  @Inject
  NotificationService service;

  @GET
  @Operation(
    operationId = "listNotifications",
    summary = "List in-app notifications for the authenticated user.",
    description = "Returns all non-expired notifications visible to the caller, ordered most-recent-first. " +
    "Includes notifications addressed to the caller's username, ALL-audience broadcasts, and (when the " +
    "caller is an instance-admin) INSTANCE_ADMIN-audience broadcasts. Service cap: 200 rows.\n\n" +
    "Pagination (APISIMP-NOTIFICATIONS-LIST-NO-PAGINATION): supply both `page` (0-based) and `pageSize` " +
    "(1–200) to slice the result. Omitting either returns all notifications. " +
    "`X-Total-Count` header carries the total before paging."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of notifications. Header X-Total-Count = total count before paging.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = NotificationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list(
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext sc
  ) {
    String username = resolveUsername(sc);
    if (username == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "authentication required");
    boolean isAdmin = sc.isUserInRole("instance-admin");
    List<NotificationIO> all = service.listForUser(username, isAdmin)
      .stream()
      .map(NotificationIO::from)
      .toList();
    int total = all.size();
    int from = (int) Math.min((long) page * pageSize, total);
    int to = (int) Math.min((long) from + pageSize, total);
    return Response.ok(all.subList(from, to)).header("X-Total-Count", total).build();
  }

  @GET
  @Path("/count")
  @Operation(
    summary = "Count unread in-app notifications for the authenticated user.",
    description = "Returns a single object with the unread notification count. Poll this endpoint " +
    "(e.g. every 30 seconds) to drive the bell-icon badge without fetching full payloads."
  )
  @APIResponse(
    responseCode = "200",
    description = "Unread notification count.",
    content = @Content(schema = @Schema(implementation = NotificationCountIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response count(@Context SecurityContext sc) {
    String username = resolveUsername(sc);
    if (username == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "authentication required");
    boolean isAdmin = sc.isUserInRole("instance-admin");
    long unread = service.countUnread(username, isAdmin);
    return Response.ok(new NotificationCountIO(unread)).build();
  }

  @PATCH
  @Path("/{appId}/read")
  @Operation(
    summary = "Mark a notification as read.",
    description = "Marks the notification identified by its appId as read. The notification must be " +
    "visible to the authenticated user."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated notification.",
    content = @Content(schema = @Schema(implementation = NotificationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Notification not found or not visible to caller.")
  public Response markRead(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String username = resolveUsername(sc);
    if (username == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "authentication required");
    boolean isAdmin = sc.isUserInRole("instance-admin");
    try {
      var updated = service.markRead(appId, username, isAdmin);
      return Response.ok(NotificationIO.from(updated)).build();
    } catch (NotFoundException e) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Notification not found",
        Response.Status.NOT_FOUND, e.getMessage());
    }
  }

  @DELETE
  @Path("/{appId}")
  @Operation(
    summary = "Dismiss (delete) a notification.",
    description = "Permanently removes the notification from the caller's notification list. " +
    "The notification must be visible to the authenticated user."
  )
  @APIResponse(responseCode = "204", description = "Notification dismissed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Notification not found or not visible to caller.")
  public Response dismiss(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String username = resolveUsername(sc);
    if (username == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "authentication required");
    boolean isAdmin = sc.isUserInRole("instance-admin");
    try {
      service.dismiss(appId, username, isAdmin);
      return Response.noContent().build();
    } catch (NotFoundException e) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Notification not found",
        Response.Status.NOT_FOUND, e.getMessage());
    }
  }

  private String resolveUsername(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
