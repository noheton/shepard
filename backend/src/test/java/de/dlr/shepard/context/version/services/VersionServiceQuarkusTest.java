package de.dlr.shepard.context.version.services;

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
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.io.SemanticRepositoryIO;
import de.dlr.shepard.context.semantic.services.SemanticAnnotationService;
import de.dlr.shepard.context.semantic.services.SemanticRepositoryService;
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
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.integrationtests.WireMockResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(WireMockResource.class)
public class VersionServiceQuarkusTest {

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
  SemanticRepositoryService semanticRepositoryService;

  @Inject
  SemanticAnnotationService semanticAnnotationService;

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
      userService.createOrUpdateUser(user);
      authenticationContext.setPrincipal(new JWTPrincipal(username, "key"));
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
    return collectionService.createCollection(cIO);
  }

  private StructuredDataReference createStructuredDataReference(
    long collectionId,
    String name,
    DataObject referencingDataObject
  ) {
    StructuredDataContainerIO containerIO = new StructuredDataContainerIO();
    containerIO.setName(name + "Container");
    StructuredDataContainer container = structuredDataContainerService.createContainer(containerIO);
    StructuredDataReferenceIO referenceIO = new StructuredDataReferenceIO();
    referenceIO.setName(name);
    referenceIO.setStructuredDataContainerId(container.getId());
    String[] OIDs = {};
    referenceIO.setStructuredDataOids(OIDs);
    return structuredDataReferenceService.createReference(
      collectionId,
      referencingDataObject.getShepardId(),
      referenceIO
    );
  }

  private TimeseriesReference createTimeseriesReference(
    long collectionShepardId,
    String name,
    DataObject referencingDataObject
  ) {
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(name + "Container");
    TimeseriesContainer container = timeseriesContainerService.createContainer(containerIO);
    TimeseriesReferenceIO referenceIO = new TimeseriesReferenceIO();
    referenceIO.setName(name);
    referenceIO.setTimeseriesContainerId(container.getId());
    List<Timeseries> timeseries = new ArrayList<Timeseries>();
    referenceIO.setTimeseries(timeseries);
    return timeseriesReferenceService.createReference(
      collectionShepardId,
      referencingDataObject.getShepardId(),
      referenceIO
    );
  }

  private FileReference createFileReference(long collectionShepardId, String name, DataObject referencingDataObject) {
    FileContainerIO containerIO = new FileContainerIO();
    containerIO.setName(name + "Container");
    FileContainer container = fileContainerService.createContainer(containerIO);
    FileReferenceIO referenceIO = new FileReferenceIO();
    referenceIO.setName(name);
    referenceIO.setFileContainerId(container.getId());
    String[] fileOIDs = {};
    referenceIO.setFileOids(fileOIDs);
    return fileReferenceService.createReference(collectionShepardId, referencingDataObject.getShepardId(), referenceIO);
  }

  private DataObject createDataObject(String name, Collection collection, Long parentId, long[] predecessorIds) {
    DataObjectIO dIO = new DataObjectIO();
    dIO.setName(name);
    dIO.setParentId(parentId);
    dIO.setPredecessorIds(predecessorIds);
    return dataObjectService.createDataObject(collection.getShepardId(), dIO);
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
    Collection collection1Version1 = collectionService.getCollectionWithDataObjectsAndIncomingReferences(
      collection1.getShepardId(),
      version1.getUid()
    );
    DataObject dataObject1Version1 = dataObjectService.getDataObject(dataObject1.getShepardId(), version1.getUid());
    DataObject dataObject2Version1 = dataObjectService.getDataObject(dataObject2.getShepardId(), version1.getUid());
    DataObject dataObject3Version1 = dataObjectService.getDataObject(dataObject3.getShepardId(), version1.getUid());

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
    StructuredDataReference reference = createStructuredDataReference(
      collection.getShepardId(),
      "reference",
      dataObject
    );
    Version HEADVersion = collection.getVersion();
    Version version1 = createVersion("version1", collection);
    StructuredDataReference HEADReference = structuredDataReferenceService.getReference(
      collection.getShepardId(),
      dataObject.getShepardId(),
      reference.getShepardId(),
      HEADVersion.getUid()
    );
    StructuredDataReference version1Reference = structuredDataReferenceService.getReference(
      collection.getShepardId(),
      dataObject.getShepardId(),
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
    TimeseriesReference reference = createTimeseriesReference(collection.getShepardId(), "reference", dataObject);
    Version HEADVersion = collection.getVersion();
    Version version1 = createVersion("version1", collection);
    TimeseriesReference HEADReference = timeseriesReferenceService.getReference(
      collection.getShepardId(),
      dataObject.getShepardId(),
      reference.getShepardId(),
      HEADVersion.getUid()
    );
    TimeseriesReference version1Reference = timeseriesReferenceService.getReference(
      collection.getShepardId(),
      dataObject.getShepardId(),
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
    FileReference reference = createFileReference(collection.getShepardId(), "reference", dataObject);
    Version HEADVersion = collection.getVersion();
    Version version1 = createVersion("version1", collection);
    FileReference HEADReference = fileReferenceService.getReference(
      collection.getShepardId(),
      dataObject.getShepardId(),
      reference.getShepardId(),
      HEADVersion.getUid()
    );
    FileReference version1Reference = fileReferenceService.getReference(
      collection.getShepardId(),
      dataObject.getShepardId(),
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
      collection1.getShepardId(),
      "dataObject1ToDataObject2",
      dataObject1,
      dataObject2
    );
    Version version1 = createVersion("version1", collection1);
    DataObjectReference dataObject1ToDataObject2Version1 = dataObjectReferenceService.getReference(
      collection1.getShepardId(),
      dataObject1.getShepardId(),
      dataObject1ToDataObject2.getShepardId(),
      version1.getUid()
    );
    DataObject dataObject1Version1 = dataObjectService.getDataObject(dataObject1.getShepardId(), version1.getUid());
    DataObject dataObject2Version1 = dataObjectService.getDataObject(dataObject2.getShepardId(), version1.getUid());
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
      collection1.getShepardId(),
      "collection1DataObject1ToCollection2DataObject1",
      collection1DataObject1,
      collection2DataObject1
    );
    Version collection2Version1 = createVersion("collection2Version1", collection2);
    DataObject collection2DataObject1Version1 = dataObjectService.getDataObject(
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
      referencingCollection.getShepardId(),
      "collectionReference",
      referencingDataObject,
      referencedCollection
    );
    Version referencedVersion1 = createVersion("referencedCollectionVersion", referencedCollection);
    Collection referencedCollectionVersion1 = collectionService.getCollectionWithDataObjectsAndIncomingReferences(
      referencedCollection.getShepardId(),
      referencedVersion1.getUid()
    );
    assertEquals(0, referencedCollectionVersion1.getIncoming().size());
    Collection referencedCollectionReloaded = collectionService.getCollectionWithDataObjectsAndIncomingReferences(
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
      referencingCollection.getShepardId(),
      "collectionReference",
      referencingDataObject,
      referencedCollection
    );
    Version referencingVersion1 = createVersion("referencingCollectionVersion", referencingCollection);
    Collection referencedCollectionReloaded = collectionService.getCollectionWithDataObjectsAndIncomingReferences(
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
      collection1.getShepardId(),
      "collection1DataObject1ToCollection2DataObject1",
      collection1DataObject1,
      collection2DataObject1
    );
    Version collection1Version1 = createVersion("collection1Version1", collection1);
    DataObjectReference collection1DataObject1ToCollection2DataObject1Version1 =
      dataObjectReferenceService.getReference(
        collection1.getShepardId(),
        collection1DataObject1.getShepardId(),
        collection1DataObject1ToCollection2DataObject1.getShepardId(),
        collection1Version1.getUid()
      );
    List<DataObjectReference> incomingDataObjectReferences = dataObjectService
      .getDataObject(collection2DataObject1.getShepardId())
      .getIncoming();
    assertEquals(2, dataObjectService.getDataObject(collection2DataObject1.getShepardId()).getIncoming().size());
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

  @Test
  @Transactional
  public void createVersionWithSemanticAnnotations() {
    Collection collectionAnnotated = createCollection("annotated collection");
    DataObject dataObjectAnnotated = createDataObject("DOAnnotated", collectionAnnotated, null, null);
    StructuredDataReference referenceAnnotated = createStructuredDataReference(
      collectionAnnotated.getShepardId(),
      "FRAnnotated",
      dataObjectAnnotated
    );
    SemanticRepositoryIO repToCreate = new SemanticRepositoryIO();
    repToCreate.setName("SemanticRepository");
    repToCreate.setType(SemanticRepositoryType.SPARQL);
    repToCreate.setEndpoint(WireMockResource.getWireMockServerURlWithPath("/sparql"));
    SemanticRepository repository = semanticRepositoryService.createRepository(repToCreate);
    SemanticAnnotationIO AnnoToCreate = new SemanticAnnotationIO();
    AnnoToCreate.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
    AnnoToCreate.setPropertyRepositoryId(repository.getId());
    AnnoToCreate.setValueIRI("http://dbpedia.org/resource/Almond_milk");
    AnnoToCreate.setValueRepositoryId(repository.getId());
    SemanticAnnotation DOAnnotation = semanticAnnotationService.createAnnotationByShepardId(
      dataObjectAnnotated.getShepardId(),
      AnnoToCreate
    );
    SemanticAnnotation collectionAnnotation = semanticAnnotationService.createAnnotationByShepardId(
      collectionAnnotated.getShepardId(),
      AnnoToCreate
    );
    SemanticAnnotation referenceAnnotation1 = semanticAnnotationService.createAnnotationByShepardId(
      referenceAnnotated.getShepardId(),
      AnnoToCreate
    );
    SemanticAnnotation referenceAnnotation2 = semanticAnnotationService.createAnnotationByShepardId(
      referenceAnnotated.getShepardId(),
      AnnoToCreate
    );
    Version Version1 = createVersion("annotationTestVersion", collectionAnnotated);
    Collection collectionVersionized = collectionService.getCollection(
      collectionAnnotated.getShepardId(),
      Version1.getUid()
    );
    List<SemanticAnnotation> versionizedCollectionAnnotations = semanticAnnotationService.getAllAnnotationsByNeo4jId(
      collectionVersionized.getId()
    );
    assertEquals(1, versionizedCollectionAnnotations.size());
    assertEquals(collectionAnnotation, versionizedCollectionAnnotations.get(0));
    DataObject DOVersionized = dataObjectService.getDataObject(dataObjectAnnotated.getShepardId(), Version1.getUid());
    List<SemanticAnnotation> versionizedDOAnnotations = semanticAnnotationService.getAllAnnotationsByNeo4jId(
      DOVersionized.getId()
    );
    assertEquals(1, versionizedDOAnnotations.size());
    assertEquals(DOAnnotation, versionizedDOAnnotations.get(0));
    StructuredDataReference referenceVersionized = structuredDataReferenceService.getReference(
      collectionAnnotated.getShepardId(),
      dataObjectAnnotated.getShepardId(),
      referenceAnnotated.getShepardId(),
      Version1.getUid()
    );
    List<SemanticAnnotation> versionizedReferenceAnnotations = semanticAnnotationService.getAllAnnotationsByNeo4jId(
      referenceVersionized.getId()
    );
    assertEquals(2, versionizedReferenceAnnotations.size());
    HashSet<SemanticAnnotation> expectedAnnotations = new HashSet<SemanticAnnotation>();
    expectedAnnotations.add(referenceAnnotation1);
    expectedAnnotations.add(referenceAnnotation2);
    assertEquals(expectedAnnotations, new HashSet<SemanticAnnotation>(versionizedReferenceAnnotations));
  }
}
