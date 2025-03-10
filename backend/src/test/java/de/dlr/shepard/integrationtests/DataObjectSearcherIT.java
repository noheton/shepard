package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.common.search.io.QueryType;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.ResultTriple;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.io.SearchParams;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.TraversalRules;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataObjectSearcherIT extends BaseTestCaseIT {

  private static DataObjectIO dataObjectIO1;
  private static DataObjectIO dataObjectIO2;
  private static DataObjectIO dataObjectIO3;
  private static DataObjectIO dataObjectIO4;
  private static String dataObjectsURL;
  private static CollectionIO collection;
  private static String searchURL;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("DataObjectSearcherTestCollection");
    dataObjectsURL = String.format("/%s/%d/%s", Constants.COLLECTIONS, collection.getId(), Constants.DATA_OBJECTS);

    searchURL = "/" + Constants.SEARCH;
    var payload1 = new DataObjectIO();
    payload1.setName("DataObjectSearchDummy1");
    payload1.setDescription("description1");
    payload1.setAttributes(Map.of("a", "1", "b", "2"));
    dataObjectIO1 = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload1)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    var payload2 = new DataObjectIO();
    payload2.setName("DataObjectSearchDummy2");
    payload2.setDescription("description2");
    payload2.setAttributes(Map.of("abc", "1", "bcd", "233"));
    dataObjectIO2 = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload2)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    dataObjectIO3 = createDataObjectWithParent("DataObjectSearchDummy3", collection.getId(), dataObjectIO1.getId());
    dataObjectIO4 = createDataObjectWithParent("DataObjectSearchDummy4", collection.getId(), dataObjectIO3.getId());
  }

  @Test
  @Order(1)
  public void findOneDataObjectWithOrTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.DataObject);
    String query = String.format(
      """
      {
        "OR": [
          {
            "property": "id",
            "value": %d,
            "operator": "eq"
          },
          {
            "property": "number",
            "value": 123,
            "operator": "le"
          }
        ]
      }""",
      dataObjectIO1.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(requestSpecOfDefaultUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple1 = new ResultTriple(collection.getId(), dataObjectIO1.getId(), null);
    assertThat(result.getResultSet()).contains(triple1);
    assertThat(result.getResults()[0].getId()).isEqualTo(dataObjectIO1.getId());
    assertThat(result.getSearchParams()).isEqualTo(searchParams);
  }

  @Test
  @Order(2)
  public void findOneDataObjectWithAndAttributesTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.DataObject);
    String query = String.format(
      """
      {
        "AND": [
          {
            "property": "id",
            "value": %d,
            "operator": "eq"
          },
          {
            "property": "attributes.a",
            "value": "1",
            "operator": "eq"
          }
        ]
      }""",
      dataObjectIO1.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(requestSpecOfDefaultUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple1 = new ResultTriple(collection.getId(), dataObjectIO1.getId(), null);
    assertThat(result.getResultSet()).contains(triple1);
  }

  @Test
  @Order(3)
  public void findOneDataObjectWithAndNameTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.DataObject);
    String query = String.format(
      """
      {
        "AND": [
          {
            "property": "id",
            "value": %d,
            "operator": "eq"
          },
          {
            "property": "name",
            "value": "DataObjectSearchDummy1",
            "operator": "eq"
          }
        ]
      }""",
      dataObjectIO1.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(requestSpecOfDefaultUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple1 = new ResultTriple(collection.getId(), dataObjectIO1.getId(), null);
    assertThat(result.getResultSet()).contains(triple1);
  }

  @Test
  @Order(4)
  public void findNoDataObjectWithAndTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.DataObject);
    String query = String.format(
      """
      {
        "AND": [
          {
            "property": "id",
            "value": %d,
            "operator": "eq"
          },
          {
            "property": "name",
            "value": "DataObjectSearchDummy2",
            "operator": "eq"
          }
        ]
      }""",
      dataObjectIO1.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(requestSpecOfDefaultUser)
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
  @Order(5)
  public void findNoneOfTwoDataObjectsTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.DataObject);
    String query = String.format(
      """
      {
        "AND": [
          {
            "property": "id",
            "value": %d,
            "operator": "eq"
          },
          {
            "property": "name",
            "value": "DataObjectSearchDummy2",
            "operator": "eq"
          }
        ]
      }""",
      dataObjectIO1.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(requestSpecOfDefaultUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple1 = new ResultTriple(collection.getId(), dataObjectIO1.getId(), null);
    ResultTriple triple2 = new ResultTriple(collection.getId(), dataObjectIO2.getId(), null);
    assertThat(result.getResultSet()).doesNotContain(triple1, triple2);
  }

  @Test
  @Order(6)
  public void findOneOutOfTwoDataObjectsTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.DataObject);
    String query = String.format(
      """
      {
        "AND": [
          {
            "property": "id",
            "value": %d,
            "operator": "eq"
          },
          {
            "property": "name",
            "value": "DataObjectSearchDummy",
            "operator": "contains"
          }
        ]
      }""",
      dataObjectIO2.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(requestSpecOfDefaultUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple1 = new ResultTriple(collection.getId(), dataObjectIO1.getId(), null);
    ResultTriple triple2 = new ResultTriple(collection.getId(), dataObjectIO2.getId(), null);
    assertThat(result.getResultSet()).doesNotContain(triple1);
    assertThat(result.getResultSet()).contains(triple2);
  }

  @Test
  @Order(7)
  public void findTwoDataObjectsWithOrTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.DataObject);
    String query = String.format(
      """
      {
        "OR": [
          {
            "property": "id",
            "value": %d,
            "operator": "eq"
          },
          {
            "property": "name",
            "value": "DataObjectSearchDummy1",
            "operator": "eq"
          }
        ]
      }""",
      dataObjectIO2.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(requestSpecOfDefaultUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple1 = new ResultTriple(collection.getId(), dataObjectIO1.getId(), null);
    ResultTriple triple2 = new ResultTriple(collection.getId(), dataObjectIO2.getId(), null);
    assertThat(result.getResultSet()).contains(triple1, triple2);
  }

  @Test
  @Order(8)
  public void findOneOutOfFourDataObjectsTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setDataObjectId(dataObjectIO1.getId());
    searchScope.setTraversalRules(new TraversalRules[] { TraversalRules.children });
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.DataObject);
    String query = String.format(
      """
      {
        "AND": [
          {
            "property": "id",
            "value": %d,
            "operator": "eq"
          },
          {
            "property": "name",
            "value": "DataObjectSearchDummy4",
            "operator": "eq"
          }
      ]}""",
      dataObjectIO4.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(requestSpecOfDefaultUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple1 = new ResultTriple(collection.getId(), dataObjectIO1.getId(), null);
    ResultTriple triple2 = new ResultTriple(collection.getId(), dataObjectIO2.getId(), null);
    ResultTriple triple3 = new ResultTriple(collection.getId(), dataObjectIO3.getId(), null);
    ResultTriple triple4 = new ResultTriple(collection.getId(), dataObjectIO4.getId(), null);
    assertThat(result.getResultSet()).doesNotContain(triple1, triple2, triple3);
    assertThat(result.getResultSet()).contains(triple4);
  }

  @Test
  @Order(8)
  public void findAnotherOneOutOfFourDataObjectsByParentsTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setCollectionId(collection.getId());
    searchScope.setDataObjectId(dataObjectIO4.getId());
    searchScope.setTraversalRules(new TraversalRules[] { TraversalRules.parents });
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.DataObject);
    String query = String.format(
      """
      {
        "AND": [
          {
            "property": "id",
            "value": %d,
            "operator": "eq"
          },
          {
            "property": "name",
            "value": "DataObjectSearchDummy1",
            "operator": "eq"
          }
      ]}""",
      dataObjectIO1.getId()
    );
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(requestSpecOfDefaultUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple1 = new ResultTriple(collection.getId(), dataObjectIO1.getId(), null);
    ResultTriple triple2 = new ResultTriple(collection.getId(), dataObjectIO2.getId(), null);
    ResultTriple triple3 = new ResultTriple(collection.getId(), dataObjectIO3.getId(), null);
    ResultTriple triple4 = new ResultTriple(collection.getId(), dataObjectIO4.getId(), null);
    assertThat(result.getResultSet()).contains(triple1);
    assertThat(result.getResultSet()).doesNotContain(triple2, triple3, triple4);
  }

  @Test
  @Order(10)
  public void stringContainsTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.DataObject);
    String query =
      """
      {
        "property": "name",
        "value": "ummy",
        "operator": "contains"
      }""";
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(requestSpecOfDefaultUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    ResultTriple triple1 = new ResultTriple(collection.getId(), dataObjectIO1.getId(), null);
    ResultTriple triple2 = new ResultTriple(collection.getId(), dataObjectIO2.getId(), null);
    ResultTriple triple3 = new ResultTriple(collection.getId(), dataObjectIO3.getId(), null);
    ResultTriple triple4 = new ResultTriple(collection.getId(), dataObjectIO4.getId(), null);
    assertThat(result.getResultSet()).contains(triple1, triple2, triple3, triple4);
  }

  @Test
  @Order(11)
  public void unauthorizedUserTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.DataObject);
    String query =
      """
      {
        "property": "referencedDataObjectId",
        "value": "ummy",
        "operator": "eq"
      }""";
    searchParams.setQuery(query);
    searchBody.setSearchParams(searchParams);
    var result = given()
      .spec(requestSpecOfOtherUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ResponseBody.class);
    assertEquals(0, result.getResultSet().length);
  }

  private static DataObjectIO createDataObjectWithParent(String name, long collectionId, long parentID) {
    var dataObjectsURL = String.format("/%s/%d/%s/", Constants.COLLECTIONS, collectionId, Constants.DATA_OBJECTS);

    DataObjectIO dataObjectIO = new DataObjectIO();
    dataObjectIO.setName(name);
    dataObjectIO.setParentId(parentID);
    var dataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObjectIO)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    return dataObject;
  }
}
