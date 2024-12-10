package de.dlr.shepard.timeseriesreference;

import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.influxtimeseries.InfluxFillOption;
import de.dlr.shepard.influxtimeseries.InfluxSingleValuedUnaryFunction;
import de.dlr.shepard.influxtimeseries.InfluxTimeseriesPayload;
import de.dlr.shepard.influxtimeseries.InfluxTimeseriesService;
import de.dlr.shepard.influxtimeseries.InfluxUtil;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.services.IReferenceService;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.DateHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RequestScoped
public class TimeseriesReferenceService implements IReferenceService<TimeseriesReference, TimeseriesReferenceIO> {

  private TimeseriesReferenceDAO timeseriesReferenceDAO;
  private InfluxTimeseriesService timeseriesService;
  private DataObjectDAO dataObjectDAO;
  private TimeseriesContainerDAO timeseriesContainerDAO;
  private ReferencedTimeseriesNodeEntityDAO timeseriesDAO;
  private UserDAO userDAO;
  private VersionDAO versionDAO;
  private DateHelper dateHelper;
  private PermissionsUtil permissionsUtil;

  TimeseriesReferenceService() {}

  @Inject
  public TimeseriesReferenceService(
    TimeseriesReferenceDAO timeseriesReferenceDAO,
    InfluxTimeseriesService timeseriesService,
    DataObjectDAO dataObjectDAO,
    TimeseriesContainerDAO timeseriesContainerDAO,
    ReferencedTimeseriesNodeEntityDAO timeseriesDAO,
    UserDAO userDAO,
    VersionDAO versionDAO,
    DateHelper dateHelper,
    PermissionsUtil permissionsUtil
  ) {
    this.timeseriesReferenceDAO = timeseriesReferenceDAO;
    this.timeseriesService = timeseriesService;
    this.dataObjectDAO = dataObjectDAO;
    this.timeseriesContainerDAO = timeseriesContainerDAO;
    this.timeseriesDAO = timeseriesDAO;
    this.userDAO = userDAO;
    this.versionDAO = versionDAO;
    this.dateHelper = dateHelper;
    this.permissionsUtil = permissionsUtil;
  }

  @Override
  public List<TimeseriesReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId) {
    var references = timeseriesReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  @Override
  public TimeseriesReference getReferenceByShepardId(long shepardId) {
    var reference = timeseriesReferenceDAO.findByShepardId(shepardId);
    if (reference == null || reference.isDeleted()) {
      Log.errorf("Timeseries Reference with id %s is null or deleted", shepardId);
      return null;
    }
    return reference;
  }

  @Override
  public TimeseriesReference createReferenceByShepardId(
    long dataObjectShepardId,
    TimeseriesReferenceIO timeseriesReference,
    String username
  ) {
    var user = userDAO.find(username);
    var dataObject = dataObjectDAO.findLightByShepardId(dataObjectShepardId);
    var container = timeseriesContainerDAO.findLightByNeo4jId(timeseriesReference.getTimeseriesContainerId());
    if (container == null || container.isDeleted()) {
      throw new InvalidBodyException(
        String.format(
          "The timeseries container with id %d could not be found.",
          timeseriesReference.getTimeseriesContainerId()
        )
      );
    }
    // sanitize timeseries
    var errors = Arrays.stream(timeseriesReference.getTimeseries())
      .map(InfluxUtil::sanitize)
      .filter(e -> !e.isBlank())
      .toList();
    if (!errors.isEmpty()) throw new InvalidBodyException(
      "The timeseries list contains illegal characters: " + String.join(", ", errors)
    );
    var toCreate = new TimeseriesReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(timeseriesReference.getName());
    toCreate.setStart(timeseriesReference.getStart());
    toCreate.setEnd(timeseriesReference.getEnd());
    toCreate.setTimeseriesContainer(container);

    for (var ts : timeseriesReference.getTimeseries()) {
      var found = timeseriesDAO.find(
        ts.getMeasurement(),
        ts.getDevice(),
        ts.getLocation(),
        ts.getSymbolicName(),
        ts.getField()
      );
      if (found != null) {
        toCreate.addTimeseries(found);
      } else {
        toCreate.addTimeseries(new ReferencedTimeseriesNodeEntity(ts));
      }
    }
    TimeseriesReference created = timeseriesReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = timeseriesReferenceDAO.createOrUpdate(created);
    Version version = versionDAO.findVersionLightByNeo4jId(dataObject.getId());
    versionDAO.createLink(created.getId(), version.getUid());
    return created;
  }

  @Override
  public boolean deleteReferenceByShepardId(long timeseriesShepardId, String username) {
    var user = userDAO.find(username);

    var old = timeseriesReferenceDAO.findByShepardId(timeseriesShepardId);
    old.setDeleted(true);
    old.setUpdatedAt(dateHelper.getDate());
    old.setUpdatedBy(user);

    timeseriesReferenceDAO.createOrUpdate(old);
    return true;
  }

  public List<InfluxTimeseriesPayload> getTimeseriesPayloadByShepardId(
    long timeseriesShepardId,
    InfluxSingleValuedUnaryFunction function,
    Long groupBy,
    InfluxFillOption fillOption,
    Set<String> devicesFilterSet,
    Set<String> locationsFilterSet,
    Set<String> symbolicNameFilterSet,
    String username
  ) {
    var reference = timeseriesReferenceDAO.findByShepardId(timeseriesShepardId);
    if (
      reference.getTimeseriesContainer() == null ||
      reference.getTimeseriesContainer().isDeleted() ||
      !permissionsUtil.isAccessTypeAllowedForUser(reference.getTimeseriesContainer().getId(), AccessType.Read, username)
    ) return reference
      .getTimeseries()
      .stream()
      .map(ts -> new InfluxTimeseriesPayload(ts.toInfluxTimeseries(), Collections.emptyList()))
      .toList();

    var database = reference.getTimeseriesContainer().getDatabase();
    return timeseriesService.getTimeseriesPayloadList(
      reference.getStart(),
      reference.getEnd(),
      database,
      reference.getTimeseries().stream().map(t -> t.toInfluxTimeseries()).toList(),
      function,
      groupBy,
      fillOption,
      devicesFilterSet,
      locationsFilterSet,
      symbolicNameFilterSet
    );
  }

  public InputStream exportTimeseriesPayloadByShepardId(
    long timeseriesShepardId,
    InfluxSingleValuedUnaryFunction function,
    Long groupBy,
    InfluxFillOption fillOption,
    Set<String> devicesFilterSet,
    Set<String> locationsFilterSet,
    Set<String> symbolicNameFilterSet,
    String username
  ) throws IOException {
    var reference = timeseriesReferenceDAO.findByShepardId(timeseriesShepardId);
    if (
      reference.getTimeseriesContainer() == null || reference.getTimeseriesContainer().isDeleted()
    ) throw new InvalidRequestException("The timeseries container in question is not accessible");
    if (
      !permissionsUtil.isAccessTypeAllowedForUser(reference.getTimeseriesContainer().getId(), AccessType.Read, username)
    ) throw new InvalidAuthException("You are not authorized to access this timeseries");

    var database = reference.getTimeseriesContainer().getDatabase();

    return timeseriesService.exportTimeseriesPayload(
      reference.getStart(),
      reference.getEnd(),
      database,
      reference.getTimeseries().stream().map(t -> t.toInfluxTimeseries()).toList(),
      function,
      groupBy,
      fillOption,
      devicesFilterSet,
      locationsFilterSet,
      symbolicNameFilterSet
    );
  }

  public InputStream exportTimeseriesPayloadByShepardId(long timeseriesId, String username) throws IOException {
    return exportTimeseriesPayloadByShepardId(
      timeseriesId,
      null,
      null,
      null,
      Collections.emptySet(),
      Collections.emptySet(),
      Collections.emptySet(),
      username
    );
  }
}
