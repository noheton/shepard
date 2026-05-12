package de.dlr.shepard.context.collection.services;

import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aCollection;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aDataObject;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aUser;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.basicreference.daos.BasicReferenceDAO;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusComponentTest
public class DataObjectServiceTest {

  @InjectMock
  DataObjectDAO dao;

  @InjectMock
  CollectionService collectionService;

  @InjectMock
  BasicReferenceDAO referenceDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  UserService userService;

  @InjectMock
  PermissionsService permissionsService;

  @Inject
  DataObjectService service;

  private final User defaultUser = aUser().username("Bob").build();

  @Test
  public void getDataObjectTest() {
    Collection collection = aCollection().id(555L).shepardId(5555L).build();
    DataObject dataObject = aDataObject().id(5L).shepardId(55L).inCollection(collection).build();
    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(
      permissionsService.isAccessTypeAllowedForUser(
        collection.getShepardId(),
        AccessType.Read,
        defaultUser.getUsername()
      )
    ).thenReturn(true);
    DataObject returned = service.getDataObject(dataObject.getShepardId());
    assertEquals(dataObject, returned);
  }

  @Test
  public void getDataObjectWithVersionUIDTest() {
    Collection collection = aCollection().id(555L).shepardId(5555L).build();
    DataObject dataObject = aDataObject().id(5L).shepardId(55L).inCollection(collection).build();
    UUID versionUID = new UUID(0L, 1L);
    when(dao.findByShepardId(dataObject.getShepardId(), versionUID)).thenReturn(dataObject);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(
      permissionsService.isAccessTypeAllowedForUser(
        collection.getShepardId(),
        AccessType.Read,
        defaultUser.getUsername()
      )
    ).thenReturn(true);
    DataObject returned = service.getDataObject(dataObject.getShepardId(), versionUID);
    assertEquals(dataObject, returned);
  }

  @Test
  public void getDataObjectTest_deleted() {
    DataObject dataObject = aDataObject().id(5L).shepardId(55L).deleted(true).build();
    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    assertThrows(InvalidPathException.class, () -> {
      service.getDataObject(dataObject.getShepardId());
    });
  }

  @Test
  public void getDataObjectTest_isNull() {
    Long shepardId = 65L;
    when(dao.findByShepardId(shepardId)).thenReturn(null);
    assertThrows(InvalidPathException.class, () -> {
      service.getDataObject(shepardId);
    });
  }

  @Test
  public void getDataObjectTest_deletedParent() {
    Collection collection = aCollection().id(555L).shepardId(5555L).build();
    DataObject parent = aDataObject().id(1L).shepardId(15L).deleted(true).build();
    DataObject dataObject = aDataObject()
      .id(2L)
      .shepardId(25L)
      .named("data-object-25")
      .inCollection(collection)
      .withParent(parent)
      .build();
    DataObject dataObjectCut = aDataObject()
      .id(2L)
      .shepardId(25L)
      .named("data-object-25")
      .inCollection(collection)
      .build();

    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(
      permissionsService.isAccessTypeAllowedForUser(
        collection.getShepardId(),
        AccessType.Read,
        defaultUser.getUsername()
      )
    ).thenReturn(true);
    DataObject returned = service.getDataObject(dataObject.getShepardId());
    assertEquals(dataObjectCut, returned);
  }

  @Test
  public void getDataObjectTest_withParent() {
    Collection collection = aCollection().id(555L).shepardId(5555L).build();
    DataObject parent = aDataObject().id(1L).shepardId(15L).build();
    DataObject dataObject = aDataObject()
      .id(2L)
      .shepardId(25L)
      .inCollection(collection)
      .withParent(parent)
      .build();

    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(
      permissionsService.isAccessTypeAllowedForUser(
        collection.getShepardId(),
        AccessType.Read,
        defaultUser.getUsername()
      )
    ).thenReturn(true);
    DataObject returned = service.getDataObject(dataObject.getShepardId());
    assertEquals(dataObject, returned);
  }

  @Test
  public void getDataObjectTest_notInCollection() {
    Collection collection = aCollection().id(555L).shepardId(5555L).build();
    Collection otherCollection = aCollection().id(15L).shepardId(10L).build();
    DataObject dataObject = aDataObject().id(2L).shepardId(25L).inCollection(otherCollection).build();

    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(
      permissionsService.isAccessTypeAllowedForUser(
        collection.getShepardId(),
        AccessType.Read,
        defaultUser.getUsername()
      )
    ).thenReturn(true);
    assertThrows(InvalidPathException.class, () ->
      service.getDataObject(collection.getShepardId(), dataObject.getShepardId())
    );
  }

  @Test
  public void getDataObjectsByShepardIdsTest() {
    DataObject dataObjectNotDeleted = aDataObject().id(5L).shepardId(55L).build();

    QueryParamHelper params = new QueryParamHelper().withName("Name");
    Long collectionShepardId = 1L;
    when(dao.findByCollectionByShepardIds(collectionShepardId, params, null)).thenReturn(List.of(dataObjectNotDeleted));
    List<DataObject> returned = service.getAllDataObjectsByShepardIds(collectionShepardId, params, null);
    assertEquals(List.of(dataObjectNotDeleted), returned);
  }

  @Test
  public void createDataObjectByShepardIdWithoutPredecessorsTest() {
    Date date = new Date(23);
    Version version = new Version(new UUID(1L, 2L));
    Collection collection = aCollection().id(2L).shepardId(25L).build();
    collection.setVersion(version);
    DataObject parent = aDataObject().id(3L).shepardId(35L).inCollection(collection).build();
    DataObjectIO input = new DataObjectIO() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setParentId(parent.getShepardId());
      }
    };
    // toCreate / created / createdWithShepardId stay inline: the service builds
    // `toCreate` via `new DataObject()` (id=null) for the first persist call —
    // fixture builders default a non-null id, so these "pre-persist" shapes
    // remain hand-rolled to match what the service produces internally.
    DataObject toCreate = new DataObject() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setCollection(collection);
        setParent(parent);
      }
    };
    DataObject created = new DataObject() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setCollection(collection);
        setParent(parent);
        setId(1L);
      }
    };
    DataObject createdWithShepardId = new DataObject() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setCollection(collection);
        setParent(parent);
        setId(created.getId());
        setShepardId(created.getId());
      }
    };
    when(dao.findByShepardId(parent.getShepardId())).thenReturn(parent);
    when(dateHelper.getDate()).thenReturn(date);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(userService.getCurrentUser()).thenReturn(defaultUser);

    DataObject actual = service.createDataObject(collection.getShepardId(), input);
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createDataObjectByShepardIdTest() {
    Date date = new Date(23);
    Version version = new Version(new UUID(1L, 2L));
    Collection collection = aCollection().id(2L).shepardId(25L).build();
    collection.setVersion(version);
    DataObject parent = aDataObject().id(3L).shepardId(35L).inCollection(collection).build();
    DataObject predecessor = aDataObject().id(4L).shepardId(45L).inCollection(collection).build();
    DataObjectIO input = new DataObjectIO() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setParentId(parent.getShepardId());
        setPredecessorIds(new long[] { predecessor.getShepardId() });
      }
    };
    DataObject toCreate = new DataObject() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setCollection(collection);
        setParent(parent);
        setPredecessors(List.of(predecessor));
      }
    };
    DataObject created = new DataObject() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setCollection(collection);
        setParent(parent);
        setPredecessors(List.of(predecessor));
        setId(1L);
      }
    };
    DataObject createdWithShepardId = new DataObject() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(defaultUser);
        setCollection(collection);
        setParent(parent);
        setPredecessors(List.of(predecessor));
        setId(created.getId());
        setShepardId(created.getId());
      }
    };
    when(dao.findByShepardId(parent.getShepardId())).thenReturn(parent);
    when(dao.findByShepardId(predecessor.getShepardId())).thenReturn(predecessor);
    when(dateHelper.getDate()).thenReturn(date);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    when(userService.getCurrentUser()).thenReturn(defaultUser);

    DataObject actual = service.createDataObject(collection.getShepardId(), input);
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createDataObjectByShepardIdTest_wrongParent() {
    User user = aUser().username("bob").build();
    Date date = new Date(23);
    Collection collection = aCollection().id(2L).shepardId(25L).build();
    DataObjectIO input = new DataObjectIO() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setParentId(3L);
      }
    };
    when(dao.findByShepardId(input.getParentId())).thenReturn(null);
    when(dateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () -> service.createDataObject(collection.getShepardId(), input));
  }

  @Test
  public void createDataObjectByShepardIdTest_wrongPredecessor() {
    User user = aUser().username("bob").build();
    Date date = new Date(23);
    Collection collection = aCollection().id(2L).shepardId(25L).build();
    DataObjectIO input = new DataObjectIO() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setPredecessorIds(new long[] { 3L });
      }
    };
    when(dao.findByShepardId(input.getPredecessorIds()[0])).thenReturn(null);
    when(dateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () -> service.createDataObject(collection.getShepardId(), input));
  }

  @Test
  public void createDataObjectByShepardIdTest_deletedParent() {
    User user = aUser().username("bob").build();
    Date date = new Date(23);
    Collection collection = aCollection().id(2L).shepardId(25L).build();
    DataObject parent = aDataObject().id(3L).shepardId(35L).deleted(true).build();
    DataObjectIO input = new DataObjectIO() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setParentId(parent.getShepardId());
      }
    };
    when(dao.findByShepardId(parent.getShepardId())).thenReturn(parent);
    when(dateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () -> service.createDataObject(collection.getShepardId(), input));
  }

  @Test
  public void createDataObjectByShepardIdTest_deletedPredecessor() {
    User user = aUser().username("bob").build();
    Date date = new Date(23);
    Collection collection = aCollection().id(2L).shepardId(25L).build();
    DataObject predecessor = aDataObject().id(3L).shepardId(35L).deleted(true).build();
    DataObjectIO input = new DataObjectIO() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setPredecessorIds(new long[] { predecessor.getShepardId() });
      }
    };
    when(dao.findByShepardId(input.getPredecessorIds()[0])).thenReturn(predecessor);
    when(dateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () -> service.createDataObject(collection.getShepardId(), input));
  }

  @Test
  public void createDataObjectByShepardIdTest_ParentWrongCollection() {
    User user = aUser().username("bob").build();
    Date date = new Date(23);
    Collection collection = aCollection().id(2L).shepardId(25L).build();
    Collection wrong = aCollection().id(200L).shepardId(2005L).build();
    DataObject parent = aDataObject().id(3L).shepardId(35L).inCollection(wrong).build();
    DataObjectIO input = new DataObjectIO() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setParentId(parent.getShepardId());
      }
    };
    when(dao.findByShepardId(parent.getShepardId())).thenReturn(parent);
    when(dateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () -> service.createDataObject(collection.getShepardId(), input));
  }

  @Test
  public void createDataObjectByShepardIdTest_PredecessorWrongCollection() {
    User user = aUser().username("bob").build();
    Date date = new Date(23);
    Collection collection = aCollection().id(2L).shepardId(25L).build();
    Collection wrong = aCollection().id(200L).shepardId(2005L).build();
    DataObject predecessor = aDataObject().id(4L).shepardId(45L).inCollection(wrong).build();
    DataObjectIO input = new DataObjectIO() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setPredecessorIds(new long[] { predecessor.getShepardId() });
      }
    };
    when(dao.findByShepardId(predecessor.getShepardId())).thenReturn(predecessor);
    when(dateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () -> service.createDataObject(collection.getShepardId(), input));
  }

  @Test
  public void updateDataObjectByShepardIdTest() {
    Collection collection = aCollection().id(100L).shepardId(1005L).build();
    Date date = new Date(23);
    User updateUser = aUser().username("claus").build();
    Date updateDate = new Date(43);

    DataObject parent = aDataObject().id(3L).shepardId(35L).inCollection(collection).build();
    DataObject aPredecessor = aDataObject().id(4L).shepardId(45L).inCollection(collection).build();
    List<DataObject> predecessors = List.of(aPredecessor);

    DataObjectIO input = new DataObjectIO() {
      {
        setId(1L);
        setAttributes(Map.of("1", "2", "c", "d"));
        setDescription("newDesc");
        setName("newName");
        setParentId(parent.getShepardId());
        setPredecessorIds(predecessors.stream().mapToLong(DataObject::getShepardId).toArray());
      }
    };
    DataObject old = aDataObject()
      .id(1L)
      .shepardId(1L)
      .named("Name")
      .withDescription("Desc")
      .withAttributes(Map.of("a", "b", "c", "d"))
      .createdAt(date)
      .ownedBy(defaultUser)
      .inCollection(collection)
      .withPredecessors(predecessors)
      .build();
    DataObject updated = aDataObject()
      .id(old.getId())
      .shepardId(old.getShepardId())
      .named("newName")
      .withDescription("newDesc")
      .withAttributes(Map.of("1", "2", "c", "d"))
      .createdAt(date)
      .ownedBy(defaultUser)
      .updatedAt(updateDate)
      .updatedBy(updateUser)
      .withParent(parent)
      .withPredecessors(predecessors)
      .inCollection(collection)
      .build();

    when(dao.findByShepardId(old.getShepardId())).thenReturn(old);
    when(dao.findByShepardId(parent.getShepardId())).thenReturn(parent);
    when(dao.createOrUpdate(old)).thenReturn(updated);
    when(dao.findByNeo4jId(aPredecessor.getId())).thenReturn(aPredecessor);

    when(dateHelper.getDate()).thenReturn(updateDate);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(
      permissionsService.isAccessTypeAllowedForUser(old.getShepardId(), AccessType.Write, updateUser.getUsername())
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(old.getShepardId(), AccessType.Read, updateUser.getUsername())
    ).thenReturn(true);

    predecessors.forEach(predecessor -> when(dao.createOrUpdate(predecessor)).thenReturn(predecessor));
    predecessors.forEach(predecessor -> when(dao.findByShepardId(predecessor.getShepardId())).thenReturn(predecessor));

    DataObject actual = service.updateDataObject(collection.getShepardId(), old.getShepardId(), input);

    predecessors.forEach(predecessor -> verify(dao, atLeast(1)).findByShepardId(predecessor.getShepardId()));
    predecessors.forEach(predecessor ->
      verify(dao).deleteHasSuccessorRelation(predecessor.getShepardId(), old.getShepardId())
    );
    assertEquals(updated, actual);
  }

  @Test
  public void updateDataObjectByShepardIdTest_UpdateParent() {
    Collection collection = aCollection().id(100L).shepardId(1005L).build();
    Date date = new Date(23);
    User updateUser = aUser().username("claus").build();
    Date updateDate = new Date(43);
    DataObject parent = aDataObject().id(3L).shepardId(35L).inCollection(collection).build();
    DataObject oldParent = aDataObject().id(3L).shepardId(35L).inCollection(collection).build();
    @SuppressWarnings("unchecked")
    List<DataObject> children = Mockito.mock(List.class);
    oldParent.setChildren(children);

    DataObjectIO input = new DataObjectIO() {
      {
        setId(1L);
        setAttributes(Map.of("1", "2", "c", "d"));
        setDescription("newDesc");
        setName("newName");
        setParentId(parent.getShepardId());
      }
    };
    DataObject old = aDataObject()
      .id(1L)
      .shepardId(1L)
      .named("Name")
      .withDescription("Desc")
      .withAttributes(Map.of("a", "b", "c", "d"))
      .createdAt(date)
      .ownedBy(defaultUser)
      .inCollection(collection)
      .withParent(oldParent)
      .build();

    DataObject updated = aDataObject()
      .id(old.getId())
      .shepardId(old.getShepardId())
      .named("newName")
      .withDescription("newDesc")
      .withAttributes(Map.of("1", "2", "c", "d"))
      .createdAt(date)
      .ownedBy(defaultUser)
      .updatedAt(updateDate)
      .updatedBy(updateUser)
      .withParent(parent)
      .inCollection(collection)
      .build();

    DataObject oldParentSpy = spy(oldParent);
    when(oldParentSpy.getChildren()).thenReturn(children);
    when(dao.findByShepardId(old.getShepardId())).thenReturn(old);
    when(dao.findByShepardId(parent.getShepardId())).thenReturn(parent);
    when(dao.findByShepardId(oldParent.getShepardId())).thenReturn(oldParent);
    when(dao.findByNeo4jId(parent.getId())).thenReturn(parent);
    when(dao.createOrUpdate(old)).thenReturn(updated);

    when(dateHelper.getDate()).thenReturn(updateDate);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(
      permissionsService.isAccessTypeAllowedForUser(old.getShepardId(), AccessType.Write, updateUser.getUsername())
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(old.getShepardId(), AccessType.Read, updateUser.getUsername())
    ).thenReturn(true);

    var actual = service.updateDataObject(collection.getShepardId(), old.getShepardId(), input);
    verify(dao).deleteHasChildRelation(anyLong(), anyLong());

    assertEquals(updated, actual);
  }

  @Test
  public void updateDataObjectByShepardIdTest_SelfReferences() {
    Collection collection = aCollection().id(100L).shepardId(1005L).build();
    Date date = new Date(23);

    DataObjectIO input = new DataObjectIO() {
      {
        setId(1L);
        setPredecessorIds(new long[] { 1L });
      }
    };
    DataObject old = aDataObject()
      .id(input.getId())
      .shepardId(input.getId())
      .inCollection(collection)
      .build();

    when(dao.findByShepardId(old.getShepardId())).thenReturn(old);
    when(dateHelper.getDate()).thenReturn(date);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(
      permissionsService.isAccessTypeAllowedForUser(old.getShepardId(), AccessType.Write, defaultUser.getUsername())
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(old.getShepardId(), AccessType.Read, defaultUser.getUsername())
    ).thenReturn(true);

    assertThrows(InvalidBodyException.class, () ->
      service.updateDataObject(collection.getShepardId(), old.getShepardId(), input)
    );
  }

  @Test
  public void deleteDataObjectByShepardIdTest() {
    Date date = new Date(23);

    Collection collection = aCollection().shepardId(1005L).build();

    DataObject dataObject = aDataObject().id(1L).shepardId(15L).inCollection(collection).build();

    when(dateHelper.getDate()).thenReturn(date);
    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(dao.deleteDataObjectByShepardId(dataObject.getShepardId(), defaultUser, date)).thenReturn(true);
    when(collectionService.getCollection(collection.getShepardId())).thenReturn(collection);
    when(userService.getCurrentUser()).thenReturn(defaultUser);
    when(
      permissionsService.isAccessTypeAllowedForUser(
        dataObject.getShepardId(),
        AccessType.Write,
        defaultUser.getUsername()
      )
    ).thenReturn(true);
    when(
      permissionsService.isAccessTypeAllowedForUser(
        dataObject.getShepardId(),
        AccessType.Read,
        defaultUser.getUsername()
      )
    ).thenReturn(true);

    assertDoesNotThrow(() -> service.deleteDataObject(1005L, dataObject.getShepardId()));
  }
}
