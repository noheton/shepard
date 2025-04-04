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
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.dataobject.daos.CollectionReferenceDAO;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.context.references.dataobject.io.CollectionReferenceIO;
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
public class CollectionReferenceServiceTest {

  @InjectMock
  CollectionReferenceDAO dao;

  @InjectMock
  DataObjectService dataObjectService;

  @InjectMock
  CollectionService collectionService;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  UserService userService;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  CollectionReferenceService service;

  private final long collectionId = 1120;
  private final User defaultUser = new User("Bob");

  @Test
  public void getCollectionReferenceByShepardIdTest_successful() {
    CollectionReference ref = new CollectionReference(1L);
    ref.setShepardId(15L);

    DataObject dataobject = new DataObject(2L);
    dataobject.setShepardId(16L);
    ref.setDataObject(dataobject);

    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);

    CollectionReference actual = service.getReference(
      collectionId,
      dataobject.getShepardId(),
      ref.getShepardId(),
      null
    );
    assertEquals(ref, actual);
  }

  @Test
  public void getCollectionReferenceByShepardIdTest_notFound() {
    Long collectionReferenceId = 15L;
    when(dao.findByShepardId(collectionReferenceId)).thenReturn(null);
    var ex = assertThrows(InvalidPathException.class, () ->
      service.getReference(collectionId, 1121L, collectionReferenceId, null)
    );
    assertEquals(ex.getMessage(), "ID ERROR - Collection Reference with id 15 is null or deleted");
  }

  @Test
  public void getCollectionReferenceByShepardIdTest_deleted() {
    CollectionReference ref = new CollectionReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);

    DataObject dataObject = new DataObject(3L);
    dataObject.setShepardId(17L);

    var ex = assertThrows(InvalidPathException.class, () ->
      service.getReference(collectionId, dataObject.getShepardId(), ref.getShepardId(), null)
    );
    assertEquals(ex.getMessage(), "ID ERROR - Collection Reference with id 15 is null or deleted");
  }

  @Test
  public void getCollectionReferenceByShepardIdTest_noAssociationBetweenDataObjectAndReference() {
    DataObject dataObject = new DataObject(2L);
    dataObject.setShepardId(20L);

    DataObject otherDataObject = new DataObject(100L);
    otherDataObject.setShepardId(110L);

    CollectionReference ref = new CollectionReference(30L);
    ref.setShepardId(35L);
    ref.setDataObject(otherDataObject);
    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);

    var ex = assertThrows(InvalidPathException.class, () ->
      service.getReference(collectionId, dataObject.getShepardId(), ref.getShepardId(), null)
    );
    assertEquals(ex.getMessage(), "ID ERROR - There is no association between dataObject and reference");
  }

  @Test
  public void getAllCollectionReferencesByShepardIdTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    CollectionReference ref1 = new CollectionReference(1L);
    ref1.setShepardId(15L);
    CollectionReference ref2 = new CollectionReference(2L);
    ref2.setShepardId(25L);
    dataObject.setReferences(List.of(ref1, ref2));

    when(dao.findByDataObjectShepardId(dataObject.getShepardId())).thenReturn(List.of(ref1, ref2));

    List<CollectionReference> actual = service.getAllReferencesByDataObjectId(
      collectionId,
      dataObject.getShepardId(),
      null
    );
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createCollectionReferenceByShepardIdTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Date date = new Date(30L);
    Collection referenced = new Collection(100L);
    referenced.setShepardId(1005L);
    Version version = new Version(new UUID(1L, 2L));
    CollectionReferenceIO input = new CollectionReferenceIO() {
      {
        setName("MyName");
        setReferencedCollectionId(referenced.getShepardId());
        setRelationship("MyRelationship");
      }
    };
    CollectionReference toCreate = new CollectionReference() {
      {
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setDataObject(dataObject);
        setName(input.getName());
        setReferencedCollection(referenced);
        setRelationship(input.getRelationship());
      }
    };
    CollectionReference created = new CollectionReference() {
      {
        setId(1L);
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setDataObject(dataObject);
        setName(toCreate.getName());
        setReferencedCollection(toCreate.getReferencedCollection());
        setRelationship(toCreate.getRelationship());
      }
    };
    CollectionReference createdWithShepardId = new CollectionReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setDataObject(dataObject);
        setName(created.getName());
        setReferencedCollection(created.getReferencedCollection());
        setRelationship(created.getRelationship());
      }
    };
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(collectionService.getCollection(referenced.getShepardId())).thenReturn(referenced);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);

    CollectionReference actual = service.createReference(collectionId, dataObject.getShepardId(), input);
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createCollectionReferenceByShepardIdReferencedNotFoundTest() {
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Date date = new Date(30L);
    Collection referenced = new Collection(100L);
    referenced.setShepardId(1005L);
    CollectionReferenceIO input = new CollectionReferenceIO() {
      {
        setName("MyName");
        setReferencedCollectionId(referenced.getShepardId());
        setRelationship("MyRelationship");
      }
    };
    CollectionReference toCreate = new CollectionReference() {
      {
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setDataObject(dataObject);
        setName(input.getName());
        setReferencedCollection(referenced);
        setRelationship(input.getRelationship());
      }
    };
    CollectionReference created = new CollectionReference() {
      {
        setId(1L);
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setDataObject(dataObject);
        setName(toCreate.getName());
        setReferencedCollection(toCreate.getReferencedCollection());
        setRelationship(toCreate.getRelationship());
      }
    };
    CollectionReference createdWithShepardId = new CollectionReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setDataObject(dataObject);
        setName(created.getName());
        setReferencedCollection(created.getReferencedCollection());
        setRelationship(created.getRelationship());
      }
    };

    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);
    when(collectionService.getCollection(referenced.getShepardId())).thenThrow(InvalidPathException.class);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);

    var ex = assertThrows(InvalidBodyException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
    assertEquals(
      "The referenced collection with id " + referenced.getShepardId() + " could not be found.",
      ex.getMessage()
    );
  }

  @Test
  public void createCollectionReferenceByShepardIdReferencedIsDeletedTest() {
    User user = new User("Bob");
    DataObject dataObject = new DataObject(200L);
    dataObject.setShepardId(2005L);
    Date date = new Date(30L);
    Collection referenced = new Collection(100L);
    referenced.setShepardId(1005L);
    referenced.setDeleted(true);
    CollectionReferenceIO input = new CollectionReferenceIO() {
      {
        setName("MyName");
        setReferencedCollectionId(referenced.getShepardId());
        setRelationship("MyRelationship");
      }
    };
    CollectionReference toCreate = new CollectionReference() {
      {
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(input.getName());
        setReferencedCollection(referenced);
        setRelationship(input.getRelationship());
      }
    };
    CollectionReference created = new CollectionReference() {
      {
        setId(1L);
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(toCreate.getName());
        setReferencedCollection(toCreate.getReferencedCollection());
        setRelationship(toCreate.getRelationship());
      }
    };
    CollectionReference createdWithShepardId = new CollectionReference() {
      {
        setId(created.getId());
        setShepardId(created.getId());
        setCreatedAt(date);
        setCreatedBy(user);
        setDataObject(dataObject);
        setName(created.getName());
        setReferencedCollection(created.getReferencedCollection());
        setRelationship(created.getRelationship());
      }
    };
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(collectionService.getCollection(referenced.getShepardId())).thenThrow(InvalidPathException.class);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    var ex = assertThrows(InvalidBodyException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
    assertEquals(
      "The referenced collection with id " + referenced.getShepardId() + " could not be found.",
      ex.getMessage()
    );
  }

  @Test
  public void createCollectionReferenceByShepardId_noPermissionsOnReferencedCollection() {
    DataObject dataObject = new DataObject(1L);
    dataObject.setShepardId(2L);

    User otherUser = new User("Chandler");
    Collection referenced = new Collection(20L);
    referenced.setShepardId(21L);
    referenced.setPermissions(new Permissions(referenced, otherUser, PermissionType.Private));
    CollectionReference ref = new CollectionReference(10L);
    ref.setShepardId(11L);
    ref.setReferencedCollection(referenced);

    CollectionReferenceIO input = new CollectionReferenceIO() {
      {
        setName("MyName");
        setReferencedCollectionId(referenced.getShepardId());
        setRelationship("MyRelationship");
      }
    };

    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dataObjectService.getDataObject(dataObject.getShepardId())).thenReturn(dataObject);
    when(collectionService.getCollection(referenced.getShepardId())).thenThrow(InvalidAuthException.class);
    var ex = assertThrows(InvalidAuthException.class, () ->
      service.createReference(collectionId, dataObject.getShepardId(), input)
    );
    assertEquals("You do not have permissions to access the referenced collection with id 21.", ex.getMessage());
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    User user = new User("Bob");
    Date date = new Date(30L);
    DataObject dataObject = new DataObject(3L);
    dataObject.setShepardId(17L);

    CollectionReference ref = new CollectionReference(1L);
    ref.setShepardId(15L);
    ref.setDataObject(dataObject);
    dataObject.setReferences(List.of(ref));

    CollectionReference expected = new CollectionReference(ref.getId());
    expected.setShepardId(ref.getShepardId());
    expected.setDeleted(true);
    expected.setUpdatedAt(date);
    expected.setUpdatedBy(user);

    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dao.findByShepardId(ref.getShepardId(), null)).thenReturn(ref);
    when(dateHelper.getDate()).thenReturn(date);
    when(dataObjectService.getDataObject(collectionId, dataObject.getShepardId())).thenReturn(dataObject);

    assertDoesNotThrow(() -> service.deleteReference(collectionId, dataObject.getShepardId(), ref.getShepardId()));
  }

  @Test
  public void getPayloadByShepardIdTest() {
    Collection referenced = new Collection(100L);
    referenced.setShepardId(1005L);
    CollectionReference reference = new CollectionReference(1L);
    reference.setShepardId(15L);
    reference.setReferencedCollection(referenced);
    DataObject dataObject = new DataObject(3L);
    dataObject.setShepardId(17L);
    reference.setDataObject(dataObject);
    dataObject.setReferences(List.of(reference));

    when(dao.findByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(referenced.getShepardId())).thenReturn(
      referenced
    );

    Collection actual = service.getPayload(collectionId, dataObject.getShepardId(), reference.getShepardId(), null);
    assertEquals(referenced, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_Deleted() {
    DataObject dataObject = new DataObject(3L);
    dataObject.setShepardId(17L);
    Collection referenced = new Collection(100L);
    referenced.setShepardId(1005L);
    referenced.setDeleted(true);
    CollectionReference reference = new CollectionReference(1L);
    reference.setShepardId(15L);

    reference.setDataObject(dataObject);
    dataObject.setReferences(List.of(reference));

    when(dao.findByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    when(collectionService.getCollection(referenced.getShepardId())).thenReturn(referenced);
    when(userService.getCurrentUser()).thenReturn(defaultUser);

    assertThrows(NotFoundException.class, () ->
      service.getPayload(collectionId, dataObject.getShepardId(), reference.getShepardId(), null)
    );
  }

  @Test
  public void getPayloadByShepardIdTest_noPermissionsOnReferencedCollection() {
    User otherUser = new User("Joey");
    DataObject dataObject = new DataObject(3L);
    dataObject.setShepardId(17L);
    Collection referenced = new Collection(100L);
    referenced.setShepardId(1005L);
    referenced.setPermissions(new Permissions(referenced, otherUser, PermissionType.Private));
    CollectionReference reference = new CollectionReference(1L);
    reference.setShepardId(15L);
    reference.setReferencedCollection(referenced);
    reference.setDataObject(dataObject);
    dataObject.setReferences(List.of(reference));

    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(dao.findByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(referenced.getShepardId())).thenThrow(
      InvalidAuthException.class
    );
    assertThrows(InvalidAuthException.class, () ->
      service.getPayload(collectionId, dataObject.getShepardId(), reference.getShepardId(), null)
    );
  }

  @Test
  public void getPayloadByShepardIdTest_referencedCollectionNotFound() {
    CollectionReference reference = new CollectionReference(1L);
    reference.setShepardId(15L);
    DataObject dataObject = new DataObject(3L);
    dataObject.setShepardId(17L);
    reference.setDataObject(dataObject);
    dataObject.setReferences(List.of(reference));

    when(dao.findByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    when(collectionService.getCollectionWithDataObjectsAndIncomingReferences(150L)).thenThrow(
      InvalidPathException.class
    );

    assertThrows(NotFoundException.class, () ->
      service.getPayload(collectionId, dataObject.getShepardId(), reference.getShepardId(), null)
    );
  }
}
