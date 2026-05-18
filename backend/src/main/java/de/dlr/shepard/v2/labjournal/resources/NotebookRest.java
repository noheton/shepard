package de.dlr.shepard.v2.labjournal.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.v2.labjournal.io.NotebookReferenceIO;
import de.dlr.shepard.v2.labjournal.io.NotebookReferenceIO.ReferenceKind;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * J1b — {@code GET /v2/lab-journal/{dataObjectAppId}/notebooks}.
 *
 * <p>Returns the list of {@code .ipynb} file references attached to a
 * DataObject — both FR1b singletons ({@link FileReference}) and files
 * inside FR1a bundles ({@link FileBundleReference}). The frontend uses this
 * list to decide which notebook tabs to render inline via nbviewer-js.
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
@Tag(name = "Lab journal (v2)")
public class NotebookRest {

  /** Canonical IANA media type for Jupyter notebook files. */
  static final String IPYNB_MIME_TYPE = "application/x-ipynb+json";

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  PermissionsService permissionsService;

  @Inject
  SingletonFileReferenceService singletonService;

  @Inject
  FileBundleReferenceDAO fileBundleReferenceDAO;

  /**
   * List {@code .ipynb} file references attached to a DataObject.
   *
   * @param dataObjectAppId the application-level identifier of the DataObject.
   * @param sc              the JAX-RS security context providing the caller identity.
   * @return 200 with a (possibly empty) list; 401 unauthenticated; 403 forbidden;
   *         404 when no DataObject with that appId exists.
   */
  @GET
  @Path("/{dataObjectAppId}/notebooks")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
    summary = "List .ipynb file references attached to a DataObject.",
    description =
      "Returns every FileReference (singleton FR1b) and every ShepardFile inside a " +
      "FileBundleReference (FR1a) whose filename ends with .ipynb (case-insensitive). " +
      "The result is ordered: singletons first (by their createdAt ascending), " +
      "then bundle files (by bundle createdAt ascending, then by filename). " +
      "Returns an empty array when no .ipynb files are attached. " +
      "Permission: Read on the parent DataObject."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of .ipynb file references (may be empty).",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(type = SchemaType.ARRAY, implementation = NotebookReferenceIO.class)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response listNotebooks(@PathParam("dataObjectAppId") String dataObjectAppId, @Context SecurityContext sc) {
    // Auth gate — 401 if not logged in
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    // Resolve DataObject OGM id — 404 if not found
    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(dataObjectAppId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // Permission gate — DataObjects don't have their own :Permissions
    // node; access is inherited from the parent Collection. The walk
    // helper does the Cypher hop. Fail-closed if the DO has no parent.
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, AccessType.Read, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    List<NotebookReferenceIO> result = new ArrayList<>();

    // ─── FR1b singletons ─────────────────────────────────────────────────────
    List<FileReference> singletons = singletonService.listByDataObject(dataObjectAppId);
    for (FileReference singleton : singletons) {
      if (singleton.isDeleted()) continue;
      ShepardFile file = singleton.getFile();
      if (file == null) continue;
      String filename = file.getFilename();
      if (!isIpynb(filename)) continue;

      result.add(
        new NotebookReferenceIO(
          singleton.getAppId(),
          filename,
          file.getFileSize(),
          IPYNB_MIME_TYPE,
          singleton.getCreatedAt(),
          singleton.getCreatedBy() != null ? DisplayNameResolver.effectiveDisplayName(singleton.getCreatedBy()) : null,
          ReferenceKind.SINGLETON
        )
      );
    }

    // ─── FR1a bundles ─────────────────────────────────────────────────────────
    List<FileBundleReference> bundles = fileBundleReferenceDAO.findByDataObjectNeo4jId(ogmId);
    for (FileBundleReference bundle : bundles) {
      if (bundle.isDeleted()) continue;
      // Walk the bundle's direct HAS_PAYLOAD files (compatibility shadow kept
      // up to date by FileBundleReferenceService; deduplication is unnecessary
      // because each ShepardFile appears at most once in the shadow list).
      List<ShepardFile> files = bundle.getFiles();
      if (files == null) continue;
      for (ShepardFile file : files) {
        if (file == null) continue;
        String filename = file.getFilename();
        if (!isIpynb(filename)) continue;

        result.add(
          new NotebookReferenceIO(
            bundle.getAppId(),
            filename,
            file.getFileSize(),
            IPYNB_MIME_TYPE,
            bundle.getCreatedAt(),
            bundle.getCreatedBy() != null
              ? DisplayNameResolver.effectiveDisplayName(bundle.getCreatedBy())
              : null,
            ReferenceKind.BUNDLE_FILE
          )
        );
      }
    }

    return Response.ok(result).build();
  }

  /**
   * Returns {@code true} when the given filename ends with {@code .ipynb},
   * case-insensitively. Null / blank filenames never match.
   */
  static boolean isIpynb(String filename) {
    return filename != null && filename.toLowerCase().endsWith(".ipynb");
  }
}
