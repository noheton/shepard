package de.dlr.shepard.v2.template.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateBodyValidator;
import de.dlr.shepard.template.services.TemplateBodyValidator.InvalidTemplateBodyException;
import de.dlr.shepard.v2.template.io.CreateShepardTemplateIO;
import de.dlr.shepard.v2.template.io.PatchShepardTemplateIO;
import de.dlr.shepard.v2.template.io.ShepardTemplateIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
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
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Admin-only CRUD over {@code :ShepardTemplate} per
 * {@code aidocs/54 §5}. List + read are also reachable by any
 * authenticated user (the picker UI needs to see what's available
 * before they pick one); write (POST / PATCH / DELETE) requires
 * the {@code instance-admin} role.
 *
 * <p>PATCH triggers a copy-on-write per {@code aidocs/54 §7}:
 * a new node is minted with {@code version + 1} and the prior row
 * is retired (soft-delete). Existing
 * {@code (:Collection)-[:USES_TEMPLATE]->(prior)} citations stay
 * valid — reproducibility per {@code aidocs/41} snapshots.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/templates")
@RequestScoped
@Tag(name = "Templates (v2)")
public class ShepardTemplateRest {

  @Inject
  ShepardTemplateDAO dao;

  @Inject
  TemplateBodyValidator bodyValidator;

  @GET
  @Operation(
    summary = "List templates (latest non-retired version per name).",
    description = "Any authenticated user can browse. Retired rows are excluded by default; admins may " +
    "set ?includeRetired=true to see them too."
  )
  @APIResponse(
    responseCode = "200",
    description = "Matching templates, ordered by name then version DESC.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ShepardTemplateIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list(
    @Parameter(description = "Filter to a single templateKind.") @QueryParam("kind") String kind,
    @Parameter(description = "Set true to include retired rows. Only meaningful for admins.")
    @DefaultValue("false") @QueryParam("includeRetired") boolean includeRetired,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    boolean effInclude = includeRetired && securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE);
    List<ShepardTemplateIO> rows = dao.list(kind, effInclude).stream().map(ShepardTemplateIO::from).toList();
    return Response.ok(rows).build();
  }

  @GET
  @Path("/{appId}")
  @Operation(summary = "Read one template by appId.")
  @APIResponse(
    responseCode = "200",
    description = "The template.",
    content = @Content(schema = @Schema(implementation = ShepardTemplateIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No template with that appId.")
  public Response get(@PathParam("appId") String appId, @Context SecurityContext securityContext) {
    if (securityContext.getUserPrincipal() == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    Optional<ShepardTemplate> t = dao.findByAppId(appId);
    return t.map(template -> Response.ok(ShepardTemplateIO.from(template)).build())
      .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
  }

  @POST
  @Operation(
    summary = "Mint a new template (version=1). Admin-only.",
    description = "Names need not be unique. Two templates can share a name when they're different " +
    "kinds; same-kind same-name should use PATCH which triggers copy-on-write."
  )
  @APIResponse(
    responseCode = "201",
    description = "Created.",
    content = @Content(schema = @Schema(implementation = ShepardTemplateIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  public Response create(CreateShepardTemplateIO body, @Context SecurityContext securityContext) {
    if (body == null || body.getName() == null || body.getTemplateKind() == null || body.getBody() == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("name, templateKind, body are required").build();
    }
    try {
      bodyValidator.validate(body.getBody(), body.getTemplateKind());
    } catch (InvalidTemplateBodyException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(java.util.Map.of("errors", e.getErrors())).build();
    }
    String caller = securityContext.getUserPrincipal().getName();
    long now = System.currentTimeMillis();
    ShepardTemplate t = new ShepardTemplate(body.getName(), body.getTemplateKind(), body.getBody());
    t.setDescription(body.getDescription());
    if (body.getTags() != null) t.setTags(body.getTags());
    t.setCreatedBy(caller);
    t.setCreatedAt(now);
    t.setUpdatedAt(now);
    ShepardTemplate saved = dao.createOrUpdate(t);
    return Response.status(Response.Status.CREATED).entity(ShepardTemplateIO.from(saved)).build();
  }

  @PATCH
  @Path("/{appId}")
  @Operation(
    summary = "Edit a template (triggers copy-on-write). Admin-only.",
    description = "The prior row is marked retired = true; a new row is minted with version + 1 " +
    "carrying the patched fields (unchanged fields copied through)."
  )
  @APIResponse(
    responseCode = "200",
    description = "The new version.",
    content = @Content(schema = @Schema(implementation = ShepardTemplateIO.class))
  )
  @APIResponse(responseCode = "404", description = "No template with that appId.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  public Response patch(@PathParam("appId") String appId, PatchShepardTemplateIO body, @Context SecurityContext securityContext) {
    Optional<ShepardTemplate> existing = dao.findByAppId(appId);
    if (existing.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    ShepardTemplate prior = existing.get();
    // Validate the body when one is supplied; null body means "no body change" and is fine.
    if (body != null && body.getBody() != null) {
      try {
        bodyValidator.validate(body.getBody(), prior.getTemplateKind());
      } catch (InvalidTemplateBodyException e) {
        return Response.status(Response.Status.BAD_REQUEST).entity(java.util.Map.of("errors", e.getErrors())).build();
      }
    }
    String caller = securityContext.getUserPrincipal().getName();
    long now = System.currentTimeMillis();

    // Retire the prior row.
    prior.setRetired(true);
    prior.setUpdatedAt(now);
    dao.createOrUpdate(prior);

    // Mint the next version.
    ShepardTemplate next = dao.nextVersionOf(prior);
    if (body != null) {
      if (body.getName() != null) next.setName(body.getName());
      if (body.getBody() != null) next.setBody(body.getBody());
      if (body.getDescription() != null) next.setDescription(body.getDescription());
      if (body.getTags() != null) next.setTags(body.getTags());
    }
    next.setCreatedBy(caller);
    next.setCreatedAt(now);
    next.setUpdatedAt(now);
    ShepardTemplate saved = dao.createOrUpdate(next);
    return Response.ok(ShepardTemplateIO.from(saved)).build();
  }

  @DELETE
  @Path("/{appId}")
  @Operation(
    summary = "Retire a template (soft-delete). Admin-only.",
    description = "Sets retired = true; the row stays on disk so existing citations remain valid."
  )
  @APIResponse(responseCode = "204", description = "Retired.")
  @APIResponse(responseCode = "404", description = "No template with that appId.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  public Response retire(@PathParam("appId") String appId, @Context SecurityContext securityContext) {
    Optional<ShepardTemplate> existing = dao.findByAppId(appId);
    if (existing.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    ShepardTemplate t = existing.get();
    t.setRetired(true);
    t.setUpdatedAt(System.currentTimeMillis());
    dao.createOrUpdate(t);
    return Response.noContent().build();
  }

  @GET
  @Path("/tags")
  @Operation(
    summary = "Distinct list of tags across all non-retired templates.",
    description = "Used by the picker UI's tag-autocomplete. Optionally narrow to one templateKind."
  )
  @APIResponse(
    responseCode = "200",
    description = "Distinct tag list, sorted ascending.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = String.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response tags(
    @Parameter(description = "Filter to a single templateKind.") @QueryParam("kind") String kind,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    return Response.ok(dao.listDistinctTags(kind)).build();
  }
}
