package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.search.user.UserSearchBody;
import de.dlr.shepard.search.user.UserSearchParams;
import de.dlr.shepard.search.user.UserSearchResult;
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
public class UserSearcherTest extends BaseTestCaseIT {

  private static String searchURL;
  private static RequestSpecification requestSpecification;
  private static UserIO userIO1;
  private static UserIO userIO2;

  @BeforeAll
  public static void setUp() {
    searchURL = "/" + Constants.SEARCH + "/" + Constants.USERS;
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    userIO1 = new UserIO(getNewUser("user1" + System.currentTimeMillis()));
    userIO2 = new UserIO(getNewUser("user2" + System.currentTimeMillis()));
  }

  @Test
  @Order(1)
  public void findUserTest() {
    String query = "{\"property\": \"username\", \"value\": \"test_it\", \"operator\": \"eq\"}";
    var params = new UserSearchParams(query);
    var searchBody = new UserSearchBody(params);
    var result = given()
      .spec(requestSpecification)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(UserSearchResult.class);
    assertThat(result.getResults()).containsExactly(userIO);
    assertThat(result.getSearchParams()).isEqualTo(params);
  }

  @Test
  @Order(2)
  public void findUsersByEmailTest() {
    String query = "{\"property\": \"email\", \"value\": \"integration@test.org\", \"operator\": \"eq\"}";
    var params = new UserSearchParams(query);
    var searchBody = new UserSearchBody(params);
    var result = given()
      .spec(requestSpecification)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(UserSearchResult.class);
    assertThat(result.getResults()).contains(userIO, userIO1, userIO2);
  }

  @Test
  @Order(3)
  public void findOneUserTest() {
    String query = "{\"property\": \"username\", \"value\": \"user1\", \"operator\": \"contains\"}";
    var params = new UserSearchParams(query);
    var searchBody = new UserSearchBody(params);
    var result = given()
      .spec(requestSpecification)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(UserSearchResult.class);
    assertThat(result.getResults()).contains(userIO1);
    assertThat(result.getResults()).doesNotContain(userIO2, userIO);
  }

  @Test
  @Order(4)
  public void findTwoUsersTest() {
    String query = "{\"property\": \"username\", \"value\": \"user\", \"operator\": \"contains\"}";
    var params = new UserSearchParams(query);
    var searchBody = new UserSearchBody(params);
    var result = given()
      .spec(requestSpecification)
      .body(searchBody)
      .when()
      .post(searchURL)
      .then()
      .statusCode(200)
      .extract()
      .as(UserSearchResult.class);
    assertThat(result.getResults()).contains(userIO1, userIO2);
    assertThat(result.getResults()).doesNotContain(userIO);
  }
}
