package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.search.io.CollectionSearchBody;
import de.dlr.shepard.common.search.io.CollectionSearchParams;
import de.dlr.shepard.common.search.io.CollectionSearchResult;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
public class PaginatedCollectionSearcherIT extends BaseTestCaseIT {

  private static RequestSpecification requestSpecification;
  private static final String searchURL = "/" + Constants.SEARCH + "/" + Constants.COLLECTIONS;

  private CollectionSearchResult searchCollections(RequestSpecification spec, CollectionSearchBody body, String url) {
    return given()
      .spec(spec)
      .body(body)
      .when()
      .post(url)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionSearchResult.class);
  }

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
    CollectionSearchResult result = searchCollections(requestSpecification, searchBody, searchURL);

    // Assert
    assertThat(result.getResults()).anyMatch(res -> res.getId().equals(collection1.getId()));
    assertThat(result.getTotalResults()).isEqualTo(1);
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
    CollectionSearchResult result = searchCollections(requestSpecification, searchBody, searchURL);

    // Assert
    assertThat(result.getResults()).anyMatch(res -> res.getId().equals(collection2.getId()));
    assertThat(result.getTotalResults()).isEqualTo(1);
    assertThat(result.getSearchParams()).isEqualTo(params);
  }

  @Test
  public void paginatedCollectionSearcher_findCompletePageByName_success() {
    // Arrange
    UUID runId = UUID.randomUUID();
    int pageSize = 5;
    String searchURL = "/" + Constants.SEARCH + "/" + Constants.COLLECTIONS + "?page=0&size=" + pageSize;
    String query =
      "{\"property\": \"name\", \"value\": \"" +
      runId +
      "testCollectionSearchWithPaginationSize\", \"operator\": \"contains\"}";
    CollectionSearchParams params = new CollectionSearchParams(query);
    CollectionSearchBody searchBody = new CollectionSearchBody(params);
    int numberOfCollections = 6;
    for (int i = 0; i < numberOfCollections; i++) {
      CollectionIO col = createCollection(runId + "testCollectionSearchWithPaginationSize" + i);
      col.setDescription("I have been updated.");
      given()
        .spec(requestSpecification)
        .body(col)
        .when()
        .put("/" + Constants.COLLECTIONS + "/" + col.getId())
        .then()
        .statusCode(200)
        .extract()
        .as(CollectionIO.class);
    }

    // Act
    CollectionSearchResult result = searchCollections(requestSpecification, searchBody, searchURL);

    // Assert
    assertThat(result.getResults().length).isEqualTo(pageSize);
    assertThat(result.getTotalResults()).isEqualTo(numberOfCollections);
    assertThat(result.getSearchParams()).isEqualTo(params);
  }

  @Test
  public void paginatedCollectionSearcher_findCompleteAndOrderedPageByName_success() {
    // Arrange
    UUID runId = UUID.randomUUID();
    int pageSize = 20;
    String searchURL =
      "/" +
      Constants.SEARCH +
      "/" +
      Constants.COLLECTIONS +
      "?page=0&size=" +
      pageSize +
      "&orderBy=updatedAt&orderDesc=false";
    String query =
      "{\"property\": \"name\", \"value\": \"" +
      runId +
      "testCollectionSearchWithPaginationSize\", \"operator\": \"contains\"}";
    CollectionSearchParams params = new CollectionSearchParams(query);
    CollectionSearchBody searchBody = new CollectionSearchBody(params);
    int numberOfCollections = 21;
    List<CollectionIO> createdCollections = new ArrayList<CollectionIO>();
    for (int i = 0; i < numberOfCollections; i++) {
      CollectionIO col = createCollection(runId + "testCollectionSearchWithPaginationSize" + i);
      createdCollections.add(col);
    }
    CollectionIO collectionToUpdate = createdCollections.get(0);
    collectionToUpdate.setDescription("I have been updated.");
    given()
      .spec(requestSpecification)
      .body(collectionToUpdate)
      .when()
      .put("/" + Constants.COLLECTIONS + "/" + collectionToUpdate.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO.class);

    // Act
    CollectionSearchResult result = searchCollections(requestSpecification, searchBody, searchURL);

    // Assert
    assertThat(result.getResults().length).isEqualTo(pageSize);
    assertThat(result.getTotalResults()).isEqualTo(numberOfCollections);
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
    CollectionSearchResult resultForUserOne = searchCollections(requestSpecification, searchBody, searchURL);
    CollectionSearchResult resultForOtherUser = searchCollections(
      requestSpecificationOfOtherUser,
      searchBody,
      searchURL
    );

    // Assert
    assertThat(resultForUserOne.getResults().length).isEqualTo(0);
    assertThat(resultForUserOne.getSearchParams()).isEqualTo(params);
    assertThat(resultForUserOne.getTotalResults()).isEqualTo(0);
    assertThat(resultForOtherUser.getResults().length).isEqualTo(1);
    assertThat(resultForOtherUser.getSearchParams()).isEqualTo(params);
    assertThat(resultForOtherUser.getTotalResults()).isEqualTo(1);
  }

  @Test
  public void paginatedCollectionSearcher_openLastPage_pageContainsCollections() {
    // Arrange
    String query = "{\"property\": \"name\", \"value\": \"\", \"operator\": \"contains\"}";
    CollectionSearchParams params = new CollectionSearchParams(query);
    CollectionSearchBody searchBody = new CollectionSearchBody(params);

    int pageSize = 20;
    int totalCollections = searchCollections(
      requestSpecification,
      searchBody,
      searchURL + "?page=0&size=" + pageSize
    ).getTotalResults();
    int lastPage = Math.floorDiv(totalCollections, pageSize);
    int numberOfItemsOnLastPage = totalCollections % pageSize;

    // Act
    CollectionSearchResult resultsOfLastPage = searchCollections(
      requestSpecification,
      searchBody,
      searchURL + "?page=" + lastPage + "&size=" + pageSize
    );

    // Assert
    assertThat(resultsOfLastPage.getResults().length).isEqualTo(numberOfItemsOnLastPage);
  }

  @Test
  public void paginatedCollectionSearcher_sortByName_orderedResults() {
    // Arrange
    UUID runId = UUID.randomUUID();
    String query = "{\"property\": \"name\", \"value\": \"" + runId + "\", \"operator\": \"contains\"}";
    CollectionSearchParams params = new CollectionSearchParams(query);
    CollectionSearchBody searchBody = new CollectionSearchBody(params);

    int numberOfCollections = 3;
    for (int i = 0; i < numberOfCollections; i++) {
      createCollection(i + " " + runId + "sortByName");
    }

    // Act
    CollectionSearchResult resultsSortedByNameDescending = searchCollections(
      requestSpecification,
      searchBody,
      searchURL + "?page=0&size=20&orderBy=name&orderDesc=true"
    );
    CollectionSearchResult resultsSortedByNameAscending = searchCollections(
      requestSpecification,
      searchBody,
      searchURL + "?page=0&size=20&orderBy=name&orderDesc=false"
    );

    // Assert
    assertThat(resultsSortedByNameDescending.getResults().length).isEqualTo(numberOfCollections);
    assertTrue(resultsSortedByNameDescending.getResults()[0].getName().startsWith("2"));
    assertTrue(resultsSortedByNameDescending.getResults()[2].getName().startsWith("0"));
    assertThat(resultsSortedByNameAscending.getResults().length).isEqualTo(numberOfCollections);
    assertTrue(resultsSortedByNameAscending.getResults()[0].getName().startsWith("0"));
    assertTrue(resultsSortedByNameAscending.getResults()[2].getName().startsWith("2"));
  }
}
