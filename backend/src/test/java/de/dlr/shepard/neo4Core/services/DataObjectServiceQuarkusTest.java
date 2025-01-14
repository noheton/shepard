package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
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

  private String username = "user";
  private User user;

  @BeforeEach
  public void setUp() {
    if (user == null) {
      User user = new User(username);
      userService.createUser(user);
    }
  }

  private Collection createCollection(String name) {
    CollectionIO cIO = new CollectionIO();
    cIO.setName(name);
    return collectionService.createCollection(cIO, username);
  }

  private DataObject createDataObject(String name, Collection collection, Long parentId, long[] predecessorIds) {
    DataObjectIO dIO = new DataObjectIO();
    dIO.setName(name);
    dIO.setParentId(parentId);
    dIO.setPredecessorIds(predecessorIds);
    return dataObjectService.createDataObjectByCollectionShepardId(collection.getShepardId(), dIO, username);
  }

  private DataObjectReference createDataObjectReference(
    String name,
    DataObject referencingDataObject,
    DataObject referencedDataObject
  ) {
    DataObjectReferenceIO dorIO = new DataObjectReferenceIO();
    dorIO.setName(name);
    dorIO.setReferencedDataObjectId(referencedDataObject.getShepardId());
    return dataObjectReferenceService.createReferenceByShepardId(referencingDataObject.getShepardId(), dorIO, username);
  }

  @Test
  @Transactional
  public void testCutDeleted() {
    Collection collection = createCollection("collection");
    DataObject parentOfFirstDataObject = createDataObject("parentCentralDataObject", collection, null, null);
    DataObject survivingPredecessor = createDataObject("survivor", collection, null, null);
    DataObject deletedPredecessor = createDataObject("toBeDeleted", collection, null, null);
    long[] firstDataObjectPredecessorIds = { survivingPredecessor.getShepardId(), deletedPredecessor.getShepardId() };
    DataObject firstDataObject = createDataObject(
      "firstDataObject",
      collection,
      parentOfFirstDataObject.getShepardId(),
      firstDataObjectPredecessorIds
    );
    DataObject referencingDataObject = createDataObject("referencingDataObject", collection, null, null);
    DataObjectReference referenceToFirstDataObject = createDataObjectReference(
      "referenceToFirstDataObject",
      referencingDataObject,
      firstDataObject
    );
    DataObject firstDataObjectReloaded = dataObjectService.getDataObjectByShepardId(firstDataObject.getShepardId());
    assertEquals(firstDataObjectReloaded.getIncoming().get(0).getId(), referenceToFirstDataObject.getId());
    assertEquals(referencingDataObject.getReferences().get(0).getId(), referenceToFirstDataObject.getId());
    dataObjectReferenceService.deleteReferenceByShepardId(referenceToFirstDataObject.getShepardId(), username);
    firstDataObjectReloaded = dataObjectService.getDataObjectByShepardId(firstDataObject.getShepardId());
    DataObject referencingDataObjectReloaded = dataObjectService.getDataObjectByShepardId(
      referencingDataObject.getShepardId()
    );
    assertEquals(0, firstDataObjectReloaded.getIncoming().size());
    assertEquals(0, referencingDataObjectReloaded.getReferences().size());
    dataObjectService.deleteDataObjectByShepardId(deletedPredecessor.getShepardId(), username);
    firstDataObjectReloaded = dataObjectService.getDataObjectByShepardId(firstDataObject.getShepardId());
    assertEquals(1, firstDataObjectReloaded.getPredecessors().size());
    assertEquals(survivingPredecessor.getId(), firstDataObjectReloaded.getPredecessors().get(0).getId());
    dataObjectService.deleteDataObjectByShepardId(firstDataObject.getShepardId(), username);
    DataObject survivingPredecessorReloaded = dataObjectService.getDataObjectByShepardId(
      survivingPredecessor.getShepardId()
    );
    assertEquals(0, survivingPredecessorReloaded.getSuccessors().size());
    DataObject parentOfFirsDataObjectReloaded = dataObjectService.getDataObjectByShepardId(
      parentOfFirstDataObject.getShepardId()
    );
    assertEquals(0, parentOfFirsDataObjectReloaded.getChildren().size());
  }
}
