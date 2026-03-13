package de.dlr.shepard.context.references.timeseriesreference.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.IReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.daos.ReferencedTimeseriesNodeEntityDAO;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.model.enums.CsvFormat;
import de.dlr.shepard.data.timeseries.model.enums.FillOption;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesCsvService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.data.timeseries.utilities.TimeseriesValidator;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RequestScoped
public class TimeseriesReferenceService implements IReferenceService<TimeseriesReference, TimeseriesReferenceIO> {

  @Inject
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesCsvService timeseriesCsvService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  ReferencedTimeseriesNodeEntityDAO timeseriesDAO;

  @Inject
  UserService userService;

  @Inject
  CollectionService collectionService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  VersionService versionService;

  @Inject
  DateHelper dateHelper;

  @Inject
  PermissionsService permissionsService;

  /**
   * Gets TimeseriesReference list for a given dataobject.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param versionUID the version UUID
   * @return List<TimeseriesReference>
   * @throws InvalidPathException If collection or dataobject cannot be found, or no association between dataobject and collection exists
   * @throws InvalidAuthException If user has no read permissions on collection or dataobject specified by request path
   */
  @Override
  public List<TimeseriesReference> getAllReferencesByDataObjectId(
    long collectionShepardId,
    long dataObjectShepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    var references = timeseriesReferenceDAO.findByDataObjectShepardId(dataObjectShepardId);
    return references;
  }

  /**
   * Gets TimeseriesReference by shepard id.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param shepardId
   * @param versionUID the version UUID
   * @return TimeseriesReference
   * @throws InvalidPathException If reference with Id does not exist or is deleted, or if collection or dataObject Id of path is not valid
   * @throws InvalidAuthException If user has no read permissions on collection or dataobject specified by request path
   */
  @Override
  public TimeseriesReference getReference(
    long collectionShepardId,
    long dataObjectShepardId,
    long shepardId,
    UUID versionUID
  ) {
    dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId, versionUID);

    TimeseriesReference reference = timeseriesReferenceDAO.findByShepardId(shepardId, versionUID);
    if (reference == null || reference.isDeleted()) {
      String errorMsg = "ID ERROR - Timeseries Reference with id %s is null or deleted".formatted(shepardId);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    if (reference.getDataObject() == null || !reference.getDataObject().getShepardId().equals(dataObjectShepardId)) {
      String errorMsg = "ID ERROR - There is no association between dataObject and reference";
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }

    return reference;
  }

  /**
   * Creates a new TimeseriesReference reference
   *
   * @param collectionShepardId
   * @param dataObjectShepardId DataObject id for the reference to be created
   * @param timeseriesReference Reference object
   * @return TimeseriesReference
   * @throws InvalidPathException if collection or dataobject specified by their Ids are null or deleted
   * @throws InvalidAuthException if user has no permission to edit referencing collection or no read permissions on referenced container
   * @throws InvalidRequestException if user provides a timeseries reference with a non-accessible container
   */
  @Override
  public TimeseriesReference createReference(
    long collectionShepardId,
    long dataObjectShepardId,
    TimeseriesReferenceIO timeseriesReference
  ) {
    DataObject dataObject = dataObjectService.getDataObject(collectionShepardId, dataObjectShepardId);
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();

    TimeseriesContainer container;
    try {
      container = timeseriesContainerService.getContainer(timeseriesReference.getTimeseriesContainerId());
    } catch (InvalidPathException ex) {
      Log.error(ex.getMessage());
      throw new InvalidRequestException(ex.getMessage());
    }

    // sanitize timeseries
    timeseriesReference
      .getTimeseries()
      .forEach(timeseries -> TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries));

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
    versionService.attachToVersionOfVersionableEntityAndReturnVersion(dataObject.getId(), created.getId());
    return created;
  }

  /**
   * Deletes the Timeseries reference.
   *
   * @param collectionShepardId
   * @param dataObjectShepardId
   * @param timeseriesReferenceShepardId
   * @throws InvalidPathException if collection or dataobject specified by their Ids are null or deleted
   * @throws InvalidAuthException if user has no permissions to edit the collection, which the reference is assigned to
   */
  @Override
  public void deleteReference(long collectionShepardId, long dataObjectShepardId, long timeseriesReferenceShepardId) {
    TimeseriesReference timeseriesReference = getReference(
      collectionShepardId,
      dataObjectShepardId,
      timeseriesReferenceShepardId,
      null
    );
    collectionService.assertIsAllowedToEditCollection(collectionShepardId);

    User user = userService.getCurrentUser();
    timeseriesReference.setDeleted(true);
    timeseriesReference.setUpdatedAt(dateHelper.getDate());
    timeseriesReference.setUpdatedBy(user);
    timeseriesReferenceDAO.createOrUpdate(timeseriesReference);
  }

  public List<TimeseriesWithDataPoints> getReferencedTimeseriesWithDataPointsList(
    long collectionShepardId,
    long dataObjectShepardId,
    long timeseriesShepardId,
    AggregateFunction function,
    Long timeSliceNanoseconds,
    FillOption fillOption,
    Set<String> devicesFilterSet,
    Set<String> locationsFilterSet,
    Set<String> symbolicNameFilterSet,
    Set<String> measurementFilterSet,
    Set<String> fieldFilterSet
  ) {
    TimeseriesReference reference = getReference(collectionShepardId, dataObjectShepardId, timeseriesShepardId, null);

    if (reference.getTimeseriesContainer() == null || reference.getTimeseriesContainer().isDeleted()) {
      String errorMsg =
        "Referenced Timeseries Container from reference with id %s is null or has been deleted".formatted(
            timeseriesShepardId
          );
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    try {
      // check that referenced container is actually accessible
      timeseriesContainerService.getContainer(reference.getTimeseriesContainer().getId());
    } catch (InvalidPathException ex) {
      throw new NotFoundException(ex.getMessage());
    }

    var timeseriesList = reference.getReferencedTimeseriesList().stream().map(ts -> ts.toTimeseries()).toList();
    var filteredTimeseriesList = timeseriesList
      .stream()
      .filter(timeseries ->
        matchFilter(
          timeseries,
          devicesFilterSet,
          locationsFilterSet,
          symbolicNameFilterSet,
          measurementFilterSet,
          fieldFilterSet
        )
      )
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
    long collectionShepardId,
    long dataObjectShepardId,
    long timeseriesShepardId,
    AggregateFunction function,
    Long timeSliceNanoseconds,
    FillOption fillOption,
    Set<String> devicesFilterSet,
    Set<String> locationsFilterSet,
    Set<String> symbolicNameFilterSet,
    Set<String> measurementFilterSet,
    Set<String> fieldFilterSet,
    CsvFormat csvFormat
  ) throws IOException {
    TimeseriesReference reference = getReference(collectionShepardId, dataObjectShepardId, timeseriesShepardId, null);

    if (reference.getTimeseriesContainer() == null || reference.getTimeseriesContainer().isDeleted()) {
      String errorMsg =
        "The referenced TimeseriesContainer is null or deleted for Reference with id %s".formatted(timeseriesShepardId);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    try {
      timeseriesContainerService.getContainer(reference.getTimeseriesContainer().getId());
    } catch (InvalidPathException ex) {
      throw new InvalidRequestException(ex.getMessage());
    }

    var timeseriesList = reference.getReferencedTimeseriesList().stream().map(ts -> ts.toTimeseries()).toList();
    var filteredTimeseriesList = timeseriesList
      .stream()
      .filter(timeseries ->
        matchFilter(
          timeseries,
          devicesFilterSet,
          locationsFilterSet,
          symbolicNameFilterSet,
          measurementFilterSet,
          fieldFilterSet
        )
      )
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
      queryParams,
      csvFormat
    );
  }

  public InputStream exportReferencedTimeseriesByShepardId(
    long collectionShepardId,
    long dataObjectShepardId,
    long referenceId,
    CsvFormat csvFormat
  ) throws IOException {
    return exportReferencedTimeseriesByShepardId(
      collectionShepardId,
      dataObjectShepardId,
      referenceId,
      null,
      null,
      null,
      Collections.emptySet(),
      Collections.emptySet(),
      Collections.emptySet(),
      Collections.emptySet(),
      Collections.emptySet(),
      csvFormat
    );
  }

  private boolean matchFilter(
    Timeseries timeseries,
    Set<String> device,
    Set<String> location,
    Set<String> symName,
    Set<String> measurement,
    Set<String> field
  ) {
    var deviceMatches = true;
    var locationMatches = true;
    var symbolicNameMatches = true;
    var measurementMatches = true;
    var fieldMatches = true;
    if (!device.isEmpty()) {
      deviceMatches = device.contains(timeseries.getDevice());
    }
    if (!location.isEmpty()) {
      locationMatches = location.contains(timeseries.getLocation());
    }
    if (!symName.isEmpty()) {
      symbolicNameMatches = symName.contains(timeseries.getSymbolicName());
    }
    if (!measurement.isEmpty()) {
      measurementMatches = measurement.contains(timeseries.getMeasurement());
    }
    if (!field.isEmpty()) {
      fieldMatches = field.contains(timeseries.getField());
    }
    return deviceMatches && locationMatches && symbolicNameMatches && measurementMatches && fieldMatches;
  }
}
