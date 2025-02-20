package de.dlr.shepard.context.references.timeseriesreference.services;

import de.dlr.shepard.auth.security.PermissionsUtil;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.daos.ReferencedTimeseriesNodeEntityDAO;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.model.enums.FillOption;
import de.dlr.shepard.data.timeseries.services.TimeseriesCsvService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.data.timeseries.utilities.TimeseriesValidator;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RequestScoped
public class TimeseriesReferenceService implements IReferenceService<TimeseriesReference, TimeseriesReferenceIO> {

  private TimeseriesReferenceDAO timeseriesReferenceDAO;
  private TimeseriesService timeseriesService;
  private TimeseriesCsvService timeseriesCsvService;
  private DataObjectDAO dataObjectDAO;
  private TimeseriesContainerDAO timeseriesContainerDAO;
  private ReferencedTimeseriesNodeEntityDAO timeseriesDAO;
  private UserDAO userDAO;
  private VersionService versionService;
  private DateHelper dateHelper;
  private PermissionsUtil permissionsUtil;

  TimeseriesReferenceService() {}

  @Inject
  public TimeseriesReferenceService(
    TimeseriesReferenceDAO timeseriesReferenceDAO,
    TimeseriesService timeseriesService,
    TimeseriesCsvService timeseriesCsvService,
    DataObjectDAO dataObjectDAO,
    TimeseriesContainerDAO timeseriesContainerDAO,
    ReferencedTimeseriesNodeEntityDAO timeseriesDAO,
    UserDAO userDAO,
    VersionService versionService,
    DateHelper dateHelper,
    PermissionsUtil permissionsUtil
  ) {
    this.timeseriesReferenceDAO = timeseriesReferenceDAO;
    this.timeseriesService = timeseriesService;
    this.timeseriesCsvService = timeseriesCsvService;
    this.dataObjectDAO = dataObjectDAO;
    this.timeseriesContainerDAO = timeseriesContainerDAO;
    this.timeseriesDAO = timeseriesDAO;
    this.userDAO = userDAO;
    this.versionService = versionService;
    this.dateHelper = dateHelper;
    this.permissionsUtil = permissionsUtil;
  }

  @Override
  public List<TimeseriesReference> getAllReferencesByDataObjectShepardId(long dataObjectShepardId, UUID versionUID) {
    var references = timeseriesReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  @Override
  public TimeseriesReference getReferenceByShepardId(long shepardId, UUID versionUID) {
    var reference = timeseriesReferenceDAO.findByShepardId(shepardId, versionUID);
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
    var dataObject = dataObjectDAO.findByShepardId(dataObjectShepardId, true);
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
    timeseriesReference
      .getReferencedTimeseriesList()
      .forEach(referencedTimeseries ->
        TimeseriesValidator.assertTimeseriesPropertiesAreValid(referencedTimeseries.toTimeseries())
      );

    var toCreate = new TimeseriesReference();
    toCreate.setCreatedAt(dateHelper.getDate());
    toCreate.setCreatedBy(user);
    toCreate.setDataObject(dataObject);
    toCreate.setName(timeseriesReference.getName());
    toCreate.setStart(timeseriesReference.getStart());
    toCreate.setEnd(timeseriesReference.getEnd());
    toCreate.setTimeseriesContainer(container);

    for (var ts : timeseriesReference.getReferencedTimeseriesList()) {
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
        toCreate.addTimeseries(ts);
      }
    }
    TimeseriesReference created = timeseriesReferenceDAO.createOrUpdate(toCreate);
    created.setShepardId(created.getId());
    created = timeseriesReferenceDAO.createOrUpdate(created);
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(dataObject.getId(), created.getId());
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

  public List<TimeseriesWithDataPoints> getReferencedTimeseriesWithDataPointsList(
    long timeseriesShepardId,
    AggregateFunction function,
    Long timeSliceNanoseconds,
    FillOption fillOption,
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
      .getReferencedTimeseriesList()
      .stream()
      .map(ts -> new TimeseriesWithDataPoints(ts.toTimeseries(), Collections.emptyList()))
      .toList();

    var timeseriesList = reference.getReferencedTimeseriesList().stream().map(ts -> ts.toTimeseries()).toList();
    var filteredTimeseriesList = timeseriesList
      .stream()
      .filter(timeseries -> matchFilter(timeseries, devicesFilterSet, locationsFilterSet, symbolicNameFilterSet))
      .toList();
    var containerId = reference.getTimeseriesContainer().getId();
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      reference.getStart(),
      reference.getEnd(),
      timeSliceNanoseconds,
      fillOption,
      function
    );

    return timeseriesService.getManyTimeseriesWithDataPoints(containerId, filteredTimeseriesList, queryParams);
  }

  public InputStream exportReferencedTimeseriesByShepardId(
    long timeseriesShepardId,
    AggregateFunction function,
    Long timeSliceNanoseconds,
    FillOption fillOption,
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

    var timeseriesList = reference.getReferencedTimeseriesList().stream().map(ts -> ts.toTimeseries()).toList();
    var filteredTimeseriesList = timeseriesList
      .stream()
      .filter(timeseries -> matchFilter(timeseries, devicesFilterSet, locationsFilterSet, symbolicNameFilterSet))
      .toList();
    var containerId = reference.getTimeseriesContainer().getId();
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      reference.getStart(),
      reference.getEnd(),
      timeSliceNanoseconds,
      fillOption,
      function
    );

    return timeseriesCsvService.exportManyTimeseriesWithDataPointsToCsv(
      containerId,
      filteredTimeseriesList,
      queryParams
    );
  }

  public InputStream exportReferencedTimeseriesByShepardId(long referenceId, String username) throws IOException {
    return exportReferencedTimeseriesByShepardId(
      referenceId,
      null,
      null,
      null,
      Collections.emptySet(),
      Collections.emptySet(),
      Collections.emptySet(),
      username
    );
  }

  private boolean matchFilter(Timeseries timeseries, Set<String> device, Set<String> location, Set<String> symName) {
    var deviceMatches = true;
    var locatioMatches = true;
    var symbolicNameMatches = true;
    if (!device.isEmpty()) {
      deviceMatches = device.contains(timeseries.getDevice());
    }
    if (!location.isEmpty()) {
      locatioMatches = location.contains(timeseries.getLocation());
    }
    if (!symName.isEmpty()) {
      symbolicNameMatches = symName.contains(timeseries.getSymbolicName());
    }
    return deviceMatches && locatioMatches && symbolicNameMatches;
  }
}
