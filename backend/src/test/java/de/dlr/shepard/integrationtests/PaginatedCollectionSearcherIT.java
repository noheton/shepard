package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.common.search.io.CollectionSearchBody;
import de.dlr.shepard.common.search.io.CollectionSearchParams;
import de.dlr.shepard.common.search.io.CollectionSearchResult;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
public class PaginatedCollectionSearcherIT extends BaseTestCaseIT {

  private static RequestSpecification requestSpecification;
  private static String searchURL = "/" + Constants.SEARCH + "/" + Constants.COLLECTIONS;

  @BeforeAll
  public static void setup() {
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
  }

  @Test
  public void paginatedCollectionSearcher_findCollectionByName_success() {
    // Arrange
    UUID runId = UUID.randomUUID();
    String query = "{\"property\": \"name\", \"value\": \"" + runId + "\", \"operator\": \"contains\"}";
    CollectionSearchParams params = new CollectionSearchParams(query);
    CollectionSearchBody searchBody = new CollectionSearchBody(params);
    CollectionIO collection1 = createCollection(runId + " firstCollection");

    // Act
    CollectionSearchResult result = given()
      .spec(requestSpecification)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionSearchResult.class);

    // Assert
    assertThat(result.getResults()).anyMatch(res -> res.getId().equals(collection1.getId()));
    assertThat(result.getSearchParams()).isEqualTo(params);
  }

  @Test
  public void paginatedCollectionSearcher_findCollectionById_success() {
    // Arrange
    CollectionIO collection2 = createCollection("collection2");
    String query = "{\"property\": \"id\", \"value\": " + collection2.getId().toString() + ", \"operator\": \"eq\"}";
    CollectionSearchParams params = new CollectionSearchParams(query);
    CollectionSearchBody searchBody = new CollectionSearchBody(params);

    // Act
    CollectionSearchResult result = given()
      .spec(requestSpecification)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionSearchResult.class);

    // Assert
    assertThat(result.getResults()).anyMatch(res -> res.getId().equals(collection2.getId()));
    assertThat(result.getSearchParams()).isEqualTo(params);
  }

  @Test
  public void paginatedCollectionSearcher_findCompletePageByName_success() {
    // Arrange
    UUID runId = UUID.randomUUID();
    int pageSize = 10;
    searchURL = "/" + Constants.SEARCH + "/" + Constants.COLLECTIONS + "?page=0&size=" + pageSize;
    String query =
      "{\"property\": \"name\", \"value\": \"" +
      runId +
      "testCollectionSearchWithPaginationSize\", \"operator\": \"contains\"}";
    CollectionSearchParams params = new CollectionSearchParams(query);
    CollectionSearchBody searchBody = new CollectionSearchBody(params);
    int numberOfCollections = 11;
    for (int i = 0; i < numberOfCollections; i++) {
      createCollection(runId + "testCollectionSearchWithPaginationSize" + i);
    }

    // Act
    CollectionSearchResult result = given()
      .spec(requestSpecification)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionSearchResult.class);

    // Assert
    assertThat(result.getResults().length).isEqualTo(pageSize);
    assertThat(result.getSearchParams()).isEqualTo(params);
  }

  @Test
  public void paginatedCollectionSearcher_searchForPrivateCollection_collectionOnlyReturnedForOwner() {
    // Arrange
    UUID runId = UUID.randomUUID();
    UserWithApiKey otherUser = getNewUserWithApiKey("the-other-one");
    RequestSpecification requestSpecificationOfOtherUser = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", otherUser.getApiKey().getJws())
      .build();
    given()
      .spec(requestSpecificationOfOtherUser)
      .body(Map.of("name", runId.toString()))
      .when()
      .post("/" + Constants.COLLECTIONS)
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionIO.class);
    String query = "{\"property\": \"name\", \"value\": \"" + runId + "\", \"operator\": \"contains\"}";
    CollectionSearchParams params = new CollectionSearchParams(query);
    CollectionSearchBody searchBody = new CollectionSearchBody(params);

    // Act
    CollectionSearchResult resultForUserOne = given()
      .spec(requestSpecification)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionSearchResult.class);
    CollectionSearchResult resultForOtherUser = given()
      .spec(requestSpecificationOfOtherUser)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionSearchResult.class);

    // Assert
    assertThat(resultForUserOne.getResults().length).isEqualTo(0);
    assertThat(resultForUserOne.getSearchParams()).isEqualTo(params);
    assertThat(resultForUserOne.getTotalResults()).isEqualTo(0);
    assertThat(resultForOtherUser.getResults().length).isEqualTo(1);
    assertThat(resultForOtherUser.getSearchParams()).isEqualTo(params);
    assertThat(resultForOtherUser.getTotalResults()).isEqualTo(1);
  }
}
