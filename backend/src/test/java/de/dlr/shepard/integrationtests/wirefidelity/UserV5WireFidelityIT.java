package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.common.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/**
 * v5 wire-fidelity test for {@code /shepard/api/users/{username}}.
 *
 * <p>Surfaces drift on the User shape — particularly the fork's additive fields
 * ({@code appId}, {@code orcid}, {@code displayName}, {@code effectiveDisplayName}).
 * If a future PR adds a sixth fork-private field without an upstream-compat policy
 * entry, this fixture breaks the build.
 */
@QuarkusIntegrationTest
public class UserV5WireFidelityIT extends V5WireFidelityTest {

  @Test
  public void getUserByUsername_wireMatchesFixture() {
    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get("/" + Constants.USERS + "/" + nameOfDefaultUser)
      .then()
      .statusCode(200)
      .extract()
      .response();
    assertWireMatches("users", "get-single", response);
  }
}
