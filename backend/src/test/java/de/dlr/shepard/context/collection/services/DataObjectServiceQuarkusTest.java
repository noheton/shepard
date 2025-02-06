package de.dlr.shepard.context.collection.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
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
    return dataObjectService.createDataObjectByCollectionShepardId(collection.getId(), dataObjectToCreate, userName);
  }

  private DataObjectReference createDataObjectReference(
    String name,
    DataObject referencingDataObject,
    DataObject referencedDataObject
  ) {
    DataObjectReferenceIO dorIO = new DataObjectReferenceIO();
    dorIO.setName(name);
    dorIO.setReferencedDataObjectId(referencedDataObject.getShepardId());
    return dataObjectReferenceService.createReferenceByShepardId(referencingDataObject.getShepardId(), dorIO, userName);
  }

  @BeforeEach
  public void setup() {
    User user = new User(userName);
    userService.createUser(user);
    CollectionIO collectionIO = new CollectionIO();
    collectionIO.setName(collectionName);
    collection = collectionService.createCollection(collectionIO, userName);
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
      "referenceToFirstDataObject",
      referencingDataObject,
      firstDataObject
    );
    DataObject firstDataObjectReloaded = dataObjectService.getDataObjectByShepardId(firstDataObject.getShepardId());
    assertEquals(firstDataObjectReloaded.getIncoming().get(0).getId(), referenceToFirstDataObject.getId());
    assertEquals(referencingDataObject.getReferences().get(0).getId(), referenceToFirstDataObject.getId());
    dataObjectReferenceService.deleteReferenceByShepardId(referenceToFirstDataObject.getShepardId(), userName);
    firstDataObjectReloaded = dataObjectService.getDataObjectByShepardId(firstDataObject.getShepardId());
    DataObject referencingDataObjectReloaded = dataObjectService.getDataObjectByShepardId(
      referencingDataObject.getShepardId()
    );
    assertEquals(0, firstDataObjectReloaded.getIncoming().size());
    assertEquals(0, referencingDataObjectReloaded.getReferences().size());
    dataObjectService.deleteDataObjectByShepardId(deletedPredecessor.getShepardId(), userName);
    firstDataObjectReloaded = dataObjectService.getDataObjectByShepardId(firstDataObject.getShepardId());
    assertEquals(1, firstDataObjectReloaded.getPredecessors().size());
    assertEquals(survivingPredecessor.getId(), firstDataObjectReloaded.getPredecessors().get(0).getId());
    dataObjectService.deleteDataObjectByShepardId(firstDataObject.getShepardId(), userName);
    DataObject survivingPredecessorReloaded = dataObjectService.getDataObjectByShepardId(
      survivingPredecessor.getShepardId()
    );
    assertEquals(0, survivingPredecessorReloaded.getSuccessors().size());
    DataObject parentOfFirsDataObjectReloaded = dataObjectService.getDataObjectByShepardId(
      parentOfFirstDataObject.getShepardId()
    );
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
    dataObjectService.updateDataObjectByShepardId(dataObject.getShepardId(), dataObjectIO, userName);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObjectByShepardId(dataObject.getShepardId());
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
    dataObjectService.updateDataObjectByShepardId(dataObject.getShepardId(), dataObjectIO, userName);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObjectByShepardId(dataObject.getShepardId());
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
    dataObjectService.updateDataObjectByShepardId(dataObject.getShepardId(), dataObjectIO, userName);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObjectByShepardId(dataObject.getShepardId());
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
    dataObjectService.updateDataObjectByShepardId(dataObject.getShepardId(), dataObjectIO, userName);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObjectByShepardId(dataObject.getShepardId());
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
    dataObjectService.updateDataObjectByShepardId(dataObject.getShepardId(), dataObjectIO, userName);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObjectByShepardId(dataObject.getShepardId());
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
    dataObjectService.updateDataObjectByShepardId(dataObject.getShepardId(), dataObjectIO, userName);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObjectByShepardId(dataObject.getShepardId());
    assertEquals(Map.of(), actualDataObject.getAttributes());
  }
}
