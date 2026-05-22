package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * v5 wire-fidelity tests for {@code /shepard/api/collections}.
 *
 * <p>Covers: POST (create), GET single, GET list. Recorded fixtures live in
 * {@code backend/src/test/resources/fixtures/v5/collections/}.
 *
 * <p>Tests use a deterministic name ({@code "CollectionWireFixture"}) and a deterministic
 * attribute map ({@code {"a":"1","b":"2"}}) so the recorded response is byte-stable modulo
 * the well-known dynamic fields (id, appId, createdAt, createdBy).
 */
@QuarkusIntegrationTest
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
public class CollectionV5WireFidelityIT extends V5WireFidelityTest {

  private static final String COLLECTIONS_URL = "/" + Constants.COLLECTIONS;
  private static final String SLUG = "collections";

  private static long createdCollectionId;

  @Test
  @Order(1)
  public void createCollection_wireMatchesFixture() {
    var payload = new CollectionIO();
    payload.setName("CollectionWireFixture");
    payload.setDescription("My Description");
    payload.setAttributes(Map.of("a", "1", "b", "2"));

    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post(COLLECTIONS_URL)
      .then()
      .statusCode(201)
      .extract()
      .response();

    createdCollectionId = response.jsonPath().getLong("id");
    assertWireMatches(SLUG, "create", response);
  }

  @Test
  @Order(2)
  public void getCollection_wireMatchesFixture() {
    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(COLLECTIONS_URL + "/" + createdCollectionId)
      .then()
      .statusCode(200)
      .extract()
      .response();

    assertWireMatches(SLUG, "get-single", response);
  }
}
