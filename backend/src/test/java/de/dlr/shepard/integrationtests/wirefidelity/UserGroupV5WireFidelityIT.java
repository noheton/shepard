package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.auth.users.io.UserGroupIO;
import de.dlr.shepard.common.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/**
 * v5 wire-fidelity test for {@code /shepard/api/userGroups}.
 *
 * <p>Exercises the {@code UserGroupIO extends BasicEntityIO} shape with its additional
 * {@code usernames} field. The fixture uses an empty {@code usernames} array so that the
 * value is byte-stable across runs (no dynamic username string to redact).
 *
 * <p>The fork-additive fields ({@code appId}, {@code revision}) appear in the response
 * as documented in {@code docs/reference/v5-cross-instance-quirks.md §Additive fields}.
 */
@QuarkusIntegrationTest
public class UserGroupV5WireFidelityIT extends V5WireFidelityTest {

  private static final String SLUG = "usergroups";

  @Test
  public void createUserGroup_wireMatchesFixture() {
    var ug = new UserGroupIO();
    ug.setName("UserGroupWireFixture");
    ug.setUsernames(new String[0]);

    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .body(ug)
      .when()
      .post("/" + Constants.USERGROUPS)
      .then()
      .statusCode(201)
      .extract()
      .response();

    assertWireMatches(SLUG, "create", response);
  }
}
