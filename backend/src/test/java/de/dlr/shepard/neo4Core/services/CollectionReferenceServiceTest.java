package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.CollectionReferenceDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.CollectionReference;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.CollectionReferenceIO;
import de.dlr.shepard.util.DateHelper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class CollectionReferenceServiceTest {

  @InjectMock
  CollectionReferenceDAO dao;

  @InjectMock
  DataObjectDAO dataObjectDAO;

  @InjectMock
  CollectionDAO collectionDAO;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  CollectionReferenceService service;

  @Test
  public void getCollectionReferenceByShepardIdTest_successful() {
    CollectionReference ref = new CollectionReference(1L);
    ref.setShepardId(15L);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    CollectionReference actual = service.getReferenceByShepardId(ref.getShepardId());
    assertEquals(ref, actual);
  }

  @Test
  public void getCollectionReferenceByShepardIdTest_notFound() {
    Long shepardId = 15L;
    when(dao.findByShepardId(shepardId)).thenReturn(null);
    CollectionReference actual = service.getReferenceByShepardId(shepardId);
    assertNull(actual);
  }

  @Test
  public void getCollectionReferenceByShepardIdTest_deleted() {
    CollectionReference ref = new CollectionReference(1L);
    ref.setShepardId(15L);
    ref.setDeleted(true);
    when(dao.findByShepardId(ref.getShepardId())).thenReturn(ref);
    CollectionReference actual = service.getReferenceByShepardId(ref.getShepardId());
    assertNull(actual);
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
    List<CollectionReference> actual = service.getAllReferencesByDataObjectShepardId(dataObject.getShepardId());
    assertEquals(List.of(ref1, ref2), actual);
  }

  @Test
  public void createCollectionReferenceByShepardIdTest() {
    User user = new User("Bob");
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
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(collectionDAO.findLightByShepardId(referenced.getShepardId())).thenReturn(referenced);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    when(versionDAO.findVersionLightByNeo4jId(dataObject.getId())).thenReturn(version);
    CollectionReference actual = service.createReferenceByShepardId(
      dataObject.getShepardId(),
      input,
      user.getUsername()
    );
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createCollectionReferenceByShepardIdReferencedNotFoundTest() {
    User user = new User("Bob");
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
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(collectionDAO.findLightByShepardId(referenced.getShepardId())).thenReturn(null);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    var ex = assertThrows(InvalidBodyException.class, () ->
      service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername())
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
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dataObjectDAO.findLightByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(collectionDAO.findLightByShepardId(referenced.getShepardId())).thenReturn(referenced);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(dateHelper.getDate()).thenReturn(date);
    var ex = assertThrows(InvalidBodyException.class, () ->
      service.createReferenceByShepardId(dataObject.getShepardId(), input, user.getUsername())
    );
    assertEquals(
      "The referenced collection with id " + referenced.getShepardId() + " could not be found.",
      ex.getMessage()
    );
  }

  @Test
  public void deleteReferenceByShepardIdTest() {
    User user = new User("Bob");
    Date date = new Date(30L);
    CollectionReference ref = new CollectionReference(1L);
    ref.setShepardId(15L);
    CollectionReference expected = new CollectionReference(ref.getId());
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
  public void getPayloadByShepardIdTest() {
    Collection referenced = new Collection(100L);
    referenced.setShepardId(1005L);
    CollectionReference reference = new CollectionReference(1L);
    reference.setShepardId(15L);
    reference.setReferencedCollection(referenced);
    when(dao.findByShepardId(reference.getShepardId())).thenReturn(reference);
    when(collectionDAO.findByShepardId(referenced.getShepardId())).thenReturn(referenced);
    Collection actual = service.getPayloadByShepardId(reference.getShepardId());
    assertEquals(referenced, actual);
  }

  @Test
  public void getPayloadByShepardIdTest_Deleted() {
    Collection referenced = new Collection(100L);
    referenced.setShepardId(1005L);
    referenced.setDeleted(true);
    CollectionReference reference = new CollectionReference(1L);
    reference.setShepardId(15L);
    reference.setReferencedCollection(referenced);

    when(dao.findByShepardId(reference.getShepardId())).thenReturn(reference);
    when(collectionDAO.findByShepardId(referenced.getShepardId())).thenReturn(referenced);
    Collection actual = service.getPayloadByShepardId(reference.getShepardId());

    assertNull(actual);
  }
}
