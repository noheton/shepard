package de.dlr.shepard.context.references.dataobject.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.context.references.dataobject.io.CollectionReferenceIO;
import de.dlr.shepard.context.references.dataobject.services.CollectionReferenceService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CollectionReferenceDAOQuarkusTest {

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  CollectionReferenceDAO collectionReferenceDAO;

  @Inject
  CollectionReferenceService collectionReferenceService;

  @Inject
  CollectionService collectionService;

  @Inject
  UserService userService;

  @Inject
  AuthenticationContext authenticationContext;

  private final String userName = "user_" + System.currentTimeMillis();
  private final String userName1 = "user1_" + System.currentTimeMillis();

  private Collection createCollection(CollectionIO collectionToCreate) {
    return collectionService.createCollection(collectionToCreate);
  }

  private DataObject createDataObject(long collectionShepardId, DataObjectIO dataObjectToCreate) {
    return dataObjectService.createDataObject(collectionShepardId, dataObjectToCreate);
  }

  private CollectionReference createCollectionReference(
    long collectionId,
    String name,
    DataObject referencingDataObject,
    Collection referencedCollection
  ) {
    CollectionReferenceIO crIO = new CollectionReferenceIO();
    crIO.setName(name);
    crIO.setReferencedCollectionId(referencedCollection.getShepardId());
    return collectionReferenceService.createReference(collectionId, referencingDataObject.getShepardId(), crIO);
  }

  @Test
  @Transactional
  public void test() {
    User user = new User(userName);
    userService.createOrUpdateUser(user);
    authenticationContext.setPrincipal(new JWTPrincipal(userName, "key"));
    CollectionIO c1IO = new CollectionIO();
    c1IO.setName("c1");
    CollectionIO c2IO = new CollectionIO();
    c1IO.setName("c2");
    CollectionIO c3IO = new CollectionIO();
    c1IO.setName("c3");
    Collection c1 = createCollection(c1IO);
    Collection c2 = createCollection(c2IO);
    Collection c3 = createCollection(c3IO);
    DataObjectIO c1d1IO = new DataObjectIO();
    c1d1IO.setName("c1d1");
    DataObject c1d1 = createDataObject(c1.getShepardId(), c1d1IO);
    CollectionReference c1d1Toc2 = createCollectionReference(c1.getShepardId(), "c1d1Toc2", c1d1, c2);
    CollectionReference c1d1Toc3 = createCollectionReference(c1.getShepardId(), "c1d1Toc2", c1d1, c3);
    List<CollectionReference> referencesByShepardId = collectionReferenceDAO.findByDataObjectShepardId(
      c1d1.getShepardId()
    );
    assertEquals(2, referencesByShepardId.size());
    assertEquals(referencesByShepardId.contains(c1d1Toc3), true);
    assertEquals(referencesByShepardId.contains(c1d1Toc2), true);
    List<CollectionReference> referencesByNeo4jId = collectionReferenceDAO.findByDataObjectNeo4jId(c1d1.getShepardId());
    assertEquals(2, referencesByNeo4jId.size());
    assertEquals(referencesByNeo4jId.contains(c1d1Toc3), true);
    assertEquals(referencesByNeo4jId.contains(c1d1Toc2), true);
  }
}
