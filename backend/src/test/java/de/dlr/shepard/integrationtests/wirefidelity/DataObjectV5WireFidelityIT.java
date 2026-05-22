package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * v5 wire-fidelity tests for {@code /shepard/api/collections/{id}/dataObjects}.
 *
 * <p>Covers: POST (create) and GET single. Recorded fixtures live in
 * {@code backend/src/test/resources/fixtures/v5/dataobjects/}.
 */
@QuarkusIntegrationTest
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
public class DataObjectV5WireFidelityIT extends V5WireFidelityTest {

  private static final String SLUG = "dataobjects";

  private static CollectionIO ownerCollection;
  private static String dataObjectsUrl;
  private static long createdDataObjectId;

  @BeforeAll
  public static void setUp() {
    ownerCollection = createCollection("WireFixtureOwner_" + System.currentTimeMillis());
    dataObjectsUrl = "/%s/%d/%s".formatted(
      Constants.COLLECTIONS,
      ownerCollection.getId(),
      Constants.DATA_OBJECTS
    );
  }

  @Test
  @Order(1)
  public void createDataObject_wireMatchesFixture() {
    var payload = new DataObjectIO();
    payload.setName("DataObjectWireFixture");

    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post(dataObjectsUrl + "/")
      .then()
      .statusCode(201)
      .extract()
      .response();

    createdDataObjectId = response.jsonPath().getLong("id");
    assertWireMatches(SLUG, "create", response);
  }

  @Test
  @Order(2)
  public void getDataObject_wireMatchesFixture() {
    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsUrl + "/" + createdDataObjectId)
      .then()
      .statusCode(200)
      .extract()
      .response();

    assertWireMatches(SLUG, "get-single", response);
  }
}
