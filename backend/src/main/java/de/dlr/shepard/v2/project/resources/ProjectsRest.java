package de.dlr.shepard.v2.project.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.project.io.ProjectByAnnotationIO;
import de.dlr.shepard.v2.project.io.ProjectIO;
import de.dlr.shepard.v2.project.io.SubCollectionsIO;
import de.dlr.shepard.v2.project.services.ProjectsService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * PROJ-REST-1 + PROJ-REST-2 — dedicated REST namespace for Project-shaped
 * reads. Lives at {@code /v2/projects/{appId}}.
 *
 * <p>A Project is a {@code :Collection} that carries
 * {@code urn:shepard:project = "true"} (see PROJ-PREDICATES-1). This namespace
 * exposes the Project-flavoured envelopes (programme strip, sub-Collection
 * roll-up, by-annotation query) without leaking Collection-internal shapes.
 * Any request whose path appId resolves to a non-Project Collection returns
 * a uniform 404 — {@code /v2/projects/} only addresses Projects. The
 * underlying Collection (whether a Project or not) is always reachable via
 * the existing {@code /v2/collections/{appId}} surface.
 *
 * <p>Reads are open to any authenticated caller in this first cut — the same
 * posture as {@code /v2/collections/{appId}/sub-collections} would have had.
 * A Project-scoped permission gate is tracked as a follow-up; the design doc
 * §3 explicitly notes that read permission "matches /v2/collections/{appId}".
 *
 * <p>Cross-reference: {@code aidocs/integrations/121-project-and-subcollections.md §3}.
 */
@Path("/v2/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Projects (v2)")
public class ProjectsRest {

  static final String PROBLEM_TYPE_NOT_FOUND     = "/problems/projects.not-found";
  static final String PROBLEM_TYPE_BAD_REQUEST   = "/problems/projects.bad-request";
  static final String PROBLEM_TYPE_UNPROCESSABLE = "/problems/projects.unprocessable";

  @Inject
  ProjectsService projectsService;

  // ─── LIST ─────────────────────────────────────────────────────────────────

  @GET
  @Operation(
    summary = "List Project appIds.",
    description =
      "Returns the appIds of every Collection that carries " +
      "`urn:shepard:project = \"true\"`. Returned in name-order. The list is " +
      "always-visible — the frontend `/projects` route uses this to render its " +
      "tile grid.\n\n" +
      "For each appId in the response, follow up with `GET /v2/projects/{appId}` " +
      "for the Project envelope (with programmes and aggregate counts)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Array of Project appIds.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = String.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list() {
    List<String> appIds = projectsService.listProjectAppIds();
    return Response.ok(appIds).build();
  }

  // ─── GET Project envelope ─────────────────────────────────────────────────

  @GET
  @Path("/{appId}")
  @Operation(
    summary = "Get a Project envelope by appId.",
    description =
      "Returns the Project-flavoured envelope: the underlying Collection's name " +
      "+ description + ownerGroup, plus the Project-only fields " +
      "(`programmes`, `subCollectionCount`, `aggregateDoCount`, " +
      "`lastActivityMillis`).\n\n" +
      "404 when the appId does not resolve to a Project — i.e. either the " +
      "appId is unknown OR the Collection at that appId does not carry " +
      "`urn:shepard:project = \"true\"`."
  )
  @APIResponse(
    responseCode = "200",
    description = "Project envelope.",
    content = @Content(schema = @Schema(implementation = ProjectIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No Project with that appId.")
  public Response get(@PathParam("appId") String appId) {
    ProjectIO io = projectsService.getProject(appId);
    if (io == null) return notFound(appId);
    return Response.ok(io)
      .header("Cache-Control", "max-age=120, must-revalidate")
      .build();
  }

  // ─── GET sub-collections ──────────────────────────────────────────────────

  @GET
  @Path("/{appId}/sub-collections")
  @Operation(
    summary = "List a Project's sub-Collections.",
    description =
      "Returns every Collection that declares this Project as a parent via " +
      "`urn:shepard:partOf`. Each row carries the child's appId + name + DataObject " +
      "count + lastActivity + ownerGroup; when the child belongs to other " +
      "Projects too (multi-`partOf`), those appIds land in `alsoMemberOf`.\n\n" +
      "The envelope also carries the parent Project's `programmes` list so the UI " +
      "can render the programme strip and the tile grid in one request."
  )
  @APIResponse(
    responseCode = "200",
    description = "Sub-Collections envelope.",
    content = @Content(schema = @Schema(implementation = SubCollectionsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No Project with that appId.")
  public Response subCollections(@PathParam("appId") String appId) {
    SubCollectionsIO io = projectsService.getSubCollections(appId);
    if (io == null) return notFound(appId);
    return Response.ok(io)
      .header("Cache-Control", "max-age=120, must-revalidate")
      .build();
  }

  // ─── GET by-annotation roll-up ────────────────────────────────────────────

  @GET
  @Path("/{appId}/by-annotation/{predicate}/{value}")
  @Operation(
    summary = "Cross-Collection by-annotation roll-up over a Project.",
    description =
      "Returns every DataObject across the Project's sub-Collections whose " +
      "direct semantic annotation matches `{predicate} = {value}`. The " +
      "`predicate` path parameter is the predicate IRI (URL-encoded — e.g. " +
      "`urn%3Ashepard%3Amffd%3Alayer`); the `value` parameter is the object " +
      "literal or IRI to match.\n\n" +
      "Pagination via `page` + `pageSize` (max 500). `include=annotations` " +
      "populates the per-row `matchedAnnotations` array with the matched " +
      "predicate+value+source rows (current implementation reports the direct " +
      "match only; full parent-walk lands as a follow-up).\n\n" +
      "404 when the appId is not a Project. 422 when the predicate is blank."
  )
  @APIResponse(
    responseCode = "200",
    description = "By-annotation roll-up envelope.",
    content = @Content(schema = @Schema(implementation = ProjectByAnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No Project with that appId.")
  @APIResponse(responseCode = "422", description = "Unknown / blank predicate.")
  public Response byAnnotation(
      @PathParam("appId") String appId,
      @PathParam("predicate") String predicate,
      @PathParam("value") String value,
      @QueryParam("include") @DefaultValue("identity") String include,
      @QueryParam("inherit") @DefaultValue("true") boolean inherit,
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("pageSize") @DefaultValue("100") int pageSize) {

    if (predicate == null || predicate.isBlank()) {
      return problem(PROBLEM_TYPE_UNPROCESSABLE, "Missing predicate",
        Response.Status.fromStatusCode(422), "Predicate path parameter must be non-blank.");
    }
    // Existence check before doing the value-coerce work.
    if (!projectsService.isProject(appId)) return notFound(appId);

    boolean includeAnnotations = "annotations".equalsIgnoreCase(include);
    // 'inherit' is accepted on the wire but not yet honoured — the design doc
    // §3.3 marks parent-walk inheritance as the follow-up shape. The flag is
    // wired so callers can opt in once the walker lands without an API break.
    ProjectByAnnotationIO io = projectsService.queryByAnnotation(
      appId, predicate, value, includeAnnotations, page, pageSize);
    if (io == null) return notFound(appId);
    return Response.ok(io).build();
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private static Response notFound(String appId) {
    return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
      Response.Status.NOT_FOUND,
      "Project with appId '" + appId + "' not found.");
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
