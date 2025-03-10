package de.dlr.shepard.context.references.spatialdata.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.spatialdata.daos.SpatialDataReferenceDAO;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.context.references.spatialdata.io.SpatialDataReferenceIO;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.services.SpatialDataPointService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class SpatialDataReferenceService implements IReferenceService<SpatialDataReference, SpatialDataReferenceIO> {

  @Inject
  SpatialDataReferenceDAO spatialDataReferenceDAO;

  @Inject
  SpatialDataPointService dataPointService;

  @Inject
  UserDAO userDAO;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  SpatialDataContainerDAO spatialDataContainerDAO;

  @Inject
  DateHelper dateHelper;

  @Inject
  VersionService versionService;

  public List<SpatialDataReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId, UUID versionUID) {
    var references = spatialDataReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  @Override
  public SpatialDataReference getReferenceByShepardId(long shepardId, UUID versionUID) {
    SpatialDataReference spatialDataReference = spatialDataReferenceDAO.findByShepardId(shepardId, versionUID);
    if (spatialDataReference == null || spatialDataReference.isDeleted()) {
      Log.errorf("SpatialData Reference with id %s is null or deleted", shepardId);
      return null;
    }
    return spatialDataReference;
  }

  @Override
  public SpatialDataReference createReferenceByShepardId(
    long dataObjectShepardId,
    SpatialDataReferenceIO spatialDataReferenceIO,
    String username
  ) {
    var user = userDAO.find(username);
    var dataObject = dataObjectService.getDataObject(dataObjectShepardId);
    var container = spatialDataContainerDAO.findLightByNeo4jId(spatialDataReferenceIO.getSpatialDataContainerId());
    if (container == null || container.isDeleted()) {
      throw new InvalidBodyException(
        String.format(
          "The spatialData container with id %d could not be found.",
          spatialDataReferenceIO.getSpatialDataContainerId()
        )
      );
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
  public boolean deleteReferenceByShepardId(long shepardId, String username) {
    SpatialDataReference spatialDataReference = spatialDataReferenceDAO.findByShepardId(shepardId);
    var user = userDAO.find(username);
    spatialDataReference.setDeleted(true);
    spatialDataReference.setUpdatedBy(user);
    spatialDataReference.setUpdatedAt(dateHelper.getDate());
    spatialDataReferenceDAO.createOrUpdate(spatialDataReference);
    return true;
  }

  public List<SpatialDataPointIO> getReferencePayload(long spatialDataReferenceId) {
    SpatialDataReference reference = getReferenceByShepardId(spatialDataReferenceId, null);

    return dataPointService.getSpatialDataPointIOs(
      reference.getSpatialDataContainer().getId(),
      reference.toSpatialDataQueryParams()
    );
  }
}
