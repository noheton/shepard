package de.dlr.shepard.context.collection.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class DataObjectDAOQuarkusTest {

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  CollectionService collectionService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  UserService userService;

  @Inject
  AuthenticationContext authenticationContext;

  private final String userName = "user_" + System.currentTimeMillis();

  Collection collection;
  DataObject dataObject;
  User user;

  @BeforeEach
  public void setup() {
    if (user == null) {
      user = new User(userName);
      userService.createOrUpdateUser(user);
    }
    authenticationContext.setPrincipal(new JWTPrincipal(userName, "key"));
    if (collection == null) {
      CollectionIO collectionIO = new CollectionIO();
      collectionIO.setName("collection");
      collection = collectionService.createCollection(collectionIO);
    }
    if (dataObject == null) {
      DataObjectIO dataObjectIO = new DataObjectIO();
      dataObjectIO.setName("dataObject");
      HashMap<String, String> attributes = new HashMap<String, String>();
      attributes.put("key", "value");
      attributes.put("key1", "value1");
      dataObjectIO.setAttributes(attributes);
      dataObject = dataObjectService.createDataObject(collection.getShepardId(), dataObjectIO);
    }
  }

  @Test
  @Transactional
  @Order(1)
  public void attributesArePresent() {
    //Arrange and Act
    DataObject dataObjectBeforeDeletingAttributes = dataObjectService.getDataObject(dataObject.getShepardId());
    //Assert
    assertEquals("value", dataObjectBeforeDeletingAttributes.getAttributes().get("key"));
    assertEquals("value1", dataObjectBeforeDeletingAttributes.getAttributes().get("key1"));
  }

  @Test
  @Transactional
  @Order(2)
  public void attributesAreDeleted() {
    //Arrange and Act
    dataObjectDAO.deleteAllAttributes(dataObject);
    //Assert
    DataObject dataObjectAfterDeletingAttributes = dataObjectService.getDataObject(dataObject.getShepardId());
    assertEquals(0, dataObjectAfterDeletingAttributes.getAttributes().size());
  }
}
