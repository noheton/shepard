package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.CollectionDAO;
import de.dlr.shepard.neo4Core.dao.DataObjectDAO;
import de.dlr.shepard.neo4Core.dao.PermissionsDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PermissionType;
import de.dlr.shepard.util.QueryParamHelper;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CollectionServiceTest extends BaseTestCase {

  @Mock
  private CollectionDAO dao;

  @Mock
  private DataObjectDAO dataObjectDAO;

  @Mock
  private BasicReferenceDAO referenceDAO;

  @Mock
  private UserDAO userDAO;

  @Mock
  private PermissionsDAO permissionsDAO;

  @Mock
  private DateHelper dateHelper;

  @InjectMocks
  private CollectionService service;

  @Test
  public void getCollectionsByShepardIdTest() {
    String username = "manni";
    Collection collectionNotDeleted = new Collection(5L);
    collectionNotDeleted.setShepardId(55L);
    Collection collectionDeleted = new Collection(6L);
    collectionDeleted.setShepardId(65L);
    collectionDeleted.setDeleted(true);

    when(dao.findAllCollectionsByShepardId(null, username)).thenReturn(List.of(collectionNotDeleted));
    List<Collection> returned = service.getAllCollectionsByShepardId(null, username);
    assertEquals(List.of(collectionNotDeleted), returned);
  }

  @Test
  public void getCollectionsByShepardIdTest_withName() {
    String username = "patrick";
    Collection collectionNotDeleted = new Collection(5L);
    collectionNotDeleted.setShepardId(55L);
    Collection collectionDeleted = new Collection(6L);
    collectionDeleted.setShepardId(65L);
    collectionDeleted.setDeleted(true);

    QueryParamHelper params = new QueryParamHelper().withName("test");
    when(dao.findAllCollectionsByShepardId(params, username)).thenReturn(List.of(collectionNotDeleted));
    List<Collection> returned = service.getAllCollectionsByShepardId(params, username);
    assertEquals(List.of(collectionNotDeleted), returned);
  }

  @Test
  public void createCollectionTest() {
    User user = new User("bob");
    Date date = new Date(23);
    CollectionIO input = new CollectionIO() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
      }
    };
    Collection toCreate = new Collection() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
      }
    };
    Collection created = new Collection() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
        setId(1L);
      }
    };
    Collection createdWithShepardId = new Collection() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
        setId(created.getId());
        setShepardId(created.getId());
      }
    };
    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(createdWithShepardId)).thenReturn(createdWithShepardId);
    Collection actual = service.createCollection(input, user.getUsername());
    assertEquals(createdWithShepardId, actual);
    verify(permissionsDAO).createOrUpdate(new Permissions(created, user, PermissionType.Private));
  }

  @Test
  public void updateCollectionByShepardIdTest() {
    User user = new User("bob");
    Date date = new Date(23);
    User updateUser = new User("claus");
    Date updateDate = new Date(43);

    CollectionIO input = new CollectionIO() {
      {
        setId(1L);
        setAttributes(Map.of("1", "2", "c", "d"));
        setDescription("newDesc");
        setName("newName");
      }
    };
    Collection old = new Collection() {
      {
        setAttributes(Map.of("a", "b", "c", "d"));
        setDescription("Desc");
        setName("Name");
        setCreatedAt(date);
        setCreatedBy(user);
        setId(15L);
        setShepardId(input.getId());
      }
    };
    var updated = new Collection() {
      {
        setAttributes(Map.of("1", "2", "c", "d"));
        setDescription("newDesc");
        setName("newName");
        setCreatedAt(date);
        setCreatedBy(user);
        setUpdatedAt(updateDate);
        setUpdatedBy(updateUser);
        setId(old.getId());
        setShepardId(old.getShepardId());
      }
    };

    when(dao.findByShepardId(old.getShepardId())).thenReturn(old);
    when(userDAO.find(updateUser.getUsername())).thenReturn(updateUser);
    when(dateHelper.getDate()).thenReturn(updateDate);
    when(dao.createOrUpdate(updated)).thenReturn(updated);

    var actual = service.updateCollectionByShepardId(old.getShepardId(), input, updateUser.getUsername());
    assertEquals(updated, actual);
  }

  @Test
  public void deleteCollectionByShepardIdTest() {
    User user = new User("bob");
    Date date = new Date(23);

    Collection collection = new Collection(1L);
    collection.setShepardId(15L);

    when(userDAO.find(user.getUsername())).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(date);
    when(dao.deleteCollectionByShepardId(collection.getShepardId(), user, date)).thenReturn(true);

    var result = service.deleteCollectionByShepardId(collection.getShepardId(), user.getUsername());
    assertTrue(result);
  }
}
