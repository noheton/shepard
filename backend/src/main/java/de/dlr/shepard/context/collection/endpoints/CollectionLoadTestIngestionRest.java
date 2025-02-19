package de.dlr.shepard.context.collection.endpoints;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.configuration.feature.toggles.LoadTestIngestionToggle;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path(Constants.COLLECTIONS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@IfBuildProperty(name = LoadTestIngestionToggle.LOAD_TEST_INGESTION_PROPERTY, stringValue = "true")
public class CollectionLoadTestIngestionRest {

  @Inject
  private CollectionService collectionService;

  @Inject
  private DateHelper dateHelper;

  @Inject
  private UserDAO userDAO;

  @Inject
  private DataObjectDAO dataObjectDAO;

  @Inject
  private VersionService versionService;

  @Context
  private SecurityContext securityContext;

  @POST
  @Tag(name = Constants.COLLECTION)
  @Path("/generate")
  @Operation(description = "Generate collections")
  @APIResponse(description = "ok", responseCode = "200")
  @Parameter(name = "baseName")
  @Parameter(name = "numberOfCollections")
  @Parameter(name = "numberOfDataObjectsPerCollection")
  public Response generateCollections(
    @QueryParam("baseName") String baseName,
    @QueryParam("numberOfCollections") Integer numberOfCollections,
    @QueryParam("numberOfDataObjectsPerCollection") Integer numberOfDataObjectsPerCollection
  ) {
    User user = userDAO.find(securityContext.getUserPrincipal().getName());

    for (int i = 0; i < numberOfCollections; i++) {
      CollectionIO collectionToCreate = new CollectionIO();
      collectionToCreate.setName(baseName + " " + i);
      Collection newCollection = collectionService.createCollection(
        collectionToCreate,
        securityContext.getUserPrincipal().getName()
      );
      for (int j = 0; j < numberOfDataObjectsPerCollection; j++) {
        DataObjectIO dataObjectToCreate = new DataObjectIO();
        dataObjectToCreate.setCollectionId(newCollection.getId());
        dataObjectToCreate.setName(j + "");

        DataObject toCreate = new DataObject();
        toCreate.setName(j + "");
        toCreate.setCollection(newCollection);
        toCreate.setCreatedAt(dateHelper.getDate());
        toCreate.setCreatedBy(user);
        DataObject created = dataObjectDAO.createOrUpdate(toCreate);
        created.setShepardId(created.getId());
        created = dataObjectDAO.createOrUpdate(created);
        versionService.attachToVersionOfVersionableEntityAndReturnVersion(
          newCollection.getShepardId(),
          created.getShepardId()
        );
      }
    }
    return Response.ok().build();
  }
}
