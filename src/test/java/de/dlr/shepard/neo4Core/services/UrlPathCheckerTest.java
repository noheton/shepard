package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidPathException;
import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.CollectionReference;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.DataObjectReference;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.FileReference;
import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import de.dlr.shepard.neo4Core.entities.URIReference;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.UserGroup;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.core.PathSegment;

public class UrlPathCheckerTest extends BaseTestCase {

	@Mock
	CollectionService collectionService;
	@Mock
	DataObjectService dataObjectService;
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
	PathSegment collectionsSeg, collectionIdSeg, dataObjectsSeg, dataObjectIdSeg, basicReferencesSeg,
			basicReferencesIdSeg;

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

	@BeforeEach
	public void setupSegments() {
		when(dummySeg.getPath()).thenReturn("dummy");
		when(dummyIdSeg.getPath()).thenReturn("123");

		when(collectionsSeg.getPath()).thenReturn(Constants.COLLECTIONS);
		when(dataObjectsSeg.getPath()).thenReturn(Constants.DATAOBJECTS);
		when(basicReferencesSeg.getPath()).thenReturn(Constants.BASIC_REFERENCES);

		when(usersSeg.getPath()).thenReturn(Constants.USERS);
		when(apiKeysSeg.getPath()).thenReturn(Constants.APIKEYS);
		when(subscriptionsSeg.getPath()).thenReturn(Constants.SUBSCRIPTIONS);

		when(timeseriesSeg.getPath()).thenReturn(Constants.TIMESERIES);
		when(timeseriesReferencesSeg.getPath()).thenReturn(Constants.TIMESERIES_REFERENCES);

		when(filesSeg.getPath()).thenReturn(Constants.FILES);
		when(fileReferencesSeg.getPath()).thenReturn(Constants.FILE_REFERENCES);

		when(structuredDatasSeg.getPath()).thenReturn(Constants.STRUCTUREDDATAS);
		when(structuredDataReferencesSeg.getPath()).thenReturn(Constants.STRUCTUREDDATA_REFERENCES);

		when(uriReferencesSeg.getPath()).thenReturn(Constants.URI_REFERENCES);

		when(dataObjectReferencesSeg.getPath()).thenReturn(Constants.DATAOBJECT_REFERENCES);

		when(collectionReferencesSeg.getPath()).thenReturn(Constants.COLLECTION_REFERENCES);

		when(userGroupsSeg.getPath()).thenReturn(Constants.USERGROUP);

		when(semanticRepositoriesSeg.getPath()).thenReturn(Constants.SEMANTIC_REPOSITORIES);
		when(semanticAnnotationsSeg.getPath()).thenReturn(Constants.SEMANTIC_ANNOTATIONS);
	}

	@Test
	public void collections_notFound() {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(collectionsSeg);
		segments.add(collectionIdSeg);
		when(collectionIdSeg.getPath()).thenReturn("100");
		when(collectionService.getCollection(100L)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - Collection does not exist", e.getMessage());
	}

	@Test
	public void collections_exists() {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(collectionsSeg);
		segments.add(collectionIdSeg);
		when(collectionIdSeg.getPath()).thenReturn("100");

		Collection collection = new Collection(100L);
		when(collectionService.getCollection(100L)).thenReturn(collection);

		urlPathChecker.checkPathSegments(segments);
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
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		dataObject.setCollection(collection);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		urlPathChecker.checkPathSegments(segments);
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
		DataObject dataObject = new DataObject(101L);
		Collection wrong = new Collection(102L);
		dataObject.setCollection(wrong);

		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(101L)).thenReturn(dataObject);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		urlPathChecker.checkPathSegments(segments);
	}

	@Test
	public void user_notFound() {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(usersSeg);
		segments.add(userIdSeg);
		when(usersSeg.getPath()).thenReturn("users");
		when(userIdSeg.getPath()).thenReturn("bob");
		when(userService.getUser("bob")).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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

		urlPathChecker.checkPathSegments(segments);
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

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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

		urlPathChecker.checkPathSegments(segments);
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

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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

		urlPathChecker.checkPathSegments(segments);
	}

	@Test
	public void timeseries_notFound() {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(timeseriesSeg);
		segments.add(timeseriesIdSeg);

		when(timeseriesIdSeg.getPath()).thenReturn("100");
		when(timeseriesContainerService.getContainer(100)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		TimeseriesReference reference = new TimeseriesReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(dataObject);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(timeseriesReferenceService.getReference(104L)).thenReturn(reference);
		urlPathChecker.checkPathSegments(segments);
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
		DataObject dataObject = new DataObject(102L);
		dataObject.setCollection(collection);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(timeseriesReferenceService.getReference(104L)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		DataObject wrong = new DataObject(103L);
		TimeseriesReference reference = new TimeseriesReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(wrong);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(timeseriesReferenceService.getReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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

		urlPathChecker.checkPathSegments(segments);
	}

	@Test
	public void structuredData_notFound() {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(structuredDatasSeg);
		segments.add(structuredDataIdSeg);

		when(structuredDataIdSeg.getPath()).thenReturn("100");
		when(structuredDataContainerService.getContainer(100)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		StructuredDataReference reference = new StructuredDataReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(dataObject);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(structuredDataReferenceService.getReference(104L)).thenReturn(reference);
		urlPathChecker.checkPathSegments(segments);
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
		DataObject dataObject = new DataObject(102L);
		dataObject.setCollection(collection);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(structuredDataReferenceService.getReference(104L)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		DataObject wrong = new DataObject(103L);
		StructuredDataReference reference = new StructuredDataReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(wrong);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(structuredDataReferenceService.getReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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

		urlPathChecker.checkPathSegments(segments);
	}

	@Test
	public void file_notFound() {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(filesSeg);
		segments.add(fileIdSeg);

		when(fileIdSeg.getPath()).thenReturn("100");
		when(fileContainerService.getContainer(100)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		FileReference reference = new FileReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(dataObject);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(fileReferenceService.getReference(104L)).thenReturn(reference);
		urlPathChecker.checkPathSegments(segments);
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
		DataObject dataObject = new DataObject(102L);
		dataObject.setCollection(collection);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(fileReferenceService.getReference(104L)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		DataObject wrong = new DataObject(103L);
		FileReference reference = new FileReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(wrong);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(fileReferenceService.getReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		URIReference reference = new URIReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(dataObject);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(uriReferenceService.getReference(104L)).thenReturn(reference);
		urlPathChecker.checkPathSegments(segments);
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
		DataObject dataObject = new DataObject(102L);
		dataObject.setCollection(collection);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(uriReferenceService.getReference(104L)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		DataObject wrong = new DataObject(103L);
		URIReference reference = new URIReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(wrong);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(uriReferenceService.getReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		CollectionReference reference = new CollectionReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(dataObject);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(collectionReferenceService.getReference(104L)).thenReturn(reference);
		urlPathChecker.checkPathSegments(segments);
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
		DataObject dataObject = new DataObject(102L);
		dataObject.setCollection(collection);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(collectionReferenceService.getReference(104L)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		DataObject wrong = new DataObject(103L);
		CollectionReference reference = new CollectionReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(wrong);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(collectionReferenceService.getReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		DataObjectReference reference = new DataObjectReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(dataObject);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(dataObjectReferenceService.getReference(104L)).thenReturn(reference);
		urlPathChecker.checkPathSegments(segments);
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
		DataObject dataObject = new DataObject(102L);
		dataObject.setCollection(collection);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(dataObjectReferenceService.getReference(104L)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		DataObject wrong = new DataObject(103L);
		DataObjectReference reference = new DataObjectReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(wrong);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(dataObjectReferenceService.getReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		BasicReference reference = new BasicReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(dataObject);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(basicReferenceService.getReference(104L)).thenReturn(reference);
		urlPathChecker.checkPathSegments(segments);
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
		DataObject dataObject = new DataObject(102L);
		dataObject.setCollection(collection);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(basicReferenceService.getReference(104L)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		DataObject wrong = new DataObject(103L);
		BasicReference reference = new BasicReference(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(wrong);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(basicReferenceService.getReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
	}

	@Test
	public void usergroups_notFound() {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(userGroupsSeg);
		segments.add(userGroupIdSeg);
		when(userGroupIdSeg.getPath()).thenReturn("100");
		when(userGroupService.getUserGroup(100L)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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

		urlPathChecker.checkPathSegments(segments);
	}

	@Test
	public void semanticRepository_exist() {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(semanticRepositoriesSeg);
		segments.add(semanticRepositoryIdSeg);

		when(semanticRepositoryIdSeg.getPath()).thenReturn("100");

		var repository = new SemanticRepository(100);
		when(semanticRepositoryService.getRepository(100)).thenReturn(repository);

		urlPathChecker.checkPathSegments(segments);
	}

	@Test
	public void semanticRepository_notFound() {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(semanticRepositoriesSeg);
		segments.add(semanticRepositoryIdSeg);

		when(semanticRepositoryIdSeg.getPath()).thenReturn("100");
		when(semanticRepositoryService.getRepository(100)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
		collection.setAnnotations(List.of(semanticAnnotation));
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(semanticAnnotationService.getAnnotation(104L)).thenReturn(semanticAnnotation);
		urlPathChecker.checkPathSegments(segments);
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
		DataObject dataObject = new DataObject(102L);
		SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
		dataObject.setCollection(collection);
		dataObject.setAnnotations(List.of(semanticAnnotation));
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(semanticAnnotationService.getAnnotation(104L)).thenReturn(semanticAnnotation);
		urlPathChecker.checkPathSegments(segments);
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
		DataObject dataObject = new DataObject(102L);
		BasicReference reference = new BasicReference(103L);
		SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(dataObject);
		reference.setAnnotations(List.of(semanticAnnotation));
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(basicReferenceService.getReference(103L)).thenReturn(reference);
		when(semanticAnnotationService.getAnnotation(104L)).thenReturn(semanticAnnotation);
		urlPathChecker.checkPathSegments(segments);
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
		DataObject dataObject = new DataObject(102L);
		dataObject.setCollection(collection);
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(semanticAnnotationService.getAnnotation(104L)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - SemanticAnnotation does not exist", e.getMessage());
	}

	@Test
	public void semanticAnnotation_wrongPath() {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(semanticAnnotationsSeg);
		segments.add(semanticAnnotationIdSeg);
		when(semanticAnnotationIdSeg.getPath()).thenReturn("104");

		SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
		when(semanticAnnotationService.getAnnotation(104L)).thenReturn(semanticAnnotation);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		SemanticAnnotation wrong = new SemanticAnnotation(103L);
		SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
		collection.setAnnotations(List.of(wrong));
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(semanticAnnotationService.getAnnotation(104L)).thenReturn(semanticAnnotation);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		DataObject dataObject = new DataObject(102L);
		SemanticAnnotation wrong = new SemanticAnnotation(103L);
		SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
		dataObject.setCollection(collection);
		dataObject.setAnnotations(List.of(wrong));
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(semanticAnnotationService.getAnnotation(104L)).thenReturn(semanticAnnotation);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
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
		when(semanticAnnotationIdSeg.getPath()).thenReturn("104");

		Collection collection = new Collection(100L);
		DataObject dataObject = new DataObject(102L);
		BasicReference reference = new BasicReference(102L);
		SemanticAnnotation wrong = new SemanticAnnotation(103L);
		SemanticAnnotation semanticAnnotation = new SemanticAnnotation(104L);
		dataObject.setCollection(collection);
		reference.setDataObject(dataObject);
		reference.setAnnotations(List.of(wrong));
		when(collectionService.getCollection(100L)).thenReturn(collection);
		when(dataObjectService.getDataObject(102L)).thenReturn(dataObject);
		when(basicReferenceService.getReference(103L)).thenReturn(reference);
		when(semanticAnnotationService.getAnnotation(104L)).thenReturn(semanticAnnotation);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - There is no association between annotation and reference", e.getMessage());
	}

	@Test
	public void emptyUrl() {
		List<PathSegment> segments = new ArrayList<>();
		urlPathChecker.checkPathSegments(segments);
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
		when(collectionService.getCollection(100L)).thenReturn(collection);

		urlPathChecker.checkPathSegments(segments);
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
		when(collectionService.getCollection(100L)).thenReturn(collection);

		urlPathChecker.checkPathSegments(segments);
	}

	@Test
	public void getPathElements_invalidNumber() {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(collectionsSeg);
		segments.add(collectionIdSeg);
		segments.add(dataObjectsSeg);
		when(collectionIdSeg.getPath()).thenReturn("abc");

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("The given path seems wrong", e.getMessage());
	}

}
