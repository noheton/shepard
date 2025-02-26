package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.common.util.Constants;
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
public class UserIT extends BaseTestCaseIT {

  private static UserIO user;

  private static String usersURL;
  private static RequestSpecification requestSpecification;

  @BeforeAll
  public static void setUp() {
    usersURL = "/" + Constants.USERS;
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
  }

  @Test
  @Order(1)
  public void getCurrentUserTest() {
    var actual = given()
      .spec(requestSpecification)
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
    assertThat(actual.getApiKeyIds()).contains(apiKeyId);
    assertThat(actual.getSubscriptionIds()).isNotNull();
  }

  @Test
  @Order(2)
  public void getUserTest() {
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(usersURL + "/" + username)
      .then()
      .statusCode(200)
      .extract()
      .as(UserIO.class);
    assertThat(actual).isEqualTo(user);
  }

  @Test
  public void getUser_userDoesNotExist_notFound() {
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(usersURL + "/fake-user-id")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    assertThat(actual.getMessage()).isEqualTo("ID ERROR - User does not exist");
  }
}
