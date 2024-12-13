package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.CollectionReferenceIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.search.unified.QueryType;
import de.dlr.shepard.search.unified.ResponseBody;
import de.dlr.shepard.search.unified.ResultTriple;
import de.dlr.shepard.search.unified.SearchBody;
import de.dlr.shepard.search.unified.SearchParams;
import de.dlr.shepard.search.unified.SearchScope;
import de.dlr.shepard.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.timeseries.model.Timeseries;
import de.dlr.shepard.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.TraversalRules;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReferenceSearcherIT extends BaseTestCaseIT {

  private static DataObjectIO dataObjectIO1;
  private static DataObjectIO dataObjectIO2;
  private static DataObjectIO dataObjectIO3;
  private static DataObjectIO dataObjectIO4;
  private static DataObjectIO collection1DataObject;
  private static DataObjectIO referenced;
  private static CollectionIO collection;
  private static CollectionIO collection1;
  private static String searchURL;
  private static RequestSpecification searchRequestSpec;
  private static String dataObjetReferencesURL;
  private static RequestSpecification dataObjectReferenceRequestSpecification;
  private static RequestSpecification fileReferenceRequestSpecification;
  private static RequestSpecification fileContainerRequestSpecification;
  private static RequestSpecification fileRequestSpecification;
  private static BasicReferenceIO referenceIO1;
  private static BasicReferenceIO referenceIO1a;
  private static BasicReferenceIO referenceIO4;
  private static String fileReferencesURL;
  private static String fileContainerURL;
  private static FileContainerIO fileContainerIO;
  private static ShepardFile file;
  private static FileReferenceIO fileReferenceIO;

  private static String sDataReferencesURL;
  private static RequestSpecification sDataReferencesRequestSpec;
  private static String sDataContainerURL;
  private static RequestSpecification sDataContainerRequestSpec;
  private static StructuredDataContainerIO sDataContainer;
  private static CollectionIO sDataCollection;
  private static DataObjectIO sDataObject;
  private static StructuredDataPayload sDataPayload;

  private static CollectionIO tSerCollection;
  private static DataObjectIO tSerDataObject;
  private static String tSerReferencesURL;
  private static RequestSpecification tSerReferencesRequestSpec;
  private static String tSerContainerURL;
  private static RequestSpecification tSerContainerRequestSpec;
  private static TimeseriesContainerIO tSerContainer;
  private static TimeseriesReferenceIO tSerReference;
  private static TimeseriesWithDataPoints timeseriesWithDataPoints;
  private static int numPoints = 32;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("DataObjectReferenceTestCollection");
    collection1 = createCollection("ReferencedSearcherTestCollection1");
    dataObjectIO1 = createDataObject("DataObjectReference1", collection.getId());
    dataObjectIO2 = createDataObject("DataObjectReference2", collection.getId());
    DataObjectIO dataObjectIO3ToCreate = new DataObjectIO();
    dataObjectIO3ToCreate.setCollectionId(collection.getId());
    dataObjectIO3ToCreate.setName("dataObject3");
    dataObjectIO3ToCreate.setPredecessorIds(new long[] { dataObjectIO1.getId() });
    dataObjectIO3 = createDataObject(dataObjectIO3ToCreate, jws);
    DataObjectIO dataObjectIO4ToCreate = new DataObjectIO();
    dataObjectIO4ToCreate.setCollectionId(collection.getId());
    dataObjectIO4ToCreate.setName("dataObject4");
    dataObjectIO4ToCreate.setPredecessorIds(new long[] { dataObjectIO3.getId() });
    dataObjectIO4 = createDataObject(dataObjectIO4ToCreate, jws);
    referenced = createDataObject("ReferencedDataObject", collection.getId());
    collection1DataObject = createDataObject("collection1DataObject", collection1.getId());

    var toCreate1 = new DataObjectReferenceIO();
    toCreate1.setName("DataObjectReferenceDummy1");
    toCreate1.setRelationship("integrationtests");
    toCreate1.setReferencedDataObjectId(referenced.getId());
    dataObjetReferencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATA_OBJECTS,
      dataObjectIO1.getId(),
      Constants.DATAOBJECT_REFERENCES
    );
    dataObjectReferenceRequestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    referenceIO1 = given()
      .spec(dataObjectReferenceRequestSpecification)
      .body(toCreate1)
      .when()
      .post(dataObjetReferencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectReferenceIO.class);

    var toCreate1a = new DataObjectReferenceIO();
    toCreate1a.setName("DataObjectReferenceDummy1a");
    toCreate1a.setRelationship("integrationtests");
    toCreate1a.setReferencedDataObjectId(referenced.getId());
    referenceIO1a = given()
      .spec(dataObjectReferenceRequestSpecification)
      .body(toCreate1a)
      .when()
      .post(dataObjetReferencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectReferenceIO.class);
    searchURL = "/" + Constants.SEARCH;
    searchRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).addHeader("X-API-KEY", jws).build();

    var toCreate4 = new DataObjectReferenceIO();
    toCreate4.setName("DataObjectReferenceDummy4");
    toCreate4.setRelationship("integrationtests");
    toCreate4.setReferencedDataObjectId(dataObjectIO4.getId());
    dataObjetReferencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATA_OBJECTS,
      dataObjectIO4.getId(),
      Constants.DATAOBJECT_REFERENCES
    );
    dataObjectReferenceRequestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    referenceIO4 = given()
      .spec(dataObjectReferenceRequestSpecification)
      .body(toCreate4)
      .when()
      .post(dataObjetReferencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectReferenceIO.class);

    fileReferencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATA_OBJECTS,
      dataObjectIO2.getId(),
      Constants.FILE_REFERENCES
    );
    fileReferenceRequestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    fileContainerURL = "/" + Constants.FILE_CONTAINERS;
    fileContainerRequestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    var fileContainerToCreate = new FileContainerIO();
    fileContainerToCreate.setName("FileContainer");
    InputStream targetStream = new ByteArrayInputStream("Hello World!".getBytes());
    fileContainerIO = given()
      .spec(fileContainerRequestSpecification)
      .body(fileContainerToCreate)
      .when()
      .post(fileContainerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(FileContainerIO.class);
    fileRequestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.MULTIPART)
      .addHeader("X-API-KEY", jws)
      .build();
    file = given()
      .spec(fileRequestSpecification)
      .multiPart("file", "test.txt", targetStream)
      .when()
      .post(String.format("%s/%d/%s", fileContainerURL, fileContainerIO.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(201)
      .extract()
      .as(ShepardFile.class);
    var fileReferenceToCreate = new FileReferenceIO();
    fileReferenceToCreate.setName("FileReferenceDummy");
    fileReferenceToCreate.setFileOids(new String[] { file.getOid() });
    fileReferenceToCreate.setFileContainerId(fileContainerIO.getId());
    fileReferenceIO = given()
      .spec(fileReferenceRequestSpecification)
      .body(fileReferenceToCreate)
      .when()
      .post(fileReferencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(FileReferenceIO.class);

    searchURL = "/" + Constants.SEARCH;
    searchRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).addHeader("X-API-KEY", jws).build();
  }

  @Test
  @Order(1)
  public void findReferenceWithoutTraveralRulesTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setDataObjectId(dataObjectIO1.getId());
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Reference);
    String query = String.format(
      """
      {
            "property": "id",
            "value": %d,
            "operator": "eq"
          }
      """,
      referenceIO1.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple1 = new ResultTriple(collection.getId(), dataObjectIO1.getId(), referenceIO1.getId());
    assertThat(result.getResultSet()).contains(triple1);
    assertThat(result.getResults()[0].getId()).isEqualTo(referenceIO1.getId());
    assertThat(result.getSearchParams()).isEqualTo(searchParams);
  }

  @Test
  @Order(2)
  public void findNoReferenceWithoutTraveralRulesTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setDataObjectId(dataObjectIO2.getId());
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Reference);
    String query = String.format(
      """
      	{
            "property": "id",
            "value": %d,
            "operator": "eq"
          }
      """,
      referenceIO1.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    assertEquals(0, result.getResultSet().length);
  }

  @Test
  @Order(3)
  public void findTwoReferencesWithoutTraveralRulesTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setDataObjectId(dataObjectIO1.getId());
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Reference);
    String query =
      """
      	{
            "property": "name",
            "value": "ummy",
            "operator": "contains"
          }
      """;
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple1 = new ResultTriple(collection.getId(), dataObjectIO1.getId(), referenceIO1.getId());
    ResultTriple triple1a = new ResultTriple(collection.getId(), dataObjectIO1.getId(), referenceIO1a.getId());
    assertThat(result.getResultSet()).contains(triple1, triple1a);
  }

  @Test
  @Order(4)
  public void findReferenceViaSuccessorsTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setDataObjectId(dataObjectIO1.getId());
    searchScope.setTraversalRules(new TraversalRules[] { TraversalRules.successors });
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Reference);
    String query =
      """
      	{
            "property": "name",
            "value": "ummy",
            "operator": "contains"
          }
      """;
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple4 = new ResultTriple(collection.getId(), dataObjectIO4.getId(), referenceIO4.getId());
    assertThat(result.getResultSet()).contains(triple4);
  }

  @Test
  @Order(5)
  public void findViaReferencedDataObjectTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Reference);
    String query = String.format(
      """
      {
            "property": "referencedDataObjectId",
            "value": %d,
            "operator": "eq"
          }
      """,
      dataObjectIO4.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple4 = new ResultTriple(collection.getId(), dataObjectIO4.getId(), referenceIO4.getId());
    assertThat(result.getResultSet()).contains(triple4);
  }

  @Test
  @Order(6)
  public void findViaFileContainerTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Reference);
    String query = String.format(
      """
      {
            "property": "fileContainerId",
            "value": %d,
            "operator": "eq"
          }
      """,
      fileContainerIO.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple2 = new ResultTriple(collection.getId(), dataObjectIO2.getId(), fileReferenceIO.getId());
    assertThat(result.getResultSet()).contains(triple2);
  }

  @Test
  @Order(7)
  public void findViaReferencedCollectionTest() {
    CollectionReferenceIO toCreate = new CollectionReferenceIO();
    toCreate.setName("CollectionReferenceDummy");
    toCreate.setRelationship("integrationtests");
    toCreate.setReferencedCollectionId(collection1.getId());
    String referencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection1.getId(),
      Constants.DATA_OBJECTS,
      collection1DataObject.getId(),
      Constants.COLLECTION_REFERENCES
    );
    RequestSpecification collectionReferenceRequestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    CollectionReferenceIO createdCollectionReference = given()
      .spec(collectionReferenceRequestSpecification)
      .body(toCreate)
      .when()
      .post(referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionReferenceIO.class);

    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Reference);
    String query = String.format(
      """
      {
            "property": "referencedCollectionId",
            "value": %d,
            "operator": "eq"
          }
      """,
      collection1.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple2 = new ResultTriple(
      collection1.getId(),
      collection1DataObject.getId(),
      createdCollectionReference.getId()
    );
    assertThat(result.getResultSet()).contains(triple2);
  }

  @Test
  @Order(8)
  public void dontFindViaReferencedCollectionTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Reference);
    String query = String.format(
      """
      {
            "property": "referencedCollectionId",
            "value": %d,
            "operator": "eq"
          }
      """,
      collection.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    assertEquals(0, result.getResultSet().length);
  }

  @Test
  @Order(9)
  public void findViaStructuredDataTest() {
    sDataCollection = createCollection("StructuredDataReferenceTestCollection");
    sDataObject = createDataObject("StructuredDataReferenceTestDataObject", sDataCollection.getId());
    sDataReferencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      sDataCollection.getId(),
      Constants.DATA_OBJECTS,
      sDataObject.getId(),
      Constants.STRUCTURED_DATA_REFERENCES
    );
    sDataReferencesRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    sDataContainerURL = "/" + Constants.STRUCTURED_DATA_CONTAINERS;
    sDataContainerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    StructuredDataContainerIO sDataContainerToCreate = new StructuredDataContainerIO();
    sDataContainerToCreate.setName("StructuredDataContainer");
    sDataContainer = given()
      .spec(sDataContainerRequestSpec)
      .body(sDataContainerToCreate)
      .when()
      .post(sDataContainerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredDataContainerIO.class);
    StructuredData structuredData = new StructuredData();
    structuredData.setName("My Structured Data");
    sDataPayload = new StructuredDataPayload(
      structuredData,
      "{\"Hallo\":\"Welt\",\"number\":123,\"list\":[\"a\",\"b\"],\"object\":{\"a\":\"b\"}}"
    );
    StructuredData actual = given()
      .spec(sDataContainerRequestSpec)
      .body(sDataPayload)
      .when()
      .post(String.format("%s/%d/%s", sDataContainerURL, sDataContainer.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredData.class);
    sDataPayload.setStructuredData(actual);
    StructuredDataReferenceIO sDataReferenceToCreate = new StructuredDataReferenceIO();
    sDataReferenceToCreate.setName("StructuredDataReferenceDummy");
    sDataReferenceToCreate.setStructuredDataOids(new String[] { sDataPayload.getStructuredData().getOid() });
    sDataReferenceToCreate.setStructuredDataContainerId(sDataContainer.getId());
    StructuredDataReferenceIO sDataReference = given()
      .spec(sDataReferencesRequestSpec)
      .body(sDataReferenceToCreate)
      .when()
      .post(sDataReferencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredDataReferenceIO.class);

    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Reference);
    String query = String.format(
      """
      {
            "property": "structuredDataContainerId",
            "value": %d,
            "operator": "eq"
          }
      """,
      sDataContainer.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple = new ResultTriple(sDataCollection.getId(), sDataObject.getId(), sDataReference.getId());
    assertThat(result.getResultSet()).contains(triple);
  }

  @Test
  @Order(10)
  public void findViaTimeseriesTest() {
    tSerCollection = createCollection("TimeseriesReferenceSearchTestCollection");
    tSerDataObject = createDataObject("TimeseriesReferenceSearchTestDataObject", tSerCollection.getId());
    tSerReferencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      tSerCollection.getId(),
      Constants.DATA_OBJECTS,
      tSerDataObject.getId(),
      Constants.TIMESERIES_REFERENCES
    );
    tSerReferencesRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    tSerContainerURL = "/" + Constants.TIMESERIES_CONTAINERS;
    tSerContainerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    var tSerContainerToCreate = new TimeseriesContainerIO();
    tSerContainerToCreate.setName("TimeseriesContainer");
    tSerContainer = given()
      .spec(tSerContainerRequestSpec)
      .body(tSerContainerToCreate)
      .when()
      .post(tSerContainerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(TimeseriesContainerIO.class);
    var currentTime = System.currentTimeMillis() * 1000000;
    var slice = (2f * Math.PI) / (numPoints - 1);
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>();
    for (int i = 0; i < numPoints; i++) {
      var offset = i * 1000000000L;
      var point = new TimeseriesDataPoint(currentTime + offset, Math.sin(slice * i));
      dataPoints.add(point);
    }
    timeseriesWithDataPoints = new TimeseriesWithDataPoints(
      new Timeseries("meas", "dev", "loc", "symName", "field"),
      dataPoints
    );

    given()
      .spec(tSerContainerRequestSpec)
      .body(timeseriesWithDataPoints)
      .when()
      .post(String.format("%s/%d/%s", tSerContainerURL, tSerContainer.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(201);
    var nanos = timeseriesWithDataPoints.getPoints().get(0).getTimestamp();
    var tSerReferenceToCreate = new TimeseriesReferenceIO();
    tSerReferenceToCreate.setName("TimeseriesReferenceDummy");
    tSerReferenceToCreate.setStart(nanos - 1000000000L);
    tSerReferenceToCreate.setEnd(nanos + 1000000000L * numPoints);
    tSerReferenceToCreate.setReferencedTimeseriesList(
      List.of(new ReferencedTimeseriesNodeEntity(timeseriesWithDataPoints.getTimeseries()))
    );
    tSerReferenceToCreate.setTimeseriesContainerId(tSerContainer.getId());
    tSerReference = given()
      .spec(tSerReferencesRequestSpec)
      .body(tSerReferenceToCreate)
      .when()
      .post(tSerReferencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(TimeseriesReferenceIO.class);

    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Reference);
    String query = String.format(
      """
      {
            "property": "timeseriesContainerId",
            "value": %d,
            "operator": "eq"
          }
      """,
      tSerContainer.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(searchRequestSpec)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple = new ResultTriple(tSerCollection.getId(), tSerDataObject.getId(), tSerReference.getId());
    assertThat(result.getResultSet()).contains(triple);
  }

  private static DataObjectIO createDataObject(DataObjectIO dataObjectIO, String jws) {
    var dataObjectsURL = String.format(
      "/%s/%d/%s/",
      Constants.COLLECTIONS,
      dataObjectIO.getCollectionId(),
      Constants.DATA_OBJECTS
    );
    var dataObjectSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    var createdDataObject = given()
      .spec(dataObjectSpecification)
      .body(dataObjectIO)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    return createdDataObject;
  }
}
