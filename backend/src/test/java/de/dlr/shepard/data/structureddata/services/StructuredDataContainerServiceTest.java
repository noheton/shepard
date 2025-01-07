package de.dlr.shepard.data.structureddata.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.entities.Permissions;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.data.structureddata.daos.StructuredDataContainerDAO;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class StructuredDataContainerServiceTest {

  @InjectMock
  StructuredDataContainerDAO dao;

  @InjectMock
  PermissionsDAO permissionsDAO;

  @InjectMock
  StructuredDataService structuredDataService;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  StructuredDataContainerService service;

  @Test
  public void getStructuredDataContainerTest_successful() {
    var container = new StructuredDataContainer(1L);

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    var actual = service.getContainer(1L);
    assertEquals(container, actual);
  }

  @Test
  public void getStructuredDataContainerTest_isNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    var actual = service.getContainer(1L);
    assertNull(actual);
  }

  @Test
  public void getStructuredDataContainerTest_isDeleted() {
    var container = new StructuredDataContainer(1L);
    container.setDeleted(true);

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    var actual = service.getContainer(1L);
    assertNull(actual);
  }

  @Test
  public void getAllStructuredDataContainerTest_successful() {
    var container1 = new StructuredDataContainer(1L);
    var container2 = new StructuredDataContainer(2L);

    when(dao.findAllStructuredDataContainers(null, "bob")).thenReturn(List.of(container1, container2));

    var actual = service.getAllContainers(null, "bob");
    assertEquals(List.of(container1, container2), actual);
  }

  @Test
  public void createStructuredDataContainerTest() {
    var user = new User("bob");
    var date = new Date(32);

    var input = new StructuredDataContainerIO() {
      {
        setName("Name");
      }
    };

    var toCreate = new StructuredDataContainer() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setMongoId("collection");
        setName("Name");
      }
    };

    var created = new StructuredDataContainer() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setMongoId("database");
        setName("Name");
        setId(1L);
      }
    };

    when(structuredDataService.createStructuredDataContainer()).thenReturn("collection");
    when(dateHelper.getDate()).thenReturn(date);
    when(userDAO.find("bob")).thenReturn(user);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);

    var actual = service.createContainer(input, "bob");
    assertEquals(created, actual);
    verify(permissionsDAO).createOrUpdate(new Permissions(created, user, PermissionType.Private));
  }

  @Test
  public void deleteStructuredDataContainerServiceTest() {
    var user = new User("bob");
    var date = new Date(23);
    var old = new StructuredDataContainer(1L);
    old.setMongoId("XYZ");

    var expected = new StructuredDataContainer(1L) {
      {
        setUpdatedAt(date);
        setUpdatedBy(user);
        setDeleted(true);
      }
    };

    when(userDAO.find("bob")).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.findByNeo4jId(1L)).thenReturn(old);
    when(dao.createOrUpdate(expected)).thenReturn(expected);
    when(structuredDataService.deleteStructuredDataContainer("XYZ")).thenReturn(true);

    var actual = service.deleteContainer(1L, "bob");
    assertTrue(actual);
  }

  @Test
  public void deleteStructuredDataContainerServiceTest_isNull() {
    var user = new User("bob");
    var date = new Date(23);

    when(userDAO.find("bob")).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    var actual = service.deleteContainer(1L, "bob");
    assertFalse(actual);
  }

  @Test
  public void createStructuredDataTest() {
    var date = new Date();
    var structuredData = new StructuredData("oid", date, "name");
    var container = new StructuredDataContainer(1L);
    container.setMongoId("mongoId");
    var payload = new StructuredDataPayload(structuredData, "payload");

    var updated = new StructuredDataContainer(1L);
    updated.setMongoId("mongoId");
    updated.addStructuredData(structuredData);

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(structuredDataService.createStructuredData("mongoId", payload)).thenReturn(structuredData);

    var actual = service.createStructuredData(1L, payload);

    assertEquals(new StructuredData("oid", date, "name"), actual);
    verify(dao).createOrUpdate(updated);
  }

  @Test
  public void createStructuredDataTest_containerIsNull() {
    var structuredData = new StructuredData("oid", new Date(), "name");
    var payload = new StructuredDataPayload(structuredData, "payload");

    when(dao.findByNeo4jId(1L)).thenReturn(null);

    var actual = service.createStructuredData(1L, payload);

    assertNull(actual);
  }

  @Test
  public void createStructuredDataTest_containerIsDeleted() {
    var structuredData = new StructuredData("oid", new Date(), "name");
    var container = new StructuredDataContainer(1L);
    container.setDeleted(true);
    var payload = new StructuredDataPayload(structuredData, "payload");

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    var actual = service.createStructuredData(1L, payload);

    assertNull(actual);
  }

  @Test
  public void createStructuredDataTest_mongoError() {
    var structuredData = new StructuredData("oid", new Date(), "name");
    var container = new StructuredDataContainer(1L);
    container.setMongoId("mongoId");
    var payload = new StructuredDataPayload(structuredData, "payload");

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(structuredDataService.createStructuredData("mongoId", payload)).thenReturn(null);

    var actual = service.createStructuredData(1L, payload);

    assertNull(actual);
  }

  @Test
  public void getStructuredDataTest() {
    var container = new StructuredDataContainer(1L);
    var structuredData = new StructuredData("oid", new Date(), "name");
    container.setMongoId("mongoId");
    var result = new StructuredDataPayload(structuredData, "payload");

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);
    when(structuredDataService.getPayload("mongoId", "oid")).thenReturn(result);

    var actual = service.getStructuredData(1L, "oid");
    assertEquals(result, actual);
  }

  @Test
  public void getStructuredDataTest_containerIsNull() {
    when(dao.findLightByNeo4jId(1L)).thenReturn(null);

    var actual = service.getStructuredData(1L, "oid");
    assertNull(actual);
  }

  @Test
  public void getStructuredDataTest_containerIsDeleted() {
    var container = new StructuredDataContainer(1L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);

    var actual = service.getStructuredData(1L, "oid");
    assertNull(actual);
  }

  @Test
  public void deleteStructuredDataTest() {
    var date = new Date();
    var container = new StructuredDataContainer(1L);
    container.setMongoId("mongoId");
    container.setStructuredDatas(
      List.of(new StructuredData("abc", new Date(), "name"), new StructuredData("123", date, "name"))
    );

    var updated = new StructuredDataContainer(1L);
    updated.setMongoId("mongoId");
    updated.setStructuredDatas(List.of(new StructuredData("123", date, "name")));

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(structuredDataService.deletePayload("mongoId", "abc")).thenReturn(true);

    var actual = service.deleteStructuredData(1L, "abc");
    assertTrue(actual);
    verify(dao).createOrUpdate(updated);
  }

  @Test
  public void deleteStructuredDataTest_deletedFalse() {
    var date = new Date();
    var container = new StructuredDataContainer(1L);
    container.setMongoId("mongoId");
    container.setStructuredDatas(
      List.of(new StructuredData("abc", new Date(), "name"), new StructuredData("123", date, "name"))
    );

    var updated = new StructuredDataContainer(1L);
    updated.setMongoId("mongoId");
    updated.setStructuredDatas(List.of(new StructuredData("123", date, "name")));

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(structuredDataService.deletePayload("mongoId", "abc")).thenReturn(false);

    var actual = service.deleteStructuredData(1L, "abc");
    assertFalse(actual);
    verify(dao, never()).createOrUpdate(updated);
  }

  @Test
  public void deleteStructuredDataTest_containerIsNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    var actual = service.deleteStructuredData(1L, "oid");
    assertFalse(actual);
  }

  @Test
  public void deleteStructuredDataTest_containerIsDeleted() {
    var container = new StructuredDataContainer(1L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    var actual = service.deleteStructuredData(1L, "oid");
    assertFalse(actual);
  }
}
