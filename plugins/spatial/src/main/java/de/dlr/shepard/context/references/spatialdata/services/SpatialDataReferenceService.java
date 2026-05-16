package de.dlr.shepard.context.references.spatialdata.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.spatialdata.daos.SpatialDataReferenceDAO;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.context.references.spatialdata.io.SpatialDataReferenceIO;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import de.dlr.shepard.data.spatialdata.services.SpatialDataContainerService;
import de.dlr.shepard.data.spatialdata.services.SpatialDataPointService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class SpatialDataReferenceService implements IReferenceService<SpatialDataReference, SpatialDataReferenceIO> {

  @Inject
  SpatialDataReferenceDAO spatialDataReferenceDAO;

  @Inject
  SpatialDataPointService dataPointService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  DateHelper dateHelper;

  @Inject
  UserService userService;

  @Inject
  CollectionService collectionService;

  @Inject
  VersionService versionService;

  @Inject
  SpatialDataContainerService containerService;

  @Inject
  AuthenticationContext authenticationContext;

  @Override
  public List<SpatialDataReference> getAllReferencesByDataObjectId(
    long collectionShepardId,
    long dataObjectShepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    var references = spatialDataReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  @Override
  public SpatialDataReference getReference(
    long collectionShepardId,
    long dataObjectShepardId,
    long shepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    SpatialDataReference spatialDataReference = spatialDataReferenceDAO.findByShepardId(shepardId, versionUID);
    if (spatialDataReference == null || spatialDataReference.isDeleted()) {
      String errorMsg = "ID ERROR - SpatialData Reference with id %s is null or deleted".formatted(shepardId);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    if (
      spatialDataReference.getDataObject() == null ||
      !spatialDataReference.getDataObject().getShepardId().equals(dataObjectShepardId)
    ) {
      String errorMsg = "ID ERROR - There is no association between dataObject and reference".formatted(shepardId);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }
    return spatialDataReference;
  }

  @Override
  public SpatialDataReference createReference(
    long collectionShepardId,
    long dataObjectShepardId,
    SpatialDataReferenceIO spatialDataReferenceIO
  ) {
    var dataObject = dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();

    SpatialDataContainer container;
    try {
      container = containerService.getContainer(spatialDataReferenceIO.getSpatialDataContainerId());
    } catch (InvalidPathException e) {
      throw new InvalidBodyException(e.getMessage());
    }

    ObjectMapper objectMapper = new ObjectMapper();
    var toCreate = new SpatialDataReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(spatialDataReferenceIO.getName());
    if (spatialDataReferenceIO.getGeometryFilter() != null) {
      try {
        toCreate.setGeometryFilter(objectMapper.writeValueAsString(spatialDataReferenceIO.getGeometryFilter()));
      } catch (JsonProcessingException e) {
        throw new InvalidRequestException("Failed to parse geometry filter");
      }
    }
    if (spatialDataReferenceIO.getMeasurementsFilter() != null) {
      try {
        toCreate.setMeasurementsFilter(objectMapper.writeValueAsString(spatialDataReferenceIO.getMeasurementsFilter()));
      } catch (JsonProcessingException e) {
        throw new InvalidRequestException("Failed to parse measurement filter");
      }
    }
    if (spatialDataReferenceIO.getMetadataFilter() != null) {
      try {
        toCreate.setMetadata(objectMapper.writeValueAsString(spatialDataReferenceIO.getMetadataFilter()));
      } catch (JsonProcessingException e) {
        throw new InvalidRequestException("Failed to parse metadata filter");
      }
    }
    toCreate.setStartTime(spatialDataReferenceIO.getStartTime());
    toCreate.setEndTime(spatialDataReferenceIO.getEndTime());
    toCreate.setLimit(spatialDataReferenceIO.getLimit());
    toCreate.setSkip(spatialDataReferenceIO.getSkip());
    toCreate.setSpatialDataContainer(container);

    List<String> validationErrors = toCreate.toSpatialDataQueryParams().validate();
    if (!validationErrors.isEmpty()) {
      throw new InvalidRequestException(
        "The specified parameters contain the following errors: " + String.join(", ", validationErrors)
      );
    }
    SpatialDataReference created = spatialDataReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = spatialDataReferenceDAO.createOrUpdate(created);
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(dataObject.getId(), created.getId());
    return created;
  }

  @Override
  public void deleteReference(long collectionShepardId, long dataObjectShepardId, long shepardId) {
    SpatialDataReference spatialDataReference = getReference(collectionShepardId, dataObjectShepardId, shepardId, null);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();
    spatialDataReference.setDeleted(true);
    spatialDataReference.setUpdatedBy(user);
    spatialDataReference.setUpdatedAt(dateHelper.getDate());
    spatialDataReferenceDAO.createOrUpdate(spatialDataReference);
  }

  public List<SpatialDataPointIO> getReferencePayload(
    long collectionShepardId,
    long dataObjectShepardId,
    long spatialDataReferenceId
  ) {
    SpatialDataReference reference = getReference(
      collectionShepardId,
      dataObjectShepardId,
      spatialDataReferenceId,
      null
    );

    if (reference.getSpatialDataContainer() == null || reference.getSpatialDataContainer().isDeleted()) {
      String errorMsg =
        "Referenced SpatialDataContainer is not set or deleted in SpatialDataReference with id %s".formatted(
            reference.getId()
          );
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    try {
      // the check for the accessibility of the actual spatialDataContainer is done in this function
      return dataPointService.getSpatialDataPointIOs(
        reference.getSpatialDataContainer().getId(),
        reference.toSpatialDataQueryParams()
      );
    } catch (InvalidPathException ex) {
      Log.error(ex.getMessage());
      throw new NotFoundException(ex.getMessage());
    }
  }
}
