package de.dlr.shepard.context.references.dataobject.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.version.services.VersionService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class DataObjectReferenceServiceQuarkusTest {

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  CollectionService collectionService;

  @Inject
  UserService userService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  StructuredDataReferenceService structuredDataReferenceService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  DataObjectReferenceService dataObjectReferenceService;

  @Inject
  VersionService versionService;

  @Inject
  CollectionReferenceService collectionReferenceService;

  private final String userName = "user_" + System.currentTimeMillis();
  private final String userName1 = "user1_" + System.currentTimeMillis();
  Collection c1;
  Collection c2;
  DataObject c1do1;
  DataObject c1do2;
  DataObject c2do1;
  DataObject c2do2;
  DataObjectReference c1do1Toc1do2;
  DataObjectReference c1do2Toc2do1;

  private Collection createCollection(CollectionIO collectionToCreate) {
    return collectionService.createCollection(collectionToCreate);
  }

  private DataObject createDataObject(Collection collection, DataObjectIO dataObjectToCreate) {
    return dataObjectService.createDataObject(collection.getId(), dataObjectToCreate);
  }

  private DataObjectReference createDataObjectReference(
    long collectionId,
    String name,
    DataObject referencingDataObject,
    DataObject referencedDataObject
  ) {
    DataObjectReferenceIO dorIO = new DataObjectReferenceIO();
    dorIO.setName(name);
    dorIO.setReferencedDataObjectId(referencedDataObject.getShepardId());
    return dataObjectReferenceService.createReference(collectionId, referencingDataObject.getShepardId(), dorIO);
  }

  @BeforeEach
  public void setup() {
    User user1 = new User(userName1);
    userService.createOrUpdateUser(user1);
    authenticationContext.setPrincipal(new JWTPrincipal(userName1, "key"));
    CollectionIO c2IO = new CollectionIO();
    c2IO.setName("c2");
    Permissions c2Permissions = new Permissions();
    c2Permissions.setPermissionType(PermissionType.Public);
    c2 = collectionService.createCollection(c2IO);
    c2.setPermissions(c2Permissions);
    collectionDAO.createOrUpdate(c2);
    DataObjectIO c2do1IO = new DataObjectIO();
    c2do1IO.setName("c2do1");
    c2do1 = createDataObject(c2, c2do1IO);
    DataObjectIO c2do2IO = new DataObjectIO();
    c2do2IO.setName("c2do2");
    c2do2 = createDataObject(c2, c2do2IO);

    User user = new User(userName);
    userService.createOrUpdateUser(user);
    authenticationContext.setPrincipal(new JWTPrincipal(userName, "key"));
    CollectionIO c1IO = new CollectionIO();
    c1IO.setName("c1");
    c1 = collectionService.createCollection(c1IO);
    DataObjectIO c1do1IO = new DataObjectIO();
    c1do1IO.setName("c1do1");
    c1do1 = createDataObject(c1, c1do1IO);
    DataObjectIO c1do2IO = new DataObjectIO();
    c1do2IO.setName("c1do2");
    c1do2 = createDataObject(c1, c1do2IO);
    c1do1Toc1do2 = createDataObjectReference(c1.getId(), userName, c1do1, c1do2);
    c1do2Toc2do1 = createDataObjectReference(c1.getId(), userName, c1do2, c2do1);
  }

  @Test
  @Transactional
  public void findByShepardId() {
    DataObjectReference c1do1Toc1do2Expected = dataObjectReferenceService.getReference(
      c1.getShepardId(),
      c1do1.getShepardId(),
      c1do1Toc1do2.getShepardId(),
      null
    );
    assertEquals(c1do1Toc1do2, c1do1Toc1do2Expected);
  }

  @Test
  @Transactional
  public void findByShepardIdInOtherCollection() {
    DataObjectReference c1do2Toc2do1Expected = dataObjectReferenceService.getReference(
      c1.getShepardId(),
      c1do2.getShepardId(),
      c1do2Toc2do1.getShepardId(),
      null
    );
    assertEquals(c1do2Toc2do1, c1do2Toc2do1Expected);
  }

  @Test
  @Transactional
  public void searchNonexistingDataObjectReference() {
    var ex = assertThrows(InvalidPathException.class, () ->
      dataObjectReferenceService.getReference(c1.getId(), c1do1.getId(), c1.getId(), null)
    );
    assertEquals(ex.getMessage(), "ID ERROR - Data Object Reference with id " + c1.getId() + " is null or deleted");
  }

  @Test
  @Transactional
  public void searchDataObjectReferenceOfWrongDataObject() {
    var ex = assertThrows(InvalidPathException.class, () ->
      dataObjectReferenceService.getReference(c1.getId(), c1do2.getId(), c1do1Toc1do2.getId(), null)
    );
    assertEquals(ex.getMessage(), "ID ERROR - There is no association between dataObject and reference");
  }

  @Test
  @Transactional
  public void createReferenceNullReferenced() {
    DataObjectReferenceIO input = new DataObjectReferenceIO() {
      {
        setName("MyName");
        setReferencedDataObjectId(c1.getShepardId());
        setRelationship("MyRelationship");
      }
    };
    assertThrows(InvalidBodyException.class, () ->
      dataObjectReferenceService.createReference(c1.getShepardId(), c1do1.getShepardId(), input)
    );
  }

  @Test
  @Transactional
  public void getAllDataObjectReferencesByShepardId() {
    List<DataObjectReference> ret = dataObjectReferenceService.getAllReferencesByDataObjectId(
      c1.getShepardId(),
      c1do1.getShepardId(),
      null
    );
    assertEquals(1, ret.size());
    assertEquals(c1do1Toc1do2, ret.get(0));
  }

  @Test
  @Transactional
  public void createReferenceToNotPermittedDataObject() {
    authenticationContext.setPrincipal(new JWTPrincipal(userName1, "key"));
    DataObjectReferenceIO input = new DataObjectReferenceIO() {
      {
        setName("MyName");
        setReferencedDataObjectId(c1do1.getShepardId());
        setRelationship("MyRelationship");
      }
    };
    var ex = assertThrows(InvalidBodyException.class, () ->
      dataObjectReferenceService.createReference(c2.getShepardId(), c2do1.getShepardId(), input)
    );
    assertEquals(
      "You do not have permissions to access the referenced DataObject with id " + c1do1.getShepardId() + ".",
      ex.getMessage()
    );
  }

  @Test
  @Transactional
  public void createReferenceToDeletedDataObject() {
    authenticationContext.setPrincipal(new JWTPrincipal(userName1, "key"));
    dataObjectService.deleteDataObject(c2.getShepardId(), c2do2.getShepardId());
    DataObjectReferenceIO input = new DataObjectReferenceIO() {
      {
        setName("MyName");
        setReferencedDataObjectId(c1.getShepardId());
        setRelationship("MyRelationship");
      }
    };
    authenticationContext.setPrincipal(new JWTPrincipal(userName, "key"));
    assertThrows(InvalidBodyException.class, () ->
      dataObjectReferenceService.createReference(c1.getShepardId(), c1do1.getShepardId(), input)
    );
  }

  @Test
  @Transactional
  public void searchDeletedDataObjectReference() {
    dataObjectReferenceService.deleteReference(c1.getShepardId(), c1do1.getShepardId(), c1do1Toc1do2.getShepardId());
    var ex = assertThrows(InvalidPathException.class, () ->
      dataObjectReferenceService.getReference(c1.getId(), c1do1.getId(), c1do1Toc1do2.getId(), null)
    );
    assertEquals(
      ex.getMessage(),
      "ID ERROR - Data Object Reference with id " + c1do1Toc1do2.getId() + " is null or deleted"
    );
  }
}
