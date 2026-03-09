package de.dlr.shepard.context.collection.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.context.references.dataobject.services.CollectionReferenceService;
import de.dlr.shepard.context.references.dataobject.services.DataObjectReferenceService;
import de.dlr.shepard.context.references.file.services.FileReferenceService;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.services.VersionService;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.data.structureddata.services.StructuredDataService;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class DataObjectServiceQuarkusTest {

  @Inject
  CollectionService collectionService;

  @Inject
  UserService userService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @Inject
  FileContainerService fileContainerService;

  @Inject
  FileReferenceService fileReferenceService;

  @Inject
  VersionDAO versionDAO;

  @Inject
  TimeseriesReferenceService timeseriesReferenceService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  StructuredDataService structuredDataService;

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

  private Collection collection;

  private final String userName = "user_name";
  private final String collectionName = "collection_name";

  private DataObject createDataObject(DataObjectIO dataObjectToCreate) {
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
    User user = new User(userName);
    userService.createOrUpdateUser(user);
    authenticationContext.setPrincipal(new JWTPrincipal(userName, "key"));
    CollectionIO collectionIO = new CollectionIO();
    collectionIO.setName(collectionName);
    collection = collectionService.createCollection(collectionIO);
    collection.setPermissions(null);
  }

  @Test
  @Transactional
  public void testCutDeleted() {
    DataObject parentOfFirstDataObject = createDataObject(
      new DataObjectIOBuilder().setName("parentCentralDataObject").build()
    );

    DataObject survivingPredecessor = createDataObject(new DataObjectIOBuilder().setName("survivor").build());
    DataObject deletedPredecessor = createDataObject(new DataObjectIOBuilder().setName("toBeDeleted").build());
    long[] firstDataObjectPredecessorIds = { survivingPredecessor.getShepardId(), deletedPredecessor.getShepardId() };

    DataObject firstDataObject = createDataObject(
      new DataObjectIOBuilder()
        .setName("firstDataObject")
        .setParentId(parentOfFirstDataObject.getShepardId())
        .setPredecessorIds(firstDataObjectPredecessorIds)
        .build()
    );
    DataObject referencingDataObject = createDataObject(
      new DataObjectIOBuilder().setName("referencingDataObject").build()
    );
    DataObjectReference referenceToFirstDataObject = createDataObjectReference(
      collection.getShepardId(),
      "referenceToFirstDataObject",
      referencingDataObject,
      firstDataObject
    );

    DataObject firstDataObjectReloaded = dataObjectService.getDataObject(
      collection.getShepardId(),
      firstDataObject.getShepardId()
    );
    assertEquals(firstDataObjectReloaded.getIncoming().get(0).getId(), referenceToFirstDataObject.getId());
    assertEquals(referencingDataObject.getReferences().get(0).getId(), referenceToFirstDataObject.getId());

    dataObjectReferenceService.deleteReference(
      collection.getShepardId(),
      referencingDataObject.getShepardId(),
      referenceToFirstDataObject.getShepardId()
    );

    firstDataObjectReloaded = dataObjectService.getDataObject(
      collection.getShepardId(),
      firstDataObject.getShepardId()
    );

    // this is only needed to notify the neo4j session to flush all existing data and start a new session
    // else the following checks have stale data
    dataObjectService.clearSession();

    DataObject referencingDataObjectReloaded = dataObjectService.getDataObject(
      collection.getShepardId(),
      referencingDataObject.getShepardId()
    );
    assertEquals(0, firstDataObjectReloaded.getIncoming().size());
    assertEquals(0, referencingDataObjectReloaded.getReferences().size());

    dataObjectService.deleteDataObject(collection.getShepardId(), deletedPredecessor.getShepardId());
    firstDataObjectReloaded = dataObjectService.getDataObject(firstDataObject.getShepardId());
    assertEquals(1, firstDataObjectReloaded.getPredecessors().size());
    assertEquals(survivingPredecessor.getId(), firstDataObjectReloaded.getPredecessors().get(0).getId());
    dataObjectService.deleteDataObject(collection.getShepardId(), firstDataObject.getShepardId());
    DataObject survivingPredecessorReloaded = dataObjectService.getDataObject(survivingPredecessor.getShepardId());
    assertEquals(0, survivingPredecessorReloaded.getSuccessors().size());
    DataObject parentOfFirsDataObjectReloaded = dataObjectService.getDataObject(parentOfFirstDataObject.getShepardId());
    assertEquals(0, parentOfFirsDataObjectReloaded.getChildren().size());
  }

  @Test
  public void deleteParentOfDataObject_success() {
    // Arrange
    DataObject parent = createDataObject(new DataObjectIOBuilder().build());
    DataObjectIO dataObjectIO = new DataObjectIOBuilder().setParentId(parent.getId()).build();
    DataObject dataObject = createDataObject(dataObjectIO);

    // Act
    dataObjectIO.setParentId(null);
    dataObjectService.updateDataObject(collection.getShepardId(), dataObject.getShepardId(), dataObjectIO);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObject(dataObject.getShepardId());
    assertEquals(null, actualDataObject.getParent());
  }

  @Test
  public void deletePredecessorsOfDataObject_success() {
    // Arrange
    DataObject predecessor = createDataObject(new DataObjectIOBuilder().build());
    DataObjectIO dataObjectIO = new DataObjectIOBuilder().setPredecessorIds(new long[] { predecessor.getId() }).build();
    DataObject dataObject = createDataObject(dataObjectIO);

    // Act
    dataObjectIO.setPredecessorIds(null);
    dataObjectService.updateDataObject(collection.getShepardId(), dataObject.getShepardId(), dataObjectIO);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObject(dataObject.getShepardId());
    assertTrue(actualDataObject.getPredecessors().isEmpty());
  }

  @Test
  public void deleteParentOfDataObjectWithPredecessors_success() {
    // Arrange
    DataObject parent = createDataObject(new DataObjectIOBuilder().build());
    DataObject predecessor = createDataObject(new DataObjectIOBuilder().build());
    DataObjectIO dataObjectIO = new DataObjectIOBuilder()
      .setParentId(parent.getId())
      .setPredecessorIds(new long[] { predecessor.getId() })
      .build();
    DataObject dataObject = createDataObject(dataObjectIO);

    // Act
    dataObjectIO.setParentId(null);
    dataObjectService.updateDataObject(collection.getShepardId(), dataObject.getShepardId(), dataObjectIO);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObject(dataObject.getShepardId());
    assertEquals(null, actualDataObject.getParent());
  }

  @Test
  public void deletePredecessorOfDataObjectWithParent_success() {
    // Arrange
    DataObject parent = createDataObject(new DataObjectIOBuilder().build());
    DataObject predecessor = createDataObject(new DataObjectIOBuilder().build());
    DataObjectIO dataObjectIO = new DataObjectIOBuilder()
      .setParentId(parent.getId())
      .setPredecessorIds(new long[] { predecessor.getId() })
      .build();
    DataObject dataObject = createDataObject(dataObjectIO);

    // Act
    dataObjectIO.setPredecessorIds(null);
    dataObjectService.updateDataObject(collection.getShepardId(), dataObject.getShepardId(), dataObjectIO);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObject(dataObject.getShepardId());
    assertTrue(actualDataObject.getPredecessors().isEmpty());
  }

  @Test
  public void deleteAllPredecessorsAndParentOfDataObjectWithParent_success() {
    // Arrange
    DataObject parent = createDataObject(new DataObjectIOBuilder().build());
    DataObject predecessor = createDataObject(new DataObjectIOBuilder().build());
    DataObjectIO dataObjectIO = new DataObjectIOBuilder()
      .setParentId(parent.getId())
      .setPredecessorIds(new long[] { predecessor.getId() })
      .build();
    DataObject dataObject = createDataObject(dataObjectIO);

    // Act
    dataObjectIO.setPredecessorIds(null);
    dataObjectIO.setParentId(null);
    dataObjectService.updateDataObject(collection.getShepardId(), dataObject.getShepardId(), dataObjectIO);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObject(dataObject.getShepardId());
    assertEquals(null, actualDataObject.getParent());
    assertTrue(actualDataObject.getPredecessors().isEmpty());
  }

  @Test
  public void deleteAttributesOfDataObject_success() {
    // Arrange
    DataObjectIO dataObjectIO = new DataObjectIOBuilder().setAttributes(Map.of("name", "my data object")).build();
    DataObject dataObject = createDataObject(dataObjectIO);

    // Act
    dataObjectIO.setAttributes(Map.of());
    dataObjectService.updateDataObject(collection.getShepardId(), dataObject.getShepardId(), dataObjectIO);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObject(dataObject.getShepardId());
    assertEquals(Map.of(), actualDataObject.getAttributes());
  }

  @Test
  public void createDataObjectWithNonEmptySuccessorList() {
    //Arrange
    long[] successorIds = { 3L };
    DataObjectIO dataObjectIO = new DataObjectIO();
    dataObjectIO.setSuccessorIds(successorIds);

    //Act and Assert
    assertThrows(InvalidBodyException.class, () -> createDataObject(dataObjectIO));
  }

  @Test
  public void createDataObjectWithEmptySuccessorList() {
    //Arrange
    long[] successorIds = {};
    DataObjectIO dataObjectIO = new DataObjectIO();
    dataObjectIO.setSuccessorIds(successorIds);

    //Act
    DataObject dataObject = createDataObject(dataObjectIO);

    //Assert
    assertEquals(0, dataObject.getSuccessors().size());
  }

  @Test
  public void updateWithIncompatibleSuccessorList() {
    //Arrange
    DataObjectIO predecessorIO = new DataObjectIO();
    DataObject predecessor = createDataObject(predecessorIO);
    long[] predecessorIds = { predecessor.getShepardId() };
    DataObjectIO successorIO = new DataObjectIO();
    successorIO.setPredecessorIds(predecessorIds);
    DataObject successor = createDataObject(successorIO);
    DataObjectIO updatedPredecessorIO = new DataObjectIO();
    long[] updatedSuccessorIds = { successor.getShepardId(), successor.getShepardId() + 1 };
    updatedPredecessorIO.setSuccessorIds(updatedSuccessorIds);

    //Act and Assert
    assertThrows(InvalidBodyException.class, () ->
      dataObjectService.updateDataObject(collection.getShepardId(), predecessor.getShepardId(), updatedPredecessorIO)
    );
  }

  @Test
  public void updateWithCompatibleSuccessorList() {
    //Arrange
    DataObjectIO predecessorIO = new DataObjectIO();
    DataObject predecessor = createDataObject(predecessorIO);
    long[] predecessorIds = { predecessor.getShepardId() };
    DataObjectIO successorIO = new DataObjectIO();
    successorIO.setPredecessorIds(predecessorIds);
    DataObject successor = createDataObject(successorIO);
    DataObjectIO updatedPredecessorIO = new DataObjectIO();
    long[] updatedSuccessorIds = { successor.getShepardId(), successor.getShepardId() };
    updatedPredecessorIO.setSuccessorIds(updatedSuccessorIds);

    //Act
    DataObject updatedPredecessor = dataObjectService.updateDataObject(
      collection.getShepardId(),
      predecessor.getShepardId(),
      updatedPredecessorIO
    );

    //Assert
    assertEquals(1, updatedPredecessor.getSuccessors().size());
    assertEquals(successor.getShepardId(), updatedPredecessor.getSuccessors().get(0).getShepardId());
  }
}
