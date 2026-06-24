package de.dlr.shepard.v2.scenegraph.resources;

import de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.io.CreateSceneFromUrdfRequestIO;
import de.dlr.shepard.v2.scenegraph.io.SceneGraphIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.ProvenanceContext;
import de.dlr.shepard.v2.scenegraph.services.ScenegraphFromUrdfService;
import de.dlr.shepard.v2.scenegraph.services.ScenegraphFromUrdfService.ExistingSceneException;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SCENEGRAPH-CREATE-FROM-URDF-1 — REST surface for one-call scene mint
 * from a URDF FileReference.
 *
 * <p>Lives in its own resource class to keep
 * {@link SceneGraphRest} focused on the core CRUD shape and to give the
 * URDF parse-and-build orchestration a dedicated home. The route still
 * lives under the {@code /v2/scene-graphs/...} prefix so it's
 * discoverable from the scene-graph tag in the OpenAPI doc.
 *
 * <p>Auth: any authenticated user; the service-layer permission walk
 * gates on Write to the parent Collection of the FileReference.
 */
@Path("/v2/scene-graphs/from-urdf")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Scene graphs (v2)")
public class ScenegraphFromUrdfRest {

  static final String HEADER_AI_AGENT = "X-AI-Agent";

  @Inject ScenegraphFromUrdfService service;

  @Context ContainerRequestContext requestContext;

  /**
   * Mint a scene from a URDF singleton FileReference in one call.
   *
   * <p>Parses the URDF bytes from the FileReference content, posts one
   * frame per {@code <link>} and one joint per {@code <joint>} via the
   * existing {@link de.dlr.shepard.v2.scenegraph.services.SceneGraphService},
   * and writes a {@code urn:shepard:scenegraph:scene-appId} annotation on
   * the FileReference pointing at the new scene's appId. The frontend's
   * {@code OpenInSceneGraphButton.vue} reads that back-annotation to
   * route to the existing scene on subsequent visits.
   *
   * @param fileReferenceAppId UUID v7 of the singleton FileReference.
   * @param body optional request body with {@code name} + {@code description}.
   * @return 201 + {@link SceneGraphIO} on success; 409 with the existing
   *   scene's appId when one already exists; 400 / 403 / 404 per spec.
   */
  @POST
  @Path("/{fileReferenceAppId}")
  @Operation(
    summary = "Create a scene graph by parsing a URDF FileReference.",
    description =
      "Resolves the singleton `:FileReference` identified by `fileReferenceAppId` (UUID v7), " +
      "streams its URDF XML content, parses it with the java stdlib " +
      "(`javax.xml.parsers.DocumentBuilder`), and materialises one " +
      "`:DigitalTwinScene` with one `:CoordinateFrame` per `<link>` and " +
      "one `:Joint` per `<joint>` — calling through the existing " +
      "`SceneGraphService` so every mutation gets the normal " +
      "`:Activity` capture.\n\n" +
      "Idempotency: if the FileReference already carries a " +
      "`urn:shepard:scenegraph:scene-appId` annotation, the endpoint returns " +
      "409 with the existing scene's appId (the frontend can then route to it).\n\n" +
      "Auth: Write permission on the parent Collection of the FileReference " +
      "(inherited via the DataObject→Collection chain). Read on the FileReference " +
      "is implied — the caller already knows the appId.\n\n" +
      "Side effects: a new `:DigitalTwinScene` is minted; one `:CoordinateFrame` " +
      "and `:Joint` per URDF element is written; a `:SemanticAnnotation` is " +
      "stamped on the FileReference; one umbrella `:Activity` is recorded for " +
      "the whole parse-and-build operation (in addition to the per-mutation " +
      "Activities the service layer mints).\n\n" +
      "Next step: `GET /v2/scene-graphs/{appId}` to read the materialised scene, " +
      "or route the user to `/scene-graphs/{appId}` in the frontend."
  )
  @APIResponse(
    responseCode = "201",
    description = "Scene minted from URDF; body carries the new scene.",
    content = @Content(schema = @Schema(implementation = SceneGraphIO.class))
  )
  @APIResponse(responseCode = "400",
    description = "FileReference is a multi-file bundle, or URDF body is invalid XML / lacks <robot> root.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403",
    description = "Caller lacks Write permission on the parent Collection.")
  @APIResponse(responseCode = "404", description = "No FileReference with that appId.")
  @APIResponse(responseCode = "409",
    description = "Scene already exists for this FileReference; body carries the existing scene's appId.")
  public Response createFromUrdf(
    @PathParam("fileReferenceAppId") @NotBlank String fileReferenceAppId,
    CreateSceneFromUrdfRequestIO body,
    @HeaderParam(HEADER_AI_AGENT) String aiAgent,
    @Context SecurityContext sc
  ) {
    String caller = (sc != null && sc.getUserPrincipal() != null)
      ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    ProvenanceContext prov = ProvenanceContext.from(caller, aiAgent);
    String name = body != null ? body.getName() : null;
    String description = body != null ? body.getDescription() : null;

    try {
      DigitalTwinScene scene = service.createFromUrdf(
        fileReferenceAppId, name, description, prov, caller
      );
      handoffProvenance();
      SceneGraphIO io = new SceneGraphIO(scene, List.of(), List.of());
      return Response.status(Response.Status.CREATED).entity(io).build();
    } catch (ExistingSceneException ese) {
      handoffProvenance();
      String existingAppId = ese.getExistingSceneAppId();
      return Response.status(Response.Status.CONFLICT)
        .entity("{\"detail\":\"scene already exists for this FileReference\","
          + "\"existingSceneAppId\":\"" + escape(existingAppId) + "\"}")
        .type(MediaType.APPLICATION_JSON)
        .build();
    } catch (ForbiddenException fe) {
      return Response.status(Response.Status.FORBIDDEN).entity(errorBody(fe)).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).entity(errorBody(nfe)).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST).entity(errorBody(bre)).build();
    }
  }

  /**
   * Hand off skip-capture to the {@link ProvenanceCaptureFilter} so it
   * does not emit a duplicate generic Activity. The service layer
   * records its own umbrella + per-mutation activities.
   */
  private void handoffProvenance() {
    try {
      if (requestContext != null) {
        requestContext.setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
      }
    } catch (RuntimeException e) {
      Log.debug("SCENEGRAPH-CREATE-FROM-URDF: skip-capture handoff failed (non-fatal)", e);
    }
  }

  private static String errorBody(Throwable t) {
    String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    return "{\"detail\":\"" + escape(msg) + "\"}";
  }

  private static String escape(String s) {
    return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
