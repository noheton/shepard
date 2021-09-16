package de.dlr.shepard.neo4Core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import de.dlr.shepard.neo4Core.entities.URIReference;
import de.dlr.shepard.neo4Core.entities.User;
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
	PathSegment slashSeg, dummySeg, dummyIdSeg;

	@Mock
	PathSegment collectionsSeg, collectionIdSeg, dataObjectsSeg, dataObjectIdSeg, basicReferencesSeg,
			basicReferencesIdSeg;

	@Mock
	PathSegment usersSeg, userIdSeg, apiKeysSeg, apiKeyIdSeg, subscriptionsSeg, subscriptionIdSeg;

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
	public void collections_exists() throws InvalidPathException {
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
	public void dataObject_exists() throws InvalidPathException {
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
		assertEquals("ID ERROR - There is no association between experiment and dataObject", e.getMessage());
	}

	@Test
	public void user_exists() throws InvalidPathException {
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
	public void apiKey_exists() throws InvalidPathException {
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
		ArrayList<ApiKey> apiKeyList = new ArrayList<ApiKey>();
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
		ArrayList<ApiKey> apiKeyList = new ArrayList<ApiKey>();
		apiKeyList.add(apiKey);
		userAssociated.setApiKeys(apiKeyList);
		when(userService.getUser("bob")).thenReturn(user);
		when(apiKeyService.getApiKey(uid)).thenReturn(apiKey);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - There is no association between apiKey and user", e.getMessage());
	}

	@Test
	public void subscription_exists() throws InvalidPathException {
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
	public void timeseries_exist() throws InvalidPathException {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(timeseriesSeg);
		segments.add(timeseriesIdSeg);

		when(timeseriesIdSeg.getPath()).thenReturn("100");

		var container = new TimeseriesContainer(100);
		when(timeseriesContainerService.getTimeseriesContainer(100)).thenReturn(container);

		urlPathChecker.checkPathSegments(segments);
	}

	@Test
	public void timeseries_notFound() throws InvalidPathException {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(timeseriesSeg);
		segments.add(timeseriesIdSeg);

		when(timeseriesIdSeg.getPath()).thenReturn("100");
		when(timeseriesContainerService.getTimeseriesContainer(100)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - Container does not exist", e.getMessage());
	}

	@Test
	public void timeseries_wrongUrl() throws InvalidPathException {
		List<PathSegment> segments = new ArrayList<>();

		segments.add(dummySeg);
		segments.add(dummyIdSeg);
		segments.add(timeseriesSeg);
		segments.add(timeseriesIdSeg);

		when(timeseriesIdSeg.getPath()).thenReturn("200");

		urlPathChecker.checkPathSegments(segments);
		verify(timeseriesContainerService, never()).getTimeseriesContainer(200);
	}

	@Test
	public void timeseriesReference_exists() throws InvalidPathException {
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
		when(timeseriesReferenceService.getTimeseriesReference(104L)).thenReturn(reference);
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
		when(timeseriesReferenceService.getTimeseriesReference(104L)).thenReturn(null);

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
		when(timeseriesReferenceService.getTimeseriesReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
	}

	@Test
	public void structuredData_exist() throws InvalidPathException {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(structuredDatasSeg);
		segments.add(structuredDataIdSeg);

		when(structuredDataIdSeg.getPath()).thenReturn("100");

		var container = new StructuredDataContainer(100);
		when(structuredDataContainerService.getStructuredDataContainer(100)).thenReturn(container);

		urlPathChecker.checkPathSegments(segments);
	}

	@Test
	public void structuredData_notFound() throws InvalidPathException {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(structuredDatasSeg);
		segments.add(structuredDataIdSeg);

		when(structuredDataIdSeg.getPath()).thenReturn("100");
		when(structuredDataContainerService.getStructuredDataContainer(100)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - Container does not exist", e.getMessage());
	}

	@Test
	public void structuredData_wrongUrl() throws InvalidPathException {
		List<PathSegment> segments = new ArrayList<>();

		segments.add(dummySeg);
		segments.add(dummyIdSeg);
		segments.add(structuredDatasSeg);
		segments.add(structuredDataIdSeg);

		when(structuredDataIdSeg.getPath()).thenReturn("200");

		urlPathChecker.checkPathSegments(segments);
		verify(structuredDataContainerService, never()).getStructuredDataContainer(200);
	}

	@Test
	public void structuredDataReference_exists() throws InvalidPathException {
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
		when(structuredDataReferenceService.getStructuredDataReference(104L)).thenReturn(reference);
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
		when(structuredDataReferenceService.getStructuredDataReference(104L)).thenReturn(null);

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
		when(structuredDataReferenceService.getStructuredDataReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
	}

	@Test
	public void file_exist() throws InvalidPathException {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(filesSeg);
		segments.add(fileIdSeg);

		when(fileIdSeg.getPath()).thenReturn("100");

		var container = new FileContainer(100);
		when(fileContainerService.getFileContainer(100)).thenReturn(container);

		urlPathChecker.checkPathSegments(segments);
	}

	@Test
	public void file_notFound() throws InvalidPathException {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(filesSeg);
		segments.add(fileIdSeg);

		when(fileIdSeg.getPath()).thenReturn("100");
		when(fileContainerService.getFileContainer(100)).thenReturn(null);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - Container does not exist", e.getMessage());
	}

	@Test
	public void file_wrongUrl() throws InvalidPathException {
		List<PathSegment> segments = new ArrayList<>();

		segments.add(dummySeg);
		segments.add(dummyIdSeg);
		segments.add(filesSeg);
		segments.add(fileIdSeg);

		when(fileIdSeg.getPath()).thenReturn("200");

		urlPathChecker.checkPathSegments(segments);
		verify(fileContainerService, never()).getFileContainer(200);
	}

	@Test
	public void fileReference_exists() throws InvalidPathException {
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
		when(fileReferenceService.getFileReference(104L)).thenReturn(reference);
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
		when(fileReferenceService.getFileReference(104L)).thenReturn(null);

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
		when(fileReferenceService.getFileReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
	}

	@Test
	public void uriReference_exists() throws InvalidPathException {
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
		when(uriReferenceService.getURIReference(104L)).thenReturn(reference);
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
		when(uriReferenceService.getURIReference(104L)).thenReturn(null);

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
		when(uriReferenceService.getURIReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
	}

	@Test
	public void collectionReference_exists() throws InvalidPathException {
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
		when(collectionReferenceService.getCollectionReference(104L)).thenReturn(reference);
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
		when(collectionReferenceService.getCollectionReference(104L)).thenReturn(null);

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
		when(collectionReferenceService.getCollectionReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
	}

	@Test
	public void dataObjectReference_exists() throws InvalidPathException {
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
		when(dataObjectReferenceService.getDataObjectReference(104L)).thenReturn(reference);
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
		when(dataObjectReferenceService.getDataObjectReference(104L)).thenReturn(null);

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
		when(dataObjectReferenceService.getDataObjectReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
	}

	@Test
	public void basicReference_exists() throws InvalidPathException {
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
		when(basicReferenceService.getBasicReference(104L)).thenReturn(reference);
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
		when(basicReferenceService.getBasicReference(104L)).thenReturn(null);

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
		when(basicReferenceService.getBasicReference(104L)).thenReturn(reference);

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("ID ERROR - There is no association between dataObject and reference", e.getMessage());
	}

	@Test
	public void emptyUrl() throws InvalidPathException {
		List<PathSegment> segments = new ArrayList<>();
		urlPathChecker.checkPathSegments(segments);
	}

	@Test
	public void getPathElements_slashPathSegment() throws InvalidPathException {
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
	public void getPathElements_slashPathSegmentEmpty() throws InvalidPathException {
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
	public void getPathElements_invalidNumber() throws InvalidPathException {
		List<PathSegment> segments = new ArrayList<>();
		segments.add(collectionsSeg);
		segments.add(collectionIdSeg);
		segments.add(dataObjectsSeg);
		when(collectionIdSeg.getPath()).thenReturn("abc");

		Exception e = assertThrows(InvalidPathException.class, () -> urlPathChecker.checkPathSegments(segments));
		assertEquals("The given path seems wrong", e.getMessage());
	}

}
