package de.dlr.shepard.common.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.apikey.services.ApiKeyService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.subscription.entities.Subscription;
import de.dlr.shepard.common.subscription.services.SubscriptionService;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.services.BasicReferenceService;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.references.dataobject.services.CollectionReferenceService;
import de.dlr.shepard.context.references.dataobject.services.DataObjectReferenceService;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.FileReferenceService;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.data.semantic.entities.SemanticRepository;
import de.dlr.shepard.data.semantic.services.SemanticAnnotationService;
import de.dlr.shepard.data.semantic.services.SemanticRepositoryService;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.PathSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class UrlPathCheckerTest extends BaseTestCase {

  @Mock
  CollectionService collectionService;

  @Mock
  DataObjectService dataObjectService;

  @Mock
  LabJournalEntryService labJournalEntryService;

  @Mock
  TimeseriesReferenceService timeseriesReferenceService;

  @Mock
  TimeseriesContainerService timeseriesContainerService;

  @Mock
  StructuredDataReferenceService structuredDataReferenceService;

  @Mock
  StructuredDataContainerService structuredDataContainerService;

  @Mock
  FileReferenceService fileReferenceService;

  @Mock
  FileContainerService fileContainerService;

  @Mock
  URIReferenceService uriReferenceService;

  @Mock
  CollectionReferenceService collectionReferenceService;

  @Mock
  DataObjectReferenceService dataObjectReferenceService;

  @Mock
  BasicReferenceService basicReferenceService;

  @Mock
  UserService userService;

  @Mock
  ApiKeyService apiKeyService;

  @Mock
  SubscriptionService subscriptionService;

  @Mock
  UserGroupService userGroupService;

  @Mock
  SemanticRepositoryService semanticRepositoryService;

  @Mock
  SemanticAnnotationService semanticAnnotationService;

  @Mock
  PathSegment slashSeg, dummySeg, dummyIdSeg;

  @Mock
  PathSegment collectionsSeg, collectionIdSeg, dataObjectsSeg, dataObjectIdSeg, basicReferencesSeg, basicReferencesIdSeg, labJournalEntrySeg, labJournalEntryIdSeg;

  @Mock
  PathSegment usersSeg, userIdSeg, apiKeysSeg, apiKeyIdSeg, subscriptionsSeg, subscriptionIdSeg;

  @Mock
  PathSegment userGroupsSeg, userGroupIdSeg;

  @Mock
  PathSegment timeseriesSeg, timeseriesIdSeg, timeseriesReferencesSeg, timeseriesReferenceIdSeg;

  @Mock
  PathSegment filesSeg, fileIdSeg, fileReferencesSeg, fileReferenceIdSeg;

  @Mock
  PathSegment structuredDatasSeg, structuredDataIdSeg, structuredDataReferencesSeg, structuredDataReferenceIdSeg;

  @Mock
  PathSegment uriReferencesSeg, uriReferencesIdSeg;

  @Mock
  PathSegment collectionReferencesSeg, collectionReferencesIdSeg;

  @Mock
  PathSegment dataObjectReferencesSeg, dataObjectReferencesIdSeg;

  @Mock
  PathSegment semanticRepositoriesSeg, semanticRepositoryIdSeg;

  @Mock
  PathSegment semanticAnnotationsSeg, semanticAnnotationIdSeg;

  @InjectMocks
  UrlPathChecker urlPathChecker;

  MultivaluedMap<String, String> queryParams;

  @BeforeEach
  public void setupSegments() {
    when(dummySeg.getPath()).thenReturn("dummy");
    when(dummyIdSeg.getPath()).thenReturn("123");

    when(collectionsSeg.getPath()).thenReturn(Constants.COLLECTIONS);
    when(dataObjectsSeg.getPath()).thenReturn(Constants.DATA_OBJECTS);
    when(basicReferencesSeg.getPath()).thenReturn(Constants.BASIC_REFERENCES);

    when(usersSeg.getPath()).thenReturn(Constants.USERS);
    when(apiKeysSeg.getPath()).thenReturn(Constants.APIKEYS);
    when(subscriptionsSeg.getPath()).thenReturn(Constants.SUBSCRIPTIONS);

    when(timeseriesSeg.getPath()).thenReturn(Constants.TIMESERIES_CONTAINERS);
    when(timeseriesReferencesSeg.getPath()).thenReturn(Constants.TIMESERIES_REFERENCES);

    when(filesSeg.getPath()).thenReturn(Constants.FILE_CONTAINERS);
    when(fileReferencesSeg.getPath()).thenReturn(Constants.FILE_REFERENCES);

    when(structuredDatasSeg.getPath()).thenReturn(Constants.STRUCTURED_DATA_CONTAINERS);
    when(structuredDataReferencesSeg.getPath()).thenReturn(Constants.STRUCTURED_DATA_REFERENCES);

    when(uriReferencesSeg.getPath()).thenReturn(Constants.URI_REFERENCES);

    when(dataObjectReferencesSeg.getPath()).thenReturn(Constants.DATAOBJECT_REFERENCES);

    when(collectionReferencesSeg.getPath()).thenReturn(Constants.COLLECTION_REFERENCES);

    when(userGroupsSeg.getPath()).thenReturn(Constants.USERGROUPS);

    when(semanticRepositoriesSeg.getPath()).thenReturn(Constants.SEMANTIC_REPOSITORIES);
    when(semanticAnnotationsSeg.getPath()).thenReturn(Constants.SEMANTIC_ANNOTATIONS);

    when(labJournalEntrySeg.getPath()).thenReturn(Constants.LAB_JOURNAL_ENTRIES);
    when(labJournalEntryIdSeg.getPath()).thenReturn(Constants.LAB_JOURNAL_ENTRY_ID);

    queryParams = new MultivaluedHashMap<String, String>();
  }

  @Test
  public void collections_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(collectionService.getCollectionByShepardId(100L, null, true)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Collection does not exist", e.getMessage());
  }

  @Test
  public void collections_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");

    Collection collection = new Collection(100L);
    collection.setShepardId(100L);
    when(collectionService.getCollectionByShepardId(100L, null, true)).thenReturn(collection);

    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void dataObject_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    when(collectionService.getCollectionByShepardId(collection.getId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(102L)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - DataObject does not exist", e.getMessage());
  }

  @Test
  public void dataObject_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    dataObject.setCollection(collection);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void dataObject_wrongAssociation() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("101");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(101L);
    dataObject.setShepardId(dataObject.getId());
    Collection wrong = new Collection(102L);
    wrong.setShepardId(wrong.getId());
    dataObject.setCollection(wrong);

    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between collection and dataObject", e.getMessage());
  }

  @Test
  public void user_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(usersSeg);
    segments.add(userIdSeg);
    when(usersSeg.getPath()).thenReturn("users");
    when(userIdSeg.getPath()).thenReturn("bob");

    User user = new User("bob");
    when(userService.getUser("bob")).thenReturn(user);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void user_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(usersSeg);
    segments.add(userIdSeg);
    when(usersSeg.getPath()).thenReturn("users");
    when(userIdSeg.getPath()).thenReturn("bob");
    when(userService.getUser("bob")).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - User does not exist", e.getMessage());
  }

  @Test
  public void apiKey_exists() {
    UUID uid = UUID.randomUUID();
    List<PathSegment> segments = new ArrayList<>();
    segments.add(usersSeg);
    segments.add(userIdSeg);
    segments.add(apiKeysSeg);
    segments.add(apiKeyIdSeg);
    when(userIdSeg.getPath()).thenReturn("bob");
    when(apiKeyIdSeg.getPath()).thenReturn(uid.toString());

    User user = new User("bob");
    ApiKey apiKey = new ApiKey(uid);
    ArrayList<ApiKey> apiKeyList = new ArrayList<>();
    apiKeyList.add(apiKey);
    user.setApiKeys(apiKeyList);
    apiKey.setBelongsTo(user);
    when(userService.getUser("bob")).thenReturn(user);
    when(apiKeyService.getApiKey(uid)).thenReturn(apiKey);

    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void apiKey_notFound() {
    UUID uid = UUID.randomUUID();
    List<PathSegment> segments = new ArrayList<>();
    segments.add(usersSeg);
    segments.add(userIdSeg);
    segments.add(apiKeysSeg);
    segments.add(apiKeyIdSeg);
    when(userIdSeg.getPath()).thenReturn("bob");
    when(apiKeyIdSeg.getPath()).thenReturn(uid.toString());

    User user = new User("bob");
    when(userService.getUser("bob")).thenReturn(user);
    when(apiKeyService.getApiKey(uid)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - ApiKey does not exist", e.getMessage());
  }

  @Test
  public void apiKey_wrongAssociation() {
    UUID uid = UUID.randomUUID();
    List<PathSegment> segments = new ArrayList<>();
    segments.add(usersSeg);
    segments.add(userIdSeg);
    segments.add(apiKeysSeg);
    segments.add(apiKeyIdSeg);
    when(userIdSeg.getPath()).thenReturn("bob");
    when(apiKeyIdSeg.getPath()).thenReturn(uid.toString());

    User user = new User("bob");
    user.setApiKeys(new ArrayList<ApiKey>());
    User userAssociated = new User("carl");
    ApiKey apiKey = new ApiKey(uid);
    apiKey.setBelongsTo(userAssociated);
    ArrayList<ApiKey> apiKeyList = new ArrayList<>();
    apiKeyList.add(apiKey);
    userAssociated.setApiKeys(apiKeyList);
    when(userService.getUser("bob")).thenReturn(user);
    when(apiKeyService.getApiKey(uid)).thenReturn(apiKey);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between apiKey and user", e.getMessage());
  }

  @Test
  public void subscription_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(usersSeg);
    segments.add(userIdSeg);
    segments.add(subscriptionsSeg);
    segments.add(subscriptionIdSeg);
    when(userIdSeg.getPath()).thenReturn("bob");
    when(subscriptionIdSeg.getPath()).thenReturn("100");

    User user = new User("bob");
    Subscription sub = new Subscription(100L);
    sub.setCreatedBy(user);
    var subscriptions = List.of(sub);
    user.setSubscriptions(subscriptions);
    when(userService.getUser("bob")).thenReturn(user);
    when(subscriptionService.getSubscription(100L)).thenReturn(sub);

    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void subscription_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(usersSeg);
    segments.add(userIdSeg);
    segments.add(subscriptionsSeg);
    segments.add(subscriptionIdSeg);
    when(userIdSeg.getPath()).thenReturn("bob");
    when(subscriptionIdSeg.getPath()).thenReturn("100");

    User user = new User("bob");
    when(userService.getUser("bob")).thenReturn(user);
    when(subscriptionService.getSubscription(100L)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Subscription does not exist", e.getMessage());
  }

  @Test
  public void subscription_wrongAssociation() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(usersSeg);
    segments.add(userIdSeg);
    segments.add(subscriptionsSeg);
    segments.add(subscriptionIdSeg);
    when(userIdSeg.getPath()).thenReturn("bob");
    when(subscriptionIdSeg.getPath()).thenReturn("100");

    User user = new User("bob");
    Subscription sub = new Subscription(100L);
    User userAssociated = new User("carl");
    sub.setCreatedBy(user);
    var subscriptions = List.of(sub);
    userAssociated.setSubscriptions(subscriptions);
    when(userService.getUser("bob")).thenReturn(user);
    when(subscriptionService.getSubscription(100L)).thenReturn(sub);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between subscription and user", e.getMessage());
  }

  @Test
  public void timeseries_exist() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(timeseriesSeg);
    segments.add(timeseriesIdSeg);

    when(timeseriesIdSeg.getPath()).thenReturn("100");

    var container = new TimeseriesContainer(100);
    when(timeseriesContainerService.getContainer(100)).thenReturn(container);

    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void timeseries_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(timeseriesSeg);
    segments.add(timeseriesIdSeg);

    when(timeseriesIdSeg.getPath()).thenReturn("100");
    when(timeseriesContainerService.getContainer(100)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Container does not exist", e.getMessage());
  }

  @Test
  public void timeseriesReference_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(timeseriesReferencesSeg);
    segments.add(timeseriesReferenceIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(timeseriesReferenceIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    TimeseriesReference reference = new TimeseriesReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(dataObject);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void timeseriesReference_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(timeseriesReferencesSeg);
    segments.add(timeseriesReferenceIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(timeseriesReferenceIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    dataObject.setCollection(collection);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesReferenceService.getReferenceByShepardId(104L, null)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Reference does not exist", e.getMessage());
  }

  @Test
  public void timeseriesReference_wrongAssociation() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(timeseriesReferencesSeg);
    segments.add(timeseriesReferenceIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(timeseriesReferenceIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    DataObject wrong = new DataObject(103L);
    wrong.setShepardId(wrong.getId());
    TimeseriesReference reference = new TimeseriesReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(wrong);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(timeseriesReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
  }

  @Test
  public void structuredData_exist() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(structuredDatasSeg);
    segments.add(structuredDataIdSeg);

    when(structuredDataIdSeg.getPath()).thenReturn("100");

    var container = new StructuredDataContainer(100);
    when(structuredDataContainerService.getContainer(100)).thenReturn(container);

    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void structuredData_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(structuredDatasSeg);
    segments.add(structuredDataIdSeg);

    when(structuredDataIdSeg.getPath()).thenReturn("100");
    when(structuredDataContainerService.getContainer(100)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Container does not exist", e.getMessage());
  }

  @Test
  public void structuredDataReference_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(structuredDataReferencesSeg);
    segments.add(structuredDataReferenceIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(structuredDataReferenceIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    StructuredDataReference reference = new StructuredDataReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(dataObject);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void structuredDataReference_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(structuredDataReferencesSeg);
    segments.add(structuredDataReferenceIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(structuredDataReferenceIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    dataObject.setCollection(collection);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataReferenceService.getReferenceByShepardId(104L, null)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Reference does not exist", e.getMessage());
  }

  @Test
  public void structuredDataReference_wrongAssociation() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(structuredDataReferencesSeg);
    segments.add(structuredDataReferenceIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(structuredDataReferenceIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    DataObject wrong = new DataObject(103L);
    wrong.setShepardId(wrong.getId());
    StructuredDataReference reference = new StructuredDataReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(wrong);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(structuredDataReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
  }

  @Test
  public void file_exist() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(filesSeg);
    segments.add(fileIdSeg);

    when(fileIdSeg.getPath()).thenReturn("100");

    var container = new FileContainer(100);
    when(fileContainerService.getContainer(100)).thenReturn(container);

    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void file_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(filesSeg);
    segments.add(fileIdSeg);

    when(fileIdSeg.getPath()).thenReturn("100");
    when(fileContainerService.getContainer(100)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Container does not exist", e.getMessage());
  }

  @Test
  public void fileReference_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(fileReferencesSeg);
    segments.add(fileReferenceIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(fileReferenceIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    FileReference reference = new FileReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(dataObject);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(fileReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void fileReference_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(fileReferencesSeg);
    segments.add(fileReferenceIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(fileReferenceIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    dataObject.setCollection(collection);
    when(collectionService.getCollectionByShepardId(collection.getId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getId())).thenReturn(dataObject);
    when(fileReferenceService.getReferenceByShepardId(104L, null)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Reference does not exist", e.getMessage());
  }

  @Test
  public void fileReference_wrongAssociation() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(fileReferencesSeg);
    segments.add(fileReferenceIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(fileReferenceIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    DataObject wrong = new DataObject(103L);
    wrong.setShepardId(wrong.getId());
    FileReference reference = new FileReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(wrong);
    when(collectionService.getCollectionByShepardId(collection.getId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(fileReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
  }

  @Test
  public void uriReference_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(uriReferencesSeg);
    segments.add(uriReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(uriReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    URIReference reference = new URIReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(dataObject);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(uriReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void uriReference_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(uriReferencesSeg);
    segments.add(uriReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(uriReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    dataObject.setCollection(collection);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(uriReferenceService.getReferenceByShepardId(104L, null)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Reference does not exist", e.getMessage());
  }

  @Test
  public void uriReference_wrongAssociation() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(uriReferencesSeg);
    segments.add(uriReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(uriReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    DataObject wrong = new DataObject(103L);
    wrong.setShepardId(wrong.getId());
    URIReference reference = new URIReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(wrong);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(uriReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
  }

  @Test
  public void collectionReference_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(collectionReferencesSeg);
    segments.add(collectionReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(collectionReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    CollectionReference reference = new CollectionReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(dataObject);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(collectionReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void collectionReference_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(collectionReferencesSeg);
    segments.add(collectionReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(collectionReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    dataObject.setCollection(collection);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(collectionReferenceService.getReferenceByShepardId(104L, null)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Reference does not exist", e.getMessage());
  }

  @Test
  public void collectionReference_wrongAssociation() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(collectionReferencesSeg);
    segments.add(collectionReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(collectionReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    DataObject wrong = new DataObject(103L);
    wrong.setShepardId(wrong.getId());
    CollectionReference reference = new CollectionReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(wrong);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(collectionReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
  }

  @Test
  public void dataObjectReference_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(dataObjectReferencesSeg);
    segments.add(dataObjectReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(dataObjectReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    DataObjectReference reference = new DataObjectReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(dataObject);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(dataObjectReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void dataObjectReference_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(dataObjectReferencesSeg);
    segments.add(dataObjectReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(dataObjectReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    dataObject.setCollection(collection);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(dataObjectReferenceService.getReferenceByShepardId(104L, null)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Reference does not exist", e.getMessage());
  }

  @Test
  public void dataObjectReference_wrongAssociation() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(dataObjectReferencesSeg);
    segments.add(dataObjectReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(dataObjectReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    DataObject wrong = new DataObject(103L);
    wrong.setShepardId(wrong.getId());
    DataObjectReference reference = new DataObjectReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(wrong);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(dataObjectReferenceService.getReferenceByShepardId(reference.getShepardId(), null)).thenReturn(reference);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
  }

  @Test
  public void basicReference_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(basicReferencesSeg);
    segments.add(basicReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(basicReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    BasicReference reference = new BasicReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(dataObject);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(basicReferenceService.getReferenceByShepardId(reference.getShepardId())).thenReturn(reference);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void basicReference_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(basicReferencesSeg);
    segments.add(basicReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(basicReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    dataObject.setCollection(collection);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(basicReferenceService.getReferenceByShepardId(104L)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - Reference does not exist", e.getMessage());
  }

  @Test
  public void basicReference_wrongAssociation() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(basicReferencesSeg);
    segments.add(basicReferencesIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(basicReferencesIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    DataObject wrong = new DataObject(103L);
    wrong.setShepardId(wrong.getId());
    BasicReference reference = new BasicReference(104L);
    reference.setShepardId(reference.getId());
    dataObject.setCollection(collection);
    reference.setDataObject(wrong);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(basicReferenceService.getReferenceByShepardId(reference.getShepardId())).thenReturn(reference);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
  }

  @Test
  public void usergroups_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(userGroupsSeg);
    segments.add(userGroupIdSeg);
    when(userGroupIdSeg.getPath()).thenReturn("100");
    when(userGroupService.getUserGroup(100L)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - UserGroup does not exist", e.getMessage());
  }

  @Test
  public void usergroups_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(userGroupsSeg);
    segments.add(userGroupIdSeg);
    when(userGroupIdSeg.getPath()).thenReturn("100");

    UserGroup userGroup = new UserGroup(100L);
    when(userGroupService.getUserGroup(100L)).thenReturn(userGroup);

    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void semanticRepository_exist() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(semanticRepositoriesSeg);
    segments.add(semanticRepositoryIdSeg);

    when(semanticRepositoryIdSeg.getPath()).thenReturn("100");

    var repository = new SemanticRepository(100);
    when(semanticRepositoryService.getRepository(100)).thenReturn(repository);

    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void semanticRepository_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(semanticRepositoriesSeg);
    segments.add(semanticRepositoryIdSeg);

    when(semanticRepositoryIdSeg.getPath()).thenReturn("100");
    when(semanticRepositoryService.getRepository(100)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - SemanticRepository does not exist", e.getMessage());
  }

  @Test
  public void semanticAnnotation_existsCollection() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(semanticAnnotationsSeg);
    segments.add(semanticAnnotationIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(semanticAnnotationIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
    collection.setAnnotations(List.of(semanticAnnotation));
    when(collectionService.getCollectionByShepardId(100L, null, true)).thenReturn(collection);
    when(collectionService.getCollectionByShepardId(100L, null, false)).thenReturn(collection);
    when(semanticAnnotationService.getAnnotationByNeo4jId(semanticAnnotation.getId())).thenReturn(semanticAnnotation);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void semanticAnnotation_existsDataObject() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(semanticAnnotationsSeg);
    segments.add(semanticAnnotationIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(semanticAnnotationIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
    dataObject.setCollection(collection);
    dataObject.setAnnotations(List.of(semanticAnnotation));
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, false)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(semanticAnnotationService.getAnnotationByNeo4jId(104L)).thenReturn(semanticAnnotation);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void semanticAnnotation_existsReference() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(basicReferencesSeg);
    segments.add(basicReferencesIdSeg);
    segments.add(semanticAnnotationsSeg);
    segments.add(semanticAnnotationIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(basicReferencesIdSeg.getPath()).thenReturn("103");
    when(semanticAnnotationIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    BasicReference reference = new BasicReference(103L);
    reference.setShepardId(reference.getId());
    SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
    dataObject.setCollection(collection);
    reference.setDataObject(dataObject);
    reference.setAnnotations(List.of(semanticAnnotation));
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, false)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(basicReferenceService.getReferenceByShepardId(reference.getShepardId())).thenReturn(reference);
    when(semanticAnnotationService.getAnnotationByNeo4jId(semanticAnnotation.getId())).thenReturn(semanticAnnotation);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void semanticAnnotation_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(semanticAnnotationsSeg);
    segments.add(semanticAnnotationIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(semanticAnnotationIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    dataObject.setCollection(collection);
    when(collectionService.getCollectionByShepardId(collection.getId(), null, false)).thenReturn(collection);
    when(collectionService.getCollectionByShepardId(collection.getId(), null, true)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getId())).thenReturn(dataObject);
    when(semanticAnnotationService.getAnnotationByNeo4jId(104L)).thenReturn(null);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - SemanticAnnotation does not exist", e.getMessage());
  }

  @Test
  public void semanticAnnotation_wrongPath() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(semanticAnnotationsSeg);
    segments.add(semanticAnnotationIdSeg);
    when(semanticAnnotationIdSeg.getPath()).thenReturn("104");

    SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
    when(semanticAnnotationService.getAnnotationByNeo4jId(104L)).thenReturn(semanticAnnotation);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - No entity was found annotated", e.getMessage());
  }

  @Test
  public void semanticAnnotation_wrongAssociationCollection() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(semanticAnnotationsSeg);
    segments.add(semanticAnnotationIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(semanticAnnotationIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    SemanticAnnotation wrong = new SemanticAnnotation(103L);
    SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
    collection.setAnnotations(List.of(wrong));
    when(collectionService.getCollectionByShepardId(100L, null, true)).thenReturn(collection);
    when(collectionService.getCollectionByShepardId(100L, null, false)).thenReturn(collection);
    when(semanticAnnotationService.getAnnotationByNeo4jId(104L)).thenReturn(semanticAnnotation);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between annotation and collection", e.getMessage());
  }

  @Test
  public void semanticAnnotation_wrongAssociationDataObject() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(semanticAnnotationsSeg);
    segments.add(semanticAnnotationIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(semanticAnnotationIdSeg.getPath()).thenReturn("104");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    SemanticAnnotation wrong = new SemanticAnnotation(103L);
    SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
    dataObject.setCollection(collection);
    dataObject.setAnnotations(List.of(wrong));
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, false)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(semanticAnnotationService.getAnnotationByNeo4jId(104L)).thenReturn(semanticAnnotation);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between annotation and dataObject", e.getMessage());
  }

  @Test
  public void semanticAnnotation_wrongAssociationReference() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(dataObjectIdSeg);
    segments.add(basicReferencesSeg);
    segments.add(basicReferencesIdSeg);
    segments.add(semanticAnnotationsSeg);
    segments.add(semanticAnnotationIdSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(dataObjectIdSeg.getPath()).thenReturn("102");
    when(basicReferencesIdSeg.getPath()).thenReturn("103");
    when(semanticAnnotationIdSeg.getPath()).thenReturn("105");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    DataObject dataObject = new DataObject(102L);
    dataObject.setShepardId(dataObject.getId());
    BasicReference reference = new BasicReference(103L);
    reference.setShepardId(reference.getId());
    SemanticAnnotation wrong = new SemanticAnnotation(104L);
    SemanticAnnotation semanticAnnotation = new SemanticAnnotation(105L);
    dataObject.setCollection(collection);
    reference.setDataObject(dataObject);
    reference.setAnnotations(List.of(wrong));
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, false)).thenReturn(collection);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getShepardId())).thenReturn(dataObject);
    when(basicReferenceService.getReferenceByShepardId(reference.getShepardId())).thenReturn(reference);
    when(semanticAnnotationService.getAnnotationByNeo4jId(semanticAnnotation.getId())).thenReturn(semanticAnnotation);

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - There is no association between annotation and reference", e.getMessage());
  }

  @Test
  public void emptyUrl() {
    List<PathSegment> segments = new ArrayList<>();
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void getPathElements_slashPathSegment() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(slashSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(slashSeg.getPath()).thenReturn("/");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    when(collectionService.getCollectionByShepardId(100L, null, true)).thenReturn(collection);

    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void getPathElements_slashPathSegmentEmpty() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    segments.add(slashSeg);
    when(collectionIdSeg.getPath()).thenReturn("100");
    when(slashSeg.getPath()).thenReturn("");

    Collection collection = new Collection(100L);
    collection.setShepardId(collection.getId());
    when(collectionService.getCollectionByShepardId(collection.getShepardId(), null, true)).thenReturn(collection);

    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void getPathElements_invalidNumber() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(collectionsSeg);
    segments.add(collectionIdSeg);
    segments.add(dataObjectsSeg);
    when(collectionIdSeg.getPath()).thenReturn("abc");

    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("The given path seems wrong", e.getMessage());
  }

  @Test
  public void labJournalEntry_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(labJournalEntrySeg);
    segments.add(labJournalEntryIdSeg);
    when(labJournalEntryIdSeg.getPath()).thenReturn("100");

    LabJournalEntry labJournalEntry = new LabJournalEntry();
    labJournalEntry.setId(100L);
    when(labJournalEntryService.getLabJournalEntry(labJournalEntry.getId())).thenReturn(labJournalEntry);
    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void labJournalEntry_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(labJournalEntrySeg);
    segments.add(labJournalEntryIdSeg);
    when(labJournalEntryIdSeg.getPath()).thenReturn("100");

    when(labJournalEntryService.getLabJournalEntry(100L)).thenReturn(null);
    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - LabJournalEntry does not exist", e.getMessage());
  }

  @Test
  public void dataObjectOfLabJournalEntry_exists() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(labJournalEntrySeg);
    queryParams.add(Constants.DATA_OBJECT_ID, "101");

    DataObject dataObject = new DataObject(101L);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getId())).thenReturn(dataObject);

    urlPathChecker.assertIfIdsAreValid(segments, queryParams);
  }

  @Test
  public void dataObjectOfLabJournalEntry_notFound() {
    List<PathSegment> segments = new ArrayList<>();
    segments.add(labJournalEntrySeg);
    queryParams.add(Constants.DATA_OBJECT_ID, "101");

    DataObject dataObject = new DataObject(102L);
    when(dataObjectService.getDataObjectByShepardId(dataObject.getId())).thenReturn(dataObject);
    Exception e = assertThrows(InvalidPathException.class, () ->
      urlPathChecker.assertIfIdsAreValid(segments, queryParams)
    );
    assertEquals("ID ERROR - DataObject does not exist", e.getMessage());
  }
}
