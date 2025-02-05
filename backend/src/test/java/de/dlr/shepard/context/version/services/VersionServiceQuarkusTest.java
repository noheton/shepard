package de.dlr.shepard.context.version.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.references.dataobject.io.CollectionReferenceIO;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.context.references.dataobject.services.CollectionReferenceService;
import de.dlr.shepard.context.references.dataobject.services.DataObjectReferenceService;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.references.file.services.FileReferenceService;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.version.daos.VersionDAO;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.context.version.io.VersionIO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.io.FileContainerIO;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.data.structureddata.services.StructuredDataService;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class VersionServiceQuarkusTest {

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

  private boolean hasExactlyOneVersion(long versionableEntityId) {
    String query =
      "MATCH(e:VersionableEntity)-[:has_version]->(v:Version) WHERE id(e) = " + versionableEntityId + " RETURN v";
    var versions = versionDAO.findByQuery(query);
    var versionIterator = versions.iterator();
    if (versionIterator.next() == null) {
      return false;
    }
    if (versionIterator.hasNext()) {
      return false;
    }
    return true;
  }

  private int getNumberOfDirectPredecessorVersions(Version version) {
    String query = "MATCH(v:Version)-[:has_predecessor]->(vp:Version) WHERE v.uid='" + version.getUid() + "' RETURN vp";
    var versions = versionDAO.findByQuery(query);
    var versionIterator = versions.iterator();
    int ret = 0;
    while (versionIterator.hasNext()) {
      ret++;
      versionIterator.next();
    }
    return ret;
  }

  private int getNumberOfDirectSuccessorVersions(Version version) {
    String query = "MATCH(v:Version)<-[:has_predecessor]-(vp:Version) WHERE v.uid='" + version.getUid() + "' RETURN vp";
    var versions = versionDAO.findByQuery(query);
    var versionIterator = versions.iterator();
    int ret = 0;
    while (versionIterator.hasNext()) {
      ret++;
      versionIterator.next();
    }
    return ret;
  }

  private Collection createCollection(String name) {
    CollectionIO cIO = new CollectionIO();
    cIO.setName(name);
    return collectionService.createCollection(cIO, username);
  }

  private StructuredDataReference createStructuredDataReference(String name, DataObject referencingDataObject) {
    StructuredDataContainerIO containerIO = new StructuredDataContainerIO();
    containerIO.setName(name + "Container");
    StructuredDataContainer container = structuredDataContainerService.createContainer(containerIO, username);
    StructuredDataReferenceIO referenceIO = new StructuredDataReferenceIO();
    referenceIO.setName(name);
    referenceIO.setStructuredDataContainerId(container.getId());
    String[] OIDs = {};
    referenceIO.setStructuredDataOids(OIDs);
    return structuredDataReferenceService.createReferenceByShepardId(
      referencingDataObject.getShepardId(),
      referenceIO,
      username
    );
  }

  private TimeseriesReference createTimeseriesReference(String name, DataObject referencingDataObject) {
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(name + "Container");
    TimeseriesContainer container = timeseriesContainerService.createContainer(containerIO.getName(), username);
    TimeseriesReferenceIO referenceIO = new TimeseriesReferenceIO();
    referenceIO.setName(name);
    referenceIO.setTimeseriesContainerId(container.getId());
    List<ReferencedTimeseriesNodeEntity> timeseries = new ArrayList<ReferencedTimeseriesNodeEntity>();
    referenceIO.setReferencedTimeseriesList(timeseries);
    return timeseriesReferenceService.createReferenceByShepardId(
      referencingDataObject.getShepardId(),
      referenceIO,
      username
    );
  }

  private FileReference createFileReference(String name, DataObject referencingDataObject) {
    FileContainerIO containerIO = new FileContainerIO();
    containerIO.setName(name + "Container");
    FileContainer container = fileContainerService.createContainer(containerIO, username);
    FileReferenceIO referenceIO = new FileReferenceIO();
    referenceIO.setName(name);
    referenceIO.setFileContainerId(container.getId());
    String[] fileOIDs = {};
    referenceIO.setFileOids(fileOIDs);
    return fileReferenceService.createReferenceByShepardId(referencingDataObject.getShepardId(), referenceIO, username);
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

  private CollectionReference createCollectionReference(
    String name,
    DataObject referencingDataObject,
    Collection referencedCollection
  ) {
    CollectionReferenceIO crIO = new CollectionReferenceIO();
    crIO.setName(name);
    crIO.setReferencedCollectionId(referencedCollection.getShepardId());
    return collectionReferenceService.createReferenceByShepardId(referencingDataObject.getShepardId(), crIO, username);
  }

  private Version createVersion(String name, Collection collection) {
    VersionIO versionIO = new VersionIO();
    versionIO.setName(name);
    return versionService.createVersion(collection.getShepardId(), versionIO, username);
  }

  @Test
  @Transactional
  public void createVersion_collectionWithChildrenAndSuccessors_copiedSuccessfully() {
    //setup
    Collection collection1 = createCollection("collection1");
    DataObject dataObject1 = createDataObject("DataObject1", collection1, null, null);
    DataObject dataObject2 = createDataObject("DataObject2", collection1, dataObject1.getShepardId(), null);
    long[] predecessorIds = { dataObject1.getShepardId(), dataObject2.getShepardId() };
    DataObject dataObject3 = createDataObject("DataObject3", collection1, null, predecessorIds);
    Version version1 = createVersion("version1", collection1);
    Version HEADVersion = versionService.getVersion(collection1.getVersion().getUid());
    Collection collection1Version1 = collectionService.getCollectionByShepardId(
      collection1.getShepardId(),
      version1.getUid()
    );
    DataObject dataObject1Version1 = dataObjectService.getDataObjectByShepardId(
      dataObject1.getShepardId(),
      version1.getUid()
    );
    DataObject dataObject2Version1 = dataObjectService.getDataObjectByShepardId(
      dataObject2.getShepardId(),
      version1.getUid()
    );
    DataObject dataObject3Version1 = dataObjectService.getDataObjectByShepardId(
      dataObject3.getShepardId(),
      version1.getUid()
    );

    //unique versions
    assertTrue(hasExactlyOneVersion(collection1.getId()));
    assertTrue(hasExactlyOneVersion(collection1Version1.getId()));
    assertTrue(hasExactlyOneVersion(dataObject1.getId()));
    assertTrue(hasExactlyOneVersion(dataObject2.getId()));
    assertTrue(hasExactlyOneVersion(dataObject3.getId()));
    assertTrue(hasExactlyOneVersion(dataObject1Version1.getId()));
    assertTrue(hasExactlyOneVersion(dataObject2Version1.getId()));
    assertTrue(hasExactlyOneVersion(dataObject3Version1.getId()));

    //structure of versions
    assertEquals(1, getNumberOfDirectPredecessorVersions(HEADVersion));
    assertEquals(0, getNumberOfDirectSuccessorVersions(HEADVersion));
    assertEquals(1, getNumberOfDirectSuccessorVersions(version1));
    assertEquals(0, getNumberOfDirectPredecessorVersions(version1));
    assertEquals(version1.getUid(), HEADVersion.getPredecessor().getUid());
    assertEquals(null, version1.getPredecessor());

    //checking the version of the new DataObjects
    assertTrue(
      dataObject1Version1.getVersion().getUid().equals(version1.getUid()) &&
      dataObject2Version1.getVersion().getUid().equals(version1.getUid()) &&
      dataObject3Version1.getVersion().getUid().equals(version1.getUid())
    );

    //inspecting DataObject3Version1
    assertEquals(2, dataObject3Version1.getPredecessors().size());
    ArrayList<DataObject> expectedPredecessorsVersion1List = new ArrayList<DataObject>();
    expectedPredecessorsVersion1List.add(dataObject1Version1);
    expectedPredecessorsVersion1List.add(dataObject2Version1);
    assertEquals(
      new HashSet<DataObject>(expectedPredecessorsVersion1List),
      new HashSet<DataObject>(dataObject3Version1.getPredecessors())
    );
    assertEquals(0, dataObject3Version1.getSuccessors().size());
    assertEquals(0, dataObject3Version1.getChildren().size());
    assertEquals(null, dataObject3Version1.getParent());

    //inspecting DataObject2Version1
    assertEquals(dataObject2Version1.getParent(), dataObject1Version1);
    assertEquals(0, dataObject2Version1.getChildren().size());
    assertEquals(0, dataObject2Version1.getPredecessors().size());
    assertEquals(1, dataObject2Version1.getSuccessors().size());
    assertEquals(dataObject3Version1, dataObject2Version1.getSuccessors().get(0));

    //inspecting DataObject1Version1
    assertEquals(1, dataObject1Version1.getSuccessors().size());
    assertEquals(dataObject1Version1.getSuccessors().get(0), dataObject3Version1);
    assertEquals(1, dataObject1Version1.getChildren().size());
    assertEquals(dataObject1Version1.getChildren().get(0), dataObject2Version1);
    assertEquals(null, dataObject1Version1.getParent());
    assertEquals(0, dataObject1Version1.getPredecessors().size());
  }

  @Test
  @Transactional
  public void createVersion_collectionWithStructuredDataReferences_copiedSuccessfully() {
    Collection collection = createCollection("collection");
    DataObject dataObject = createDataObject("DataObject", collection, null, null);
    StructuredDataReference reference = createStructuredDataReference("reference", dataObject);
    Version HEADVersion = collection.getVersion();
    Version version1 = createVersion("version1", collection);
    StructuredDataReference HEADReference = structuredDataReferenceService.getReferenceByShepardId(
      reference.getShepardId(),
      HEADVersion.getUid()
    );
    StructuredDataReference version1Reference = structuredDataReferenceService.getReferenceByShepardId(
      reference.getShepardId(),
      version1.getUid()
    );
    assertEquals(
      HEADReference.getStructuredDataContainer().getId(),
      version1Reference.getStructuredDataContainer().getId()
    );
    assertTrue(hasExactlyOneVersion(HEADReference.getId()));
    assertTrue(hasExactlyOneVersion(version1Reference.getId()));
    assertTrue(hasExactlyOneVersion(collection.getId()));
    assertTrue(hasExactlyOneVersion(dataObject.getId()));
    assertTrue(hasExactlyOneVersion(reference.getId()));
  }

  @Test
  @Transactional
  public void createVersion_collectionWithTimeseriesReferences_copiedSuccessfully() {
    Collection collection = createCollection("collection");
    DataObject dataObject = createDataObject("DataObject", collection, null, null);
    TimeseriesReference reference = createTimeseriesReference("reference", dataObject);
    Version HEADVersion = collection.getVersion();
    Version version1 = createVersion("version1", collection);
    TimeseriesReference HEADReference = timeseriesReferenceService.getReferenceByShepardId(
      reference.getShepardId(),
      HEADVersion.getUid()
    );
    TimeseriesReference version1Reference = timeseriesReferenceService.getReferenceByShepardId(
      reference.getShepardId(),
      version1.getUid()
    );
    assertEquals(HEADReference.getTimeseriesContainer().getId(), version1Reference.getTimeseriesContainer().getId());
    assertTrue(hasExactlyOneVersion(HEADReference.getId()));
    assertTrue(hasExactlyOneVersion(collection.getId()));
    assertTrue(hasExactlyOneVersion(dataObject.getId()));
    assertTrue(hasExactlyOneVersion(reference.getId()));
    assertTrue(hasExactlyOneVersion(version1Reference.getId()));
  }

  @Test
  @Transactional
  public void createVersion_collectionWithFileReferences_copiedSuccessfully() {
    Collection collection = createCollection("collection");
    DataObject dataObject = createDataObject("DataObject", collection, null, null);
    FileReference reference = createFileReference("reference", dataObject);
    Version HEADVersion = collection.getVersion();
    Version version1 = createVersion("version1", collection);
    FileReference HEADReference = fileReferenceService.getReferenceByShepardId(
      reference.getShepardId(),
      HEADVersion.getUid()
    );
    FileReference version1Reference = fileReferenceService.getReferenceByShepardId(
      reference.getShepardId(),
      version1.getUid()
    );
    assertEquals(HEADReference.getFileContainer().getId(), version1Reference.getFileContainer().getId());
    assertTrue(hasExactlyOneVersion(collection.getId()));
    assertTrue(hasExactlyOneVersion(dataObject.getId()));
    assertTrue(hasExactlyOneVersion(reference.getId()));
    assertTrue(hasExactlyOneVersion(HEADReference.getId()));
    assertTrue(hasExactlyOneVersion(version1Reference.getId()));
  }

  @Test
  @Transactional
  public void createVersion_collectionWithInternalDataObjectReferences_copiedSuccessfullyInNewVersion() {
    Collection collection1 = createCollection("collection1");
    DataObject dataObject1 = createDataObject("DataObject1", collection1, null, null);
    DataObject dataObject2 = createDataObject("DataObject1", collection1, dataObject1.getShepardId(), null);
    DataObjectReference dataObject1ToDataObject2 = createDataObjectReference(
      "dataObject1ToDataObject2",
      dataObject1,
      dataObject2
    );
    Version version1 = createVersion("version1", collection1);
    DataObjectReference dataObject1ToDataObject2Version1 = dataObjectReferenceService.getReferenceByShepardId(
      dataObject1ToDataObject2.getShepardId(),
      version1.getUid()
    );
    DataObject dataObject1Version1 = dataObjectService.getDataObjectByShepardId(
      dataObject1.getShepardId(),
      version1.getUid()
    );
    DataObject dataObject2Version1 = dataObjectService.getDataObjectByShepardId(
      dataObject2.getShepardId(),
      version1.getUid()
    );
    assertEquals(dataObject1ToDataObject2Version1.getReferencedDataObject().getId(), dataObject2Version1.getId());
    assertEquals(dataObject1Version1.getReferences().get(0).getId(), dataObject1ToDataObject2Version1.getId());
    assertTrue(hasExactlyOneVersion(collection1.getId()));
    assertTrue(hasExactlyOneVersion(dataObject1.getId()));
    assertTrue(hasExactlyOneVersion(dataObject2.getId()));
    assertTrue(hasExactlyOneVersion(dataObject1Version1.getId()));
    assertTrue(hasExactlyOneVersion(dataObject2Version1.getId()));
    assertTrue(hasExactlyOneVersion(dataObject1ToDataObject2.getId()));
    assertTrue(hasExactlyOneVersion(dataObject1ToDataObject2Version1.getId()));
  }

  @Test
  @Transactional
  public void createVersion_dataObjectReferencedByExternalReference_onlyHEADVersionReferenced() {
    Collection collection1 = createCollection("collection1");
    DataObject collection1DataObject1 = createDataObject("collection1DataObject1", collection1, null, null);
    Collection collection2 = createCollection("collection2");
    DataObject collection2DataObject1 = createDataObject("collection2DataObject1", collection2, null, null);
    DataObjectReference collection1DataObject1ToCollection2DataObject1 = createDataObjectReference(
      "collection1DataObject1ToCollection2DataObject1",
      collection1DataObject1,
      collection2DataObject1
    );
    Version collection2Version1 = createVersion("collection2Version1", collection2);
    DataObject collection2DataObject1Version1 = dataObjectService.getDataObjectByShepardId(
      collection2DataObject1.getShepardId(),
      collection2Version1.getUid()
    );
    assertEquals(0, collection2DataObject1Version1.getIncoming().size());
    assertTrue(hasExactlyOneVersion(collection1DataObject1ToCollection2DataObject1.getId()));
    assertTrue(hasExactlyOneVersion(collection1.getId()));
    assertTrue(hasExactlyOneVersion(collection1DataObject1.getId()));
    assertTrue(hasExactlyOneVersion(collection1DataObject1ToCollection2DataObject1.getId()));
    assertTrue(hasExactlyOneVersion(collection2.getId()));
    assertTrue(hasExactlyOneVersion(collection2DataObject1.getId()));
    assertTrue(hasExactlyOneVersion(collection2DataObject1Version1.getId()));
  }

  @Test
  @Transactional
  public void createVersion_collectionReference_versionizeReferencedCollection() {
    Collection referencingCollection = createCollection("referencingCollection");
    Collection referencedCollection = createCollection("referencedCollection");
    DataObject referencingDataObject = createDataObject("referencingDataObject", referencingCollection, null, null);
    CollectionReference collectionReference = createCollectionReference(
      "collectionReference",
      referencingDataObject,
      referencedCollection
    );
    Version referencedVersion1 = createVersion("referencedCollectionVersion", referencedCollection);
    Collection referencedCollectionVersion1 = collectionService.getCollectionByShepardId(
      referencedCollection.getShepardId(),
      referencedVersion1.getUid()
    );
    assertEquals(0, referencedCollectionVersion1.getIncoming().size());
    Collection referencedCollectionReloaded = collectionService.getCollectionByShepardId(
      referencedCollection.getShepardId()
    );
    assertEquals(collectionReference.getId(), referencedCollectionReloaded.getIncoming().get(0).getId());
    assertEquals(1, referencedCollectionReloaded.getIncoming().size());
    assertTrue(hasExactlyOneVersion(collectionReference.getId()));
    assertTrue(hasExactlyOneVersion(referencedCollection.getId()));
    assertTrue(hasExactlyOneVersion(referencedCollectionReloaded.getId()));
    assertTrue(hasExactlyOneVersion(referencingCollection.getId()));
    assertTrue(hasExactlyOneVersion(referencingDataObject.getId()));
  }

  @Test
  @Transactional
  public void createVersion_collectionReference_versionizeReferencingCollection() {
    Collection referencingCollection = createCollection("referencingCollection");
    Collection referencedCollection = createCollection("referencedCollection");
    DataObject referencingDataObject = createDataObject("referencingDataObject", referencingCollection, null, null);
    CollectionReference collectionReference = createCollectionReference(
      "collectionReference",
      referencingDataObject,
      referencedCollection
    );
    Version referencingVersion1 = createVersion("referencingCollectionVersion", referencingCollection);
    Collection referencedCollectionReloaded = collectionService.getCollectionByShepardId(
      referencedCollection.getShepardId()
    );
    assertEquals(2, referencedCollectionReloaded.getIncoming().size());
    assertTrue(hasExactlyOneVersion(collectionReference.getId()));
    assertTrue(hasExactlyOneVersion(referencedCollection.getId()));
    assertTrue(hasExactlyOneVersion(referencedCollectionReloaded.getId()));
    assertTrue(hasExactlyOneVersion(referencingCollection.getId()));
    assertTrue(hasExactlyOneVersion(referencingDataObject.getId()));
  }

  @Test
  @Transactional
  public void createVersion_collectionWithExternalDataObjectReferences_referencePresentInNewVersion() {
    Collection collection1 = createCollection("collection1");
    DataObject collection1DataObject1 = createDataObject("collection1DataObject1", collection1, null, null);
    Collection collection2 = createCollection("collection2");
    DataObject collection2DataObject1 = createDataObject("collection2DataObject1", collection2, null, null);
    DataObjectReference collection1DataObject1ToCollection2DataObject1 = createDataObjectReference(
      "collection1DataObject1ToCollection2DataObject1",
      collection1DataObject1,
      collection2DataObject1
    );
    Version collection1Version1 = createVersion("collection1Version1", collection1);
    DataObjectReference collection1DataObject1ToCollection2DataObject1Version1 =
      dataObjectReferenceService.getReferenceByShepardId(
        collection1DataObject1ToCollection2DataObject1.getShepardId(),
        collection1Version1.getUid()
      );
    List<DataObjectReference> incomingDataObjectReferences = dataObjectService
      .getDataObjectByShepardId(collection2DataObject1.getShepardId())
      .getIncoming();
    assertEquals(
      2,
      dataObjectService.getDataObjectByShepardId(collection2DataObject1.getShepardId()).getIncoming().size()
    );
    List<DataObjectReference> expectedIncomingReferences = new ArrayList<>();
    expectedIncomingReferences.add(collection1DataObject1ToCollection2DataObject1);
    expectedIncomingReferences.add(collection1DataObject1ToCollection2DataObject1Version1);
    assertEquals(
      new HashSet<DataObjectReference>(expectedIncomingReferences),
      new HashSet<DataObjectReference>(incomingDataObjectReferences)
    );
    assertTrue(hasExactlyOneVersion(collection1DataObject1ToCollection2DataObject1.getId()));
    assertTrue(hasExactlyOneVersion(collection1DataObject1ToCollection2DataObject1Version1.getId()));
  }
}
