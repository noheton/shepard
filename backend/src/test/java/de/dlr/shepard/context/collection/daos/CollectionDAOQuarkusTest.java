package de.dlr.shepard.context.collection.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.endpoints.DataObjectAttributes;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CollectionDAOQuarkusTest {

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  CollectionService collectionService;

  @Inject
  UserService userService;

  @Inject
  AuthenticationContext authenticationContext;

  private final String userName = "user_" + System.currentTimeMillis();
  private final String userName1 = "user1_" + System.currentTimeMillis();

  Collection collection1u;
  Collection collection1u1;
  Collection collection2u1;

  private Collection createCollection(CollectionIO collectionToCreate) {
    return collectionService.createCollection(collectionToCreate);
  }

  @BeforeEach
  public void setup() {
    User user = new User(userName);
    userService.createOrUpdateUser(user);
    User user1 = new User(userName1);
    userService.createOrUpdateUser(user1);
    authenticationContext.setPrincipal(new JWTPrincipal(userName, "key"));
    CollectionIO collectionIO1u = new CollectionIO();
    collectionIO1u.setName("collection1u");
    collection1u = createCollection(collectionIO1u);
    authenticationContext.setPrincipal(new JWTPrincipal(userName1, "key"));
    CollectionIO collectionIO1u1 = new CollectionIO();
    collectionIO1u1.setName("collection1u1");
    collection1u1 = createCollection(collectionIO1u1);
    CollectionIO collectionIO2u1 = new CollectionIO();
    collectionIO2u1.setName("collection2u1");
    collection2u1 = createCollection(collectionIO2u1);
  }

  @Test
  @Transactional
  public void findAll_WithoutNameByNeo4jId_user() {
    QueryParamHelper params = new QueryParamHelper();
    List<Collection> ret = collectionDAO.findAllCollectionsByNeo4jId(params, userName);
    assertEquals(ret.size(), 1);
    assertEquals(ret.get(0), collection1u);
  }

  @Test
  @Transactional
  public void findAll_WithoutNameByNeo4jId_user1() {
    QueryParamHelper params = new QueryParamHelper();
    List<Collection> ret = collectionDAO.findAllCollectionsByNeo4jId(params, userName1);
    assertEquals(ret.size(), 2);
    Set<Collection> retSet = new HashSet<Collection>();
    retSet.add(ret.get(0));
    retSet.add(ret.get(1));
    Set<Collection> expSet = new HashSet<Collection>();
    expSet.add(collection2u1);
    expSet.add(collection1u1);
    assertEquals(retSet, expSet);
  }

  @Test
  @Transactional
  public void findAll_WithoutNameByShepardId_user() {
    QueryParamHelper params = new QueryParamHelper();
    List<Collection> ret = collectionDAO.findAllCollectionsByShepardId(params, userName);
    assertEquals(ret.size(), 1);
    assertEquals(ret.get(0), collection1u);
  }

  @Test
  @Transactional
  public void findAll_WithoutNameByShepardId_user1() {
    QueryParamHelper params = new QueryParamHelper();
    List<Collection> ret = collectionDAO.findAllCollectionsByShepardId(params, userName1);
    assertEquals(ret.size(), 2);
    Set<Collection> retSet = new HashSet<Collection>();
    retSet.add(ret.get(0));
    retSet.add(ret.get(1));
    Set<Collection> expSet = new HashSet<Collection>();
    expSet.add(collection2u1);
    expSet.add(collection1u1);
    assertEquals(retSet, expSet);
  }

  @Test
  @Transactional
  public void findAll_WithoutNameByNeo4jIdOrderByNameDesc() {
    var params = new QueryParamHelper();
    var collectionAttribute = DataObjectAttributes.name;
    params = params.withOrderByAttribute(collectionAttribute, true);
    List<Collection> ret = collectionDAO.findAllCollectionsByNeo4jId(params, userName1);
    assertEquals(ret.size(), 2);
    assertEquals(ret.get(0), collection2u1);
    assertEquals(ret.get(1), collection1u1);
  }

  @Test
  @Transactional
  public void findAll_WithoutNameByShepardIdOrderByNameDesc() {
    var params = new QueryParamHelper();
    var collectionAttribute = DataObjectAttributes.name;
    params = params.withOrderByAttribute(collectionAttribute, true);
    List<Collection> ret = collectionDAO.findAllCollectionsByShepardId(params, userName1);
    assertEquals(ret.size(), 2);
    assertEquals(ret.get(0), collection2u1);
    assertEquals(ret.get(1), collection1u1);
  }

  @Test
  @Transactional
  public void findAll_ByNameByShepardId() {
    var params = new QueryParamHelper().withName(collection1u.getName());
    List<Collection> ret = collectionDAO.findAllCollectionsByShepardId(params, userName);
    assertEquals(ret.size(), 1);
    assertEquals(ret.get(0), collection1u);
  }

  @Test
  @Transactional
  public void findAll_ByNameByNeo4jId() {
    var params = new QueryParamHelper().withName(collection2u1.getName());
    List<Collection> ret = collectionDAO.findAllCollectionsByNeo4jId(params, userName1);
    assertEquals(ret.size(), 1);
    assertEquals(ret.get(0), collection2u1);
  }

  @Test
  @Transactional
  public void findAll_ByNeo4jIdWithPage0() {
    var params = new QueryParamHelper().withPageAndSize(0, 1);
    var collectionAttribute = DataObjectAttributes.name;
    params = params.withOrderByAttribute(collectionAttribute, false);
    List<Collection> ret = collectionDAO.findAllCollectionsByNeo4jId(params, userName1);
    assertEquals(ret.size(), 1);
    assertEquals(ret.get(0), collection1u1);
  }

  @Test
  @Transactional
  public void findAll_ByNeo4jIdWithPage1() {
    var params = new QueryParamHelper().withPageAndSize(1, 1);
    var collectionAttribute = DataObjectAttributes.name;
    params = params.withOrderByAttribute(collectionAttribute, false);
    List<Collection> ret = collectionDAO.findAllCollectionsByNeo4jId(params, userName1);
    assertEquals(ret.size(), 1);
    assertEquals(ret.get(0), collection2u1);
  }

  @Test
  @Transactional
  public void findAll_ByShepardIdWithPage0() {
    var params = new QueryParamHelper().withPageAndSize(0, 1);
    var collectionAttribute = DataObjectAttributes.name;
    params = params.withOrderByAttribute(collectionAttribute, false);
    List<Collection> ret = collectionDAO.findAllCollectionsByShepardId(params, userName1);
    assertEquals(ret.size(), 1);
    assertEquals(ret.get(0), collection1u1);
  }

  @Test
  @Transactional
  public void findAll_ByShepardIdWithPage1() {
    var params = new QueryParamHelper().withPageAndSize(1, 1);
    var collectionAttribute = DataObjectAttributes.name;
    params = params.withOrderByAttribute(collectionAttribute, false);
    List<Collection> ret = collectionDAO.findAllCollectionsByShepardId(params, userName1);
    assertEquals(ret.size(), 1);
    assertEquals(ret.get(0), collection2u1);
  }

  @Test
  @Transactional
  public void deleteByShepardId() {
    QueryParamHelper params = new QueryParamHelper();
    List<Collection> retBeforeDeletion = collectionDAO.findAllCollectionsByNeo4jId(params, userName);
    User user = new User(userName);
    Date date = new Date(1L);
    collectionDAO.deleteCollectionByShepardId(collection1u.getShepardId(), user, date);
    List<Collection> retAfterDeletion = collectionDAO.findAllCollectionsByNeo4jId(params, userName);
    assertEquals(1, retBeforeDeletion.size());
    assertEquals(0, retAfterDeletion.size());
  }
}
