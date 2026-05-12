package de.dlr.shepard.context.references.file.daos;

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
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.references.file.services.FileBundleReferenceService;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.io.FileContainerIO;
import de.dlr.shepard.data.file.services.FileContainerService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class FileBundleReferenceDAOQuarkusTest {

  @Inject
  DataObjectService dataObjectService;

  @Inject
  CollectionService collectionService;

  @Inject
  FileContainerService fileContainerService;

  @Inject
  FileBundleReferenceService fileReferenceService;

  @Inject
  FileBundleReferenceDAO fileReferenceDAO;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  UserService userService;

  private DataObject dataObject1;
  private DataObject dataObject2;
  private FileContainer fileContainer1;
  private Collection collection1;
  private final String userName = "user_" + System.currentTimeMillis();
  private FileBundleReference fileReference11;
  private FileBundleReference fileReference12;

  @BeforeEach
  public void setup() {
    User user = new User(userName);
    userService.createOrUpdateUser(user);
    authenticationContext.setPrincipal(new JWTPrincipal(userName, "key"));
    CollectionIO collectionIO1 = new CollectionIO();
    collectionIO1.setName("collection1");
    collection1 = collectionService.createCollection(collectionIO1);
    DataObjectIO dataObjectIO1 = new DataObjectIO();
    dataObjectIO1.setName("dataObject1");
    dataObject1 = dataObjectService.createDataObject(collection1.getShepardId(), dataObjectIO1);
    DataObjectIO dataObjectIO2 = new DataObjectIO();
    dataObjectIO2.setName("dataObject2");
    dataObject2 = dataObjectService.createDataObject(collection1.getShepardId(), dataObjectIO2);
    FileContainerIO fileContainerIO1 = new FileContainerIO();
    fileContainerIO1.setName("fileContainer1");
    fileContainer1 = fileContainerService.createContainer(fileContainerIO1);
    FileReferenceIO fileReferenceIO11 = new FileReferenceIO();
    fileReferenceIO11.setName("fileReference11");
    fileReferenceIO11.setDataObjectId(dataObject1.getShepardId());
    fileReferenceIO11.setFileContainerId(fileContainer1.getId());
    String[] fileOIDs = {};
    fileReferenceIO11.setFileOids(fileOIDs);
    fileReference11 = fileReferenceService.createReference(
      collection1.getShepardId(),
      dataObject1.getShepardId(),
      fileReferenceIO11
    );
    FileReferenceIO fileReferenceIO12 = new FileReferenceIO();
    fileReferenceIO12.setName("fileReference12");
    fileReferenceIO12.setDataObjectId(dataObject1.getShepardId());
    fileReferenceIO12.setFileContainerId(fileContainer1.getId());
    fileReferenceIO12.setFileOids(fileOIDs);
    fileReference12 = fileReferenceService.createReference(
      collection1.getShepardId(),
      dataObject1.getShepardId(),
      fileReferenceIO12
    );
  }

  @Test
  @Order(1)
  @Transactional
  public void findByDataObjectNeo4jIdTest() {
    List<FileBundleReference> actualFileReferences = fileReferenceDAO.findByDataObjectNeo4jId(dataObject1.getId());
    assertEquals(2, actualFileReferences.size());
    assertEquals(true, actualFileReferences.contains(fileReference11));
    assertEquals(true, actualFileReferences.contains(fileReference12));
  }

  @Test
  @Order(2)
  @Transactional
  public void findNothingByDataObjectNeo4jIdTest() {
    List<FileBundleReference> actualFileReferences = fileReferenceDAO.findByDataObjectNeo4jId(dataObject2.getId());
    assertEquals(0, actualFileReferences.size());
  }

  @Test
  @Order(3)
  @Transactional
  public void findByDataObjectShepardIdTest() {
    List<FileBundleReference> actualFileReferences = fileReferenceDAO.findByDataObjectNeo4jId(dataObject1.getShepardId());
    assertEquals(2, actualFileReferences.size());
    assertEquals(true, actualFileReferences.contains(fileReference11));
    assertEquals(true, actualFileReferences.contains(fileReference12));
  }

  @Test
  @Order(4)
  @Transactional
  public void findNothingByDataObjectShepardIdTest() {
    List<FileBundleReference> actualFileReferences = fileReferenceDAO.findByDataObjectNeo4jId(dataObject2.getShepardId());
    assertEquals(0, actualFileReferences.size());
  }
}
