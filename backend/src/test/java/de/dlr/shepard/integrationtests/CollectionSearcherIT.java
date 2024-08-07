package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.neo4Core.io.SemanticRepositoryIO;
import de.dlr.shepard.neo4Core.io.UserGroupIO;
import de.dlr.shepard.search.unified.QueryType;
import de.dlr.shepard.search.unified.ResponseBody;
import de.dlr.shepard.search.unified.ResultTriple;
import de.dlr.shepard.search.unified.SearchBody;
import de.dlr.shepard.search.unified.SearchParams;
import de.dlr.shepard.search.unified.SearchScope;
import de.dlr.shepard.semantics.SemanticRepositoryType;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.TraversalRules;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CollectionSearcherIT extends BaseTestCaseIT {

  private static String collectionsURL;
  private static RequestSpecification requestSpecification;
  private static CollectionIO collection1;
  private static CollectionIO collection2;
  private static String searchURL;
  private static RequestSpecification searchRequestSpec;
  private static UserWithApiKey user1;
  private static String jws1;
  private static RequestSpecification searchRequestSpec1;

  private static String repositoryURL;
  private static RequestSpecification repositoryRequestSpec;
  private static SemanticRepositoryIO repository;
  private static SemanticAnnotationIO annotation;
  private static CollectionIO annotatedCollection;
  private static String annotatedCollectionURL;
  private static RequestSpecification annotatedCollectionRequestSpec;

  @BeforeAll
  public static void setUp() {
    collectionsURL = "/" + Constants.COLLECTIONS;
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    var payload1 = new CollectionIO();
    payload1.setName("CollectionDummy");
    payload1.setDescription("First Collection");
    collection1 = given()
      .spec(requestSpecification)
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
      .spec(requestSpecification)
      .body(payload2)
      .when()
      .post(collectionsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionIO.class);
    searchURL = "/" + Constants.SEARCH;
    searchRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).addHeader("X-API-KEY", jws).build();
    user1 = getNewUserWithApiKey("user1" + System.currentTimeMillis());
    jws1 = user1.getApiKey().getJws();
    searchRequestSpec1 = new RequestSpecBuilder().setContentType(ContentType.JSON).addHeader("X-API-KEY", jws1).build();

    // for search involving SemanticAnnotations
    repositoryURL = "/" + Constants.SEMANTIC_REPOSITORIES;
    repositoryRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    annotatedCollection = createCollection("SemanticsCollection");
    annotatedCollectionURL = String.format(
      "/%s/%d/semanticAnnotations",
      Constants.COLLECTIONS,
      annotatedCollection.getId()
    );
    annotatedCollectionRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    var repositoryToCreate = new SemanticRepositoryIO();
    repositoryToCreate.setName("SemanticRepository");
    repositoryToCreate.setType(SemanticRepositoryType.SPARQL);
    repositoryToCreate.setEndpoint("https://dbpedia.org/sparql/");
    repository = given()
      .spec(repositoryRequestSpec)
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
      .spec(annotatedCollectionRequestSpec)
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
      .spec(searchRequestSpec)
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
      .spec(searchRequestSpec)
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
  @Order(3)
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
      .spec(searchRequestSpec)
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
  @Order(4)
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
      .spec(searchRequestSpec)
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
  @Order(5)
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
      .spec(searchRequestSpec)
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
  @Order(6)
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
      .spec(searchRequestSpec1)
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
  @Order(7)
  public void authorizedCollectionsSearchTest() {
    String permissionsURL = "/collections/" + collection1.getId() + "/permissions";
    RequestSpecification permissionsSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    PermissionsIO permissions = given()
      .spec(permissionsSpecification)
      .when()
      .get(permissionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(PermissionsIO.class);
    String[] reader = { user1.getUser().getUsername() };
    permissions.setReader(reader);
    given()
      .spec(permissionsSpecification)
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
      .spec(searchRequestSpec1)
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
  @Order(8)
  public void collectionsSearchTestReaderGroup() {
    String userGroupURL = "/" + Constants.USERGROUP;
    UserGroupIO userGroup = new UserGroupIO();
    userGroup.setName("userGroup");
    userGroup.setUsernames(new String[] { user1.getUser().getUsername() });
    RequestSpecification userGroupSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    UserGroupIO userGroupCreated = given()
      .spec(userGroupSpecification)
      .body(userGroup)
      .when()
      .post(userGroupURL)
      .then()
      .statusCode(201)
      .extract()
      .as(UserGroupIO.class);

    String permissionsURL = "/" + Constants.COLLECTIONS + "/" + collection2.getId() + "/" + Constants.PERMISSIONS;
    RequestSpecification permissionsSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    PermissionsIO permissions = given()
      .spec(permissionsSpecification)
      .when()
      .get(permissionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(PermissionsIO.class);
    long[] readerGroupIds = { userGroupCreated.getId() };
    permissions.setReaderGroupIds(readerGroupIds);
    given()
      .spec(permissionsSpecification)
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
      .spec(searchRequestSpec1)
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
  @Order(8)
  public void inTest() {
    String userGroupURL = "/" + Constants.USERGROUP;
    UserGroupIO userGroup = new UserGroupIO();
    userGroup.setName("userGroup");
    userGroup.setUsernames(new String[] { user1.getUser().getUsername() });
    RequestSpecification userGroupSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    UserGroupIO userGroupCreated = given()
      .spec(userGroupSpecification)
      .body(userGroup)
      .when()
      .post(userGroupURL)
      .then()
      .statusCode(201)
      .extract()
      .as(UserGroupIO.class);

    String permissionsURL = "/" + Constants.COLLECTIONS + "/" + collection2.getId() + "/" + Constants.PERMISSIONS;
    RequestSpecification permissionsSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    PermissionsIO permissions = given()
      .spec(permissionsSpecification)
      .when()
      .get(permissionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(PermissionsIO.class);
    long[] readerGroupIds = { userGroupCreated.getId() };
    permissions.setReaderGroupIds(readerGroupIds);
    given()
      .spec(permissionsSpecification)
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
      .spec(searchRequestSpec1)
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
  @Order(9)
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
      .spec(searchRequestSpec)
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
  @Order(10)
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
      .spec(searchRequestSpec)
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
