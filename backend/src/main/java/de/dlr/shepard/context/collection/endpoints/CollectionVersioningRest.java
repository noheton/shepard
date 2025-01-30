package de.dlr.shepard.context.collection.endpoints;

import de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.context.version.io.VersionIO;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path(Constants.COLLECTIONS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@IfBuildProperty(name = VersioningFeatureToggle.TOGGLE_PROPERTY, stringValue = "true")
public class CollectionVersioningRest {

  private VersionService versionService;

  @Context
  private SecurityContext securityContext;

  CollectionVersioningRest() {}

  @Inject
  public CollectionVersioningRest(VersionService versionService) {
    this.versionService = versionService;
  }

  @GET
  @Path("/{" + Constants.COLLECTION_ID + "}/" + Constants.VERSIONS)
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get versions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = VersionIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  public Response getVersions(@PathParam(Constants.COLLECTION_ID) long collectionId) {
    List<Version> versions = versionService.getAllVersions(collectionId);
    var result = new ArrayList<VersionIO>(versions.size());
    for (var version : versions) {
      result.add(new VersionIO(version));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.COLLECTION_ID + "}/" + Constants.VERSIONS + "/{" + Constants.VERSION_UID + "}")
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get version")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = VersionIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getVersion(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.VERSION_UID) UUID versionUID
  ) {
    Version version = versionService.getVersion(versionUID);
    return Response.ok(new VersionIO(version)).build();
  }

  @POST
  @Path("/{" + Constants.COLLECTION_ID + "}/" + Constants.VERSIONS)
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Create a new version")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = VersionIO.class))
  )
  @Parameter(name = Constants.COLLECTION_ID)
  public Response createVersion(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = VersionIO.class))
    ) @Valid VersionIO version
  ) {
    Version newVersion = versionService.createVersion(
      collectionId,
      version,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new VersionIO(newVersion)).status(Status.CREATED).build();
  }
}
