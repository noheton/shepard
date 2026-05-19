package de.dlr.shepard.context.references.timeseriesreference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.timeseriesreference.daos.ReferencedTimeseriesNodeEntityDAO;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.model.enums.CsvFormat;
import de.dlr.shepard.data.timeseries.model.enums.FillOption;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesCsvService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class TimeseriesReferenceServiceTest {

  @InjectMock
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  TimeseriesService timeseriesService;

  @InjectMock
  DataObjectService dataObjectService;

  @InjectMock
  ReferencedTimeseriesNodeEntityDAO timeseriesDAO;

  @InjectMock
  UserService userService;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  AuthenticationContext authenticationContext;

  @InjectMock
  TimeseriesCsvService timeseriesCsvService;

  @InjectMock
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  TimeseriesReferenceService referenceService;

  private final long collectionShepardId = 12345L;
  private final User user = new User("Testuser");

  @Test
  public void getTimeseriesReferenceByShepardIdTest_successful() {
    TimeseriesReference ref = new TimeseriesReference(1L);
    ref.setShepardId(15L);

    DataObject dataObject = new DataObject(4321L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    TimeseriesReference actual = referenceService.getReference(
      collectionShepardId,
      dataObject.getShepardId(),
      ref.getShepardId(),
      null
    );
    assertEquals(ref, actual);
  }

  @Test
  public void getTimeseriesReferenceByShepardIdTest_deleted() {
    TimeseriesReference ref = new TimeseriesReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);

    DataObject dataObject = new DataObject(4321L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);

    assertThrows(InvalidPathException.class, () ->
      referenceService.getReference(collectionShepardId, dataObject.getShepardId(), ref.getShepardId(), null)
    );
  }

  @Test
  public void getTimeseriesReferenceByShepardIdTest_notFound() {
    Long shepardId = 15L;

    when(timeseriesReferenceDAO.findByShepardId(shepardId, null)).thenReturn(null);
    assertThrows(InvalidPathException.class, () ->
      referenceService.getReference(collectionShepardId, 54321L, shepardId, null)
    );
  }

  @Test
  public void getTimeseriesReferenceByShepardIdTestIsDeleted() {
    Long shepardId = 15L;
    TimeseriesReference ref = new TimeseriesReference(20L);
    ref.setShepardId(shepardId);
    ref.setDeleted(true);

    DataObject dataObject = new DataObject(4321L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(timeseriesReferenceDAO.findByShepardId(shepardId, null)).thenReturn(ref);
    assertThrows(InvalidPathException.class, () ->
      referenceService.getReference(collectionShepardId, dataObject.getShepardId(), shepardId, null)
    );
  }

  @Test
  public void getAllTimeseriesReferencesTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);

    TimeseriesReference ref1 = new TimeseriesReference(1L);
    ref1.setShepardId(15L);
    TimeseriesReference ref2 = new TimeseriesReference(2L);
    ref2.setShepardId(25L);
    dataObject.setReferences(List.of(ref1, ref2));
    when(timeseriesReferenceDAO.findByDataObjectShepardId(dataObject.getShepardId())).thenReturn(List.of(ref1, ref2));
    List<TimeseriesReference> actual = referenceService.getAllReferencesByDataObjectId(
      collectionShepardId,
      dataObject.getShepardId(),
      null
    );
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createTimeseriesReferenceByShepardIdTest() {
    Version version = new Version(new UUID(1L, 2L));
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    TimeseriesContainer container = new TimeseriesContainer(300L);
    Date date = new Date(30L);
    Timeseries timeseries = new Timeseries("meas", "dev", "loc", "symName", "field");

    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setTimeseries(List.of(timeseries));
        setTimeseriesContainerId(container.getId());
      }
    };
    var toCreate = new TimeseriesReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setStart(input.getStart());
        setEnd(input.getEnd());
        setReferencedTimeseriesList(List.of(new ReferencedTimeseriesNodeEntity(timeseries)));
        setTimeseriesContainer(container);
      }
    };
    var created = new TimeseriesReference() {
      {
        setId(1L);
        setCreatedAt(toCreate.getCreatedAt());
        setCreatedBy(toCreate.getCreatedBy());
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setStart(toCreate.getStart());
        setEnd(toCreate.getEnd());
        setReferencedTimeseriesList(toCreate.getReferencedTimeseriesList());
        setTimeseriesContainer(toCreate.getTimeseriesContainer());
      }
    };
    var createdWithShepardId = new TimeseriesReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setStart(created.getStart());
        setEnd(created.getEnd());
        setReferencedTimeseriesList(created.getReferencedTimeseriesList());
        setTimeseriesContainer(created.getTimeseriesContainer());
      }
    };

    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerService.getContainer(300L)).thenReturn(container);
    when(
      timeseriesDAO.find(
        timeseries.getMeasurement(),
        timeseries.getDevice(),
        timeseries.getLocation(),
        timeseries.getSymbolicName(),
        timeseries.getField()
      )
    ).thenReturn(new ReferencedTimeseriesNodeEntity(timeseries));
    when(timeseriesReferenceDAO.createOrUpdate(toCreate)).thenReturn(created);
    when(timeseriesReferenceDAO.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(true);

    var actual = referenceService.createReference(collectionShepardId, dataObject.getShepardId(), input);
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createTimeseriesReferenceByShepardIdTest_timeseriesNotFound() {
    Version version = new Version(new UUID(1L, 2L));
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    TimeseriesContainer container = new TimeseriesContainer(300L);
    Date date = new Date(30L);
    Timeseries timeseries = new Timeseries("meas", "dev", "loc", "symName", "field");
    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setTimeseries(List.of(timeseries));
        setTimeseriesContainerId(container.getId());
      }
    };
    TimeseriesReference toCreate = new TimeseriesReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setStart(input.getStart());
        setEnd(input.getEnd());
        setReferencedTimeseriesList(List.of(new ReferencedTimeseriesNodeEntity(timeseries)));
        setTimeseriesContainer(container);
      }
    };
    TimeseriesReference created = new TimeseriesReference() {
      {
        setId(1L);
        setCreatedAt(toCreate.getCreatedAt());
        setCreatedBy(toCreate.getCreatedBy());
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setStart(toCreate.getStart());
        setEnd(toCreate.getEnd());
        setReferencedTimeseriesList(toCreate.getReferencedTimeseriesList());
        setTimeseriesContainer(toCreate.getTimeseriesContainer());
      }
    };
    var createdWithShepardId = new TimeseriesReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setStart(created.getStart());
        setEnd(created.getEnd());
        setReferencedTimeseriesList(created.getReferencedTimeseriesList());
        setTimeseriesContainer(created.getTimeseriesContainer());
      }
    };
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerService.getContainer(container.getId())).thenReturn(container);
    when(
      timeseriesDAO.find(
        timeseries.getMeasurement(),
        timeseries.getDevice(),
        timeseries.getLocation(),
        timeseries.getSymbolicName(),
        timeseries.getField()
      )
    ).thenReturn(null);
    when(timeseriesReferenceDAO.createOrUpdate(toCreate)).thenReturn(created);
    when(timeseriesReferenceDAO.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(true);

    TimeseriesReference actual = referenceService.createReference(
      collectionShepardId,
      dataObject.getShepardId(),
      input
    );
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createTimeseriesReferenceByShepardIdTest_invalidTimeseries() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    TimeseriesContainer container = new TimeseriesContainer(300L);
    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setTimeseries(List.of(new Timeseries("me.as", "dev", "loc", "symName", "field")));
        setTimeseriesContainerId(container.getId());
      }
    };

    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(timeseriesContainerService.getContainer(container.getId())).thenReturn(container);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);
    when(userService.getCurrentUser()).thenReturn(user);
    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(true);

    var ex = assertThrows(InvalidBodyException.class, () ->
      referenceService.createReference(collectionShepardId, 2005L, input)
    );
    assertEquals(
      "measurement is not allowed to be empty or contain one of those characters: 'Space, Comma, Point, Slash'",
      ex.getMessage()
    );
  }

  @Test
  public void createTimeseriesReferenceByShepardIdTest_ContainerIsNull() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    TimeseriesContainer container = new TimeseriesContainer(300L);
    container.setDeleted(true);
    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setTimeseries(List.of(new Timeseries("meas", "dev", "loc", "symName", "field")));
        setTimeseriesContainerId(container.getId());
      }
    };

    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(timeseriesContainerService.getContainer(container.getId())).thenThrow(new InvalidPathException());
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);
    when(userService.getCurrentUser()).thenReturn(user);
    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(true);

    var ex = assertThrows(InvalidRequestException.class, () ->
      referenceService.createReference(collectionShepardId, 2005L, input)
    );
  }

  @Test
  public void createTimeseriesReferenceByShepardIdTest_ContainerIsDeleted() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Long containerShepardId = 12345L;
    TimeseriesReferenceIO input = new TimeseriesReferenceIO() {
      {
        setName("MyName");
        setStart(123L);
        setEnd(321L);
        setTimeseries(List.of(new Timeseries("meas", "dev", "loc", "symName", "field")));
        setTimeseriesContainerId(containerShepardId);
      }
    };
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerService.getContainer(12345L)).thenThrow(new InvalidPathException());
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var ex = assertThrows(InvalidRequestException.class, () ->
      referenceService.createReference(collectionShepardId, 2005L, input)
    );
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    Date date = new Date(30L);
    TimeseriesReference ref = new TimeseriesReference(1L);
    ref.setShepardId(15L);
    TimeseriesReference expected = new TimeseriesReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedAt(date);
    expected.setUpdatedBy(user);

    DataObject dataObject = new DataObject(6789L);
    dataObject.setShepardId(67890L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(userService.getCurrentUser()).thenReturn(user);
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(date);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);

    assertDoesNotThrow(() ->
      referenceService.deleteReference(collectionShepardId, dataObject.getShepardId(), ref.getShepardId())
    );
  }

  @Test
  public void getPayloadByShepardIdTest() {
    TimeseriesContainer container = new TimeseriesContainer(2L);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };

    DataObject dataObject = new DataObject(6789L);
    dataObject.setShepardId(67890L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    TimeseriesWithDataPoints timeseriesWithDataPoints = new TimeseriesWithDataPoints(
      ts.toTimeseries(),
      List.of(new TimeseriesDataPoint(50L, 7))
    );
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      ref.getStart(),
      ref.getEnd(),
      10L,
      FillOption.LINEAR,
      AggregateFunction.MEAN
    );
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(
      timeseriesService.getManyTimeseriesWithDataPoints(container.getId(), List.of(ts.toTimeseries()), queryParams)
    ).thenReturn(List.of(timeseriesWithDataPoints));
    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(true);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerService.getContainer(container.getId())).thenReturn(container);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    List<TimeseriesWithDataPoints> actual = referenceService.getReferencedTimeseriesWithDataPointsList(
      collectionShepardId,
      dataObject.getShepardId(),
      ref.getShepardId(),
      AggregateFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("symName"),
      Set.of("meas"),
      Set.of("field")
    );

    assertEquals(List.of(timeseriesWithDataPoints), actual);
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsDeleted() {
    TimeseriesContainer container = new TimeseriesContainer(2L);
    container.setDeleted(true);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };

    DataObject dataObject = new DataObject(6789L);
    dataObject.setShepardId(67890L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(true);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);

    var ex = assertThrows(NotFoundException.class, () ->
      referenceService.getReferencedTimeseriesWithDataPointsList(
        collectionShepardId,
        dataObject.getShepardId(),
        ref.getShepardId(),
        AggregateFunction.MEAN,
        10L,
        FillOption.LINEAR,
        Set.of("dev"),
        Set.of("loc"),
        Set.of("name"),
        Set.of("measurement"),
        Set.of("field")
      )
    );

    assertEquals(
      "Referenced Timeseries Container from reference with id 15 is null or has been deleted",
      ex.getMessage()
    );
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsNull() {
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
      }
    };

    DataObject dataObject = new DataObject(6789L);
    dataObject.setShepardId(67890L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);

    var ex = assertThrows(NotFoundException.class, () ->
      referenceService.getReferencedTimeseriesWithDataPointsList(
        collectionShepardId,
        dataObject.getShepardId(),
        ref.getShepardId(),
        AggregateFunction.MEAN,
        10L,
        FillOption.LINEAR,
        Set.of("dev"),
        Set.of("loc"),
        Set.of("name"),
        Set.of("measurement"),
        Set.of("field")
      )
    );

    assertEquals(
      "Referenced Timeseries Container from reference with id 15 is null or has been deleted",
      ex.getMessage()
    );
  }

  @Test
  public void getPayloadByShepardIdTest_notAllowed() {
    TimeseriesContainer container = new TimeseriesContainer(2L);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };

    DataObject dataObject = new DataObject(6789L);
    dataObject.setShepardId(67890L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(false);
    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerService.getContainer(container.getId())).thenThrow(new InvalidAuthException());

    assertThrows(InvalidAuthException.class, () ->
      referenceService.getReferencedTimeseriesWithDataPointsList(
        collectionShepardId,
        dataObject.getShepardId(),
        15L,
        AggregateFunction.MEAN,
        10L,
        FillOption.LINEAR,
        Set.of("dev"),
        Set.of("loc"),
        Set.of("name"),
        Set.of("measurement"),
        Set.of("field")
      )
    );
  }

  @Test
  public void exportByShepardIdTest() throws IOException, InvalidAuthException {
    ByteArrayInputStream exportedFileStream = new ByteArrayInputStream("Hello World".getBytes());
    TimeseriesContainer container = new TimeseriesContainer(2L);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };

    DataObject dataObject = new DataObject(6789L);
    dataObject.setShepardId(67890L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      ref.getStart(),
      ref.getEnd(),
      10L,
      FillOption.LINEAR,
      AggregateFunction.MEAN
    );

    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(true);
    when(
      timeseriesCsvService.exportManyTimeseriesWithDataPointsToCsv(
        container.getId(),
        List.of(ts.toTimeseries()),
        queryParams,
        CsvFormat.ROW
      )
    ).thenReturn(exportedFileStream);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerService.getContainer(container.getId())).thenReturn(container);
    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(true);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var actual = referenceService.exportReferencedTimeseriesByShepardId(
      collectionShepardId,
      dataObject.getShepardId(),
      ref.getShepardId(),
      AggregateFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("symName"),
      Set.of("meas"),
      Set.of("field"),
      CsvFormat.ROW
    );
    assertEquals(exportedFileStream, actual);
  }

  @Test
  public void exportByShepardIdTest_lessParams() throws IOException, InvalidAuthException {
    ByteArrayInputStream is = new ByteArrayInputStream("Hello World".getBytes());
    TimeseriesContainer container = new TimeseriesContainer(2L);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };

    DataObject dataObject = new DataObject(6789L);
    dataObject.setShepardId(67890L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerService.getContainer(container.getId())).thenReturn(container);
    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(true);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    when(
      referenceService.exportReferencedTimeseriesByShepardId(
        collectionShepardId,
        dataObject.getShepardId(),
        ref.getShepardId(),
        null,
        null,
        null,
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        CsvFormat.ROW
      )
    ).thenReturn(is);
    var actual = referenceService.exportReferencedTimeseriesByShepardId(
      collectionShepardId,
      dataObject.getShepardId(),
      ref.getShepardId(),
      CsvFormat.ROW
    );
    assertEquals(is, actual);
  }

  @Test
  public void exportByShepardIdTest_notAllowed() throws IOException, InvalidAuthException {
    TimeseriesContainer container = new TimeseriesContainer(2L);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };

    DataObject dataObject = new DataObject(6789L);
    dataObject.setShepardId(67890L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerService.getContainer(container.getId())).thenThrow(new InvalidAuthException());
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(false);

    assertThrows(InvalidAuthException.class, () ->
      referenceService.exportReferencedTimeseriesByShepardId(
        collectionShepardId,
        dataObject.getShepardId(),
        15L,
        AggregateFunction.MEAN,
        10L,
        FillOption.LINEAR,
        Set.of("dev"),
        Set.of("loc"),
        Set.of("name"),
        Set.of("measurement"),
        Set.of("field"),
        CsvFormat.ROW
      )
    );
  }

  @Test
  public void exportByShepardIdTest_ContainerIsDeleted() throws IOException {
    TimeseriesContainer container = new TimeseriesContainer(2L);
    container.setDeleted(true);
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
        setTimeseriesContainer(container);
      }
    };

    DataObject dataObject = new DataObject(6789L);
    dataObject.setShepardId(67890L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesContainerService.getContainer(container.getId())).thenReturn(container);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(eq(container.getId()), eq(AccessType.Read), eq(user.getUsername()), anyLong())
    ).thenReturn(true);

    assertThrows(NotFoundException.class, () ->
      referenceService.exportReferencedTimeseriesByShepardId(
        collectionShepardId,
        dataObject.getShepardId(),
        ref.getShepardId(),
        AggregateFunction.MEAN,
        10L,
        FillOption.LINEAR,
        Set.of("dev"),
        Set.of("loc"),
        Set.of("name"),
        Set.of("measurement"),
        Set.of("field"),
        CsvFormat.ROW
      )
    );
  }

  @Test
  public void exportByShepardIdTest_ContainerIsNull() throws IOException {
    ReferencedTimeseriesNodeEntity ts = new ReferencedTimeseriesNodeEntity("meas", "dev", "loc", "symName", "field");
    TimeseriesReference ref = new TimeseriesReference() {
      {
        setId(1L);
        setShepardId(15L);
        setEnd(321);
        setStart(123);
        setReferencedTimeseriesList(List.of(ts));
      }
    };

    DataObject dataObject = new DataObject(6789L);
    dataObject.setShepardId(67890L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(timeseriesReferenceDAO.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionShepardId, dataObject.getShepardId())).thenReturn(dataObject);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    assertThrows(NotFoundException.class, () ->
      referenceService.exportReferencedTimeseriesByShepardId(
        collectionShepardId,
        dataObject.getShepardId(),
        ref.getShepardId(),
        AggregateFunction.MEAN,
        10L,
        FillOption.LINEAR,
        Set.of("dev"),
        Set.of("loc"),
        Set.of("name"),
        Set.of("measurement"),
        Set.of("field"),
        CsvFormat.ROW
      )
    );
  }
}
