package de.dlr.shepard.v2.labjournal.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO.NotebookProjection;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.labjournal.io.NotebookReferenceIO;
import de.dlr.shepard.v2.labjournal.io.NotebookReferenceIO.ReferenceKind;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * J1b — {@code GET /v2/lab-journal/{appId}/notebooks}.
 *
 * <p>Returns the list of {@code .ipynb} file references attached to a
 * DataObject — both FR1b singletons and files inside FR1a bundles.
 * The frontend uses this list to decide which notebook tabs to render
 * inline via nbviewer-js.
 *
 * <p>Permission: Read on the DataObject — same gate as other
 * DataObject-scoped {@code /v2/} endpoints.
 *
 * <p>Filtering is case-insensitive: a file named {@code Analysis.IPYNB}
 * is included in the same way as {@code analysis.ipynb}.
 *
 * <p>The {@link NotebookReferenceIO#getAppId()} field carries the parent
 * Reference's appId:
 * <ul>
 *   <li>For {@code SINGLETON}: the singleton's own appId — clients can
 *       download bytes via {@code GET /v2/files/{appId}/content}.</li>
 *   <li>For {@code BUNDLE_FILE}: the bundle's appId (the addressable
 *       {@code :FileReference} node) — clients reach bytes via the
 *       upstream bundle download surface.</li>
 * </ul>
 *
 * @see LabJournalRenderRest J1a — markdown render endpoint on the same path prefix
 */
@Path("/v2/lab-journal")
@RequestScoped
@Tag(name = "Lab journal")
public class NotebookRest {

  /** Canonical IANA media type for Jupyter notebook files. */
  static final String IPYNB_MIME_TYPE = "application/x-ipynb+json";

  private static final String PT_UNAUTHORIZED = "/problems/lab-journal.unauthorized";
  private static final String PT_NOT_FOUND = "/problems/lab-journal.not-found";
  private static final String PT_FORBIDDEN = "/problems/lab-journal.forbidden";

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  PermissionsService permissionsService;

  @Inject
  SingletonFileReferenceDAO singletonFileReferenceDAO;

  @Inject
  FileBundleReferenceDAO fileBundleReferenceDAO;

  /**
   * List {@code .ipynb} file references attached to a DataObject.
   *
   * @param appId the application-level identifier of the DataObject.
   * @param sc              the JAX-RS security context providing the caller identity.
   * @return 200 with a (possibly empty) list; 401 unauthenticated; 403 forbidden;
   *         404 when no DataObject with that appId exists.
   */
  @GET
  @Path("/{appId}/notebooks")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
    operationId = "listNotebooks",
    summary = "List .ipynb file references attached to a DataObject.",
    description =
      "Returns every FileReference (singleton FR1b) and every ShepardFile inside a " +
      "FileBundleReference (FR1a) whose filename ends with .ipynb (case-insensitive). " +
      "The result is ordered: singletons first (by their createdAt ascending), " +
      "then bundle files (by bundle createdAt ascending, then by filename). " +
      "Returns an empty paged envelope when no .ipynb files are attached.\n\n" +
      "Pagination: `page` (0-based, default 0) and `pageSize` (1–200, default 50).\n\n" +
      "Permission: Read on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged envelope of .ipynb file references (items may be empty).",
    headers = @Header(name = "X-Total-Count", description = "Total .ipynb reference count before paging.", schema = @Schema(implementation = Long.class)),
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = PagedResponseIO.class)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response listNotebooks(
    @PathParam("appId") String appId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext sc
  ) {
    // Auth gate — 401 if not logged in
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No authenticated principal.");

    // Existence check — 404 if no DataObject with that appId
    try {
      entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return problem(PT_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No DataObject with appId: " + appId);
    }

    // Permission gate — DataObjects don't have their own :Permissions
    // node; access is inherited from the parent Collection. The walk
    // helper does the Cypher hop. Fail-closed if the DO has no parent.
    if (!permissionsService.isAccessAllowedForDataObjectAppId(appId, AccessType.Read, caller)) {
      return problem(PT_FORBIDDEN, "Forbidden", Response.Status.FORBIDDEN, "Caller lacks Read permission on the DataObject.");
    }

    // Cross-source pagination: singletons first, then bundle files.
    // Each count + page query is pushed to the DAO layer so we never
    // materialise all notebooks in memory.
    long singletonCount = singletonFileReferenceDAO.countNotebooks(appId);
    long bundleCount = fileBundleReferenceDAO.countNotebooks(appId);
    long total = singletonCount + bundleCount;
    long skip = (long) page * pageSize;

    List<NotebookReferenceIO> pageItems = new ArrayList<>();

    // ─── FR1b singletons ─────────────────────────────────────────────────────
    long singletonSkip = Math.min(skip, singletonCount);
    int singletonLimit = (int) Math.min(pageSize, Math.max(0L, singletonCount - singletonSkip));
    if (singletonLimit > 0) {
      for (NotebookProjection p : singletonFileReferenceDAO.listNotebooks(appId, singletonSkip, singletonLimit)) {
        pageItems.add(toIO(p, ReferenceKind.SINGLETON));
      }
    }

    // ─── FR1a bundles ─────────────────────────────────────────────────────────
    int bundleSpace = pageSize - pageItems.size();
    if (bundleSpace > 0 && bundleCount > 0) {
      long bundleSkip = Math.max(0L, skip - singletonCount);
      if (bundleSkip < bundleCount) {
        for (NotebookProjection p : fileBundleReferenceDAO.listNotebooks(appId, bundleSkip, bundleSpace)) {
          pageItems.add(toIO(p, ReferenceKind.BUNDLE_FILE));
        }
      }
    }

    return Response.ok(new PagedResponseIO<>(pageItems, total, page, pageSize))
        .header("X-Total-Count", total)
        .build();
  }

  private static NotebookReferenceIO toIO(NotebookProjection p, ReferenceKind kind) {
    return new NotebookReferenceIO(
      p.appId(), p.filename(), p.fileSize(), IPYNB_MIME_TYPE,
      p.createdAt(), p.createdBy(), kind
    );
  }

}
