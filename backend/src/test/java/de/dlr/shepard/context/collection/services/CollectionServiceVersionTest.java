package de.dlr.shepard.context.collection.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
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
import de.dlr.shepard.context.version.services.VersionService;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CollectionServiceVersionTest {

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

  @Inject
  CollectionDAO collectionDAO;

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
  public void getIncomingReferenceDepth2() {
    //setup
    Collection collection1 = createCollection("c1");
    Collection collection2 = createCollection("c2");
    DataObject dataObject11 = createDataObject("do11", collection1, null, null);
    CollectionReference do11Toc2 = createCollectionReference("do11Toc2", dataObject11, collection2);
    Collection collection2Retrieved = collectionDAO.findByShepardId(collection2.getShepardId());
    Version collection2HEADVersion = collection2.getVersion();
    Collection collection2Depth2 = collectionDAO.findCollectionByShepardIdDepth2(
      collection2.getShepardId(),
      collection2HEADVersion.getUid()
    );
    System.out.println("shepardId retrieved: " + collection2Depth2.getShepardId());
    System.out.println("incoming.shepardID: " + collection2Depth2.getIncoming().get(0).getShepardId());
    //get Version of incoming collectionReference
    System.out.println("incoming.Version.UID: " + collection2Depth2.getIncoming().get(0).getVersion().getUid());
  }

  @Test
  @Transactional
  public void getNakedCollectionDepth2() {
    Collection collection1 = createCollection("c1");
    Version collection1HEADVersion = collection1.getVersion();
    Collection collection1Depth2 = collectionDAO.findCollectionByShepardIdDepth2(
      collection1.getShepardId(),
      collection1HEADVersion.getUid()
    );
    System.out.println("shepardId: " + collection1Depth2.getShepardId());
    System.out.println("createdBy: " + collection1Depth2.getCreatedBy().getUsername());
  }

  @Test
  @Transactional
  public void getTwoIncomingReferencesDepth2() {
    //setup
    Collection collection1 = createCollection("c1");
    Collection collection2 = createCollection("c2");
    Collection collection3 = createCollection("c3");
    DataObject dataObject11 = createDataObject("do11", collection1, null, null);
    DataObject dataObject12 = createDataObject("do12", collection1, null, null);
    DataObject dataObject31 = createDataObject("do31", collection3, null, null);
    CollectionReference do11Toc2 = createCollectionReference("do11Toc2", dataObject11, collection2);
    CollectionReference do12Toc2 = createCollectionReference("do12Toc2", dataObject12, collection2);
    CollectionReference do31Toc2 = createCollectionReference("do31Toc2", dataObject31, collection2);
    Collection collection2Retrieved = collectionDAO.findByShepardId(collection2.getShepardId());
    Version collection2HEADVersion = collection2.getVersion();
    Collection collection2Depth2 = collectionDAO.findCollectionByShepardIdDepth2(
      collection2.getShepardId(),
      collection2HEADVersion.getUid()
    );
    System.out.println("shepardId retrieved: " + collection2Depth2.getShepardId());
    System.out.println("incoming.shepardID: " + collection2Depth2.getIncoming().get(0).getShepardId());
    //get Versions of incoming collectionReferences
    System.out.println("incoming[0].Version.UID: " + collection2Depth2.getIncoming().get(0).getVersion().getUid());
    System.out.println("incoming[1].Version.UID: " + collection2Depth2.getIncoming().get(1).getVersion().getUid());
    System.out.println("incoming[2].Version.UID: " + collection2Depth2.getIncoming().get(2).getVersion().getUid());
    assertEquals(3, collection2Depth2.getIncoming().size());
    assertEquals(username, collection2Depth2.getCreatedBy().getUsername());
  }

  @Test
  @Transactional
  public void depth2Test() {
    //setup
    Collection collection1 = createCollection("c1");
    Collection collection2 = createCollection("c2");
    Version collection1HEADVersion = collection1.getVersion();
    Version collection2HEADVersion = collection2.getVersion();
    DataObject dataObject21 = createDataObject("do21", collection2, null, null);
    CollectionReference do21Toc1 = createCollectionReference("da021Toc1", dataObject21, collection1);
    Collection collection1Depth2 = collectionDAO.findCollectionByShepardIdDepth2(
      collection1.getShepardId(),
      collection1.getVersion().getUid()
    );
    Version do21Toc1Version = collection1Depth2.getIncoming().get(0).getVersion();
    assertEquals(collection2HEADVersion.getUid(), do21Toc1Version.getUid());
    CollectionIO c1IO = new CollectionIO(collection1Depth2);
    assertEquals(c1IO.getIncomingReferences()[0].versionUID, do21Toc1Version.getUid());
  }
}
