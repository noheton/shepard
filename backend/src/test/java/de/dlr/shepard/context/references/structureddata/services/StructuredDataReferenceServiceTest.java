package de.dlr.shepard.context.references.structureddata.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
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
import de.dlr.shepard.data.structureddata.services.StructuredDataService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
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
  DataObjectDAO dataObjectDAO;

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

  @Inject
  StructuredDataReferenceService service;

  @Test
  public void getStructuredDataReferenceByShepardIdTest_successful() {
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    StructuredDataReference actual = service.getReferenceByShepardId(ref.getShepardId(), null);
    assertEquals(ref, actual);
  }

  @Test
  public void getStructuredDataReferenceByShepardIdTest_notFound() {
    Long shepardId = 15L;
    when(dao.findByShepardId(shepardId)).thenReturn(null);
    StructuredDataReference actual = service.getReferenceByShepardId(shepardId, null);
    assertNull(actual);
  }

  @Test
  public void getStructuredDataReferenceByShepardIdTest_deleted() {
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    StructuredDataReference actual = service.getReferenceByShepardId(ref.getShepardId(), null);
    assertNull(actual);
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
    List<StructuredDataReference> actual = service.getAllReferencesByDataObjectShepardId(
      dataObject.getShepardId(),
      null
    );
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTest() {
    User user = new User("Bob");
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
    when(dataObjectDAO.findByShepardId(dataObject.getShepardId(), true)).thenReturn(dataObject);
    when(structuredDataContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(structuredDataDAO.find(container.getId(), structuredData.getOid())).thenReturn(structuredData);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    var actual = service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername());
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTest_notFound() {
    User user = new User("Bob");
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
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findByShepardId(dataObject.getShepardId(), true)).thenReturn(dataObject);
    when(structuredDataContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(structuredDataDAO.find(container.getId(), input.getStructuredDataOids()[0])).thenReturn(null);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    var actual = service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername());
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTestIsDeleted() {
    User user = new User("Bob");
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
    when(dataObjectDAO.findByShepardId(dataObject.getShepardId(), true)).thenReturn(dataObject);
    when(structuredDataContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(structuredDataDAO.find(container.getId(), input.getStructuredDataOids()[0])).thenReturn(structuredData);
    var ex = assertThrows(InvalidBodyException.class, () ->
      service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername())
    );
    assertEquals(ex.getMessage(), "invalid container");
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTestContainerIsNull() {
    User user = new User("Bob");
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
    when(dataObjectDAO.findByShepardId(dataObject.getShepardId(), true)).thenReturn(dataObject);
    when(structuredDataContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(null);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(structuredDataDAO.find(container.getId(), input.getStructuredDataOids()[0])).thenReturn(structuredData);
    var ex = assertThrows(InvalidBodyException.class, () ->
      service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername())
    );
    assertEquals(ex.getMessage(), "invalid container");
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTest_ContainerIsDeleted() {
    User user = new User("Bob");
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
    when(dataObjectDAO.findByShepardId(dataObject.getShepardId(), true)).thenReturn(dataObject);
    when(structuredDataContainerDAO.findLightByNeo4jId(container.getId())).thenReturn(container);
    assertThrows(InvalidBodyException.class, () ->
      service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername())
    );
  }

  @Test
  public void createStructuredDataReferenceByShepardIdTest_ContainerIsNull() {
    User user = new User("Bob");
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
    when(dataObjectDAO.findByShepardId(dataObject.getShepardId(), true)).thenReturn(dataObject);
    when(structuredDataContainerDAO.findLightByNeo4jId(nullContainerId)).thenReturn(null);
    assertThrows(InvalidBodyException.class, () ->
      service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername())
    );
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    User user = new User("Bob");
    Date date = new Date(30L);
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    StructuredDataReference expected = new StructuredDataReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedAt(date);
    expected.setUpdatedBy(user);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(date);
    boolean actual = service.deleteReferenceByShepardId(ref.getShepardId(), user.getUsername());
    verify(dao).createOrUpdate(expected);
    assertTrue(actual);
  }

  @Test
  public void getAllPayloadByShepardIdTest() {
    String username = "blob";
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
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    when(structuredDataService.getPayload(container.getMongoId(), structuredDataA.getOid())).thenReturn(payloadA);
    when(structuredDataService.getPayload(container.getMongoId(), structuredDataB.getOid())).thenReturn(payloadB);
    List<StructuredDataPayload> actual = service.getAllPayloadsByShepardId(ref.getShepardId(), username);
    assertEquals(List.of(payloadA, payloadB), actual);
  }

  @Test
  public void getAllPayloadByShepardIdTest_notAllowed() {
    String username = "blubb";
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDataContainer(container);
    StructuredData structuredDataA = new StructuredData("abc", new Date(), "name");
    StructuredData structuredDataB = new StructuredData("def", new Date(), "name");
    ref.setStructuredDatas(List.of(structuredDataA, structuredDataB));
    StructuredDataPayload payloadA = new StructuredDataPayload(structuredDataA, null);
    StructuredDataPayload payloadB = new StructuredDataPayload(structuredDataB, null);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(false);
    var actual = service.getAllPayloadsByShepardId(ref.getShepardId(), username);
    assertEquals(List.of(payloadA, payloadB), actual);
    verify(structuredDataService, never()).getPayload(eq(container.getMongoId()), any(String.class));
  }

  @Test
  public void getAllPayloadByShepardIdTest_unknownOid() {
    String username = "bla";
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDataContainer(container);
    StructuredData structuredDataA = new StructuredData("abc", new Date(), "name");
    StructuredData structuredDataB = new StructuredData("def", new Date(), "name");
    ref.setStructuredDatas(List.of(structuredDataA, structuredDataB));
    StructuredDataPayload payloadA = new StructuredDataPayload(structuredDataA, "json1");
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    when(structuredDataService.getPayload(container.getMongoId(), structuredDataA.getOid())).thenReturn(payloadA);
    when(structuredDataService.getPayload(container.getMongoId(), structuredDataB.getOid())).thenReturn(null);
    var actual = service.getAllPayloadsByShepardId(ref.getShepardId(), username);
    assertEquals(List.of(payloadA, new StructuredDataPayload(structuredDataB, null)), actual);
  }

  @Test
  public void getAllPayloadByShepardIdTest_isNull() {
    String username = "schorsch";
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    StructuredData structuredData = new StructuredData("abc", new Date(), "name");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDataContainer(container);
    ref.setStructuredDatas(List.of(structuredData));
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    when(structuredDataService.getPayload("mongoId", "abc")).thenReturn(null);
    List<StructuredDataPayload> actual = service.getAllPayloadsByShepardId(ref.getShepardId(), username);
    StructuredDataPayload payload = new StructuredDataPayload(structuredData, null);
    assertEquals(List.of(payload), actual);
  }

  @Test
  public void getAllPayloadByShepardIdTest_ContainerIsDeleted() {
    String username = "schorsch";
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    container.setDeleted(true);
    StructuredData structuredData = new StructuredData("abc", new Date(), "name");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDataContainer(container);
    ref.setStructuredDatas(List.of(structuredData));
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    List<StructuredDataPayload> actual = service.getAllPayloadsByShepardId(ref.getShepardId(), username);
    StructuredDataPayload payload = new StructuredDataPayload(structuredData, null);
    assertEquals(List.of(payload), actual);
  }

  @Test
  public void getAllPayloadByShepardIdTest_ContainerIsNull() {
    String username = "schorsch";
    StructuredData structuredData = new StructuredData("abc", new Date(), "name");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDatas(List.of(structuredData));
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    List<StructuredDataPayload> actual = service.getAllPayloadsByShepardId(ref.getShepardId(), username);
    StructuredDataPayload payload = new StructuredDataPayload(structuredData, null);
    assertEquals(List.of(payload), actual);
  }

  @Test
  public void getPayloadByShepardIdTest() {
    String username = "Murat";
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15l);
    ref.setStructuredDataContainer(container);
    StructuredData structuredDataA = new StructuredData("abc", new Date(), "name");
    ref.setStructuredDatas(List.of(structuredDataA));
    StructuredDataPayload payloadA = new StructuredDataPayload(structuredDataA, "json1");
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(true);
    when(structuredDataService.getPayload(container.getMongoId(), structuredDataA.getOid())).thenReturn(payloadA);
    var actual = service.getPayloadByShepardId(ref.getShepardId(), structuredDataA.getOid(), username);
    assertEquals(payloadA, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsNull() {
    String username = "Murat";
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15l);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    assertThrows(InvalidRequestException.class, () -> service.getPayloadByShepardId(ref.getShepardId(), "oid", username)
    );
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsDeleted() {
    String username = "Murat";
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    container.setDeleted(true);
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15l);
    ref.setStructuredDataContainer(container);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    assertThrows(InvalidRequestException.class, () -> service.getPayloadByShepardId(ref.getShepardId(), "oid", username)
    );
  }

  @Test
  public void getPayloadByShepardIdTest_notAllowed() {
    String username = "Mehmet";
    StructuredDataContainer container = new StructuredDataContainer(20L);
    container.setMongoId("mongoId");
    StructuredDataReference ref = new StructuredDataReference(1L);
    ref.setShepardId(15L);
    ref.setStructuredDataContainer(container);
    StructuredData structuredDataA = new StructuredData("abc", new Date(), "name");
    ref.setStructuredDatas(List.of(structuredDataA));
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    when(permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username)).thenReturn(false);
    assertThrows(InvalidAuthException.class, () ->
      service.getPayloadByShepardId(ref.getShepardId(), structuredDataA.getOid(), username)
    );
  }
}
