package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.auth.apikey.io.ApiKeyIO;
import de.dlr.shepard.auth.apikey.io.ApiKeyWithJWTIO;
import de.dlr.shepard.common.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApiKeyIT extends BaseTestCaseIT {

  private static String apiKeyURL;
  private static ApiKeyIO apikey;

  @BeforeAll
  public static void setUp() {
    apiKeyURL = String.format("/%s/%s/%s", Constants.USERS, nameOfDefaultUser, Constants.APIKEYS);
  }

  @Test
  @Order(1)
  public void createApiKey_createNewKey_success() {
    var toCreate = new ApiKeyIO();
    toCreate.setName("ApiKeyDummy");

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(apiKeyURL)
      .then()
      .statusCode(201)
      .extract()
      .as(ApiKeyWithJWTIO.class);
    apikey = new ApiKeyIO() {
      {
        setBelongsTo(actual.getBelongsTo());
        setCreatedAt(actual.getCreatedAt());
        setName(actual.getName());
        setUid(actual.getUid());
      }
    };

    assertThat(actual.getUid()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getBelongsTo()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getName()).isEqualTo("ApiKeyDummy");
    assertThat(actual.getJwt()).isNotNull();

    var newSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", actual.getJwt())
      .build();
    given().spec(newSpec).when().get(apiKeyURL).then().statusCode(200);
  }

  @Test
  @Order(2)
  public void getApiKey_getExistingKey_success() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(apiKeyURL + "/" + apikey.getUid())
      .then()
      .statusCode(200)
      .extract()
      .as(ApiKeyIO.class);
    assertThat(actual).isEqualTo(apikey);
  }

  @Test
  @Order(3)
  public void getApiKey_getNonExistingKey_notFound() {
    ErrorResponse response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(apiKeyURL + "/" + UUID.randomUUID())
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    assertThat(response.getMessage()).isEqualTo("ID ERROR - ApiKey does not exist");
  }

  @Test
  @Order(4)
  public void getApiKey_getByKeyUuidOfOtherUser_notFound() {
    ErrorResponse response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(apiKeyURL + "/" + otherUser.getApiKey().getUid())
      .then()
      .statusCode(403)
      .extract()
      .as(ErrorResponse.class);
    assertThat(response.getMessage()).isEqualTo("You do not have permissions for this ApiKey.");
  }

  @Test
  @Order(5)
  public void getApiKey_getKeyOfOtherUser_Forbidden() {
    ErrorResponse response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(
        String.format(
          "/%s/%s/%s/%s",
          Constants.USERS,
          otherUser.getUser().getUsername(),
          Constants.APIKEYS,
          otherUser.getApiKey().getUid()
        )
      )
      .then()
      .statusCode(403)
      .extract()
      .as(ErrorResponse.class);
    assertThat(response.getMessage()).isEqualTo("The requested action is forbidden by the permission policies");
  }

  @Test
  @Order(6)
  public void getApiKey_getNonExistingKeyOfOtherUser_Forbidden() {
    ErrorResponse response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(
        String.format(
          "/%s/%s/%s/%s",
          Constants.USERS,
          otherUser.getUser().getUsername(),
          Constants.APIKEYS,
          UUID.randomUUID()
        )
      )
      .then()
      .statusCode(403)
      .extract()
      .as(ErrorResponse.class);
    assertThat(response.getMessage()).isEqualTo("The requested action is forbidden by the permission policies");
  }

  @Test
  @Order(7)
  public void getApiKey_getAllKeys_existingKeyReturned() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(apiKeyURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ApiKeyIO[].class);
    assertThat(actual).contains(apikey);
  }

  @Test
  @Order(8)
  public void deleteApiKey_deleteExistingKey_success() {
    given().spec(requestSpecOfDefaultUser).when().delete(apiKeyURL + "/" + apikey.getUid()).then().statusCode(204);
    given().spec(requestSpecOfDefaultUser).when().get(apiKeyURL + "/" + apikey.getUid()).then().statusCode(404);
  }

  @Test
  @Order(9)
  public void deleteApiKey_deleteNonExistingKey_notFound() {
    given().spec(requestSpecOfDefaultUser).when().delete(apiKeyURL + "/" + UUID.randomUUID()).then().statusCode(404);
  }

  @Test
  @Order(10)
  public void deleteApiKey_deleteKeyOfOtherUser_Forbidden() {
    ErrorResponse response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(
        String.format(
          "/%s/%s/%s/%s",
          Constants.USERS,
          otherUser.getUser().getUsername(),
          Constants.APIKEYS,
          UUID.randomUUID()
        )
      )
      .then()
      .statusCode(403)
      .extract()
      .as(ErrorResponse.class);
    assertThat(response.getMessage()).isEqualTo("The requested action is forbidden by the permission policies");
  }

  @Test
  @Order(11)
  public void deleteApiKey_deleteNonexistingKeyOfOtherUser_Forbidden() {
    ErrorResponse response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(
        String.format(
          "/%s/%s/%s/%s",
          Constants.USERS,
          otherUser.getUser().getUsername(),
          Constants.APIKEYS,
          otherUser.getApiKey().getUid()
        )
      )
      .then()
      .statusCode(403)
      .extract()
      .as(ErrorResponse.class);
    assertThat(response.getMessage()).isEqualTo("The requested action is forbidden by the permission policies");
  }
}
