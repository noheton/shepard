package de.dlr.shepard.context.references.file.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.data.file.daos.FileContainerDAO;
import de.dlr.shepard.data.file.daos.ShepardFileDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.file.services.FileService;
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
public class FileBundleReferenceServiceTest {

  @InjectMock
  FileBundleReferenceDAO dao;

  @InjectMock
  FileService fileService;

  @InjectMock
  DataObjectService dataObjectService;

  @InjectMock
  FileContainerDAO fileContainerDAO;

  @InjectMock
  ShepardFileDAO fileDAO;

  @InjectMock
  AuthenticationContext authenticationContext;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  PermissionsService permissionsService;

  @InjectMock
  UserService userService;

  @Inject
  FileBundleReferenceService service;

  @Inject
  FileContainerService fileContainerService;

  private final long collectionId = 112200L;

  @Test
  public void getFileReferenceByShepardIdTest_successful() {
    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15L);

    DataObject dataObject = new DataObject(1121L);
    dataObject.setShepardId(2212L);
    ref.setDataObject(dataObject);
    dataObject.setReferences(List.of(ref));

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    FileBundleReference actual = service.getReference(collectionId, dataObject.getShepardId(), ref.getShepardId(), null);
    assertEquals(ref, actual);
  }

  @Test
  public void getFileReferenceByShepardIdTest_notFound() {
    Long shepardId = 15L;
    when(dao.findByShepardId(shepardId)).thenReturn(null);

    var ex = assertThrows(InvalidPathException.class, () -> service.getReference(collectionId, 1114L, shepardId, null));
    assertEquals(ex.getMessage(), "ID ERROR - File Reference with id 15 is null or deleted");
  }

  @Test
  public void getFileReferenceByShepardIdTest_deleted() {
    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);

    var ex = assertThrows(InvalidPathException.class, () ->
      service.getReference(collectionId, 1114L, ref.getShepardId(), null)
    );
    assertEquals(ex.getMessage(), "ID ERROR - File Reference with id 15 is null or deleted");
  }

  @Test
  public void getAllFileReferencesByShepardIdTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    FileBundleReference ref1 = new FileBundleReference(1L);
    ref1.setShepardId(15L);
    FileBundleReference ref2 = new FileBundleReference(2L);
    ref2.setShepardId(25L);
    dataObject.setReferences(List.of(ref1, ref2));

    when(dao.findByDataObjectShepardId(dataObject.getShepardId())).thenReturn(List.of(ref1, ref2));

    List<FileBundleReference> actual = service.getAllReferencesByDataObjectId(collectionId, dataObject.getShepardId(), null);
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createFileReferenceByShepardIdTest() {
    User user = new User("Bob");
    Version version = new Version(new UUID(1L, 2L));
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    FileContainer container = new FileContainer(300L);
    container.setMongoId("mongoId");
    Date date = new Date(30L);
    ShepardFile fileComplete = new ShepardFile("oid", new Date(), "name", "md5");
    FileReferenceIO input = new FileReferenceIO() {
      {
        setName("MyName");
        setFileOids(new String[] { "oid" });
        setFileContainerId(container.getId());
      }
    };
    FileBundleReference toCreate = new FileBundleReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setFiles(List.of(fileComplete));
        setFileContainer(container);
      }
    };
    FileBundleReference created = new FileBundleReference() {
      {
        setId(1L);
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setFiles(toCreate.getFiles());
        setFileContainer(toCreate.getFileContainer());
      }
    };
    FileBundleReference createdWithShepardId = new FileBundleReference() {
      {
        setId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setFiles(created.getFiles());
        setFileContainer(created.getFileContainer());
        setShepardId(created.getId());
      }
    };
    when(userService.getCurrentUser()).thenReturn(user);
    when(fileContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(fileDAO.find(container.getId(), "oid")).thenReturn(fileComplete);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);

    FileBundleReference actual = service.createReference(collectionId, dataObject.getShepardId(), input);
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createFileReferenceByShepardIdTest_newFileIsNull() {
    User user = new User("Bob");
    Version version = new Version(new UUID(1L, 2L));
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    FileContainer container = new FileContainer(300L);
    container.setMongoId("mongoId");
    Date date = new Date(30L);
    FileReferenceIO input = new FileReferenceIO() {
      {
        setName("MyName");
        setFileOids(new String[] { "oid" });
        setFileContainerId(300L);
      }
    };
    var toCreate = new FileBundleReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName("MyName");
        setFiles(Collections.emptyList());
        setFileContainer(container);
      }
    };
    var created = new FileBundleReference() {
      {
        setId(1L);
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName("MyName");
        setFiles(Collections.emptyList());
        setFileContainer(container);
      }
    };
    var createdWithShepardId = new FileBundleReference() {
      {
        setId(1L);
        setShepardId(1L);
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName("MyName");
        setFiles(Collections.emptyList());
        setFileContainer(container);
      }
    };
    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(fileContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(fileDAO.find(container.getId(), "oid")).thenReturn(null);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);

    var actual = service.createReference(collectionId, dataObject.getShepardId(), input);
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createFileReferenceByShepardIdTest_ContainerIsDeleted() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    FileContainer container = new FileContainer(300L);
    container.setDeleted(true);
    FileReferenceIO input = new FileReferenceIO() {
      {
        setName("MyName");
        setFileOids(new String[] { "oid" });
        setFileContainerId(container.getId());
      }
    };
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(fileContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);

    assertThrows(InvalidBodyException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
  }

  @Test
  public void createFileReferenceByShepardIdTest_ContainerIsNull() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Long nullFileContainerId = 300L;
    FileReferenceIO input = new FileReferenceIO() {
      {
        setName("MyName");
        setFileOids(new String[] { "oid" });
        setFileContainerId(nullFileContainerId);
      }
    };

    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(nullFileContainerId, AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(fileContainerDAO.findByNeo4jId(nullFileContainerId)).thenReturn(null);

    assertThrows(InvalidBodyException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    User user = new User("Bob");
    Date date = new Date(30L);
    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15L);

    DataObject dataObject = new DataObject(1121L);
    dataObject.setShepardId(2212L);
    ref.setDataObject(dataObject);
    dataObject.setReferences(List.of(ref));

    FileBundleReference expected = new FileBundleReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedAt(date);
    expected.setUpdatedBy(user);

    when(userService.getCurrentUser()).thenReturn(user);
    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(date);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId(), null)).thenReturn(dataObject);

    assertDoesNotThrow(() -> service.deleteReference(collectionId, dataObject.getShepardId(), ref.getShepardId()));
  }

  @Test
  public void getPayloadByShepardIdTest() {
    User user = new User("123");
    String fileOID = "oid";
    FileContainer container = new FileContainer(20L);
    container.setMongoId("mongoId");
    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15L);
    ref.setFileContainer(container);

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(541231L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    NamedInputStream result = new NamedInputStream(fileOID, null, "myInputStream", 123L);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(
      permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, user.getUsername(), anyLong())
    ).thenReturn(true);
    when(fileContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);
    when(fileService.getPayload(container.getMongoId(), fileOID)).thenReturn(result);

    NamedInputStream actual = service.getPayload(
      collectionId,
      dataObject.getShepardId(),
      ref.getShepardId(),
      fileOID,
      null
    );

    assertEquals(result, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsNull() {
    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15l);

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(541231L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId(), null)).thenReturn(dataObject);

    assertThrows(NotFoundException.class, () ->
      service.getPayload(collectionId, dataObject.getShepardId(), ref.getShepardId(), "oid", null)
    );
  }

  @Test
  public void getPayloadByShepardIdTest_ContainerIsDeleted() {
    FileContainer container = new FileContainer(20L);
    container.setMongoId("mongoId");
    container.setDeleted(true);
    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15l);
    ref.setFileContainer(container);

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(541231L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    assertThrows(NotFoundException.class, () ->
      service.getPayload(collectionId, dataObject.getShepardId(), ref.getShepardId(), "oid", null)
    );
  }

  @Test
  public void getPayloadByShepardIdTest_NotAllowed() {
    String username = "Xrj§84eEi6fY?";

    FileContainer container = new FileContainer(20L);
    container.setMongoId("mongoId");

    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15L);
    ref.setFileContainer(container);

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(541231L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(fileContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);
    when(permissionsService.isAccessTypeAllowedForUser(20L, AccessType.Read, username, anyLong())).thenReturn(false);

    assertThrows(InvalidAuthException.class, () ->
      service.getPayload(15L, dataObject.getShepardId(), ref.getShepardId(), "oid", null)
    );
  }

  @Test
  public void getAllPayloadsByShepardIdTest() {
    String username = "123";
    FileContainer container = new FileContainer(20L);
    container.setMongoId("mongoId");
    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15L);
    ref.setFileContainer(container);
    ref.setFiles(List.of(new ShepardFile("oid1", null, "", "md5"), new ShepardFile("oid2", null, "", "md5")));
    var nis1 = new NamedInputStream("oid1", null, "myInputStream", 123L);
    var nis2 = new NamedInputStream("oid1", null, "mySecondStream", 124L);

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(541231L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(fileContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);
    when(authenticationContext.getCurrentUserName()).thenReturn(username);
    when(permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username, anyLong())).thenReturn(true);
    when(fileService.getPayload(container.getMongoId(), "oid1")).thenReturn(nis1);
    when(fileService.getPayload(container.getMongoId(), "oid2")).thenReturn(nis2);

    var actual = service.getAllPayloads(collectionId, dataObject.getShepardId(), ref.getShepardId());

    assertEquals(List.of(nis1, nis2), actual);
  }

  @Test
  public void getAllPayloadsByShepardIdTest_IsNull() {
    String username = "123";
    FileContainer container = new FileContainer(20L);
    container.setMongoId("mongoId");
    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15L);
    ref.setFileContainer(container);
    ref.setFiles(
      List.of(new ShepardFile("oid1", null, "file123", "md5"), new ShepardFile("oid2", null, "file456", "md5"))
    );
    var nis = List.of(
      new NamedInputStream("oid1", null, "file123", 123L),
      new NamedInputStream("oid2", null, "file456", 0L)
    );

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(541231L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(authenticationContext.getCurrentUserName()).thenReturn(username);
    when(permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username, anyLong())).thenReturn(true);
    when(fileContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);
    when(fileService.getPayload(container.getMongoId(), "oid1")).thenReturn(nis.getFirst());
    when(fileService.getPayload(container.getMongoId(), "oid2")).thenThrow(new NotFoundException());

    var actual = service.getAllPayloads(collectionId, dataObject.getShepardId(), ref.getShepardId());

    assertEquals(nis, actual);
  }

  @Test
  public void getAllPayloadsByShepardIdTest_ContainerIsDeleted() {
    String username = "123";

    FileContainer container = new FileContainer(20L);
    container.setMongoId("mongoId");
    container.setDeleted(true);

    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15L);
    ref.setFileContainer(container);
    ref.setFiles(List.of(new ShepardFile("oid1", null, "", "md5"), new ShepardFile("oid2", null, "", "md5")));

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(541231L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(permissionsService.isAccessTypeAllowedForUser(container.getId(), AccessType.Read, username, anyLong())).thenReturn(true);
    when(fileContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);

    assertThrows(NotFoundException.class, () ->
      service.getAllPayloads(collectionId, dataObject.getShepardId(), ref.getShepardId())
    );
  }

  @Test
  public void getAllPayloadsByShepardIdTest_ContainerIsNull() {
    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15L);
    ref.setFiles(List.of(new ShepardFile("oid1", null, "", "md5"), new ShepardFile("oid2", null, "", "md5")));

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(541231L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    assertThrows(NotFoundException.class, () ->
      service.getAllPayloads(collectionId, dataObject.getShepardId(), ref.getShepardId())
    );
  }

  @Test
  public void getAllPayloadsByShepardIdTest_NotAllowed() {
    String username = "Xrj§84eEi6fY?";
    FileContainer container = new FileContainer(20L);
    container.setMongoId("mongoId");
    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15L);
    ref.setFileContainer(container);

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(541231L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(permissionsService.isAccessTypeAllowedForUser(20L, AccessType.Read, username, anyLong())).thenReturn(false);
    when(fileContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);

    assertThrows(InvalidAuthException.class, () -> service.getAllPayloads(collectionId, dataObject.getShepardId(), 15L)
    );
  }

  @Test
  public void getFilesByShepardIdTest() {
    User user = new User("1234");
    List<ShepardFile> files = List.of(
      new ShepardFile("a", new Date(), "b", "c"),
      new ShepardFile("d", new Date(), "e", "f")
    );
    FileBundleReference ref = new FileBundleReference(1L);
    ref.setShepardId(15L);
    ref.setFiles(files);

    FileContainer container = new FileContainer(20L);
    container.setMongoId("mongoId");
    ref.setFileContainer(container);

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(541231L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(fileContainerDAO.findByNeo4jId(container.getId())).thenReturn(container);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
    when(permissionsService.isAccessTypeAllowedForUser(20L, AccessType.Read, user.getUsername(), anyLong())).thenReturn(true);

    List<ShepardFile> actual = service.getFiles(collectionId, dataObject.getShepardId(), ref.getShepardId(), null);
    assertEquals(files, actual);
  }
}
