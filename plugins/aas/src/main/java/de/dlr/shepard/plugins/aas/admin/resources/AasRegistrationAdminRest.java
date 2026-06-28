package de.dlr.shepard.plugins.aas.admin.resources;

import de.dlr.shepard.plugins.aas.daos.AasRegistrationDAO;
import de.dlr.shepard.plugins.aas.services.AasRegistryOutboxService;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.aas.admin.io.AasRegistrationIO;
import de.dlr.shepard.plugins.aas.admin.io.AasSyncResultIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * AAS1-reg Commit 3 — admin REST surface for the AAS registry outbox.
 *
 * <p>Two endpoints, both gated on {@code instance-admin}:
 * <ul>
 *   <li>{@code GET  /v2/admin/aas/registrations}       — list all outbox rows</li>
 *   <li>{@code POST /v2/admin/aas/registrations/sync}  — trigger {@link AasRegistryOutboxService#syncAll()}</li>
 * </ul>
 *
 * <p>The sync endpoint is useful when a registry was temporarily unavailable at
 * startup: the operator can retry without restarting the pod.
 */
@Path("/v2/admin/aas/registrations")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class AasRegistrationAdminRest {

  @Inject
  AasRegistrationDAO registrationDAO;

  @Inject
  AasRegistryOutboxService outboxService;

  static final int MAX_PAGE_SIZE = 200;

  @GET
  @Operation(
    operationId = "listAasRegistrations",
    summary = "List AAS registry outbox rows (paginated).",
    description = "Returns one row per (shell, registry-url) pair tracked in the " +
    ":AasRegistration outbox. Status is PENDING, SYNCED, or FAILED. " +
    "Results are cursor-stable: sorted by shellAppId, registryUrl. " +
    "Gated on the instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged outbox rows (may be empty if no collections exist or no registry is configured).",
    content = @Content(schema = @Schema(implementation = AasRegistrationIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response listRegistrations(
    @Parameter(description = "0-based page index. Default 0.")
    @QueryParam("page") @DefaultValue("0") int page,
    @Parameter(description = "Page size. Default 50, max " + MAX_PAGE_SIZE + ".")
    @QueryParam("pageSize") @DefaultValue("50") int pageSize
  ) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    long total = registrationDAO.countAll();
    List<AasRegistrationIO> rows = registrationDAO.listAll(safePage, safeSize)
      .stream()
      .map(AasRegistrationIO::from)
      .toList();
    return Response.ok(new PagedResponseIO<>(rows, total, safePage, safeSize)).build();
  }

  @POST
  @Path("/sync")
  @Operation(
    operationId = "triggerAasRegistrySync",
    summary = "Trigger an on-demand AAS registry sync.",
    description = "Calls AasRegistryOutboxService.syncAll(): seeds PENDING rows for any " +
    "unregistered collections, then pushes all PENDING/FAILED rows to the configured " +
    "IDTA AAS Registry. Best-effort — failures flip the row to FAILED and are logged at WARN. " +
    "Returns the count of shells successfully registered in this invocation. " +
    "Gated on the instance-admin role."
  )
  @APIResponse(
    responseCode = "200",
    description = "Sync completed; synced field contains the success count (0 when no registry is configured).",
    content = @Content(schema = @Schema(implementation = AasSyncResultIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response triggerSync() {
    int synced = outboxService.syncAll();
    return Response.ok(new AasSyncResultIO(synced)).build();
  }
}
