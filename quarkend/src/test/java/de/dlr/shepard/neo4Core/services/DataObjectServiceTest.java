package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.dao.VersionDAO;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.DataObjectReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.QueryParamHelper;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DataObjectServiceTest extends BaseTestCase {

  @Mock
  private DataObjectDAO dao;

  @Mock
  private CollectionDAO collectionDAO;

  @Mock
  private BasicReferenceDAO referenceDAO;

  @Mock
  private UserDAO userDAO;

  @Mock
  private VersionDAO versionDAO;

  @Mock
  private DateHelper dateHelper;

  @InjectMocks
  private DataObjectService service;

  @Test
  public void getDataObjectByShepardIdTest() {
    DataObject dataObject = new DataObject(5L);
    dataObject.setShepardId(55L);
    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    DataObject returned = service.getDataObjectByShepardId(dataObject.getShepardId());
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
  public void getDataObjectByShepardIdTest_deletedEntities() {
    DataObject dataObjectNotDeleted = new DataObject(1L);
    dataObjectNotDeleted.setShepardId(15L);
    DataObject dataObjectDeleted = new DataObject(2L);
    dataObjectDeleted.setShepardId(25L);
    dataObjectDeleted.setDeleted(true);

    DataObjectReference doRefNotDeleted = new DataObjectReference(6L);
    doRefNotDeleted.setShepardId(65L);
    DataObjectReference doRefDeleted = new DataObjectReference(7L);
    doRefDeleted.setShepardId(75L);
    doRefDeleted.setDeleted(true);

    BasicReference refNotDeleted = new BasicReference(3L);
    refNotDeleted.setShepardId(35L);
    BasicReference refDeleted = new BasicReference(4L);
    refDeleted.setShepardId(45L);
    refDeleted.setDeleted(true);

    DataObject dataObject = new DataObject(5L);
    dataObject.setShepardId(55L);
    dataObject.setChildren(List.of(dataObjectDeleted, dataObjectNotDeleted));
    dataObject.setPredecessors(List.of(dataObjectDeleted, dataObjectNotDeleted));
    dataObject.setSuccessors(List.of(dataObjectDeleted, dataObjectNotDeleted));
    dataObject.setReferences(List.of(refDeleted, refNotDeleted));
    dataObject.setIncoming(List.of(doRefDeleted, doRefNotDeleted));

    DataObject dataObjectCut = new DataObject(dataObject.getId());
    dataObjectCut.setShepardId(dataObject.getShepardId());
    dataObjectCut.setChildren(List.of(dataObjectNotDeleted));
    dataObjectCut.setPredecessors(List.of(dataObjectNotDeleted));
    dataObjectCut.setSuccessors(List.of(dataObjectNotDeleted));
    dataObjectCut.setReferences(List.of(refNotDeleted));
    dataObjectCut.setIncoming(List.of(doRefNotDeleted));

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
    when(dao.findByCollectionByShepardIds(collectionShepardId, params)).thenReturn(List.of(dataObjectNotDeleted));
    List<DataObject> returned = service.getAllDataObjectsByShepardIds(collectionShepardId, params);
    assertEquals(List.of(dataObjectNotDeleted), returned);
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
    when(DateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionDAO.findByShepardId(collection.getShepardId())).thenReturn(collection);
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
    when(DateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionDAO.findByShepardId(collection.getShepardId())).thenReturn(collection);
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
    when(DateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionDAO.findByShepardId(collection.getShepardId())).thenReturn(collection);
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
    when(DateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionDAO.findByShepardId(collection.getShepardId())).thenReturn(collection);
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
    when(DateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionDAO.findByShepardId(collection.getShepardId())).thenReturn(collection);
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
    when(DateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionDAO.findByShepardId(collection.getShepardId())).thenReturn(collection);
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
    when(DateHelper.getDate()).thenReturn(date);
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(collectionDAO.findByShepardId(collection.getShepardId())).thenReturn(collection);
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
    DataObject predecessor = new DataObject(4L);
    predecessor.setShepardId(45L);
    predecessor.setCollection(collection);

    DataObjectIO input = new DataObjectIO() {
      {
        setId(1L);
        setAttributes(Map.of("1", "2", "c", "d"));
        setDescription("newDesc");
        setName("newName");
        setParentId(parent.getShepardId());
        setPredecessorIds(new long[] { predecessor.getShepardId() });
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
        setPredecessors(List.of(predecessor));
        setId(old.getId());
        setShepardId(old.getShepardId());
        setCollection(collection);
      }
    };

    when(dao.findByShepardId(old.getShepardId())).thenReturn(old);
    when(dao.findByShepardId(parent.getShepardId())).thenReturn(parent);
    when(dao.findByShepardId(predecessor.getShepardId())).thenReturn(predecessor);
    when(userDAO.find(updateUser.getUsername())).thenReturn(updateUser);
    when(DateHelper.getDate()).thenReturn(updateDate);
    when(dao.createOrUpdate(updated)).thenReturn(updated);

    var actual = service.updateDataObjectByShepardId(old.getShepardId(), input, updateUser.getUsername());
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
    when(DateHelper.getDate()).thenReturn(date);

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
    when(DateHelper.getDate()).thenReturn(date);
    when(dao.findByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(dao.deleteDataObjectByShepardId(dataObject.getShepardId(), user, date)).thenReturn(true);

    var result = service.deleteDataObjectByShepardId(dataObject.getShepardId(), user.getUsername());
    assertTrue(result);
  }
}
