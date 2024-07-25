package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.neo4Core.io.ApiKeyIO;
import de.dlr.shepard.neo4Core.io.ApiKeyWithJWTIO;
import de.dlr.shepard.util.Constants;
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
public class ApiKeyTest extends BaseTestCaseIT {

  private static String apiKeyURL;
  private static RequestSpecification requestSpecification;
  private static ApiKeyIO apikey;

  @BeforeAll
  public static void setUp() {
    apiKeyURL = String.format("/%s/%s/%s", Constants.USERS, username, Constants.APIKEYS);
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
  }

  @Test
  @Order(1)
  public void createApiKeyTest() {
    var toCreate = new ApiKeyIO();
    toCreate.setName("ApiKeyDummy");

    var actual = given()
      .spec(requestSpecification)
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
    assertThat(actual.getBelongsTo()).isEqualTo(username);
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
  public void getApiKeyTest() {
    var actual = given()
      .spec(requestSpecification)
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
  public void getApiKeysTest() {
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(apiKeyURL)
      .then()
      .statusCode(200)
      .extract()
      .as(ApiKeyIO[].class);
    assertThat(actual).contains(apikey);
  }

  @Test
  @Order(4)
  public void deleteApiKeyTest() {
    given().spec(requestSpecification).when().delete(apiKeyURL + "/" + apikey.getUid()).then().statusCode(204);
    given().spec(requestSpecification).when().get(apiKeyURL + "/" + apikey.getUid()).then().statusCode(404);
  }
}
