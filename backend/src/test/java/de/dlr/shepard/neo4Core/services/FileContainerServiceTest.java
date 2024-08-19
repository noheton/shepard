package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.mongoDB.FileService;
import de.dlr.shepard.mongoDB.NamedInputStream;
import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PermissionType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class FileContainerServiceTest {

  @InjectMock
  FileContainerDAO dao;

  @InjectMock
  PermissionsDAO permissionsDAO;

  @InjectMock
  FileService fileService;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  FileContainerService service;

  @Test
  public void getFileContainerTest_successful() {
    var container = new FileContainer(1L);

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    var actual = service.getContainer(1L);
    assertEquals(container, actual);
  }

  @Test
  public void getFileContainerTest_isNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    var actual = service.getContainer(1L);
    assertNull(actual);
  }

  @Test
  public void getFileContainerTest_isDeleted() {
    var container = new FileContainer(1L);
    container.setDeleted(true);

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    var actual = service.getContainer(1L);
    assertNull(actual);
  }

  @Test
  public void getAllFileContainerTest_successful() {
    var container1 = new FileContainer(1L);
    var container2 = new FileContainer(2L);

    when(dao.findAllFileContainers(null, "bob")).thenReturn(List.of(container1, container2));

    var actual = service.getAllContainers(null, "bob");
    assertEquals(List.of(container1, container2), actual);
  }

  @Test
  public void createFileContainerTest() {
    var user = new User("bob");
    var date = new Date(32);

    var input = new FileContainerIO() {
      {
        setName("Name");
      }
    };

    var toCreate = new FileContainer() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setMongoId("collection");
        setName("Name");
      }
    };

    var created = new FileContainer() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setMongoId("database");
        setName("Name");
        setId(1L);
      }
    };

    when(fileService.createFileContainer()).thenReturn("collection");
    when(dateHelper.getDate()).thenReturn(date);
    when(userDAO.find("bob")).thenReturn(user);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);

    var actual = service.createContainer(input, "bob");
    assertEquals(created, actual);
    verify(permissionsDAO).createOrUpdate(new Permissions(created, user, PermissionType.Private));
  }

  @Test
  public void deleteFileContainerServiceTest() {
    var user = new User("bob");
    var date = new Date(23);
    var old = new FileContainer(1L);
    old.setMongoId("XYZ");

    var expected = new FileContainer(1L) {
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
    when(fileService.deleteFileContainer("XYZ")).thenReturn(true);

    var actual = service.deleteContainer(1L, "bob");
    assertTrue(actual);
  }

  @Test
  public void deleteFileContainerServiceTest_isNull() {
    var user = new User("bob");
    var date = new Date(23);

    when(userDAO.find("bob")).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    var actual = service.deleteContainer(1L, "bob");
    assertFalse(actual);
  }

  @Test
  public void createFileTest() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    var file = new ShepardFile("oid", new Date(), "name", "md5");

    var updated = new FileContainer(1L);
    updated.setMongoId("mongoId");
    updated.addFile(file);

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(fileService.createFile("mongoId", "filename", null)).thenReturn(file);
    var actual = service.createFile(1L, "filename", null);

    assertEquals(file, actual);
    verify(dao).createOrUpdate(updated);
  }

  @Test
  public void createFileTest_filenameIsNull() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    var file = new ShepardFile("oid", new Date(), "name", "md5");

    var date = new Date();
    when(dateHelper.getDate()).thenReturn(date);
    var sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
    var dateStr = sdf.format(dateHelper.getDate());
    var fileName = "shepard-file-" + dateStr;

    var updated = new FileContainer(1L);
    updated.setMongoId("mongoId");
    updated.addFile(file);

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(fileService.createFile("mongoId", fileName, null)).thenReturn(file);
    var actual = service.createFile(1L, null, null);

    assertEquals(file, actual);
    verify(dao).createOrUpdate(updated);
  }

  @Test
  public void createFileTest_filenameIsBlank() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    var file = new ShepardFile("oid", new Date(), "name", "md5");

    var date = new Date();
    when(dateHelper.getDate()).thenReturn(date);
    var sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
    var dateStr = sdf.format(dateHelper.getDate());
    var fileName = "shepard-file-" + dateStr;

    var updated = new FileContainer(1L);
    updated.setMongoId("mongoId");
    updated.addFile(file);

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(fileService.createFile("mongoId", fileName, null)).thenReturn(file);
    var actual = service.createFile(1L, "", null);

    assertEquals(file, actual);
    verify(dao).createOrUpdate(updated);
  }

  @Test
  public void createFileTest_containerIsNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);
    var actual = service.createFile(1L, "filename", null);

    assertNull(actual);
  }

  @Test
  public void createFileTest_containerIsDeleted() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    var actual = service.createFile(1L, "filename", null);

    assertNull(actual);
  }

  @Test
  public void createFileTest_mongoError() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(fileService.createFile("mongoId", "filename", null)).thenReturn(null);
    var actual = service.createFile(1L, "filename", null);

    assertNull(actual);
  }

  @Test
  public void getFileTest() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    var result = new NamedInputStream("oid", null, "name", 123L);

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);
    when(fileService.getPayload("mongoId", "oid")).thenReturn(result);

    var actual = service.getFile(1L, "oid");
    assertEquals(result, actual);
  }

  @Test
  public void getFileTest_containerIsNull() {
    when(dao.findLightByNeo4jId(1L)).thenReturn(null);

    var actual = service.getFile(1L, "oid");
    assertNull(actual);
  }

  @Test
  public void getFileTest_containerIsDeleted() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);

    var actual = service.getFile(1L, "oid");
    assertNull(actual);
  }

  @Test
  public void deleteFileTest() {
    var file1 = new ShepardFile("abc", new Date(), "name", "md5");
    var file2 = new ShepardFile("123", new Date(), "name", "md5");

    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    container.setFiles(List.of(file1, file2));

    var updated = new FileContainer(1L);
    updated.setMongoId("mongoId");
    updated.setFiles(List.of(file2));

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(fileService.deleteFile("mongoId", "abc")).thenReturn(true);

    var actual = service.deleteFile(1L, "abc");
    assertTrue(actual);
    verify(dao).createOrUpdate(updated);
  }

  @Test
  public void deleteFileTest_deletedFalse() {
    var file1 = new ShepardFile("abc", new Date(), "name", "md5");
    var file2 = new ShepardFile("123", new Date(), "name", "md5");

    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    container.setFiles(List.of(file1, file2));

    var updated = new FileContainer(1L);
    updated.setMongoId("mongoId");
    updated.setFiles(List.of(file2));

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(fileService.deleteFile("mongoId", "abc")).thenReturn(false);

    var actual = service.deleteFile(1L, "abc");
    assertFalse(actual);
    verify(dao, never()).createOrUpdate(updated);
  }

  @Test
  public void deleteFileTest_containerIsNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    var actual = service.deleteFile(1L, "oid");
    assertFalse(actual);
  }

  @Test
  public void deleteFileTest_containerIsDeleted() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    var actual = service.deleteFile(1L, "oid");
    assertFalse(actual);
  }
}
