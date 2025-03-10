package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.common.search.io.QueryType;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.ResultTriple;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.io.SearchParams;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.TraversalRules;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.data.semantic.SemanticRepositoryType;
import de.dlr.shepard.data.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.data.semantic.io.SemanticRepositoryIO;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@WithTestResource(WireMockResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CollectionSearcherIT extends BaseTestCaseIT {

  private static String collectionsURL;
  private static CollectionIO collection1;
  private static CollectionIO collection2;
  private static String searchURL;

  private static String repositoryURL;
  private static SemanticRepositoryIO repository;
  private static SemanticAnnotationIO annotation;
  private static CollectionIO annotatedCollection;
  private static String annotatedCollectionURL;

  @BeforeAll
  public static void setUp() {
    collectionsURL = "/" + Constants.COLLECTIONS;

    var payload1 = new CollectionIO();
    payload1.setName("CollectionDummy");
    payload1.setDescription("First Collection");
    payload1.setAttributes(Map.of("a", "1", "b", "2"));
    collection1 = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload1)
      .when()
      .post(collectionsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionIO.class);
    var payload2 = new CollectionIO();
    payload2.setName("secondCollectionDummy");
    payload2.setDescription("Second Collection");
    collection2 = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload2)
      .when()
      .post(collectionsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionIO.class);
    searchURL = "/" + Constants.SEARCH;

    // for search involving SemanticAnnotations
    repositoryURL = "/" + Constants.SEMANTIC_REPOSITORIES;
    annotatedCollection = createCollection("SemanticsCollection");
    annotatedCollectionURL = String.format(
      "/%s/%d/semanticAnnotations",
      Constants.COLLECTIONS,
      annotatedCollection.getId()
    );
    var repositoryToCreate = new SemanticRepositoryIO();
    repositoryToCreate.setName("SemanticRepository");
    repositoryToCreate.setType(SemanticRepositoryType.SPARQL);
    repositoryToCreate.setEndpoint(WireMockResource.getWireMockServerURlWithPath("/sparql"));
    repository = given()
      .spec(requestSpecOfDefaultUser)
      .body(repositoryToCreate)
      .when()
      .post(repositoryURL)
      .then()
      .statusCode(201)
      .extract()
      .as(SemanticRepositoryIO.class);

    var annotationToCreate = new SemanticAnnotationIO();
    annotationToCreate.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
    annotationToCreate.setPropertyRepositoryId(repository.getId());
    annotationToCreate.setValueIRI("http://dbpedia.org/resource/Almond_milk");
    annotationToCreate.setValueRepositoryId(repository.getId());
    annotation = given()
      .spec(requestSpecOfDefaultUser)
      .body(annotationToCreate)
      .when()
      .post(annotatedCollectionURL)
      .then()
      .statusCode(201)
      .extract()
      .as(SemanticAnnotationIO.class);
  }

  @Test
  @Order(1)
  public void findOneOutOfTwoCollectionsTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
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
      ]}""",
      collection1.getId()
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
    ResultTriple triple1 = new ResultTriple(collection1.getId(), null, null);
    assertThat(result.getResultSet()).containsExactly(triple1);
    assertThat(result.getResults()[0].getId()).isEqualTo(collection1.getId());
    assertThat(result.getSearchParams()).isEqualTo(searchParams);
  }

  @Test
  @Order(2)
  public void findCollectionByAttribute() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
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
      collection1.getId()
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
    ResultTriple triple1 = new ResultTriple(collection1.getId(), null, null);
    assertThat(result.getResultSet()).containsExactly(triple1);
    assertThat(result.getResults()[0].getId()).isEqualTo(collection1.getId());
    assertThat(result.getSearchParams()).isEqualTo(searchParams);
  }

  @Test
  @Order(3)
  public void neTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
    String query = String.format(
      """
      {
        "property": "id",
        "value": %d,
        "operator": "ne"
      }""",
      collection1.getId()
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
    ResultTriple triple1 = new ResultTriple(collection1.getId(), null, null);
    assertThat(result.getResultSet()).doesNotContain(triple1);
  }

  @Test
  @Order(4)
  public void findTwoOutOfTwoCollectionsTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
    String query = String.format(
      """
      {
        "OR": [
          {
            "property": "id",
            "value": %d,
            "operator": "ge"
          },
          {
            "property": "id",
            "value": %d,
            "operator": "le"
          }
      ]}""",
      collection1.getId(),
      collection2.getId()
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
    ResultTriple triple1 = new ResultTriple(collection1.getId(), null, null);
    ResultTriple triple2 = new ResultTriple(collection2.getId(), null, null);
    assertThat(result.getResultSet()).contains(triple1, triple2);
  }

  @Test
  @Order(5)
  public void findNoCollectionTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
    String query = String.format(
      """
      {
        "AND": [
          {
            "property": "id",
            "value": %d,
            "operator": "gt"
          },
          {
            "property": "id",
            "value": %d,
            "operator": "gt"
          }
      ]}""",
      collection1.getId(),
      collection2.getId()
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
    ResultTriple triple1 = new ResultTriple(collection1.getId(), null, null);
    ResultTriple triple2 = new ResultTriple(collection2.getId(), null, null);
    assertThat(result.getResultSet()).doesNotContain(triple1, triple2);
  }

  @Test
  @Order(6)
  public void findByAndTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
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
            "value": "%s",
            "operator": "eq"
          }
      ]}""",
      collection1.getId(),
      collection1.getName()
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
    ResultTriple triple1 = new ResultTriple(collection1.getId(), null, null);
    ResultTriple triple2 = new ResultTriple(collection2.getId(), null, null);
    assertThat(result.getResultSet()).contains(triple1);
    assertThat(result.getResultSet()).doesNotContain(triple2);
  }

  @Test
  @Order(7)
  public void unauthorizedCollectionsSearchTest() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
    String query = String.format(
      """
      {
        "OR": [
          {
            "property": "id",
            "value": %d,
            "operator": "ge"
          },
          {
            "property": "id",
            "value": %d,
            "operator": "le"
          }
      ]}""",
      collection1.getId(),
      collection2.getId()
    );
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
    ResultTriple triple1 = new ResultTriple(collection1.getId(), null, null);
    ResultTriple triple2 = new ResultTriple(collection2.getId(), null, null);
    assertThat(result.getResultSet()).doesNotContain(triple1, triple2);
  }

  @Test
  @Order(8)
  public void authorizedCollectionsSearchTest() {
    String permissionsURL = "/collections/" + collection1.getId() + "/permissions";
    PermissionsIO permissions = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(permissionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(PermissionsIO.class);
    String[] reader = { otherUser.getUser().getUsername() };
    permissions.setReader(reader);
    given()
      .spec(requestSpecOfDefaultUser)
      .body(permissions)
      .when()
      .put(permissionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(PermissionsIO.class);
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
    String query = String.format(
      """
      	{
        "OR": [
          {
            "property": "id",
            "value": %d,
            "operator": "ge"
          },
          {
            "property": "id",
            "value": %d,
            "operator": "le"
          }
      ]}""",
      collection1.getId(),
      collection2.getId()
    );
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
    ResultTriple triple1 = new ResultTriple(collection1.getId(), null, null);
    ResultTriple triple2 = new ResultTriple(collection2.getId(), null, null);
    assertThat(result.getResultSet()).contains(triple1);
    assertThat(result.getResultSet()).doesNotContain(triple2);
  }

  @Test
  @Order(9)
  public void collectionsSearchTestReaderGroup() {
    String userGroupURL = "/" + Constants.USERGROUPS;
    UserGroupIO userGroup = new UserGroupIO();
    userGroup.setName("userGroup");
    userGroup.setUsernames(new String[] { otherUser.getUser().getUsername() });
    UserGroupIO userGroupCreated = given()
      .spec(requestSpecOfDefaultUser)
      .body(userGroup)
      .when()
      .post(userGroupURL)
      .then()
      .statusCode(201)
      .extract()
      .as(UserGroupIO.class);

    String permissionsURL = "/" + Constants.COLLECTIONS + "/" + collection2.getId() + "/" + Constants.PERMISSIONS;
    PermissionsIO permissions = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(permissionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(PermissionsIO.class);
    long[] readerGroupIds = { userGroupCreated.getId() };
    permissions.setReaderGroupIds(readerGroupIds);
    given()
      .spec(requestSpecOfDefaultUser)
      .body(permissions)
      .when()
      .put(permissionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(PermissionsIO.class);
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
    String query = String.format(
      """
      	{
        "OR": [
          {
            "property": "id",
            "value": %d,
            "operator": "ge"
          },
          {
            "property": "id",
            "value": %d,
            "operator": "le"
          }
      ]}""",
      collection1.getId(),
      collection2.getId()
    );
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
    ResultTriple triple1 = new ResultTriple(collection1.getId(), null, null);
    ResultTriple triple2 = new ResultTriple(collection2.getId(), null, null);
    assertThat(result.getResultSet()).contains(triple1);
    assertThat(result.getResultSet()).contains(triple2);
  }

  @Test
  @Order(10)
  public void inTest() {
    String userGroupURL = "/" + Constants.USERGROUPS;
    UserGroupIO userGroup = new UserGroupIO();
    userGroup.setName("userGroup");
    userGroup.setUsernames(new String[] { otherUser.getUser().getUsername() });
    UserGroupIO userGroupCreated = given()
      .spec(requestSpecOfDefaultUser)
      .body(userGroup)
      .when()
      .post(userGroupURL)
      .then()
      .statusCode(201)
      .extract()
      .as(UserGroupIO.class);

    String permissionsURL = "/" + Constants.COLLECTIONS + "/" + collection2.getId() + "/" + Constants.PERMISSIONS;
    PermissionsIO permissions = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(permissionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(PermissionsIO.class);
    long[] readerGroupIds = { userGroupCreated.getId() };
    permissions.setReaderGroupIds(readerGroupIds);
    given()
      .spec(requestSpecOfDefaultUser)
      .body(permissions)
      .when()
      .put(permissionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(PermissionsIO.class);
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
    String query = String.format(
      """
      {
        "property": "id",
        "value": [%d,%d],
        "operator": "in"
      }""",
      collection1.getId(),
      collection2.getId()
    );
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
    ResultTriple triple1 = new ResultTriple(collection1.getId(), null, null);
    ResultTriple triple2 = new ResultTriple(collection2.getId(), null, null);
    assertThat(result.getResultSet()).contains(triple1);
    assertThat(result.getResultSet()).contains(triple2);
  }

  @Test
  @Order(11)
  public void searchCollectionsViaPropertyIRI() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
    String query =
      "{\"property\": \"propertyIRI\",\"value\": \"" + annotation.getPropertyIRI() + "\",\"operator\": \"eq\"}";
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
    ResultTriple triple = new ResultTriple(annotatedCollection.getId(), null, null);
    assertThat(result.getResultSet()).contains(triple);
  }

  @Test
  @Order(12)
  public void searchCollectionsViaValueIRI() {
    SearchBody searchBody = new SearchBody();
    SearchScope searchScope = new SearchScope();
    searchScope.setTraversalRules(new TraversalRules[] {});
    searchBody.setScopes(new SearchScope[] { searchScope });
    SearchParams searchParams = new SearchParams();
    searchParams.setQueryType(QueryType.Collection);
    String query =
      "{\"property\": \"valueIRI\",\"value\": \"" +
      annotation.getValueIRI().substring(2, 10) +
      "\",\"operator\": \"contains\"}";
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
    ResultTriple triple = new ResultTriple(annotatedCollection.getId(), null, null);
    assertThat(result.getResultSet()).contains(triple);
  }
}
