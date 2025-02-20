package de.dlr.shepard.context.collection.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
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
  CollectionDAO collectionDAO;

  @InjectMock
  BasicReferenceDAO referenceDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  VersionDAO versionDAO;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  DataObjectService service;

  @Test
  public void getDataObjectByShepardIdTest() {
    DataObject dataObject = new DataObject(5L);
    dataObject.setShepardId(55L);
    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    DataObject returned = service.getDataObjectByShepardId(dataObject.getShepardId());
    assertEquals(dataObject, returned);
  }

  @Test
  public void getDataObjectByShepardIdWithVersionUIDTest() {
    DataObject dataObject = new DataObject(5L);
    UUID versionUID = new UUID(0L, 1L);
    dataObject.setShepardId(55L);
    when(dao.findByShepardId(dataObject.getShepardId(), versionUID)).thenReturn(dataObject);
    DataObject returned = service.getDataObjectByShepardId(dataObject.getShepardId(), versionUID);
    assertEquals(dataObject, returned);
  }

  @Test
  public void getDataObjectByShepardIdTest_deleted() {
    DataObject dataObject = new DataObject(5L);
    dataObject.setDeleted(true);
    dataObject.setShepardId(55L);
    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    assertNull(service.getDataObjectByShepardId(dataObject.getShepardId()));
  }

  @Test
  public void getDataObjectByShepardIdTest_isNull() {
    Long shepardId = 65L;
    when(dao.findByShepardId(shepardId)).thenReturn(null);
    assertNull(service.getDataObjectByShepardId(shepardId));
  }

  @Test
  public void getDataObjectByShepardIdTest_deletedParent() {
    DataObject parent = new DataObject(1L);
    parent.setShepardId(15L);
    parent.setDeleted(true);
    DataObject dataObject = new DataObject(2L);
    dataObject.setShepardId(25L);
    dataObject.setParent(parent);
    DataObject dataObjectCut = new DataObject(2L);
    dataObjectCut.setShepardId(25L);

    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    DataObject returned = service.getDataObjectByShepardId(dataObject.getShepardId());
    assertEquals(dataObjectCut, returned);
  }

  @Test
  public void getDataObjectByShepardIdTest_withParent() {
    DataObject parent = new DataObject(1L);
    parent.setShepardId(15L);
    DataObject dataObject = new DataObject(2L);
    dataObject.setShepardId(25L);
    dataObject.setParent(parent);

    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    DataObject returned = service.getDataObjectByShepardId(dataObject.getShepardId());
    assertEquals(dataObject, returned);
  }

  @Test
  public void getDataObjectsByShepardIdsTest() {
    DataObject dataObjectNotDeleted = new DataObject(5L);
    dataObjectNotDeleted.setShepardId(55L);

    QueryParamHelper params = new QueryParamHelper().withName("Name");
    Long collectionShepardId = 1L;
    when(dao.findByCollectionByShepardIds(collectionShepardId, params, null)).thenReturn(List.of(dataObjectNotDeleted));
    List<DataObject> returned = service.getAllDataObjectsByShepardIds(collectionShepardId, params, null);
    assertEquals(List.of(dataObjectNotDeleted), returned);
  }

  @Test
  public void createDataObjectByShepardIdWithoutPredecessorsTest() {
    User user = new User("bob");
    Date date = new Date(23);
    Version version = new Version(new UUID(1L, 2L));
    Collection collection = new Collection(2L);
    collection.setShepardId(25L);
    collection.setVersion(version);
    DataObject parent = new DataObject(3L);
    parent.setShepardId(35L);
    parent.setCollection(collection);
    DataObjectIO input = new DataObjectIO() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setParentId(parent.getShepardId());
      }
    };
    DataObject toCreate = new DataObject() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
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
        setCreatedBy(user);
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
        setCreatedBy(user);
        setCollection(collection);
        setParent(parent);
        setId(created.getId());
        setShepardId(created.getId());
      }
    };
    when(dao.findByShepardId(parent.getShepardId())).thenReturn(parent);
    when(dateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionDAO.findByShepardId(collection.getShepardId(), true)).thenReturn(collection);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    DataObject actual = service.createDataObjectByCollectionShepardId(
      collection.getShepardId(),
      input,
      user.getUsername()
    );
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createDataObjectByShepardIdTest() {
    User user = new User("bob");
    Date date = new Date(23);
    Version version = new Version(new UUID(1L, 2L));
    Collection collection = new Collection(2L);
    collection.setShepardId(25L);
    collection.setVersion(version);
    DataObject parent = new DataObject(3L);
    parent.setShepardId(35L);
    parent.setCollection(collection);
    DataObject predecessor = new DataObject(4L);
    predecessor.setShepardId(45L);
    predecessor.setCollection(collection);
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
        setCreatedBy(user);
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
        setCreatedBy(user);
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
        setCreatedBy(user);
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
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionDAO.findByShepardId(collection.getShepardId(), true)).thenReturn(collection);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    DataObject actual = service.createDataObjectByCollectionShepardId(
      collection.getShepardId(),
      input,
      user.getUsername()
    );
    assertEquals(createdWithShepardId, actual);
  }

  @Test
  public void createDataObjectByShepardIdTest_wrongParent() {
    User user = new User("bob");
    Date date = new Date(23);
    Collection collection = new Collection(2L);
    collection.setShepardId(25L);
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
    when(collectionDAO.findByShepardId(collection.getShepardId(), true)).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () ->
      service.createDataObjectByCollectionShepardId(collection.getShepardId(), input, user.getUsername())
    );
  }

  @Test
  public void createDataObjectByShepardIdTest_wrongPredecessor() {
    User user = new User("bob");
    Date date = new Date(23);
    Collection collection = new Collection(2L);
    collection.setShepardId(25L);
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
    when(collectionDAO.findByShepardId(collection.getShepardId(), true)).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () ->
      service.createDataObjectByCollectionShepardId(collection.getShepardId(), input, user.getUsername())
    );
  }

  @Test
  public void createDataObjectByShepardIdTest_deletedParent() {
    User user = new User("bob");
    Date date = new Date(23);
    Collection collection = new Collection(2L);
    collection.setShepardId(25L);
    DataObject parent = new DataObject(3L);
    parent.setShepardId(35L);
    parent.setDeleted(true);
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
    when(collectionDAO.findByShepardId(collection.getShepardId(), true)).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () ->
      service.createDataObjectByCollectionShepardId(collection.getShepardId(), input, user.getUsername())
    );
  }

  @Test
  public void createDataObjectByShepardIdTest_deletedPredecessor() {
    User user = new User("bob");
    Date date = new Date(23);
    Collection collection = new Collection(2L);
    collection.setShepardId(25L);
    DataObject predecessor = new DataObject(3L);
    predecessor.setShepardId(35L);
    predecessor.setDeleted(true);
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
    when(collectionDAO.findByShepardId(collection.getShepardId(), true)).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () ->
      service.createDataObjectByCollectionShepardId(collection.getShepardId(), input, user.getUsername())
    );
  }

  @Test
  public void createDataObjectByShepardIdTest_ParentWrongCollection() {
    User user = new User("bob");
    Date date = new Date(23);
    Collection collection = new Collection(2L);
    collection.setShepardId(25L);
    Collection wrong = new Collection(200L);
    wrong.setShepardId(2005L);
    DataObject parent = new DataObject(3L);
    parent.setShepardId(35L);
    parent.setCollection(wrong);
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
    when(collectionDAO.findByShepardId(collection.getShepardId(), true)).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () ->
      service.createDataObjectByCollectionShepardId(collection.getShepardId(), input, user.getUsername())
    );
  }

  @Test
  public void createDataObjectByShepardIdTest_PredecessorWrongCollection() {
    User user = new User("bob");
    Date date = new Date(23);
    Collection collection = new Collection(2L);
    collection.setShepardId(25L);
    Collection wrong = new Collection(200L);
    wrong.setShepardId(2005L);
    DataObject predecessor = new DataObject(4L);
    predecessor.setShepardId(45L);
    predecessor.setCollection(wrong);
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
    when(collectionDAO.findByShepardId(collection.getShepardId(), true)).thenReturn(collection);
    assertThrows(InvalidBodyException.class, () ->
      service.createDataObjectByCollectionShepardId(collection.getShepardId(), input, user.getUsername())
    );
  }

  @Test
  public void updateDataObjectByShepardIdTest() {
    Collection collection = new Collection(100L);
    collection.setShepardId(1005L);
    User user = new User("bob");
    Date date = new Date(23);
    User updateUser = new User("claus");
    Date updateDate = new Date(43);
    DataObject parent = new DataObject(3L);
    parent.setShepardId(35L);
    parent.setCollection(collection);
    DataObject aPredecessor = new DataObject(4L);
    aPredecessor.setShepardId(45L);
    aPredecessor.setCollection(collection);
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
    DataObject old = new DataObject() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
        setId(1L);
        setShepardId(1L);
        setCollection(collection);
        setPredecessors(predecessors);
      }
    };
    DataObject updated = new DataObject() {
      {
        setAttributes(Map.of("1", "2", "c", "d"));
        setDescription("newDesc");
        setName("newName");
        setCreatedAt(date);
        setCreatedBy(user);
        setUpdatedAt(updateDate);
        setUpdatedBy(updateUser);
        setParent(parent);
        setPredecessors(predecessors);
        setId(old.getId());
        setShepardId(old.getShepardId());
        setCollection(collection);
      }
    };

    when(dao.findByShepardId(old.getShepardId())).thenReturn(old);
    when(dao.findByShepardId(parent.getShepardId())).thenReturn(parent);
    when(userDAO.find(updateUser.getUsername())).thenReturn(updateUser);
    when(dateHelper.getDate()).thenReturn(updateDate);
    when(dao.createOrUpdate(updated)).thenReturn(updated);
    predecessors.forEach(predecessor -> when(dao.createOrUpdate(predecessor)).thenReturn(predecessor));
    predecessors.forEach(predecessor -> when(dao.findByShepardId(predecessor.getShepardId())).thenReturn(predecessor));

    var actual = service.updateDataObjectByShepardId(old.getShepardId(), input, updateUser.getUsername());
    predecessors.forEach(predecessor -> verify(dao, atLeast(1)).findByShepardId(predecessor.getShepardId()));
    predecessors.forEach(predecessor ->
      verify(dao).deleteHasSuccessorRelation(predecessor.getShepardId(), old.getShepardId())
    );
    assertEquals(updated, actual);
  }

  @Test
  public void updateDataObjectByShepardIdTest_UpdateParent() {
    Collection collection = new Collection(100L);
    collection.setShepardId(1005L);
    User user = new User("bob");
    Date date = new Date(23);
    User updateUser = new User("claus");
    Date updateDate = new Date(43);
    DataObject parent = new DataObject(3L);
    parent.setShepardId(35L);
    parent.setCollection(collection);
    DataObject oldParent = new DataObject(3L);
    oldParent.setShepardId(35L);
    oldParent.setCollection(collection);
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
    DataObject old = new DataObject() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
        setId(1L);
        setShepardId(1L);
        setCollection(collection);
        setParent(oldParent);
      }
    };

    oldParent.addChild(old);
    DataObject updated = new DataObject() {
      {
        setAttributes(Map.of("1", "2", "c", "d"));
        setDescription("newDesc");
        setName("newName");
        setCreatedAt(date);
        setCreatedBy(user);
        setUpdatedAt(updateDate);
        setUpdatedBy(updateUser);
        setParent(parent);
        setId(old.getId());
        setShepardId(old.getShepardId());
        setCollection(collection);
      }
    };

    DataObject oldParentSpy = spy(oldParent);
    when(oldParentSpy.getChildren()).thenReturn(children);

    when(dao.findByShepardId(old.getShepardId())).thenReturn(old);
    when(dao.findByShepardId(parent.getShepardId())).thenReturn(parent);
    when(dao.findByShepardId(oldParent.getShepardId())).thenReturn(oldParent);
    when(userDAO.find(updateUser.getUsername())).thenReturn(updateUser);
    when(dateHelper.getDate()).thenReturn(updateDate);
    when(dao.createOrUpdate(updated)).thenReturn(updated);

    var actual = service.updateDataObjectByShepardId(old.getShepardId(), input, updateUser.getUsername());
    verify(dao).deleteHasChildRelation(anyLong(), anyLong());

    assertEquals(updated, actual);
  }

  @Test
  public void updateDataObjectByShepardIdTest_SelfReferences() {
    Collection collection = new Collection(100L);
    collection.setShepardId(1005L);
    User user = new User("bob");
    Date date = new Date(23);

    DataObjectIO input = new DataObjectIO() {
      {
        setId(1L);
        setPredecessorIds(new long[] { 1L });
      }
    };
    DataObject old = new DataObject() {
      {
        setId(input.getId());
        setShepardId(input.getId());
        setCollection(collection);
      }
    };

    when(dao.findByShepardId(old.getShepardId())).thenReturn(old);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);

    assertThrows(InvalidBodyException.class, () ->
      service.updateDataObjectByShepardId(old.getShepardId(), input, user.getUsername())
    );
  }

  @Test
  public void deleteDataObjectByShepardIdTest() {
    User user = new User("bob");
    Date date = new Date(23);

    DataObject dataObject = new DataObject(1L);
    dataObject.setShepardId(15L);

    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(dao.deleteDataObjectByShepardId(dataObject.getShepardId(), user, date)).thenReturn(true);

    var result = service.deleteDataObjectByShepardId(dataObject.getShepardId(), user.getUsername());
    assertTrue(result);
  }
}
