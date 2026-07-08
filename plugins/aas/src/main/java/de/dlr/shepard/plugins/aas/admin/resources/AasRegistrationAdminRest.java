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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
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
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
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
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(
      name = "X-Total-Count",
      description = "Total element count before paging.",
      schema = @Schema(type = SchemaType.INTEGER)
    )
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response listRegistrations(
    @Parameter(description = "0-based page index. Default 0. Returns 400 when negative.")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, range [1, " + MAX_PAGE_SIZE + "]. Default 50. Returns 400 when out of range.")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(MAX_PAGE_SIZE) int pageSize
  ) {
    long total = registrationDAO.countAll();
    List<AasRegistrationIO> rows = registrationDAO.listAll(page, pageSize)
      .stream()
      .map(AasRegistrationIO::from)
      .toList();
    return Response.ok(new PagedResponseIO<>(rows, total, page, pageSize))
        .header("X-Total-Count", total)
        .build();
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
