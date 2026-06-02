package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.common.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserIT extends BaseTestCaseIT {

  private static UserIO user;

  private static String usersURL;

  @BeforeAll
  public static void setUp() {
    usersURL = "/" + Constants.USERS;
  }

  @Test
  @Order(1)
  public void getCurrentUserTest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(usersURL)
      .then()
      .statusCode(200)
      .extract()
      .as(UserIO.class);
    user = actual;

    assertThat(actual.getUsername()).isEqualTo("test_it");
    assertThat(actual.getEmail()).isEqualTo("integration@test.org");
    assertThat(actual.getFirstName()).isEqualTo("Integration");
    assertThat(actual.getLastName()).isEqualTo("Test");
    assertThat(actual.getApiKeyIds()).contains(defaultUser.getApiKey().getUid());
    assertThat(actual.getSubscriptionIds()).isNotNull();
    assertThat(actual.getEffectiveRoles()).as("effectiveRoles must be non-null on the self-view (BE-USERS-ME-ROLES-1)").isNotNull();
  }

  @Test
  @Order(2)
  public void getUserTest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(usersURL + "/" + nameOfDefaultUser)
      .then()
      .statusCode(200)
      .extract()
      .as(UserIO.class);
    assertThat(actual.getUsername()).isEqualTo(user.getUsername());
    assertThat(actual.getEmail()).isEqualTo(user.getEmail());
    assertThat(actual.getFirstName()).isEqualTo(user.getFirstName());
    assertThat(actual.getLastName()).isEqualTo(user.getLastName());
    assertThat(actual.getApiKeyIds()).containsExactlyInAnyOrder(user.getApiKeyIds());
    assertThat(actual.getSubscriptionIds()).containsExactlyInAnyOrder(user.getSubscriptionIds());
    assertThat(actual.getEffectiveRoles()).as("effectiveRoles is null on third-party lookups — not the caller's roles").isNull();
  }

  @Test
  public void getUser_userDoesNotExist_notFound() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(usersURL + "/fake-user-id")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    assertThat(actual.getMessage()).isEqualTo("User with name %s not found".formatted("fake-user-id"));
  }
}
