package de.dlr.shepard.context.collection.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
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
  public void changeAttributesOfDataObject_success() {
    // Arrange
    DataObjectIO dataObjectIO = new DataObjectIOBuilder().setAttributes(Map.of("name", "my data object")).build();
    DataObject dataObject = createDataObject(dataObjectIO);

    // Act
    dataObjectIO.setAttributes(Map.of("newname", "my new data object"));
    dataObjectService.updateDataObject(collection.getShepardId(), dataObject.getShepardId(), dataObjectIO);

    // Assert
    DataObject actualDataObject = dataObjectService.getDataObject(dataObject.getShepardId());
    assertEquals(1, actualDataObject.getAttributes().size());
    assertEquals("my new data object", actualDataObject.getAttributes().get("newname"));
  }

  @Test
  public void testCreation() {
    DataObjectIO dataObjectIO1 = new DataObjectIOBuilder().setName("do1").setDescription("do1Description").build();
    DataObject dataObject1 = createDataObject(dataObjectIO1);

    long[] dataObject2SuccessorIds = { dataObject1.getShepardId() };
    DataObjectIO dataObjectIO2 = new DataObjectIOBuilder()
      .setName("do2")
      .setDescription("do2Description")
      .setSuccessorIds(dataObject2SuccessorIds)
      .build();
    DataObject dataObject2 = createDataObject(dataObjectIO2);

    long[] dataObject3PredecessorIds = { dataObject1.getShepardId(), dataObject2.getShepardId() };
    DataObjectIO dataObjectIO3 = new DataObjectIOBuilder()
      .setName("do3")
      .setDescription("do3Description")
      .setPredecessorIds(dataObject3PredecessorIds)
      .setAttributes(Map.of("name", "my data object", "another name", "another value"))
      .build();
    DataObject dataObject3 = createDataObject(dataObjectIO3);

    dataObject1 = dataObjectService.getDataObject(dataObject1.getShepardId());
    dataObject2 = dataObjectService.getDataObject(dataObject2.getShepardId());
    dataObject3 = dataObjectService.getDataObject(dataObject3.getShepardId());

    assertEquals(1, dataObject1.getPredecessors().size());
    assertEquals(1, dataObject1.getSuccessors().size());
    assertEquals(dataObject3.getShepardId(), dataObject1.getSuccessors().get(0).getShepardId());
    assertEquals(dataObject2.getShepardId(), dataObject1.getPredecessors().get(0).getShepardId());
    assertEquals(0, dataObject2.getPredecessors().size());
    assertEquals(2, dataObject2.getSuccessors().size());
    assertEquals(
      true,
      dataObject2.getSuccessors().get(0).getShepardId().equals(dataObject1.getShepardId()) ||
      dataObject2.getSuccessors().get(1).getShepardId().equals(dataObject1.getShepardId())
    );
    assertEquals(
      true,
      dataObject2.getSuccessors().get(0).getShepardId().equals(dataObject3.getShepardId()) ||
      dataObject2.getSuccessors().get(1).getShepardId().equals(dataObject3.getShepardId())
    );
    assertEquals(0, dataObject3.getSuccessors().size());
    assertEquals(2, dataObject3.getPredecessors().size());
    assertEquals(
      true,
      dataObject3.getPredecessors().get(0).getShepardId().equals(dataObject1.getShepardId()) ||
      dataObject3.getPredecessors().get(1).getShepardId().equals(dataObject1.getShepardId())
    );
    assertEquals(
      true,
      dataObject3.getPredecessors().get(0).getShepardId().equals(dataObject2.getShepardId()) ||
      dataObject3.getPredecessors().get(1).getShepardId().equals(dataObject2.getShepardId())
    );
    assertEquals(dataObjectIO1.getName(), dataObject1.getName());
    assertEquals(dataObjectIO1.getDescription(), dataObject1.getDescription());
    assertEquals(dataObjectIO2.getName(), dataObject2.getName());
    assertEquals(dataObjectIO2.getDescription(), dataObject2.getDescription());
    assertEquals(dataObjectIO3.getName(), dataObject3.getName());
    assertEquals(dataObjectIO3.getDescription(), dataObject3.getDescription());
  }

  @Test
  public void testUpdateNeighborhood() {
    DataObjectIO dataObjectIO1 = new DataObjectIOBuilder().setName("do1").setDescription("do1Description").build();
    DataObject dataObject1 = createDataObject(dataObjectIO1);

    long[] dataObject2SuccessorIds = { dataObject1.getShepardId() };
    DataObjectIO dataObjectIO2 = new DataObjectIOBuilder()
      .setName("do2")
      .setDescription("do2Description")
      .setSuccessorIds(dataObject2SuccessorIds)
      .build();
    DataObject dataObject2 = createDataObject(dataObjectIO2);

    long[] dataObject3SuccessorIds = { dataObject1.getShepardId(), dataObject2.getShepardId() };
    DataObjectIO dataObjectIO3 = new DataObjectIOBuilder()
      .setName("do3")
      .setDescription("do3Description")
      .setSuccessorIds(dataObject3SuccessorIds)
      .setAttributes(Map.of("name", "my data object", "another name", "another value"))
      .build();
    DataObject dataObject3 = createDataObject(dataObjectIO3);

    long[] dataObject2NewPredecessorIds = { dataObject1.getShepardId() };
    long[] dataObject2NewSuccessorIds = { dataObject1.getShepardId(), dataObject3.getShepardId() };
    dataObjectIO2.setPredecessorIds(dataObject2NewPredecessorIds);
    dataObjectIO2.setSuccessorIds(dataObject2NewSuccessorIds);
    dataObjectService.updateDataObject(collection.getShepardId(), dataObject2.getShepardId(), dataObjectIO2);

    dataObject2 = dataObjectService.getDataObject(dataObject2.getShepardId());
    assertEquals(2, dataObject2.getSuccessors().size());
    assertEquals(1, dataObject2.getPredecessors().size());
    assertEquals(dataObject1.getShepardId(), dataObject2.getPredecessors().get(0).getShepardId());
    assertEquals(
      true,
      dataObject2.getSuccessors().get(0).getShepardId().equals(dataObject3.getShepardId()) ||
      dataObject2.getSuccessors().get(1).getShepardId().equals(dataObject3.getShepardId())
    );
    assertEquals(
      true,
      dataObject2.getSuccessors().get(0).getShepardId().equals(dataObject1.getShepardId()) ||
      dataObject2.getSuccessors().get(1).getShepardId().equals(dataObject1.getShepardId())
    );
  }
}
