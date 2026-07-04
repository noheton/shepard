package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.v2.project.io.ProjectByAnnotationIO;
import de.dlr.shepard.v2.project.io.ProjectIO;
import de.dlr.shepard.v2.project.io.SubCollectionsIO;
import de.dlr.shepard.v2.project.services.ProjectsService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * PROJ-MCP-1 + PROJ-MCP-2 — MCP-shaped exposure of the
 * {@code /v2/projects/{appId}} REST surface so Claude / agent clients can walk
 * a Project's structure without hand-rolling SemanticAnnotation queries.
 *
 * <p>Cross-reference: {@code aidocs/integrations/121-project-and-subcollections.md §3.6}.
 */
@ApplicationScoped
public class ProjectMcpTools {

  @Inject
  ProjectsService projectsService;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  @Tool(
    name = "get_project",
    description =
      "Get the Project envelope for one Collection-acting-as-Project. A Project " +
      "is a Collection carrying the semantic annotation `urn:shepard:project = " +
      "\"true\"`. It bundles non-exclusive child Collections via `urn:shepard:partOf` " +
      "and may declare one or more funder/programme labels via " +
      "`urn:shepard:programme`.\n\n" +
      "Returns the project flavoured shape: name, description, ownerGroup, " +
      "programmes array, subCollectionCount, aggregateDoCount, lastActivityMillis. " +
      "When the appId resolves to a Collection that is NOT a Project, the tool " +
      "fails with 'not found' — call `list_collections` and pick a Project " +
      "(those with the project annotation), or use `list_projects` for the " +
      "top-level project roll.\n\n" +
      "Use this as the entry point for any multi-step research effort (MFFD, " +
      "PLUTO, LUMEN) to discover the bundled child Collections."
  )
  public String getProject(
    @ToolArg(description = "UUID v7 of the Project Collection.") String projectAppId
  ) {
    return support.run("get_project", () -> {
      contextBridge.bind();
      ProjectIO io = projectsService.getProject(projectAppId);
      if (io == null) {
        throw new jakarta.ws.rs.NotFoundException("No Project with appId '" + projectAppId + "'");
      }
      return support.toJson(io);
    });
  }

  @Tool(
    name = "list_projects",
    description =
      "List every Collection appId that is a Project. Returns an ordered " +
      "array of appIds (alphabetical by Collection name). Feed each appId to " +
      "`get_project` for the envelope, or to `get_project_sub_collections` for " +
      "its child-Collection grid.\n\n" +
      "This is the operator's one entrypoint to all multi-step efforts on the " +
      "instance (every MFFD, PLUTO, LUMEN umbrella shows up here)."
  )
  public String listProjects() {
    return support.run("list_projects", () -> {
      contextBridge.bind();
      return support.toJson(projectsService.listProjectAppIds());
    });
  }

  @Tool(
    name = "get_project_sub_collections",
    description =
      "List the child Collections of a Project. Each row carries the child's " +
      "appId + name + DataObject count + lastActivity + ownerGroup; when the " +
      "child belongs to more than one Project (multi-`partOf`), those other " +
      "Project appIds land in `alsoMemberOf`.\n\n" +
      "Use after `get_project` when you need to drill into one of the " +
      "Project's sub-Collections (each one is itself a normal Collection — " +
      "feed its appId to `list_data_objects`)."
  )
  public String getProjectSubCollections(
    @ToolArg(description = "UUID v7 of the Project Collection.") String projectAppId
  ) {
    return support.run("get_project_sub_collections", () -> {
      contextBridge.bind();
      SubCollectionsIO io = projectsService.getSubCollections(projectAppId);
      if (io == null) {
        throw new jakarta.ws.rs.NotFoundException("No Project with appId '" + projectAppId + "'");
      }
      return support.toJson(io);
    });
  }

  @Tool(
    name = "query_project_by_annotation",
    description =
      "Cross-Collection by-annotation roll-up: walk a Project's `urn:shepard:partOf` " +
      "children and return every DataObject across them whose annotation " +
      "`{predicate} = {value}` matches. This is the single generic surface for " +
      "Project-scoped queries — domain-specific roll-ups (MFFD per-Layer, PLUTO " +
      "per-mission-phase, LUMEN per-test-bench) all resolve through here with their " +
      "domain's predicate IRI.\n\n" +
      "Example: `query_project_by_annotation(mffd-appId, \"urn:shepard:mffd:layer\", \"18\")` " +
      "→ every DataObject across the MFFD sub-Collections with that annotation.\n\n" +
      "Pagination via `page` (zero-based, default 0) + `pageSize` (default 100, " +
      "max 500). The result envelope carries totalCount + this-page results."
  )
  public String queryProjectByAnnotation(
    @ToolArg(description = "UUID v7 of the Project Collection.") String projectAppId,
    @ToolArg(description = "Predicate IRI to match (e.g. 'urn:shepard:mffd:layer').") String predicate,
    @ToolArg(description = "Value to match (literal or IRI).") String value,
    @ToolArg(required = false, description = "Include the matched annotation row in each result. Default false.") Boolean includeAnnotations,
    @ToolArg(required = false, description = "Zero-based page index. Default 0.") Integer page,
    @ToolArg(required = false, description = "Page size, capped at 500. Default 100.") Integer pageSize
  ) {
    return support.run("query_project_by_annotation", () -> {
      contextBridge.bind();
      boolean include = Boolean.TRUE.equals(includeAnnotations);
      int safePage = page != null ? Math.max(page, 0) : 0;
      int safeSize = pageSize != null ? Math.max(1, Math.min(pageSize, 500)) : 100;
      ProjectByAnnotationIO io = projectsService.queryByAnnotation(
        projectAppId, predicate, value, include, safePage, safeSize);
      if (io == null) {
        throw new jakarta.ws.rs.NotFoundException("No Project with appId '" + projectAppId + "'");
      }
      return support.toJson(io);
    });
  }
}
