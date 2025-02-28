package de.dlr.shepard.context.references.spatialdata.services;

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
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataQueryParams;
import de.dlr.shepard.data.spatialdata.services.SpatialDataPointService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
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
    return null;
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
    var toCreate = new SpatialDataReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(spatialDataReference.getName());
    //    toCreate.setGeometryFilter(spatialDataReference.getGeometryFilter());
    //    toCreate.setMeasurementsFilter(spatialDataReference.getMeasurementFilters());
    toCreate.setStartTime(spatialDataReference.getStartTime());
    toCreate.setEndTime(spatialDataReference.getEndTime());
    toCreate.setLimit(spatialDataReference.getLimit());
    toCreate.setOffset(spatialDataReference.getOffset());
    toCreate.setSkip(spatialDataReference.getSkip());
    toCreate.setSpatialDataContainer(container);

    SpatialDataQueryParams spatialDataParams = new SpatialDataQueryParams(
      spatialDataReference.getGeometryFilter(),
      spatialDataReference.getMetadata(),
      spatialDataReference.getMeasurementFilters(),
      spatialDataReference.getStartTime(),
      spatialDataReference.getEndTime(),
      spatialDataReference.getLimit(),
      spatialDataReference.getOffset(),
      spatialDataReference.getSkip()
    );
    List<SpatialDataPointIO> spatialDataPoints = dataPointService.getSpatialDataPointIOs(
      container.getId(),
      spatialDataParams
    );

    toCreate.setSpatialDataPointsList(spatialDataPoints);

    SpatialDataReference created = spatialDataReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = spatialDataReferenceDAO.createOrUpdate(created);
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(dataObject.getId(), created.getId());
    return created;
  }

  @Override
  public boolean deleteReferenceByShepardId(long shepardId, String username) {
    return false;
  }
}
