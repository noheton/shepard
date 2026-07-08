package de.dlr.shepard.v2.template.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateBodyValidator;
import de.dlr.shepard.template.services.TemplateBodyValidator.InvalidTemplateBodyException;
import de.dlr.shepard.template.services.TemplateInheritanceResolver;
import de.dlr.shepard.v2.template.io.CreateShepardTemplateIO;
import de.dlr.shepard.v2.template.io.PatchShepardTemplateIO;
import de.dlr.shepard.v2.template.io.ShepardTemplateIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
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
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import org.eclipse.microprofile.openapi.annotations.Operation;
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
@Tag(name = "Templates")
public class ShepardTemplateRest {

  private static final String PT_BAD_REQUEST  = "/problems/templates.bad-request";
  private static final String PT_NOT_FOUND    = "/problems/templates.not-found";
  private static final String PT_UNAUTHORIZED = "/problems/templates.unauthorized";

  @Inject
  ShepardTemplateDAO dao;

  @Inject
  TemplateBodyValidator bodyValidator;

  @Inject
  TemplateInheritanceResolver inheritanceResolver;

  @GET
  @Operation(
    operationId = "listTemplates",
    summary = "List templates (latest non-retired version per name).",
    description = "Any authenticated user can browse. Retired rows are excluded by default; admins may " +
    "set ?includeRetired=true to see them too."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged result set, ordered by name then version DESC.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list(
    @Parameter(description = "Filter to a single templateKind.") @QueryParam("kind") String kind,
    @Parameter(description = "Set true to include retired rows. Only meaningful for admins.")
    @DefaultValue("false") @QueryParam("includeRetired") boolean includeRetired,
    @Parameter(description = "Zero-based page index.") @DefaultValue("0") @QueryParam("page") @PositiveOrZero int page,
    @Parameter(description = "Page size (1–200). Default 50.") @DefaultValue("50") @QueryParam("pageSize") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, null);
    boolean effInclude = includeRetired && securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE);
    long total = dao.count(kind, effInclude);
    List<ShepardTemplateIO> rows = dao.list(kind, effInclude, page, pageSize).stream().map(ShepardTemplateIO::from).toList();
    return Response.ok(new PagedResponseIO<>(rows, total, page, pageSize)).build();
  }

  @GET
  @Path("/{appId}")
  @Operation(
    operationId = "getTemplate",
    summary = "Read one template by appId.")
  @APIResponse(
    responseCode = "200",
    description = "The template.",
    content = @Content(schema = @Schema(implementation = ShepardTemplateIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No template with that appId.")
  public Response get(
    @PathParam("appId") String appId,
    @Parameter(description = "When true, return the inheritance-flattened body + iconKey (parent fields merged, child overrides). Design: aidocs/integrations/123.")
    @DefaultValue("false") @QueryParam("flatten") boolean flatten,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, null);
    Optional<ShepardTemplate> t = dao.findByAppId(appId);
    if (t.isEmpty()) return problem(PT_NOT_FOUND, "Template not found", Response.Status.NOT_FOUND, "No template with appId " + appId);
    ShepardTemplate template = t.get();
    ShepardTemplateIO io = ShepardTemplateIO.from(template);
    if (flatten && template.getParentTemplateAppId() != null && !template.getParentTemplateAppId().isBlank()) {
      io.setBody(inheritanceResolver.flattenBody(template));
      io.setIconKey(inheritanceResolver.flattenIconKey(template));
    }
    return Response.ok(io).build();
  }

  @POST
  @Operation(
    operationId = "createTemplate",
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
      return problem(PT_BAD_REQUEST, "Required fields missing", Response.Status.BAD_REQUEST,
        "name, templateKind, body are required");
    }
    try {
      bodyValidator.validate(body.getBody(), body.getTemplateKind());
    } catch (InvalidTemplateBodyException e) {
      return problem(PT_BAD_REQUEST, "Template body invalid", Response.Status.BAD_REQUEST,
          null, java.util.Map.of("errors", e.getErrors()));
    }
    // Inheritance guard (aidocs/integrations/123 §2): parent must exist and share the kind.
    String parentAppId = body.getParentTemplateAppId();
    if (parentAppId != null && !parentAppId.isBlank()) {
      Optional<ShepardTemplate> parent = dao.findByAppId(parentAppId);
      if (parent.isEmpty()) {
        return problem(PT_BAD_REQUEST, "Parent template not found", Response.Status.BAD_REQUEST,
          "parentTemplateAppId not found: " + parentAppId);
      }
      if (!body.getTemplateKind().equals(parent.get().getTemplateKind())) {
        return problem(PT_BAD_REQUEST, "Parent kind mismatch", Response.Status.BAD_REQUEST,
          "parent template kind (" + parent.get().getTemplateKind() + ") must match child kind (" + body.getTemplateKind() + ")");
      }
    }
    String caller = securityContext.getUserPrincipal().getName();
    long now = System.currentTimeMillis();
    ShepardTemplate t = new ShepardTemplate(body.getName(), body.getTemplateKind(), body.getBody());
    t.setDescription(body.getDescription());
    if (body.getTags() != null) t.setTags(body.getTags());
    if (body.getIconKey() != null) t.setIconKey(body.getIconKey());
    if (parentAppId != null && !parentAppId.isBlank()) t.setParentTemplateAppId(parentAppId);
    t.setCreatedBy(caller);
    t.setCreatedAt(now);
    t.setUpdatedAt(now);
    ShepardTemplate saved = dao.createOrUpdate(t);
    return Response.status(Response.Status.CREATED).entity(ShepardTemplateIO.from(saved)).build();
  }

  @PATCH
  @Path("/{appId}")
  @Operation(
    operationId = "patchTemplate",
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
    if (existing.isEmpty()) return problem(PT_NOT_FOUND, "Template not found", Response.Status.NOT_FOUND, "No template with appId " + appId);
    ShepardTemplate prior = existing.get();
    // Validate the body when one is supplied; null body means "no body change" and is fine.
    if (body != null && body.getBody() != null) {
      try {
        bodyValidator.validate(body.getBody(), prior.getTemplateKind());
      } catch (InvalidTemplateBodyException e) {
        return problem(PT_BAD_REQUEST, "Template body invalid", Response.Status.BAD_REQUEST,
          null, java.util.Map.of("errors", e.getErrors()));
      }
    }
    // Inheritance guard (aidocs/integrations/123 §2): validate parent change before mutating.
    if (body != null && body.getParentTemplateAppId() != null && !body.getParentTemplateAppId().isBlank()) {
      String newParent = body.getParentTemplateAppId();
      Optional<ShepardTemplate> parent = dao.findByAppId(newParent);
      if (parent.isEmpty()) {
        return problem(PT_BAD_REQUEST, "Parent template not found", Response.Status.BAD_REQUEST,
          "parentTemplateAppId not found: " + newParent);
      }
      if (!prior.getTemplateKind().equals(parent.get().getTemplateKind())) {
        return problem(PT_BAD_REQUEST, "Parent kind mismatch", Response.Status.BAD_REQUEST,
          "parent template kind (" + parent.get().getTemplateKind() + ") must match child kind (" + prior.getTemplateKind() + ")");
      }
      if (inheritanceResolver.wouldCreateCycle(appId, newParent)) {
        return problem(PT_BAD_REQUEST, "Inheritance cycle", Response.Status.BAD_REQUEST,
          "parentTemplateAppId would create an inheritance cycle");
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
      // iconKey: empty string clears (sets to null) so admins can revert to per-kind default.
      if (body.getIconKey() != null) {
        next.setIconKey(body.getIconKey().isEmpty() ? null : body.getIconKey());
      }
      // parentTemplateAppId: empty string clears (template becomes a root); null = no change.
      if (body.getParentTemplateAppId() != null) {
        next.setParentTemplateAppId(body.getParentTemplateAppId().isEmpty() ? null : body.getParentTemplateAppId());
      }
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
    operationId = "deleteTemplate",
    summary = "Retire a template (soft-delete). Admin-only.",
    description = "Sets retired = true; the row stays on disk so existing citations remain valid."
  )
  @APIResponse(responseCode = "204", description = "Retired.")
  @APIResponse(responseCode = "404", description = "No template with that appId.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  public Response retire(@PathParam("appId") String appId, @Context SecurityContext securityContext) {
    Optional<ShepardTemplate> existing = dao.findByAppId(appId);
    if (existing.isEmpty()) return problem(PT_NOT_FOUND, "Template not found", Response.Status.NOT_FOUND, "No template with appId " + appId);
    ShepardTemplate t = existing.get();
    t.setRetired(true);
    t.setUpdatedAt(System.currentTimeMillis());
    dao.createOrUpdate(t);
    return Response.noContent().build();
  }

  @GET
  @Path("/tags")
  @Operation(
    operationId = "tags",
    summary = "Distinct list of tags across all non-retired templates.",
    description = "Used by the picker UI's tag-autocomplete. Optionally narrow to one templateKind. " +
    "Server-side cap: at most 500 distinct tags are returned (alphabetically first 500). " +
    "Pagination: `page` (0-based, default 0) and `pageSize` (1–500, default 50). " +
    "`X-Total-Count` header carries the total before paging."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged tag list, sorted ascending. X-Total-Count = total before paging.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response tags(
    @Parameter(description = "Filter to a single templateKind.") @QueryParam("kind") String kind,
    @Parameter(description = "Zero-based page index.") @DefaultValue("0") @QueryParam("page") @PositiveOrZero int page,
    @Parameter(description = "Page size (1–500). Default 50.") @DefaultValue("50") @QueryParam("pageSize") @Min(1) @Max(500) int pageSize,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, null);
    List<String> all = dao.listDistinctTags(kind);
    long total = all.size();
    int skip = (int) Math.min((long) page * pageSize, total);
    List<String> slice = all.subList(skip, (int) Math.min((long) skip + pageSize, total));
    return Response.ok(new PagedResponseIO<>(slice, total, page, pageSize))
        .header("X-Total-Count", total)
        .build();
  }

  // ─── helpers ───────────────────────────────────────────────────────────────

  private static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status).type("application/problem+json")
      .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null)).build();
  }

  private static Response problem(String type, String title, Response.Status status, String detail,
      java.util.Map<String, Object> ext) {
    return Response.status(status).type("application/problem+json")
      .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null, ext)).build();
  }
}
