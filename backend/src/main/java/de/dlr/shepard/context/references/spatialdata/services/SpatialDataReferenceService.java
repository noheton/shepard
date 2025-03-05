package de.dlr.shepard.context.references.spatialdata.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.spatialdata.daos.SpatialDataReferenceDAO;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.context.references.spatialdata.io.SpatialDataReferenceIO;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.spatialdata.daos.SpatialDataContainerDAO;
import de.dlr.shepard.data.spatialdata.endpoints.SpatialDataParamParser;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataQueryParams;
import de.dlr.shepard.data.spatialdata.services.SpatialDataPointService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class SpatialDataReferenceService implements IReferenceService<SpatialDataReference, SpatialDataReferenceIO> {

  private SpatialDataReferenceDAO spatialDataReferenceDAO;
  private SpatialDataPointService dataPointService;
  private UserDAO userDAO;
  private DataObjectDAO dataObjectDAO;
  private SpatialDataContainerDAO spatialDataContainerDAO;
  private DateHelper dateHelper;
  private VersionService versionService;

  @Inject
  public SpatialDataReferenceService(
    SpatialDataReferenceDAO spatialDataReferenceDAO,
    SpatialDataPointService dataPointService,
    UserDAO userDAO,
    DataObjectDAO dataObjectDAO,
    SpatialDataContainerDAO spatialDataContainerDAO,
    DateHelper dateHelper,
    VersionService versionService
  ) {
    this.spatialDataReferenceDAO = spatialDataReferenceDAO;
    this.dataPointService = dataPointService;
    this.userDAO = userDAO;
    this.dataObjectDAO = dataObjectDAO;
    this.spatialDataContainerDAO = spatialDataContainerDAO;
    this.dateHelper = dateHelper;
    this.versionService = versionService;
  }

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
    SpatialDataReferenceIO spatialDataReference,
    String username
  ) {
    var user = userDAO.find(username);
    var dataObject = dataObjectDAO.findLightByNeo4jId(dataObjectShepardId);
    var container = spatialDataContainerDAO.findLightByNeo4jId(spatialDataReference.getSpatialDataContainerId());
    if (container == null || container.isDeleted()) {
      throw new InvalidBodyException(
        String.format(
          "The spatialData container with id %d could not be found.",
          spatialDataReference.getSpatialDataContainerId()
        )
      );
    }
    ObjectMapper objectMapper = new ObjectMapper();
    var toCreate = new SpatialDataReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(spatialDataReference.getName());
    try {
      toCreate.setGeometryFilter(objectMapper.writeValueAsString(spatialDataReference.getGeometryFilter()));
    } catch (JsonProcessingException e) {
      throw new InternalServerErrorException("Failed to parse geometry filter");
    }
    if (spatialDataReference.getMeasurementFilters() != null) {
      try {
        toCreate.setMeasurementsFilter(objectMapper.writeValueAsString(spatialDataReference.getMeasurementFilters()));
      } catch (JsonProcessingException e) {
        throw new InternalServerErrorException("Failed to parse measurement filter");
      }
    }
    if (spatialDataReference.getMetadata() != null) {
      try {
        toCreate.setMetadata(objectMapper.writeValueAsString(spatialDataReference.getMetadata()));
      } catch (JsonProcessingException e) {
        throw new InternalServerErrorException("Failed to parse metadata filter");
      }
    }
    toCreate.setStartTime(spatialDataReference.getStartTime());
    toCreate.setEndTime(spatialDataReference.getEndTime());
    toCreate.setLimit(spatialDataReference.getLimit());
    toCreate.setSkip(spatialDataReference.getSkip());
    toCreate.setSpatialDataContainer(container);

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

    SpatialDataQueryParams params = new SpatialDataQueryParams(
      SpatialDataParamParser.parseGeometryFilter(reference.getGeometryFilter()),
      SpatialDataParamParser.parseMetadata(reference.getMetadata()).orElse(Collections.emptyMap()),
      SpatialDataParamParser.parseMeasurementsFilter(reference.getMeasurementsFilter()).orElse(Collections.emptyList()),
      reference.getStartTime(),
      reference.getEndTime(),
      reference.getLimit(),
      reference.getSkip()
    );
    return dataPointService.getSpatialDataPointIOs(reference.getSpatialDataContainer().getId(), params);
  }
}
