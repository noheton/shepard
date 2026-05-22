package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/**
 * v5 wire-fidelity test for {@code /shepard/api/collections/{id}/permissions}.
 *
 * <p>{@code PermissionsIO} carries the {@code entityId} / {@code owner} /
 * {@code permissionType} / {@code reader} / {@code writer} / {@code manager} /
 * {@code readerGroupIds} / {@code writerGroupIds} keys. A regression in any
 * of these breaks every permission-aware upstream client; this fixture catches it.
 */
@QuarkusIntegrationTest
public class PermissionsV5WireFidelityIT extends V5WireFidelityTest {

  @Test
  public void getCollectionPermissions_wireMatchesFixture() {
    CollectionIO c = createCollection("PermissionsWireFixture_" + System.currentTimeMillis());
    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get("/" + Constants.COLLECTIONS + "/" + c.getId() + "/" + Constants.PERMISSIONS)
      .then()
      .statusCode(200)
      .extract()
      .response();
    assertWireMatches("permissions", "collection-permissions", response);
  }
}
