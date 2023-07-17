package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
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
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.neo4Core.io.UserGroupIO;
import de.dlr.shepard.search.unified.QueryType;
import de.dlr.shepard.search.unified.ResponseBody;
import de.dlr.shepard.search.unified.ResultTriple;
import de.dlr.shepard.search.unified.SearchBody;
import de.dlr.shepard.search.unified.SearchParams;
import de.dlr.shepard.search.unified.SearchScope;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.TraversalRules;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StructuredDataSearcherTest extends BaseTestCaseIT {
	private static String containerURL;
	private static RequestSpecification containerRequestSpec;
	private static CollectionIO collection;
	private static DataObjectIO rootObject;
	private static DataObjectIO firstChild;
	private static DataObjectIO secondChild;
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
	private static UserWithApiKey user1;
	private static String jws1;
	private static RequestSpecification searchRequestSpec1;
	private static UserWithApiKey user2;
	private static String jws2;
	private static RequestSpecification searchRequestSpec2;

	@BeforeAll
	public static void setUp() {
		collection = createCollection("SearchTestCollection");
		rootObject = createDataObject("RootDataObject", collection.getId());
		firstChild = createDataObjectWithParent("firstChild", collection.getId(), rootObject.getId());
		secondChild = createDataObjectWithParent("secondChild", collection.getId(), rootObject.getId());
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
		user1 = getNewUserWithApiKey("user1" + System.currentTimeMillis());
		jws1 = user1.getApiKey().getJws();
		searchRequestSpec1 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(searchURL)
				.addHeader("X-API-KEY", jws1).build();
		user2 = getNewUserWithApiKey("user2" + System.currentTimeMillis());
		jws2 = user2.getApiKey().getJws();
		searchRequestSpec2 = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(searchURL)
				.addHeader("X-API-KEY", jws2).build();
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
		ResultTriple triple = new ResultTriple(collection.getId(), secondChild.getId(), reference.getId());
		assertThat(result.getResultSet()).containsExactly(triple);
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
		assertThat(Arrays.asList(result.getResults()).stream().map(elem -> elem.getId()))
				.containsExactlyInAnyOrder(reference.getId());
	}

	@Test
	@Order(2)
	public void testFindWithoutDataObjectId() {
		SearchBody searchBody = new SearchBody();
		SearchScope searchScope = new SearchScope();
		searchScope.setCollectionId(collection.getId());
		TraversalRules[] traversalRules = {};
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
		ResultTriple triple = new ResultTriple(collection.getId(), secondChild.getId(), reference.getId());
		assertThat(result.getResultSet()).containsExactly(triple);
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
		assertThat(Arrays.asList(result.getResults()).stream().map(elem -> elem.getId()))
				.containsExactlyInAnyOrder(reference.getId());
	}

	@Test
	@Order(3)
	public void testFindViaChildrenUniversalSyntaxAND() {
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
		String query = "{\"AND\": [{\"property\": \"number1\", \"value\": 3, \"operator\": \"ge\"},{\"property\": \"number1\",\"value\": 3, \"operator\": \"le\"}]}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		ResultTriple triple = new ResultTriple(collection.getId(), secondChild.getId(), reference.getId());
		assertThat(result.getResultSet()).containsExactly(triple);
		assertEquals(query, result.getSearchParams().getQuery());
		assertThat(Arrays.asList(result.getResults()).stream().map(elem -> elem.getId()))
				.containsExactlyInAnyOrder(reference.getId());
	}

	@Test
	@Order(4)
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
		assertEquals(0, result.getResultSet().length);
		assertEquals(0, result.getResults().length);
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
	}

	@Test
	@Order(5)
	public void testFindViaChildrenUniversalSyntaxOR() {
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
		String query = "{\"OR\": [{\"property\": \"number1\", \"value\": 3, \"operator\": \"ge\"},"
				+ "{\"property\": \"number1\",\"value\": 3, \"operator\": \"le\"}]}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(2, result.getResultSet().length);
		ResultTriple triple = new ResultTriple(collection.getId(), secondChild.getId(), reference.getId());
		ResultTriple triple1 = new ResultTriple(collection.getId(), firstChild.getId(), reference1.getId());
		assertThat(result.getResultSet()).contains(triple);
		assertThat(result.getResultSet()).contains(triple1);
		assertEquals(query, result.getSearchParams().getQuery());
		assertThat(Arrays.asList(result.getResults()).stream().map(elem -> elem.getId()))
				.containsExactlyInAnyOrder(reference.getId(), reference1.getId());
	}

	@Test
	@Order(6)
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
		ResultTriple triple = new ResultTriple(collection.getId(), secondChild.getId(), reference.getId());
		assertThat(result.getResultSet()).containsExactly(triple);
		assertThat(Arrays.asList(result.getResults()).stream().map(elem -> elem.getId()))
				.containsExactlyInAnyOrder(reference.getId());
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
	}

	@Test
	@Order(7)
	public void testFindViaChildrenUniversalSyntaxNOT() {
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
		String query = "{\"NOT\": {\"property\": \"number1\", \"value\": 3, \"operator\": \"eq\"}}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(1, result.getResultSet().length);
		ResultTriple triple1 = new ResultTriple(collection.getId(), firstChild.getId(), reference1.getId());
		assertThat(result.getResultSet()).contains(triple1);
		assertEquals(query, result.getSearchParams().getQuery());
		assertThat(Arrays.asList(result.getResults()).stream().map(elem -> elem.getId())).contains(reference1.getId());
	}

	@Test
	@Order(8)
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
		assertEquals(0, result.getResultSet().length);
		assertEquals(0, result.getResults().length);
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
	}

	@Test
	@Order(9)
	public void testFindViaChildrenUniversalSyntaxDeMorgan() {
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
		String query = "{\"NOT\": {\"OR\": [{\"property\": \"number1\", \"value\": 4, \"operator\": \"gt\"},"
				+ " {\"property\": \"number1\", \"value\": 1, \"operator\": \"lt\"}]}}";
		searchParams.setQuery(query);
		searchBody.setSearchParams(searchParams);
		var result = given().spec(searchRequestSpec).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(1, result.getResultSet().length);
		ResultTriple triple = new ResultTriple(collection.getId(), secondChild.getId(), reference.getId());
		assertThat(result.getResultSet()).contains(triple);
		assertEquals(query, result.getSearchParams().getQuery());
		assertThat(Arrays.asList(result.getResults()).stream().map(elem -> elem.getId())).contains(reference.getId());
	}

	@Test
	@Order(10)
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
		ResultTriple triple = new ResultTriple(collection.getId(), secondChild.getId(), reference.getId());
		assertThat(result.getResultSet()).containsExactly(triple);
		assertThat(Arrays.asList(result.getResults()).stream().map(elem -> elem.getId()))
				.containsExactlyInAnyOrder(reference.getId());
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
	}

	@Test
	@Order(11)
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
		ResultTriple triple = new ResultTriple(collection.getId(), firstSuccessor.getId(), referenceSuccessor.getId());
		assertThat(result.getResultSet()).containsExactly(triple);
		assertThat(Arrays.asList(result.getResults()).stream().map(elem -> elem.getId()))
				.containsExactlyInAnyOrder(referenceSuccessor.getId());
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
	}

	@Test
	@Order(12)
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
		assertEquals(2, result.getResultSet().length);
		HashSet<ResultTriple> resultTriples = new HashSet<>();
		resultTriples.add(result.getResultSet()[0]);
		resultTriples.add(result.getResultSet()[1]);
		ResultTriple expectedResult0 = new ResultTriple(collection.getId(), secondChild.getId(), reference.getId());
		ResultTriple expectedResult1 = new ResultTriple(collection.getId(), firstChild.getId(), reference1.getId());
		assertThat(resultTriples).containsExactlyInAnyOrder(expectedResult0, expectedResult1);
		assertThat(Arrays.asList(result.getResults()).stream().map(elem -> elem.getId()))
				.containsExactlyInAnyOrder(reference.getId(), reference1.getId());
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
	}

	@Test
	@Order(13)
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
		assertEquals(1, result.getResultSet().length);
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
	}

	@Test
	@Order(14)
	public void testFindViaPredecessorCycleUnauthorized() {
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
		var result = given().spec(searchRequestSpec1).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(0, result.getResultSet().length);
		assertEquals(0, result.getResults().length);
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
	}

	@Test
	@Order(15)
	public void testFindViaPredecessorCyclePermissionsReader() {
		String permissionsURL = baseURL + "/collections/" + collection.getId() + "/permissions";
		RequestSpecification permissionsSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(permissionsURL).addHeader("X-API-KEY", jws).build();
		PermissionsIO permissions = given().spec(permissionsSpecification).when().get(permissionsURL).then()
				.statusCode(200).extract().as(PermissionsIO.class);
		String[] reader = { user1.getUser().getUsername() };
		permissions.setReader(reader);
		given().spec(permissionsSpecification).body(permissions).when().put(permissionsURL).then().statusCode(200)
				.extract().as(PermissionsIO.class);
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
		var result = given().spec(searchRequestSpec1).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(1, result.getResultSet().length);
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
	}

	@Test
	@Order(16)
	public void testFindViaPredecessorCycleReaderGroup() {
		String userGroupURL = String.format("%s/usergroup", baseURL);
		UserGroupIO userGroup = new UserGroupIO();
		userGroup.setName("userGroup");
		userGroup.setUsernames(new String[] { user2.getUser().getUsername() });
		RequestSpecification userGroupSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(userGroupURL).addHeader("X-API-KEY", jws).build();
		UserGroupIO userGroupCreated = given().spec(userGroupSpecification).body(userGroup).when().post().then()
				.statusCode(201).extract().as(UserGroupIO.class);

		String permissionsURL = baseURL + "/" + Constants.COLLECTIONS + "/" + collection.getId() + "/"
				+ Constants.PERMISSIONS;
		RequestSpecification permissionsSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(permissionsURL).addHeader("X-API-KEY", jws).build();
		PermissionsIO permissions = given().spec(permissionsSpecification).when().get(permissionsURL).then()
				.statusCode(200).extract().as(PermissionsIO.class);
		long[] readerGroupsIds = { userGroupCreated.getId() };
		permissions.setReaderGroupIds(readerGroupsIds);
		given().spec(permissionsSpecification).body(permissions).when().put(permissionsURL).then().statusCode(200)
				.extract().as(PermissionsIO.class);
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
		var result = given().spec(searchRequestSpec2).body(searchBody).when().post().then().statusCode(200).extract()
				.as(ResponseBody.class);
		assertEquals(1, result.getResultSet().length);
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
	}

	@Test
	@Order(17)
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
		assertEquals(0, result.getResultSet().length);
		assertThat(result.getSearchParams()).isEqualTo(searchParams);
	}

	private static DataObjectIO createDataObjectWithParent(String name, long collectionId, long parentID) {
		var dataObjectsURL = String.format("%s/%s/%d/%s/", baseURL, Constants.COLLECTIONS, collectionId,
				Constants.DATAOBJECTS);
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
		var dataObjectsURL = String.format("%s/%s/%d/%s/", baseURL, Constants.COLLECTIONS, collectionId,
				Constants.DATAOBJECTS);
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
		containerURL = String.format("%s/%s", baseURL, Constants.STRUCTUREDDATAS);
		containerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(containerURL)
				.addHeader("X-API-KEY", jws).build();
		StructuredDataContainerIO containerToCreate = new StructuredDataContainerIO();
		containerToCreate.setName(name);
		return given().spec(containerRequestSpec).body(containerToCreate).when().post().then().statusCode(201).extract()
				.as(StructuredDataContainerIO.class);
	}

	private static StructuredData createStructuredData(StructuredData structuredDataToCreate, long containerID,
			StructuredDataPayload payload) {
		containerURL = String.format("%s/%s", baseURL, Constants.STRUCTUREDDATAS);
		return given().spec(containerRequestSpec).body(payload).when()
				.post(String.format("%s/%d/%s", containerURL, containerID, Constants.PAYLOAD)).then().statusCode(201)
				.extract().as(StructuredData.class);
	}

	private static StructuredDataReferenceIO createStructuredDataReference(String name, String[] structuredDataOIDs,
			StructuredDataContainerIO container, DataObjectIO dataObject) {
		StructuredDataReferenceIO toCreate = new StructuredDataReferenceIO();
		toCreate.setName(name);
		toCreate.setStructuredDataOids(structuredDataOIDs);
		toCreate.setStructuredDataContainerId(container.getId());
		String referencesURL = String.format("%s/%s/%d/%s/%d/%s", baseURL, Constants.COLLECTIONS, collection.getId(),
				Constants.DATAOBJECTS, dataObject.getId(), Constants.STRUCTUREDDATA_REFERENCES);
		RequestSpecification referencesRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(referencesURL).addHeader("X-API-KEY", jws).build();
		return given().spec(referencesRequestSpec).body(toCreate).when().post().then().statusCode(201).extract()
				.as(StructuredDataReferenceIO.class);
	}

	private static void deleteDataObject(DataObjectIO dataObject) {
		String dataObjectsURL = String.format("%s/%s/%d/%s", baseURL, Constants.COLLECTIONS, collection.getId(),
				Constants.DATAOBJECTS);
		RequestSpecification dataObjectRequestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(dataObjectsURL).addHeader("X-API-KEY", jws).build();
		given().spec(dataObjectRequestSpecification).when().delete(dataObjectsURL + "/" + dataObject.getId()).then()
				.statusCode(204);
	}

	private static void putDataObject(Long dataObjectToChangeID, Long collectionID, DataObjectIO changedDataObject) {
		String putURL = String.format("%s/%s/%d/%s", baseURL, Constants.COLLECTIONS, collection.getId(),
				Constants.DATAOBJECTS);
		putURL = putURL + "/" + dataObjectToChangeID;
		RequestSpecification putSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(putURL).addHeader("X-API-KEY", jws).build();
		given().spec(putSpecification).body(changedDataObject).when().put(putURL).then().statusCode(200);
	}

}
