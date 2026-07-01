package de.dlr.shepard.v2.notifications.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.notifications.io.NotificationIO;
import de.dlr.shepard.v2.notifications.io.NotificationTestDeliveryIO;
import de.dlr.shepard.v2.notifications.io.TestNotificationIO;
import de.dlr.shepard.v2.notifications.services.NotificationService;
import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.services.NotificationTransportRegistry;
import de.dlr.shepard.v2.notifications.transport.services.NotificationTransportService;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationMessage;
import io.quarkus.logging.Log;
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
import java.util.Optional;
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

  // NTF1-BACKEND-TEST-PER-TRANSPORT — lookup + dispatch for the
  // transportId branch. Injected lazily by CDI; absent in pre-NTF1
  // installs is impossible (these are in-tree singletons).
  @Inject
  NotificationTransportService transportService;

  @Inject
  NotificationTransportRegistry transportRegistry;

  private static final String PROBLEM_TYPE_NOT_FOUND = "/problems/notifications.not-found";
  private static final String PROBLEM_TYPE_SERVICE_UNAVAILABLE = "/problems/notifications.service-unavailable";
  private static final String PROBLEM_TYPE_BAD_GATEWAY = "/problems/notifications.bad-gateway";

  @POST
  @Path("/test")
  @Operation(
    operationId = "sendTest",
    summary = "Send a test in-app notification.",
    description = "Publishes a test notification to validate that the notification system is working. " +
    "The notification appears in the target audience's bell panel within one poll cycle (~30 seconds). " +
    "Use audience=INSTANCE_ADMIN to send only to admins, or audience=USER with targetUsername to target " +
    "a specific user. This endpoint is the acceptance test for NTF1a."
  )
  @APIResponse(
    responseCode = "201",
    description = "Test notification published (in-app path).",
    content = @Content(schema = @Schema(implementation = NotificationIO.class))
  )
  @APIResponse(
    responseCode = "200",
    description = "Test notification delivered via transport.",
    content = @Content(schema = @Schema(implementation = NotificationTestDeliveryIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks instance-admin role.")
  @APIResponse(
    responseCode = "404",
    description = "transportId does not resolve.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(
    responseCode = "502",
    description = "Transport returned failure.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(
    responseCode = "503",
    description = "No sender registered for this transport kind.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
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

    // NTF1-BACKEND-TEST-PER-TRANSPORT — when transportId is set, route
    // the test through the named transport via the registry. When
    // unset, the legacy in-app path runs unchanged (full backwards
    // compatibility with the NTF1a smoke-test endpoint).
    if (body.getTransportId() != null && !body.getTransportId().isBlank()) {
      return sendViaTransport(body, title, notifBody);
    }

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

  /**
   * NTF1-BACKEND-TEST-PER-TRANSPORT — resolve the named transport,
   * dispatch via the registry-bound {@code NotificationSender}, and
   * report the outcome.
   *
   * <p>Outcome → HTTP status:
   * <ul>
   *   <li>404 — appId resolves to no transport.</li>
   *   <li>503 — kind has no registered sender (e.g. SMTP before
   *       NTF1-BACKEND-SMTP shipped).</li>
   *   <li>200 — sender returned true.</li>
   *   <li>502 — sender returned false (recoverable transport error).</li>
   * </ul>
   *
   * <p>Each invocation updates the transport's {@code lastTestResult},
   * {@code lastTestedAt}, and {@code lastTestDetail} so the admin pane
   * can render per-row status badges.
   */
  private Response sendViaTransport(TestNotificationIO body, String title, String notifBody) {
    String appId = body.getTransportId();
    Optional<NotificationTransport> found = transportService.findByAppId(appId);
    if (found.isEmpty()) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Transport not found", Response.Status.NOT_FOUND, "no transport with appId=" + appId);
    }
    NotificationTransport transport = found.get();
    var sender = transportRegistry.resolve(transport);
    if (sender.isEmpty()) {
      Log.warnf("NTF1: no registered sender for transport appId=%s kind=%s",
          transport.getAppId(), transport.getKind());
      recordTestOutcome(transport, "FAIL", "no registered sender for kind=" + transport.getKind());
      return problem(PROBLEM_TYPE_SERVICE_UNAVAILABLE, "No sender registered", Response.Status.SERVICE_UNAVAILABLE, "no registered sender for kind=" + transport.getKind());
    }

    NotificationMessage msg = new NotificationMessage(
        body.getRecipientAddress(),
        title,
        notifBody,
        body.getActionUrl());
    boolean ok;
    try {
      ok = sender.get().send(transport, msg);
    } catch (RuntimeException e) {
      Log.warnf(e, "NTF1: sender for kind=%s threw — treating as failure", transport.getKind());
      recordTestOutcome(transport, "FAIL", e.getMessage());
      return problem(PROBLEM_TYPE_BAD_GATEWAY, "Transport error", Response.Status.BAD_GATEWAY, "transport send threw: " + e.getMessage());
    }
    if (ok) {
      recordTestOutcome(transport, "OK", "delivered");
      return Response.ok(new NotificationTestDeliveryIO("delivered", transport.getKind())).build();
    }
    recordTestOutcome(transport, "FAIL", "sender returned false (see backend logs)");
    return problem(PROBLEM_TYPE_BAD_GATEWAY, "Transport error", Response.Status.BAD_GATEWAY, "transport send returned false");
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  /** Persist last-test fields on the transport row. Best-effort — never throws. */
  private void recordTestOutcome(NotificationTransport t, String result, String detail) {
    try {
      t.setLastTestResult(result);
      t.setLastTestedAt(System.currentTimeMillis());
      t.setLastTestDetail(detail);
      transportService.save(t);
    } catch (RuntimeException e) {
      Log.warnf(e, "NTF1: could not record test outcome on transport appId=%s — continuing",
          t.getAppId());
    }
  }
}
