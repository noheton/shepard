package de.dlr.shepard.context.references.structureddata.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.daos.UserDAO;
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
import de.dlr.shepard.context.references.structureddata.daos.StructuredDataReferenceDAO;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.data.structureddata.daos.StructuredDataContainerDAO;
import de.dlr.shepard.data.structureddata.daos.StructuredDataDAO;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.data.structureddata.services.StructuredDataService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class StructuredDataReferenceServiceTest {

  @InjectMock
  StructuredDataReferenceDAO dao;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  StructuredDataService structuredDataService;

  @InjectMock
  DataObjectService dataObjectService;

  @InjectMock
  StructuredDataContainerDAO structuredDataContainerDAO;

  @InjectMock
  StructuredDataDAO structuredDataDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  AuthenticationContext authenticationContext;

  @InjectMock
  UserService userService;

  @Inject
  StructuredDataReferenceService service;

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  private final long collectionId = 14123L;
  private final User user = new User("Testuser");

  @Test
  public void getStructuredDataReferenceByShepardIdTest_successful() {
    DataObject dataObject = new DataObject(123L);
    dataObject.setShepardId(321L);

    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setDataObject(dataObject);
    dataObject.setReferences(List.of(ref));

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);

    StructuredDataReference actual = service.getReference(
      collectionId,
      dataObject.getShepardId(),
      ref.getShepardId(),
      null
    );
    assertEquals(ref, actual);
  }

  @Test
  public void getStructuredDataReferenceByShepardIdTest_notFound() {
    Long shepardId = 15L;
    when(dao.findByShepardId(shepardId)).thenReturn(null);
    var ex = assertThrows(InvalidPathException.class, () -> service.getReference(collectionId, 312L, shepardId, null));
    assertEquals(ex.getMessage(), "ID ERROR - Structured Data Reference with id 15 is null or deleted");
  }

  @Test
  public void getStructuredDataReferenceByShepardIdTest_deleted() {
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(4321L);
    ref.setDataObject(dataObject);
    dataObject.setReferences(List.of(ref));

    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    var ex = assertThrows(InvalidPathException.class, () ->
      service.getReference(collectionId, 312L, ref.getShepardId(), null)
    );
    assertEquals(ex.getMessage(), "ID ERROR - Structured Data Reference with id 15 is null or deleted");
  }

  @Test
  public void getAllStructuredDataReferencesByShepardIdTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    StructuredDataReference ref1 = new StructuredDataReference(1L);
    ref1.setShepardId(15L);
    StructuredDataReference ref2 = new StructuredDataReference(2L);
    ref2.setShepardId(25L);
    dataObject.setReferences(List.of(ref1, ref2));
    when(dao.findByDataObjectShepardId(dataObject.getShepardId())).thenReturn(List.of(ref1, ref2));
    List<StructuredDataReference> actual = service.getAllReferencesByDataObjectId(
      collectionId,
      dataObject.getShepardId(),
      null
    );
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTest() {
    Version version = new Version(new UUID(1L, 2L));
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    StructuredDataContainer container = new StructuredDataContainer(300L);
    container.setMongoId("MongoId");
    Date date = new Date(30L);
    StructuredData structuredData = new StructuredData("oid", new Date(), "name");
    StructuredDataReferenceIO input = new StructuredDataReferenceIO() {
      {
        setName("MyName");
        setStructuredDataOids(new String[] { structuredData.getOid() });
        setStructuredDataContainerId(container.getId());
      }
    };
    StructuredDataReference toCreate = new StructuredDataReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setStructuredDatas(List.of(structuredData));
        setStructuredDataContainer(container);
      }
    };
    StructuredDataReference created = new StructuredDataReference() {
      {
        setId(1L);
        setCreatedAt(toCreate.getCreatedAt());
        setCreatedBy(toCreate.getCreatedBy());
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setStructuredDatas(toCreate.getStructuredDatas());
        setStructuredDataContainer(toCreate.getStructuredDataContainer());
      }
    };
    StructuredDataReference createdWithShepardId = new StructuredDataReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setStructuredDatas(created.getStructuredDatas());
        setStructuredDataContainer(created.getStructuredDataContainer());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(structuredDataDAO.find(container.getId(), structuredData.getOid())).thenReturn(structuredData);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    var actual = service.createReference(collectionId, dataObject.getShepardId(), input);
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTest_notFound() {
    Version version = new Version(new UUID(1L, 2L));
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    StructuredDataContainer container = new StructuredDataContainer(300L);
    container.setMongoId("MongoId");
    Date date = new Date(30L);
    StructuredDataReferenceIO input = new StructuredDataReferenceIO() {
      {
        setName("MyName");
        setStructuredDataOids(new String[] { "oid" });
        setStructuredDataContainerId(container.getId());
      }
    };
    StructuredDataReference toCreate = new StructuredDataReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setStructuredDatas(Collections.emptyList());
        setStructuredDataContainer(container);
      }
    };
    var created = new StructuredDataReference() {
      {
        setId(1L);
        setCreatedAt(toCreate.getCreatedAt());
        setCreatedBy(toCreate.getCreatedBy());
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setStructuredDatas(toCreate.getStructuredDatas());
        setStructuredDataContainer(toCreate.getStructuredDataContainer());
      }
    };
    var createdWithShepardId = new StructuredDataReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setStructuredDatas(created.getStructuredDatas());
        setStructuredDataContainer(created.getStructuredDataContainer());
      }
    };

    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dateHelper.getDate()).thenReturn(date);

    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(structuredDataDAO.find(container.getId(), input.getStructuredDataOids()[0])).thenReturn(null);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(userService.getCurrentUser()).thenReturn(user);

    var actual = service.createReference(collectionId, dataObject.getShepardId(), input);
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTestIsDeleted() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    StructuredDataContainer container = new StructuredDataContainer(300L);
    container.setMongoId("MongoId");
    container.setDeleted(true);
    Date date = new Date(30L);
    StructuredData structuredData = new StructuredData("oid", new Date(), "name");
    StructuredDataReferenceIO input = new StructuredDataReferenceIO() {
      {
        setName("MyName");
        setStructuredDataOids(new String[] { "oid" });
        setStructuredDataContainerId(container.getId());
      }
    };
    StructuredDataReference toCreate = new StructuredDataReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setStructuredDatas(Collections.emptyList());
        setStructuredDataContainer(container);
      }
    };
    var created = new StructuredDataReference() {
      {
        setId(1L);
        setCreatedAt(toCreate.getCreatedAt());
        setCreatedBy(toCreate.getCreatedBy());
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setStructuredDatas(toCreate.getStructuredDatas());
        setStructuredDataContainer(toCreate.getStructuredDataContainer());
      }
    };
    var createdWithShepardId = new StructuredDataReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setStructuredDatas(created.getStructuredDatas());
        setStructuredDataContainer(created.getStructuredDataContainer());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(structuredDataDAO.find(container.getId(), input.getStructuredDataOids()[0])).thenReturn(structuredData);
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(userService.getCurrentUser()).thenReturn(user);
    var ex = assertThrows(InvalidBodyException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
    assertEquals(ex.getMessage(), "ID ERROR - Structured Data Container with id 300 is null or deleted");
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTestContainerIsNull() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    StructuredDataContainer container = new StructuredDataContainer(300L);
    container.setMongoId("MongoId");
    container.setDeleted(true);
    Date date = new Date(30L);
    StructuredData structuredData = new StructuredData("oid", new Date(), "name");
    StructuredDataReferenceIO input = new StructuredDataReferenceIO() {
      {
        setName("MyName");
        setStructuredDataOids(new String[] { "oid" });
        setStructuredDataContainerId(container.getId());
      }
    };
    StructuredDataReference toCreate = new StructuredDataReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setStructuredDatas(Collections.emptyList());
        setStructuredDataContainer(container);
      }
    };
    var created = new StructuredDataReference() {
      {
        setId(1L);
        setCreatedAt(toCreate.getCreatedAt());
        setCreatedBy(toCreate.getCreatedBy());
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setStructuredDatas(toCreate.getStructuredDatas());
        setStructuredDataContainer(toCreate.getStructuredDataContainer());
      }
    };
    var createdWithShepardId = new StructuredDataReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setStructuredDatas(created.getStructuredDatas());
        setStructuredDataContainer(created.getStructuredDataContainer());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(null);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(structuredDataDAO.find(container.getId(), input.getStructuredDataOids()[0])).thenReturn(structuredData);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(userService.getCurrentUser()).thenReturn(user);
    var ex = assertThrows(InvalidBodyException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
    assertEquals(ex.getMessage(), "ID ERROR - Structured Data Container with id 300 is null or deleted");
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTest_ContainerIsDeleted() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    StructuredDataContainer container = new StructuredDataContainer(300L);
    container.setDeleted(true);
    StructuredDataReferenceIO input = new StructuredDataReferenceIO() {
      {
        setName("MyName");
        setStructuredDataOids(new String[] { "oid" });
        setStructuredDataContainerId(container.getId());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    assertThrows(InvalidBodyException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTest_ContainerIsNull() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Long nullContainerId = 300L;
    StructuredDataReferenceIO input = new StructuredDataReferenceIO() {
      {
        setName("MyName");
        setStructuredDataOids(new String[] { "oid" });
        setStructuredDataContainerId(nullContainerId);
      }
    };

    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findLightByNeo4jId(nullContainerId)).thenReturn(null);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    assertThrows(InvalidBodyException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    Date date = new Date(30L);
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    StructuredDataReference expected = new StructuredDataReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedAt(date);
    expected.setUpdatedBy(user);

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(4321L);
    ref.setDataObject(dataObject);
    dataObject.setReferences(List.of(ref));

    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(date);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);

    assertDoesNotThrow(() -> service.deleteReference(collectionId, dataObject.getShepardId(), ref.getShepardId()));
  }

  @Test
  public void getAllPayloadByShepardIdTest() {
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDataContainer(container);
    StructuredData structuredDataA = new StructuredData("abc", new Date(), "name");
    StructuredData structuredDataB = new StructuredData("def", new Date(), "name");
    ref.setStructuredDatas(List.of(structuredDataA, structuredDataB));
    StructuredDataPayload payloadA = new StructuredDataPayload(structuredDataA, "json1");
    StructuredDataPayload payloadB = new StructuredDataPayload(structuredDataB, "json2");

    DataObject dataObject = new DataObject(12345L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(structuredDataService.getPayload(container.getMongoId(), structuredDataA.getOid())).thenReturn(payloadA);
    when(structuredDataService.getPayload(container.getMongoId(), structuredDataB.getOid())).thenReturn(payloadB);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);

    List<StructuredDataPayload> actual = service.getAllPayloads(
      collectionId,
      dataObject.getShepardId(),
      ref.getShepardId()
    );
    assertEquals(List.of(payloadA, payloadB), actual);
  }

  @Test
  public void getAllPayloadByShepardIdTest_notAllowed() {
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDataContainer(container);
    StructuredData structuredDataA = new StructuredData("abc", new Date(), "name");
    StructuredData structuredDataB = new StructuredData("def", new Date(), "name");
    ref.setStructuredDatas(List.of(structuredDataA, structuredDataB));

    DataObject dataObject = new DataObject(12345L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(false);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);

    assertThrows(InvalidAuthException.class, () ->
      service.getAllPayloads(collectionId, dataObject.getShepardId(), ref.getShepardId())
    );

    verify(structuredDataService, never()).getPayload(eq(container.getMongoId()), any(String.class));
  }

  @Test
  public void getAllPayloadByShepardIdTest_unknownOid() {
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDataContainer(container);
    StructuredData structuredDataA = new StructuredData("abc", new Date(), "name");
    StructuredData structuredDataB = new StructuredData("def", new Date(), "name");
    ref.setStructuredDatas(List.of(structuredDataA, structuredDataB));
    StructuredDataPayload payloadA = new StructuredDataPayload(structuredDataA, "json1");

    DataObject dataObject = new DataObject(12345L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(structuredDataService.getPayload(container.getMongoId(), structuredDataA.getOid())).thenReturn(payloadA);
    when(structuredDataService.getPayload(container.getMongoId(), structuredDataB.getOid())).thenThrow(
      new NotFoundException()
    );

    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);

    var actual = service.getAllPayloads(collectionId, dataObject.getShepardId(), ref.getShepardId());
    assertEquals(List.of(payloadA, new StructuredDataPayload(structuredDataB, null)), actual);
  }

  @Test
  public void getAllPayloadByShepardIdTest_isNull_returnsNullPayload() {
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    StructuredData structuredData = new StructuredData("abc", new Date(), "name");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDataContainer(container);
    ref.setStructuredDatas(List.of(structuredData));

    DataObject dataObject = new DataObject(12345L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(structuredDataService.getPayload("mongoId", "abc")).thenThrow(new NotFoundException());
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);

    List<StructuredDataPayload> actual = service.getAllPayloads(
      collectionId,
      dataObject.getShepardId(),
      ref.getShepardId()
    );
    StructuredDataPayload payload = new StructuredDataPayload(structuredData, null);
    assertEquals(List.of(payload), actual);
  }

  @Test
  public void getAllPayloadByShepardIdTest_ContainerIsDeleted_ThrowsNotFoundException() {
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    container.setDeleted(true);
    StructuredData structuredData = new StructuredData("abc", new Date(), "name");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDataContainer(container);
    ref.setStructuredDatas(List.of(structuredData));

    DataObject dataObject = new DataObject(12345L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);

    var ex = assertThrows(NotFoundException.class, () ->
      service.getAllPayloads(collectionId, dataObject.getShepardId(), ref.getShepardId())
    );
    assertEquals(
      "StructuredData Container referenced by StructuredData Reference with Id 15 is null or deleted",
      ex.getMessage()
    );
  }

  @Test
  public void getAllPayloadByShepardIdTest_ContainerIsNull_ThrowsNotFoundException() {
    StructuredData structuredData = new StructuredData("abc", new Date(), "name");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDatas(List.of(structuredData));

    DataObject dataObject = new DataObject(12345L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);

    var ex = assertThrows(NotFoundException.class, () ->
      service.getAllPayloads(collectionId, dataObject.getShepardId(), ref.getShepardId())
    );
    assertEquals(
      "StructuredData Container referenced by StructuredData Reference with Id 15 is null or deleted",
      ex.getMessage()
    );
  }

  @Test
  public void getPayloadByShepardIdTest() {
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");

    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15l);
    ref.setStructuredDataContainer(container);

    StructuredData structuredDataA = new StructuredData("abc", new Date(), "name");
    ref.setStructuredDatas(List.of(structuredDataA));
    StructuredDataPayload payloadA = new StructuredDataPayload(structuredDataA, "json1");

    DataObject dataObject = new DataObject(12345L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(structuredDataService.getPayload(container.getMongoId(), structuredDataA.getOid())).thenReturn(payloadA);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(structuredDataContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);

    var actual = service.getPayload(
      collectionId,
      dataObject.getShepardId(),
      ref.getShepardId(),
      structuredDataA.getOid()
    );
    assertEquals(payloadA, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsNull() {
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15l);

    DataObject dataObject = new DataObject(12345L);
    dataObject.setShepardId(54321L);
    ref.setDataObject(dataObject);
    dataObject.setReferences(List.of(ref));

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);

    assertThrows(InvalidRequestException.class, () ->
      service.getPayload(collectionId, dataObject.getShepardId(), ref.getShepardId(), "oid")
    );
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsDeleted() {
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15l);
    ref.setStructuredDataContainer(container);

    DataObject dataObject = new DataObject(12345L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);

    assertThrows(InvalidRequestException.class, () ->
      service.getPayload(collectionId, dataObject.getShepardId(), ref.getShepardId(), "oid")
    );
  }

  @Test
  public void getPayloadByShepardIdTest_notAllowed() {
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDataContainer(container);
    StructuredData structuredDataA = new StructuredData("abc", new Date(), "name");
    ref.setStructuredDatas(List.of(structuredDataA));

    DataObject dataObject = new DataObject(12345L);
    dataObject.setShepardId(54321L);
    ref.setDataObject(dataObject);
    dataObject.setReferences(List.of(ref));

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(false);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);

    assertThrows(InvalidAuthException.class, () ->
      service.getPayload(collectionId, dataObject.getShepardId(), ref.getShepardId(), structuredDataA.getOid())
    );
  }
}
