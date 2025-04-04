package de.dlr.shepard.context.references.dataobject.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.dataobject.daos.DataObjectReferenceDAO;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class DataObjectReferenceServiceTest {

  @InjectMock
  DataObjectReferenceDAO dao;

  @InjectMock
  DataObjectService dataObjectService;

  @InjectMock
  UserService userService;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  VersionDAO versionDAO;

  @Inject
  DataObjectReferenceService service;

  private final Long collectionId = 1120L;
  private final User defaultUser = new User("Martha");

  @Test
  public void getDataObjectReferenceByShepardIdTest_successful() {
    DataObjectReference ref = new DataObjectReference(1L);
    ref.setShepardId(15L);

    DataObject dataObject = new DataObject(1121L);
    dataObject.setShepardId(2212L);
    ref.setDataObject(dataObject);
    dataObject.setReferences(List.of(ref));

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);

    DataObjectReference actual = service.getReference(
      collectionId,
      dataObject.getShepardId(),
      ref.getShepardId(),
      null
    );
    assertEquals(ref, actual);
  }

  @Test
  public void getDataObjectReferenceByShepardIdTest_notFound() {
    Long shepardId = 1L;
    when(dao.findByShepardId(shepardId)).thenReturn(null);
    var ex = assertThrows(InvalidPathException.class, () -> service.getReference(collectionId, 1114L, shepardId, null));
    assertEquals(ex.getMessage(), "ID ERROR - Data Object Reference with id 1 is null or deleted");
  }

  @Test
  public void getDataObjectReferenceByShepardIdTest_deleted() {
    var ref = new DataObjectReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    var ex = assertThrows(InvalidPathException.class, () ->
      service.getReference(collectionId, 1114L, ref.getShepardId(), null)
    );
    assertEquals(ex.getMessage(), "ID ERROR - Data Object Reference with id 15 is null or deleted");
  }

  @Test
  public void getDataObjectReferenceShepardIdTest_noAssociationBetweenDataObjectAndReference() {
    DataObject dataObject = new DataObject(2L);
    dataObject.setShepardId(20L);

    DataObject otherDataObject = new DataObject(100L);
    otherDataObject.setShepardId(110L);

    DataObjectReference ref = new DataObjectReference(30L);
    ref.setShepardId(35L);
    ref.setDataObject(otherDataObject);
    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);

    var ex = assertThrows(InvalidPathException.class, () ->
      service.getReference(collectionId, dataObject.getShepardId(), ref.getShepardId(), null)
    );
    assertEquals(ex.getMessage(), "ID ERROR - There is no association between dataObject and reference");
  }

  @Test
  public void getAllDataObjectReferencesByShepardIdTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    DataObjectReference ref1 = new DataObjectReference(1L);
    ref1.setShepardId(15L);
    DataObjectReference ref2 = new DataObjectReference(2L);
    ref2.setShepardId(25L);
    DataObjectReference ref3 = new DataObjectReference(3L);
    ref3.setShepardId(35L);
    ref3.setDeleted(true);
    dataObject.setReferences(List.of(ref1, ref2, ref3));
    when(dao.findByDataObjectShepardId(dataObject.getShepardId(), null)).thenReturn(List.of(ref1, ref2));
    List<DataObjectReference> actual = service.getAllReferencesByDataObjectId(
      collectionId,
      dataObject.getShepardId(),
      null
    );
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createDataObjectReferenceByShepardIdTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Date date = new Date(30L);
    DataObject referenced = new DataObject(100L);
    referenced.setShepardId(1005L);
    Version version = new Version(new UUID(1L, 2L));
    DataObjectReferenceIO input = new DataObjectReferenceIO() {
      {
        setName("MyName");
        setReferencedDataObjectId(referenced.getShepardId());
        setRelationship("MyRelationship");
      }
    };
    DataObjectReference toCreate = new DataObjectReference() {
      {
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setDataObject(dataObject);
        setName(input.getName());
        setReferencedDataObject(referenced);
        setRelationship(input.getRelationship());
      }
    };
    DataObjectReference created = new DataObjectReference() {
      {
        setId(1L);
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setDataObject(dataObject);
        setName(toCreate.getName());
        setReferencedDataObject(toCreate.getReferencedDataObject());
        setRelationship(toCreate.getRelationship());
      }
    };
    DataObjectReference createdWithShepardId = new DataObjectReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setDataObject(dataObject);
        setName(created.getName());
        setReferencedDataObject(created.getReferencedDataObject());
        setRelationship(created.getRelationship());
      }
    };
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(dataObjectService.getDataObject(referenced.getShepardId())).thenReturn(referenced);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);

    DataObjectReference actual = service.createReference(collectionId, dataObject.getShepardId(), input);
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createDataObjectReferenceByShepardIdTest_ReferencedIsNull() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Long nullDataObjectShepardId = 100L;
    DataObjectReferenceIO input = new DataObjectReferenceIO() {
      {
        setName("MyName");
        setReferencedDataObjectId(nullDataObjectShepardId);
        setRelationship("MyRelationship");
      }
    };
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(dataObjectService.getDataObject(nullDataObjectShepardId)).thenThrow(InvalidPathException.class);
    assertThrows(InvalidBodyException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
  }

  @Test
  public void createDataObjectReferenceByShepardIdTest_ReferencedIsDeleted() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    DataObject referenced = new DataObject(100L);
    referenced.setShepardId(1005L);
    referenced.setDeleted(true);
    DataObjectReferenceIO input = new DataObjectReferenceIO() {
      {
        setName("MyName");
        setReferencedDataObjectId(referenced.getShepardId());
        setRelationship("MyRelationship");
      }
    };
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(dataObjectService.getDataObject(referenced.getShepardId())).thenThrow(InvalidPathException.class);
    assertThrows(InvalidBodyException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
  }

  @Test
  public void createDataObjectReferenceByShepardId_noPermissionsOnReferencedDataObject() {
    DataObject dataObject = new DataObject(1L);
    dataObject.setShepardId(2L);

    User otherUser = new User("Chandler");
    Collection referenceCollection = new Collection(18L);
    referenceCollection.setShepardId(19L);
    referenceCollection.setPermissions(new Permissions(referenceCollection, otherUser, PermissionType.Private));
    DataObject referenced = new DataObject(20L);
    referenced.setShepardId(21L);
    referenced.setCollection(referenceCollection);
    DataObjectReference ref = new DataObjectReference(10L);
    ref.setShepardId(11L);
    ref.setReferencedDataObject(referenced);

    DataObjectReferenceIO input = new DataObjectReferenceIO() {
      {
        setName("MyName");
        setReferencedDataObjectId(referenced.getShepardId());
        setRelationship("MyRelationship");
      }
    };

    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(dataObjectService.getDataObject(referenced.getShepardId())).thenThrow(InvalidAuthException.class);
    var ex = assertThrows(InvalidBodyException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
    assertEquals("You do not have permissions to access the referenced DataObject with id 21.", ex.getMessage());
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    User user = new User("Bob");
    Date date = new Date(30L);
    DataObjectReference ref = new DataObjectReference(1L);
    ref.setShepardId(15L);
    DataObjectReference expected = new DataObjectReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedAt(date);
    expected.setUpdatedBy(user);

    DataObject dataObject = new DataObject(1234L);
    dataObject.setShepardId(541231L);
    dataObject.setReferences(List.of(ref));
    ref.setDataObject(dataObject);

    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(date);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);

    assertDoesNotThrow(() -> service.deleteReference(collectionId, dataObject.getShepardId(), ref.getShepardId()));
  }

  @Test
  public void getPayloadByShepardIdTest() {
    DataObject referenced = new DataObject(100L);
    referenced.setShepardId(1005L);

    DataObjectReference reference = new DataObjectReference(1L);
    reference.setShepardId(15L);
    reference.setReferencedDataObject(referenced);

    DataObject dataObject = new DataObject(1002L);
    dataObject.setShepardId(12412L);
    dataObject.setReferences(List.of(reference));
    reference.setDataObject(dataObject);

    when(dao.findByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    when(dataObjectService.getDataObject(referenced.getShepardId())).thenReturn(referenced);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);

    DataObject actual = service.getPayload(collectionId, dataObject.getShepardId(), reference.getShepardId(), null);
    assertEquals(referenced, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_Deleted() {
    DataObject referenced = new DataObject(100L);
    referenced.setShepardId(1005L);
    referenced.setDeleted(true);
    DataObjectReference reference = new DataObjectReference(1L);
    reference.setShepardId(15L);

    DataObject dataObject = new DataObject(1002L);
    dataObject.setShepardId(12412L);
    dataObject.setReferences(List.of(reference));
    reference.setDataObject(dataObject);

    when(dao.findByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    when(dataObjectService.getDataObject(referenced.getShepardId())).thenReturn(referenced);
    assertThrows(NotFoundException.class, () ->
      service.getPayload(collectionId, dataObject.getShepardId(), reference.getShepardId(), null)
    );
  }

  @Test
  public void getPayloadByShepardIdTest_noPermissionsOnReferencedDataObject() {
    User otherUser = new User("Monica");
    DataObject dataObject = new DataObject(3L);
    dataObject.setShepardId(17L);
    Collection referenceCollection = new Collection(18L);
    referenceCollection.setShepardId(19L);
    referenceCollection.setPermissions(new Permissions(referenceCollection, otherUser, PermissionType.Private));
    DataObject referenced = new DataObject(20L);
    referenced.setShepardId(21L);
    referenced.setCollection(referenceCollection);
    DataObjectReference ref = new DataObjectReference(10L);
    ref.setShepardId(11L);
    ref.setReferencedDataObject(referenced);
    ref.setDataObject(dataObject);

    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dataObjectService.getDataObject(referenced.getShepardId())).thenThrow(InvalidAuthException.class);
    assertThrows(InvalidAuthException.class, () ->
      service.getPayload(collectionId, dataObject.getShepardId(), ref.getShepardId(), null)
    );
  }

  @Test
  public void getPayloadByShepardIdTest_referencedDataObjectNotFound() {
    DataObjectReference ref = new DataObjectReference(1L);
    ref.setShepardId(15L);
    DataObject dataObject = new DataObject(3L);
    dataObject.setShepardId(17L);
    ref.setDataObject(dataObject);
    dataObject.setReferences(List.of(ref));

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dataObjectService.getDataObject(200L)).thenThrow(InvalidPathException.class);

    assertThrows(NotFoundException.class, () ->
      service.getPayload(collectionId, dataObject.getShepardId(), ref.getShepardId(), null)
    );
  }
}
