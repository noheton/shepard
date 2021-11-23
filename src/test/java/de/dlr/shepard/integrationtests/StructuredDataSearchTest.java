package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.search.QueryType;
import de.dlr.shepard.search.ResponseBody;
import de.dlr.shepard.search.ResultTriple;
import de.dlr.shepard.search.SearchBody;
import de.dlr.shepard.search.SearchParams;
import de.dlr.shepard.search.SearchScope;
import de.dlr.shepard.util.TraversalRules;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StructuredDataSearchTest extends BaseTestCaseIT {
	private static String containerURL;
	private static RequestSpecification containerRequestSpec;
	private static CollectionIO collection;
	private static DataObjectIO rootObject;
	private static DataObjectIO firstChild;
	private static DataObjectIO secondChild;
	private static DataObjectIO firstChildFirstGrandchild;
	private static DataObjectIO secondChildFirstGrandchild;
	private static DataObjectIO cyclicSuccessor;
	private static DataObjectIO firstSuccessor;
	private static StructuredDataContainerIO structuredDataContainer;
	private static StructuredDataContainerIO structuredDataContainerSuccessor;
	private static StructuredData structuredData;
	private static StructuredData structuredData1;
	private static StructuredData structuredDataSuccessor;
	private static StructuredDataPayload payload;
	private static StructuredDataPayload payload1;
	private static StructuredDataPayload payloadSuccessor;
	private static String structuredDataOID;
	private static String structuredDataOID1;
	private static String structuredDataOIDSuccessor;
	private static StructuredDataReferenceIO reference;
	private static StructuredDataReferenceIO reference1;
	private static StructuredDataReferenceIO referenceSuccessor;
	private static String searchURL;
	private static RequestSpecification searchRequestSpec;

	@BeforeAll
	public static void setUp() {
		collection = createCollection("SearchTestCollection");
		rootObject = createDataObject("RootDataObject", collection.getId());
		firstChild = createDataObjectWithParent("firstChild", collection.getId(), rootObject.getId());
		secondChild = createDataObjectWithParent("secondChild", collection.getId(), rootObject.getId());
		firstChildFirstGrandchild = createDataObjectWithParent("firstChildFirstGrandchild", collection.getId(),
				firstChild.getId());
		secondChildFirstGrandchild = createDataObjectWithParent("secondChildFirstGrandchild", collection.getId(),
				secondChild.getId());
		long[] firstPredecessorIDs = { firstChild.getId(), secondChild.getId() };
		firstSuccessor = createDataObjectWithPredecessors("firstSuccessor", collection.getId(), firstPredecessorIDs);
		structuredDataContainer = createDataContainer("DataContainer");
		structuredDataContainerSuccessor = createDataContainer("DataContainerSuccessor");
		cyclicSuccessor = createDataObjectWithPredecessors("cyclicSuccessor", collection.getId(),
				new long[] { firstSuccessor.getId() });
		long[] newPredecessorIDs = new long[firstSuccessor.getPredecessorIds().length + 1];
		for (int i = 0; i < firstSuccessor.getPredecessorIds().length; i++)
			newPredecessorIDs[i] = firstSuccessor.getPredecessorIds()[i];
		newPredecessorIDs[newPredecessorIDs.length - 1] = cyclicSuccessor.getId();
		firstSuccessor.setPredecessorIds(newPredecessorIDs);
		putDataObject(firstSuccessor.getId(), collection.getId(), firstSuccessor);
		// create and store first payload
		var structuredDataToCreate = new StructuredData();
		structuredDataToCreate.setName("StructuredData");
		payload = new StructuredDataPayload(structuredDataToCreate, "{\"number1\":3,\"number2\":456}");
		structuredData = createStructuredData(structuredDataToCreate, structuredDataContainer.getId(), payload);
		payload.setStructuredData(structuredData);
		structuredDataOID = structuredData.getOid();
		String[] dataOIDs = { structuredDataOID };
		reference = createStructuredDataReference("reference", dataOIDs, structuredDataContainer, secondChild);
		// create and store another payload
		var structuredDataToCreate1 = new StructuredData();
		structuredDataToCreate1.setName("StructuredData1");
		payload1 = new StructuredDataPayload(structuredDataToCreate, "{\"number1\":0,\"number2\":123}");
		structuredData1 = createStructuredData(structuredDataToCreate1, structuredDataContainer.getId(), payload1);
		payload1.setStructuredData(structuredData1);
		structuredDataOID1 = structuredData1.getOid();
		String[] dataOIDs1 = { structuredDataOID1 };
		reference1 = createStructuredDataReference("reference1", dataOIDs1, structuredDataContainer, firstChild);
		// create and store successor payload
		var structuredDataToCreateSuccessor = new StructuredData();
		structuredDataToCreateSuccessor.setName("StructuredDataSuccessor");
		payloadSuccessor = new StructuredDataPayload(structuredDataToCreateSuccessor,
				"{\"success\":0,\"number2\":123}");
		structuredDataSuccessor = createStructuredData(structuredDataToCreateSuccessor,
				structuredDataContainerSuccessor.getId(), payloadSuccessor);
		payloadSuccessor.setStructuredData(structuredDataSuccessor);
		structuredDataOIDSuccessor = structuredDataSuccessor.getOid();
		String[] dataOIDsSuccessor = { structuredDataOIDSuccessor };
		referenceSuccessor = createStructuredDataReference("referenceSucessor", dataOIDsSuccessor,
				structuredDataContainerSuccessor, firstSuccessor);
		// prepare search API calls
		searchURL = String.format("%s/search", baseURL);
		searchRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(searchURL)
				.addHeader("X-API-KEY", jws).build();
	}

	@Test
	@Order(1)
	public void testFindViaChildren() {
		SearchBody searchBody = new SearchBody();
		SearchScope searchScope = new SearchScope();
		searchScope.setCollectionId(collection.getId());
		searchScope.setDataObjectId(rootObject.getId());
		TraversalRules[] traversalRules = { TraversalRules.children };
		searchScope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { searchScope };
		searchBody.setScopes(scopes);
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.StructuredData);
		String query = "number1: {$gt: 1}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(result.getResultSet().length, 1);
		assertEquals(result.getResultSet()[0].getCollectionId(), collection.getId());
		assertEquals(result.getResultSet()[0].getDataObjectId(), secondChild.getId());
		assertEquals(result.getResultSet()[0].getReferenceId(), reference.getId());
		assertEquals(result.getSearchParams().getQuery(), query);
	}

	@Test
	@Order(2)
	public void testDoNotFindViaChildren() {
		SearchBody searchBody = new SearchBody();
		SearchScope searchScope = new SearchScope();
		searchScope.setCollectionId(collection.getId());
		searchScope.setDataObjectId(rootObject.getId());
		TraversalRules[] traversalRules = { TraversalRules.children };
		searchScope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { searchScope };
		searchBody.setScopes(scopes);
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.StructuredData);
		String query = "number1: {$lt: 0}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(result.getResultSet().length, 0);
	}

	@Test
	@Order(3)
	public void testFindViaPredecessor() {
		SearchBody searchBody = new SearchBody();
		SearchScope searchScope = new SearchScope();
		searchScope.setCollectionId(collection.getId());
		searchScope.setDataObjectId(firstSuccessor.getId());
		TraversalRules[] traversalRules = { TraversalRules.predecessors };
		searchScope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { searchScope };
		searchBody.setScopes(scopes);
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.StructuredData);
		String query = "number1: {$gt: 1}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(result.getResultSet().length, 1);
		assertEquals(result.getResultSet()[0].getCollectionId(), collection.getId());
		assertEquals(result.getResultSet()[0].getDataObjectId(), secondChild.getId());
		assertEquals(result.getResultSet()[0].getReferenceId(), reference.getId());
	}

	@Test
	@Order(4)
	public void testDoNotFindViaPredecessor() {
		SearchBody searchBody = new SearchBody();
		SearchScope searchScope = new SearchScope();
		searchScope.setCollectionId(collection.getId());
		searchScope.setDataObjectId(firstSuccessor.getId());
		TraversalRules[] traversalRules = { TraversalRules.predecessors };
		searchScope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { searchScope };
		searchBody.setScopes(scopes);
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.StructuredData);
		String query = "number1: {$lt: 0}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(result.getResultSet().length, 0);
	}

	@Test
	@Order(5)
	public void testFindViaParent() {
		SearchBody searchBody = new SearchBody();
		SearchScope searchScope = new SearchScope();
		searchScope.setCollectionId(collection.getId());
		searchScope.setDataObjectId(secondChildFirstGrandchild.getId());
		TraversalRules[] traversalRules = { TraversalRules.parents };
		searchScope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { searchScope };
		searchBody.setScopes(scopes);
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.StructuredData);
		String query = "number1: {$gt: 1}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(result.getResultSet().length, 1);
		assertEquals(result.getResultSet()[0].getCollectionId(), collection.getId());
		assertEquals(result.getResultSet()[0].getDataObjectId(), secondChild.getId());
		assertEquals(result.getResultSet()[0].getReferenceId(), reference.getId());
	}

	@Test
	@Order(6)
	public void testFindViaSuccessor() {
		SearchBody searchBody = new SearchBody();
		SearchScope searchScope = new SearchScope();
		searchScope.setCollectionId(collection.getId());
		searchScope.setDataObjectId(secondChild.getId());
		TraversalRules[] traversalRules = { TraversalRules.successors };
		searchScope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { searchScope };
		searchBody.setScopes(scopes);
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.StructuredData);
		String query = "success: {$eq: 0}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(result.getResultSet().length, 1);
		assertEquals(result.getResultSet()[0].getCollectionId(), collection.getId());
		assertEquals(result.getResultSet()[0].getDataObjectId(), firstSuccessor.getId());
		assertEquals(result.getResultSet()[0].getReferenceId(), referenceSuccessor.getId());
	}

	@Test
	@Order(7)
	public void testFindMultipleResults() {
		SearchBody searchBody = new SearchBody();
		SearchScope searchScope = new SearchScope();
		searchScope.setCollectionId(collection.getId());
		searchScope.setDataObjectId(rootObject.getId());
		TraversalRules[] traversalRules = { TraversalRules.children };
		searchScope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { searchScope };
		searchBody.setScopes(scopes);
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.StructuredData);
		String query = "number2: {$gt: 0}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(result.getResultSet().length, 2);
		HashSet<ResultTriple> resultTriples = new HashSet<ResultTriple>();
		resultTriples.add(result.getResultSet()[0]);
		resultTriples.add(result.getResultSet()[1]);
		ResultTriple expectedResult0 = new ResultTriple();
		expectedResult0.setCollectionId(collection.getId());
		expectedResult0.setDataObjectId(secondChild.getId());
		expectedResult0.setReferenceId(reference.getId());
		ResultTriple expectedResult1 = new ResultTriple();
		expectedResult1.setCollectionId(collection.getId());
		expectedResult1.setDataObjectId(firstChild.getId());
		expectedResult1.setReferenceId(reference1.getId());
		assertThat(resultTriples).containsExactlyInAnyOrder(expectedResult0, expectedResult1);
	}

	@Test
	@Order(8)
	public void testFindViaPredecessorCycle() {
		SearchBody searchBody = new SearchBody();
		SearchScope searchScope = new SearchScope();
		searchScope.setCollectionId(collection.getId());
		searchScope.setDataObjectId(cyclicSuccessor.getId());
		TraversalRules[] traversalRules = { TraversalRules.predecessors };
		searchScope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { searchScope };
		searchBody.setScopes(scopes);
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.StructuredData);
		String query = "success: {$eq: 0}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(result.getResultSet().length, 1);
	}

	@Test
	@Order(9)
	public void testDoNotFindViaDeletedNode() {
		deleteDataObject(secondChild);
		SearchBody searchBody = new SearchBody();
		SearchScope searchScope = new SearchScope();
		searchScope.setCollectionId(collection.getId());
		searchScope.setDataObjectId(rootObject.getId());
		TraversalRules[] traversalRules = { TraversalRules.children };
		searchScope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { searchScope };
		searchBody.setScopes(scopes);
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.StructuredData);
		String query = "number1: {$gt: 1}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(result.getResultSet().length, 0);
	}

	private static DataObjectIO createDataObjectWithParent(String name, long collectionId, long parentID) {
		var dataObjectsURL = String.format("%s/collections/%d/dataObjects/", baseURL, collectionId);
		var dataObjectSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(dataObjectsURL).addHeader("X-API-KEY", jws).build();
		DataObjectIO dataObjectIO = new DataObjectIO();
		dataObjectIO.setName(name);
		dataObjectIO.setParentId(parentID);
		var dataObject = given().spec(dataObjectSpecification).body(dataObjectIO).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);
		return dataObject;
	}

	private static DataObjectIO createDataObjectWithPredecessors(String name, long collectionId,
			long[] predecessorsIDs) {
		var dataObjectsURL = String.format("%s/collections/%d/dataObjects/", baseURL, collectionId);
		var dataObjectSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(dataObjectsURL).addHeader("X-API-KEY", jws).build();
		DataObjectIO dataObjectIO = new DataObjectIO();
		dataObjectIO.setName(name);
		dataObjectIO.setPredecessorIds(predecessorsIDs);
		var dataObject = given().spec(dataObjectSpecification).body(dataObjectIO).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);
		return dataObject;
	}

	private static StructuredDataContainerIO createDataContainer(String name) {
		containerURL = String.format("%s/structureddatas", baseURL);
		containerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(containerURL)
				.addHeader("X-API-KEY", jws).build();
		StructuredDataContainerIO containerToCreate = new StructuredDataContainerIO();
		containerToCreate.setName(name);
		return given().spec(containerRequestSpec).body(containerToCreate).when().post().then().statusCode(201).extract()
				.as(StructuredDataContainerIO.class);
	}

	private static StructuredData createStructuredData(String name, long containerID, StructuredDataPayload payload) {
		containerURL = String.format("%s/structureddatas", baseURL);
		StructuredData structuredDataToCreate = new StructuredData();
		structuredDataToCreate.setName(name);
		return given().spec(containerRequestSpec).body(payload).when()
				.post(String.format("%s/%d/payload", containerURL, containerID)).then().statusCode(201).extract()
				.as(StructuredData.class);
	}

	private static StructuredData createStructuredData(StructuredData structuredDataToCreate, long containerID,
			StructuredDataPayload payload) {
		containerURL = String.format("%s/structureddatas", baseURL);
		return given().spec(containerRequestSpec).body(payload).when()
				.post(String.format("%s/%d/payload", containerURL, containerID)).then().statusCode(201).extract()
				.as(StructuredData.class);
	}

	private static StructuredDataReferenceIO createStructuredDataReference(String name, String[] structuredDataOIDs,
			StructuredDataContainerIO container, DataObjectIO dataObject) {
		StructuredDataReferenceIO toCreate = new StructuredDataReferenceIO();
		toCreate.setName(name);
		toCreate.setStructuredDataOids(structuredDataOIDs);
		toCreate.setStructuredDataContainerId(container.getId());
		String referencesURL = String.format("%s/collections/%d/dataObjects/%d/structureddataReferences", baseURL,
				collection.getId(), dataObject.getId());
		RequestSpecification referencesRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(referencesURL).addHeader("X-API-KEY", jws).build();
		return given().spec(referencesRequestSpec).body(toCreate).when().post().then().statusCode(201).extract()
				.as(StructuredDataReferenceIO.class);
	}

	private static void deleteDataObject(DataObjectIO dataObject) {
		String dataObjectsURL = String.format("%s/collections/%d/dataObjects", baseURL, collection.getId());
		RequestSpecification dataObjectRequestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(dataObjectsURL).addHeader("X-API-KEY", jws).build();
		given().spec(dataObjectRequestSpecification).when().delete(dataObjectsURL + "/" + dataObject.getId()).then()
				.statusCode(204);
	}

	private static void putDataObject(Long dataObjectToChangeID, Long collectionID, DataObjectIO changedDataObject) {
		String putURL = String.format("%s/collections/%d/dataObjects", baseURL, collection.getId());
		putURL = putURL + "/" + dataObjectToChangeID;
		RequestSpecification putSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(putURL).addHeader("X-API-KEY", jws).build();
		DataObjectIO changeResult = given().spec(putSpecification).body(changedDataObject).when().put(putURL).then()
				.statusCode(200).extract().as(DataObjectIO.class);
	}

}
