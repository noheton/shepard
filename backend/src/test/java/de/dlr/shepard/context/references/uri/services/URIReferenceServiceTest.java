package de.dlr.shepard.context.references.uri.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.uri.daos.URIReferenceDAO;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class URIReferenceServiceTest {

  @InjectMock
  URIReferenceDAO dao;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  DataObjectService dataObjectService;

  @InjectMock
  UserService userService;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  AuthenticationContext authenticationContext;

  @Inject
  URIReferenceService service;

  private final long collectionId = 12345L;
  private final User user = new User("Testuser");

  @Test
  public void getURIReferenceByShepardIdTest_successful() {
    URIReference ref = new URIReference(1L);
    ref.setShepardId(15L);

    DataObject dataObject = new DataObject(123L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    URIReference actual = service.getReference(collectionId, dataObject.getShepardId(), ref.getShepardId(), null);
    assertEquals(ref, actual);
  }

  @Test
  public void getURIReferenceByShepardIdTest_notFound() {
    Long shepardId = 15L;
    when(dao.findByShepardId(shepardId)).thenReturn(null);

    var ex = assertThrows(InvalidPathException.class, () -> service.getReference(collectionId, 4321L, shepardId, null));
    assertEquals("ID ERROR - URI Reference with id 15 is null or deleted", ex.getMessage());
  }

  @Test
  public void getURIReferenceByShepardIdTestIsDeleted() {
    Long shepardId = 15L;
    URIReference ref = new URIReference(20L);
    ref.setShepardId(shepardId);
    ref.setDeleted(true);
    when(dao.findByShepardId(shepardId)).thenReturn(ref);

    var ex = assertThrows(InvalidPathException.class, () -> service.getReference(collectionId, 4321L, shepardId, null));
    assertEquals("ID ERROR - URI Reference with id 15 is null or deleted", ex.getMessage());
  }

  @Test
  public void getAllURIReferencesByShepardIdTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    URIReference ref1 = new URIReference(1L);
    ref1.setShepardId(15L);
    URIReference ref2 = new URIReference(2L);
    ref2.setShepardId(25L);
    dataObject.setReferences(List.of(ref1, ref2));

    when(dao.findByDataObjectShepardId(dataObject.getShepardId())).thenReturn(List.of(ref1, ref2));

    List<URIReference> actual = service.getAllReferencesByDataObjectId(collectionId, dataObject.getShepardId(), null);

    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createURIReferenceByShepardIdTest() {
    Version version = new Version(new UUID(1L, 2L));
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Date date = new Date(30L);
    URIReferenceIO input = new URIReferenceIO() {
      {
        setName("MyName");
        setUri("http;//example.com");
      }
    };
    URIReference toCreate = new URIReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setUri(input.getUri());
      }
    };
    URIReference created = new URIReference() {
      {
        setId(1L);
        setCreatedAt(toCreate.getCreatedAt());
        setCreatedBy(toCreate.getCreatedBy());
        setDataObject(toCreate.getDataObject());
        setName(toCreate.getName());
        setUri(toCreate.getUri());
      }
    };
    URIReference createdWithShepardId = new URIReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(created.getCreatedAt());
        setCreatedBy(created.getCreatedBy());
        setDataObject(created.getDataObject());
        setName(created.getName());
        setUri(created.getUri());
      }
    };
    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    URIReference actual = service.createReference(collectionId, dataObject.getShepardId(), input);
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    Date date = new Date(30L);
    URIReference ref = new URIReference(1L);
    ref.setShepardId(15L);
    URIReference expected = new URIReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedAt(date);
    expected.setUpdatedBy(user);

    DataObject dataObject = new DataObject(123L);
    dataObject.setShepardId(54321L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(userService.getCurrentUser()).thenReturn(user);
    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(date);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    assertDoesNotThrow(() -> service.deleteReference(collectionId, dataObject.getShepardId(), ref.getShepardId()));
  }
}
