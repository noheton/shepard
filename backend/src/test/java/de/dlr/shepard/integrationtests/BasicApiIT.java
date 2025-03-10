package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.context.collection.io.CollectionIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * This Integration test should check basic capabilities of our API, like responding 404 for undefined paths.
 */
@QuarkusIntegrationTest
public class BasicApiIT extends BaseTestCaseIT {

  private static RequestSpecification requestSpecificationWithoutUser;

  @BeforeAll
  public static void setUp() {
    requestSpecificationWithoutUser = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
  }

  @Test
  public void getIndexRoute_notFound() {
    given().spec(requestSpecificationWithoutUser).when().get("/").then().statusCode(404);
    given().spec(requestSpecOfDefaultUser).when().get("/").then().statusCode(404);
  }

  @Test
  public void getCollections_pathsWithExtraSlashes_success() {
    var responseWithCleanPath = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get("/collections")
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO[].class);
    var responseWithExtraSlash = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get("/collections/")
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO[].class);

    assertThat(responseWithExtraSlash).isEqualTo(responseWithCleanPath);
  }

  @Test
  public void getCollections_twoLeadingSlashes_notFound() {
    given().spec(requestSpecOfDefaultUser).when().get("//collections").then().statusCode(404);
  }

  @Test
  // TODO: Fix these issues and reenable the test
  @Disabled
  public void getCollectionTest_invalidId_badRequest() {
    given().spec(requestSpecOfDefaultUser).when().get("/collections/-1").then().statusCode(400);
    given().spec(requestSpecOfDefaultUser).when().get("/collections/abc").then().statusCode(400);
  }
}
