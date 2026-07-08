package de.dlr.shepard.v2.template.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.template.io.AllowedTemplatesIO;
import de.dlr.shepard.v2.template.io.ShepardTemplateIO;
import de.dlr.shepard.v2.template.io.TemplateInstantiationIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;

/**
 * {@code /v2/collections/{appId}/templates/...} — per-Collection
 * template wiring per {@code aidocs/54 §3} (T1c).
 *
 * <p>Two read endpoints (any auth user with Read on the Collection):
 * <ul>
 *   <li>{@code GET .../allowed} — what the owner has curated as
 *       visible inside this Collection.</li>
 *   <li>{@code GET .../used} — the {@code :USES_TEMPLATE} provenance
 *       edges set — what this Collection was created from.</li>
 * </ul>
 *
 * <p>One write endpoint (Manage on the Collection):
 * <ul>
 *   <li>{@code PUT .../allowed} — full replace of the curated set.</li>
 * </ul>
 *
 * <p>Recording usage ({@code :USES_TEMPLATE}) is done implicitly when
 * a Collection is instantiated from a template — see future
 * {@code POST /v2/collections/{appId}/from-template/{templateAppId}}
 * (T1c-instantiate, queued).
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/collections/{appId}/templates")
@RequestScoped
@Tag(name = "Collections")
public class CollectionTemplatesRest {

  private static final String PT_UNAUTHORIZED = "/problems/collection-templates.unauthorized";
  private static final String PT_NOT_FOUND    = "/problems/collection-templates.not-found";
  private static final String PT_FORBIDDEN    = "/problems/collection-templates.forbidden";

  @Inject
  ShepardTemplateDAO templateDAO;

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  @Inject
  CollectionPropertiesDAO collectionPropsDAO;

  @Inject
  PermissionsService permissionsService;

  @GET
  @Path("/allowed")
  @Operation(operationId = "listAllowed", summary = "List templates the Collection owner has curated as allowed inside this Collection.")
  @APIResponse(
    responseCode = "200",
    description = "Allowed templates (retired excluded) wrapped in a PagedResponseIO envelope.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(
      name = "X-Total-Count",
      description = "Total element count before paging.",
      schema = @Schema(type = SchemaType.INTEGER)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response listAllowed(
    @PathParam("appId") String collectionAppId,
    @Parameter(description = "Zero-based page index (default 0).") @DefaultValue("0") @Min(0) @QueryParam("page") int page,
    @Parameter(description = "Items per page (1–200). Default 50.") @DefaultValue("50") @Min(1) @Max(200) @QueryParam("pageSize") int pageSize,
    @Context SecurityContext securityContext
  ) {
    Optional<Long> ogmId = resolveAndGate(collectionAppId, AccessType.Read, securityContext);
    if (ogmId.isEmpty()) return forbiddenOrNotFound(collectionAppId, securityContext);
    long total = templateDAO.countAllowedForCollection(collectionAppId);
    List<ShepardTemplateIO> items = templateDAO.listAllowedForCollection(collectionAppId, page, pageSize)
        .stream().map(ShepardTemplateIO::from).toList();
    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize))
        .header("X-Total-Count", total)
        .build();
  }

  @GET
  @Path("/used")
  @Operation(operationId = "listUsed", summary = "List templates the Collection has cited via :USES_TEMPLATE.")
  @APIResponse(
    responseCode = "200",
    description = "Used templates (includes retired rows) wrapped in a PagedResponseIO envelope.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(
      name = "X-Total-Count",
      description = "Total element count before paging.",
      schema = @Schema(type = SchemaType.INTEGER)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response listUsed(
    @PathParam("appId") String collectionAppId,
    @Parameter(description = "Zero-based page index (default 0).") @DefaultValue("0") @Min(0) @QueryParam("page") int page,
    @Parameter(description = "Items per page (1–200). Default 50.") @DefaultValue("50") @Min(1) @Max(200) @QueryParam("pageSize") int pageSize,
    @Context SecurityContext securityContext
  ) {
    Optional<Long> ogmId = resolveAndGate(collectionAppId, AccessType.Read, securityContext);
    if (ogmId.isEmpty()) return forbiddenOrNotFound(collectionAppId, securityContext);
    long total = templateDAO.countUsedByCollection(collectionAppId);
    List<ShepardTemplateIO> items = templateDAO.listUsedByCollection(collectionAppId, page, pageSize)
        .stream().map(ShepardTemplateIO::from).toList();
    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize))
        .header("X-Total-Count", total)
        .build();
  }

  @PUT
  @Path("/allowed")
  @Operation(
    operationId = "setAllowed",
    summary = "Replace the allowed-template set for this Collection.",
    description = "Full replace (not merge). Empty list = no curation; the picker falls back to all live templates. Returns 204; follow up with GET .../allowed to see the new set."
  )
  @APIResponse(responseCode = "204", description = "Set replaced successfully.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response setAllowed(
    @PathParam("appId") String collectionAppId,
    @RequestBody AllowedTemplatesIO body,
    @Context SecurityContext securityContext
  ) {
    Optional<Long> ogmId = resolveAndGate(collectionAppId, AccessType.Manage, securityContext);
    if (ogmId.isEmpty()) return forbiddenOrNotFound(collectionAppId, securityContext);

    List<String> ids = body == null ? List.of() : (body.getTemplateAppIds() == null ? List.of() : body.getTemplateAppIds());
    templateDAO.setAllowedForCollection(collectionAppId, ids);
    return Response.noContent().build();
  }

  @POST
  @Path("/from/{templateAppId}")
  @Operation(
    operationId = "instantiate",
    summary = "Instantiate this Collection from a template — records :USES_TEMPLATE and returns the recipe body.",
    description = "Stamps the :USES_TEMPLATE edge (idempotent) so the Collection's provenance trail records " +
    "which template it was created from. Returns the template body so the client (frontend / CLI) can " +
    "interpret the JSON DSL and mint entities. Server-side body interpretation lands in T1c-apply " +
    "(queued). Requires Write on the Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Edge recorded (or already existed); template body returned for client interpretation.",
    content = @Content(schema = @Schema(implementation = TemplateInstantiationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the Collection.")
  @APIResponse(responseCode = "404", description = "Collection or template not found, or template retired.")
  public Response instantiate(
    @PathParam("appId") String collectionAppId,
    @PathParam("templateAppId") String templateAppId,
    @Context SecurityContext securityContext
  ) {
    Optional<Long> ogmId = resolveAndGate(collectionAppId, AccessType.Write, securityContext);
    if (ogmId.isEmpty()) return forbiddenOrNotFound(collectionAppId, securityContext);

    Optional<ShepardTemplate> template = templateDAO.findByAppId(templateAppId);
    if (template.isEmpty()) {
      return problem("/problems/templates.not-found", "Not Found", Response.Status.NOT_FOUND,
          "No template with appId " + templateAppId);
    }
    ShepardTemplate t = template.get();
    if (t.isRetired()) {
      return problem("/problems/templates.not-found", "Not Found", Response.Status.NOT_FOUND,
          "Template " + templateAppId + " is retired");
    }

    boolean edgeCreated = templateDAO.recordUsageReportingCreation(collectionAppId, templateAppId);
    return Response.ok(
      new TemplateInstantiationIO(collectionAppId, templateAppId, t.getTemplateKind(), t.getVersion(), t.getBody(), edgeCreated)
    ).build();
  }

  /**
   * Resolve the Collection's OGM id and run the permission check.
   * Returns {@link Optional#empty()} when the Collection is missing OR
   * the caller is unauthenticated OR the caller lacks the access type.
   * The {@link #forbiddenOrNotFound} helper turns empty into the right
   * 4xx response.
   */
  private Optional<Long> resolveAndGate(String collectionAppId, AccessType access, SecurityContext sc) {
    if (sc.getUserPrincipal() == null) return Optional.empty();
    Optional<Long> ogmId = collectionPropsDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return Optional.empty();
    String caller = sc.getUserPrincipal().getName();
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), access, caller, 0L)) return Optional.empty();
    return ogmId;
  }

  private Response forbiddenOrNotFound(String collectionAppId, SecurityContext sc) {
    if (sc.getUserPrincipal() == null) return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, null);
    Optional<Long> ogmId = collectionPropsDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return problem(PT_NOT_FOUND, "Collection not found", Response.Status.NOT_FOUND,
        "No Collection with appId " + collectionAppId);
    return problem(PT_FORBIDDEN, "Insufficient permission", Response.Status.FORBIDDEN,
        "Caller lacks the required permission on Collection " + collectionAppId);
  }
}
