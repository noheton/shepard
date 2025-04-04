package de.dlr.shepard.data.structureddata.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.MongoCollection;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.AccessType;
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
import jakarta.ws.rs.InternalServerErrorException;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class StructuredDataContainerServiceTest {

  @InjectMock
  StructuredDataContainerDAO dao;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  UserService userService;

  @InjectMock
  AuthenticationContext authenticationContext;

  @InjectMock
  MongoCollection<Document> mongoCollection;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  StructuredDataContainerService service;

  @InjectMock
  StructuredDataService structuredDataService;

  @Test
  public void getStructuredDataContainerTest_successful() {
    String userName = "Alice";
    var container = new StructuredDataContainer(1L);

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(authenticationContext.getCurrentUserName()).thenReturn(userName);
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, userName)).thenReturn(true);

    var actual = service.getContainer(1L);
    assertEquals(container, actual);
  }

  @Test
  public void getStructuredDataContainerTest_isNull() {
    String userName = "Alice";

    when(dao.findByNeo4jId(1L)).thenReturn(null);
    when(authenticationContext.getCurrentUserName()).thenReturn(userName);
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, userName)).thenReturn(true);

    assertThrows(InvalidPathException.class, () -> service.getContainer(1L));
  }

  @Test
  public void getStructuredDataContainerTest_isDeleted() {
    String userName = "Alice";
    var container = new StructuredDataContainer(1L);
    container.setDeleted(true);

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(authenticationContext.getCurrentUserName()).thenReturn(userName);
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, userName)).thenReturn(true);

    assertThrows(InvalidPathException.class, () -> service.getContainer(1L));
  }

  @Test
  public void getAllStructuredDataContainerTest_successful() {
    User user = new User("Alice");
    var container1 = new StructuredDataContainer(1L);
    var container2 = new StructuredDataContainer(2L);

    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, user.getUsername())).thenReturn(true);
    when(dao.findAllStructuredDataContainers(null, user.getUsername())).thenReturn(List.of(container1, container2));
    when(userService.getCurrentUser()).thenReturn(user);

    var actual = service.getAllContainers(null);
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
    when(userService.getCurrentUser()).thenReturn(user);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);

    var actual = service.createContainer(input);
    assertEquals(created, actual);
    verify(permissionsService).createPermissions(created, user, PermissionType.Private);
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

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.findByNeo4jId(1L)).thenReturn(old);
    when(dao.createOrUpdate(expected)).thenReturn(expected);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, user.getUsername())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, user.getUsername())).thenReturn(true);

    assertDoesNotThrow(() -> service.deleteContainer(1L));
  }

  @Test
  public void deleteStructuredDataContainerServiceTest_isNull() {
    var user = new User("bob");
    var date = new Date(23);

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    assertThrows(InvalidPathException.class, () -> service.deleteContainer(1L));
  }

  @Test
  public void createStructuredDataTest() {
    var user = new User("bob");
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
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, user.getUsername())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, user.getUsername())).thenReturn(true);

    var actual = service.createStructuredData(1L, payload);

    assertEquals(new StructuredData("oid", date, "name"), actual);
    verify(dao).createOrUpdate(updated);
  }

  @Test
  public void createStructuredDataTest_containerIsNull() {
    var user = new User("bob");
    var structuredData = new StructuredData("oid", new Date(), "name");
    var payload = new StructuredDataPayload(structuredData, "payload");

    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, user.getUsername())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, user.getUsername())).thenReturn(true);
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    assertThrows(InvalidPathException.class, () -> service.createStructuredData(1L, payload));
  }

  @Test
  public void createStructuredDataTest_containerIsDeleted() {
    var user = new User("bob");
    var structuredData = new StructuredData("oid", new Date(), "name");
    var container = new StructuredDataContainer(1L);
    container.setDeleted(true);
    var payload = new StructuredDataPayload(structuredData, "payload");

    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, user.getUsername())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, user.getUsername())).thenReturn(true);
    when(dao.findByNeo4jId(1L)).thenReturn(container);

    assertThrows(InvalidPathException.class, () -> service.createStructuredData(1L, payload));
  }

  @Test
  public void createStructuredDataTest_mongoError() {
    var user = new User("bob");
    var structuredData = new StructuredData("oid", new Date(), "name");
    var container = new StructuredDataContainer(1L);
    container.setMongoId("mongoId");
    var payload = new StructuredDataPayload(structuredData, "payload");

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, user.getUsername())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, user.getUsername())).thenReturn(true);
    when(structuredDataService.createStructuredData("mongoId", payload)).thenThrow(InternalServerErrorException.class);

    //TODO: as of right now, this test does not properly test anything
    // in order to implement a mongoError, we would need to introduce partial mocking on the StructuredDataService

    assertThrows(InternalServerErrorException.class, () -> service.createStructuredData(1L, payload));
  }

  @Test
  public void getStructuredDataTest() {
    var user = new User("bob");
    var container = new StructuredDataContainer(1L);
    var structuredData = new StructuredData("oid", new Date(), "name");
    container.setMongoId("mongoId");
    var result = new StructuredDataPayload(structuredData, "payload");

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(structuredDataService.getPayload("mongoId", "oid")).thenReturn(result);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, user.getUsername())).thenReturn(true);

    var actual = service.getStructuredData(1L, "oid");
    assertEquals(result, actual);
  }

  @Test
  public void getStructuredDataTest_containerIsNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);
    assertThrows(InvalidPathException.class, () -> service.getStructuredData(1L, "oid"));
  }

  @Test
  public void getStructuredDataTest_containerIsDeleted() {
    var container = new StructuredDataContainer(1L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);

    assertThrows(InvalidPathException.class, () -> service.getStructuredData(1L, "oid"));
  }

  @Test
  public void deleteStructuredDataTest() {
    var user = new User("bob");
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
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, user.getUsername())).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, user.getUsername())).thenReturn(true);

    assertDoesNotThrow(() -> service.deleteStructuredData(1L, "abc"));
    verify(dao).createOrUpdate(updated);
  }

  @Test
  public void deleteStructuredDataTest_containerIsNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    assertThrows(InvalidPathException.class, () -> service.deleteStructuredData(1L, "oid"));
  }

  @Test
  public void deleteStructuredDataTest_containerIsDeleted() {
    var container = new StructuredDataContainer(1L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    assertThrows(InvalidPathException.class, () -> service.deleteStructuredData(1L, "oid"));
  }
}
