package de.dlr.shepard.v2.admin.storage;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.v2.admin.storage.io.StorageAdapterIO;
import de.dlr.shepard.v2.admin.storage.io.StorageStatusIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
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
 * FS1e1 — {@code GET /v2/admin/storage}.
 *
 * <p>Returns the list of all discovered storage adapters with their
 * enabled state and which one is currently active. Replaces the
 * FS1a placeholder in {@code StorageStatusCommand} that read the
 * MongoDB health check as a proxy.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/admin/storage")
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class StorageAdminRest {

  @Inject
  FileStorageRegistry registry;

  @GET
  @Operation(
    summary = "List all file-storage adapters.",
    description = "Returns all discovered FileStorage adapters with their id, enabled state, " +
    "and which one is active for new uploads. The active provider is set via " +
    "shepard.storage.provider (deploy-time only)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Storage adapter list.",
    content = @Content(schema = @Schema(implementation = StorageStatusIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response get() {
    String activeId = registry.activeStorage().map(FileStorage::id).orElse(null);
    List<StorageAdapterIO> adapters = registry.list().stream()
      .map(s -> new StorageAdapterIO(s.id(), s.isEnabled(), s.id().equals(activeId)))
      .toList();
    return Response.ok(new StorageStatusIO(activeId, adapters)).build();
  }
}
