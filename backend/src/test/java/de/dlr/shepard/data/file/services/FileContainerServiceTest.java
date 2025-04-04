package de.dlr.shepard.data.file.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.data.file.daos.FileContainerDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.io.FileContainerIO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class FileContainerServiceTest {

  @InjectMock
  FileContainerDAO dao;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  FileService fileService;

  @InjectMock
  UserService userService;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  AuthenticationContext authenticationContext;

  @Inject
  FileContainerService service;

  private final User defaultUser = new User("Anna");

  @Test
  public void getFileContainerTest_successful() {
    FileContainer container = new FileContainer(1L);

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(authenticationContext.getCurrentUserName()).thenReturn(defaultUser.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, defaultUser.getUsername())).thenReturn(
      true
    );

    FileContainer actual = service.getContainer(1L);
    assertEquals(container, actual);
  }

  @Test
  public void getFileContainerTest_isNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);
    when(authenticationContext.getCurrentUserName()).thenReturn(defaultUser.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, defaultUser.getUsername())).thenReturn(
      true
    );
    assertThrows(InvalidPathException.class, () -> service.getContainer(1L));
  }

  @Test
  public void getFileContainerTest_isDeleted() {
    FileContainer container = new FileContainer(1L);
    container.setDeleted(true);

    when(authenticationContext.getCurrentUserName()).thenReturn(defaultUser.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, defaultUser.getUsername())).thenReturn(
      true
    );
    when(dao.findByNeo4jId(1L)).thenReturn(container);

    assertThrows(InvalidPathException.class, () -> service.getContainer(1L));
  }

  @Test
  public void getAllFileContainerTest_successful() {
    var container1 = new FileContainer(1L);
    var container2 = new FileContainer(2L);

    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dao.findAllFileContainers(null, defaultUser.getUsername())).thenReturn(List.of(container1, container2));

    var actual = service.getAllContainers(null);
    assertEquals(List.of(container1, container2), actual);
  }

  @Test
  public void createFileContainerTest() {
    var date = new Date(32);

    var input = new FileContainerIO() {
      {
        setName("Name");
      }
    };

    var toCreate = new FileContainer() {
      {
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setMongoId("collection");
        setName("Name");
      }
    };

    var created = new FileContainer() {
      {
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setMongoId("database");
        setName("Name");
        setId(1L);
      }
    };

    when(fileService.createFileContainer()).thenReturn("collection");
    when(dateHelper.getDate()).thenReturn(date);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);

    var actual = service.createContainer(input);
    assertEquals(created, actual);
    verify(permissionsService).createPermissions(created, defaultUser, PermissionType.Private);
  }

  @Test
  public void deleteFileContainerServiceTest() {
    var date = new Date(23);
    var old = new FileContainer(1L);
    old.setMongoId("XYZ");

    var expected = new FileContainer(1L) {
      {
        setUpdatedAt(date);
        setUpdatedBy(defaultUser);
        setDeleted(true);
      }
    };

    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.findByNeo4jId(1L)).thenReturn(old);
    when(dao.createOrUpdate(expected)).thenReturn(expected);
    when(authenticationContext.getCurrentUserName()).thenReturn(defaultUser.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, defaultUser.getUsername())).thenReturn(
      true
    );
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, defaultUser.getUsername())).thenReturn(
      true
    );

    assertDoesNotThrow(() -> service.deleteContainer(1L));
  }

  @Test
  public void deleteFileContainerServiceTest_isNull() {
    var date = new Date(23);

    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    assertThrows(InvalidPathException.class, () -> service.deleteContainer(1L));
  }

  @Test
  public void createFileTest() {
    FileContainer container = new FileContainer(1L);
    container.setMongoId("mongoId");
    ShepardFile file = new ShepardFile("oid", new Date(), "name", "md5");

    FileContainer updated = new FileContainer(1L);
    updated.setMongoId("mongoId");
    updated.addFile(file);

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(fileService.createFile("mongoId", "filename", null)).thenReturn(file);
    when(authenticationContext.getCurrentUserName()).thenReturn(defaultUser.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, defaultUser.getUsername())).thenReturn(
      true
    );
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, defaultUser.getUsername())).thenReturn(
      true
    );

    ShepardFile actual = service.createFile(1L, "filename", null);

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
    when(authenticationContext.getCurrentUserName()).thenReturn(defaultUser.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, defaultUser.getUsername())).thenReturn(
      true
    );
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, defaultUser.getUsername())).thenReturn(
      true
    );

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
    when(authenticationContext.getCurrentUserName()).thenReturn(defaultUser.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, defaultUser.getUsername())).thenReturn(
      true
    );
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, defaultUser.getUsername())).thenReturn(
      true
    );
    var actual = service.createFile(1L, "", null);

    assertEquals(file, actual);
    verify(dao).createOrUpdate(updated);
  }

  @Test
  public void createFileTest_containerIsNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);
    var ex = assertThrows(InvalidPathException.class, () -> service.createFile(1L, "filename", null));
    assertEquals(ex.getMessage(), "ID ERROR - File Container with id 1 is null or deleted");
  }

  @Test
  public void createFileTest_containerIsDeleted() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    var ex = assertThrows(InvalidPathException.class, () -> service.createFile(1L, "filename", null));
    assertEquals("ID ERROR - File Container with id 1 is null or deleted", ex.getMessage());
  }

  @Test
  public void createFileTest_mongoError() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(authenticationContext.getCurrentUserName()).thenReturn(defaultUser.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, defaultUser.getUsername())).thenReturn(
      true
    );
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, defaultUser.getUsername())).thenReturn(
      true
    );
    when(fileService.createFile("mongoId", "filename", null)).thenThrow(InternalServerErrorException.class);
    //TODO: this unit test does not really test anything, we need to implement partial mocking on the FileService to simulate the MongoError

    assertThrows(InternalServerErrorException.class, () -> service.createFile(1L, "filename", null));
  }

  @Test
  public void getFileTest() {
    FileContainer container = new FileContainer(1L);
    container.setMongoId("mongoId");

    NamedInputStream result = new NamedInputStream("oid", null, "name", 123L);

    when(dao.findByNeo4jId(1L)).thenReturn(container);
    when(fileService.getPayload("mongoId", "oid")).thenReturn(result);
    when(authenticationContext.getCurrentUserName()).thenReturn(defaultUser.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, defaultUser.getUsername())).thenReturn(
      true
    );
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, defaultUser.getUsername())).thenReturn(
      true
    );

    var actual = service.getFile(1L, "oid");
    assertEquals(result, actual);
  }

  @Test
  public void getFileTest_containerIsNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    var ex = assertThrows(InvalidPathException.class, () -> service.getFile(1L, "oid"));
    assertEquals(ex.getMessage(), "ID ERROR - File Container with id 1 is null or deleted");
  }

  @Test
  public void getFileTest_containerIsDeleted() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    when(dao.findLightByNeo4jId(1L)).thenReturn(container);

    var ex = assertThrows(InvalidPathException.class, () -> service.getFile(1L, "oid"));
    assertEquals(ex.getMessage(), "ID ERROR - File Container with id 1 is null or deleted");
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
    when(authenticationContext.getCurrentUserName()).thenReturn(defaultUser.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, defaultUser.getUsername())).thenReturn(
      true
    );
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, defaultUser.getUsername())).thenReturn(
      true
    );

    assertDoesNotThrow(() -> service.deleteFile(1L, "abc"));
    verify(dao).createOrUpdate(updated);
  }

  @Test
  public void deleteFileTest_deletedFalse() {
    ShepardFile file1 = new ShepardFile("abc", new Date(), "name", "md5");
    ShepardFile file2 = new ShepardFile("123", new Date(), "name", "md5");

    FileContainer container = new FileContainer(1L);
    container.setMongoId("mongoId");
    container.setFiles(List.of(file1, file2));

    FileContainer updated = new FileContainer(1L);
    updated.setMongoId("mongoId");
    updated.setFiles(List.of(file2));

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    doThrow(new NotFoundException("Could not find and delete file with oid: abc"))
      .when(fileService)
      .deleteFile("mongoId", "abc");
    when(authenticationContext.getCurrentUserName()).thenReturn(defaultUser.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Read, defaultUser.getUsername())).thenReturn(
      true
    );
    when(permissionsService.isAccessTypeAllowedForUser(1L, AccessType.Write, defaultUser.getUsername())).thenReturn(
      true
    );

    assertThrows(NotFoundException.class, () -> service.deleteFile(1L, "abc"));

    verify(dao, never()).createOrUpdate(updated);
  }

  @Test
  public void deleteFileTest_containerIsNull() {
    when(dao.findByNeo4jId(1L)).thenReturn(null);

    assertThrows(InvalidPathException.class, () -> service.deleteFile(1L, "oid"));
  }

  @Test
  public void deleteFileTest_containerIsDeleted() {
    var container = new FileContainer(1L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    when(dao.findByNeo4jId(1L)).thenReturn(container);

    assertThrows(InvalidPathException.class, () -> service.deleteFile(1L, "oid"));
  }
}
