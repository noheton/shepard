package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.neo4Core.io.SubscriptionIO;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.RequestMethod;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SubscriptionTest extends BaseTestCaseIT {

  private static String subscriptionsURL;
  private static RequestSpecification requestSpecification;

  private static SubscriptionIO subscription;

  @BeforeAll
  public static void setUp() {
    subscriptionsURL = String.format("/%s/%s/%s", Constants.USERS, username, Constants.SUBSCRIPTIONS);
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
  }

  @Test
  @Order(1)
  public void createSubscriptionTest() {
    var toCreate = new SubscriptionIO();
    toCreate.setName("SubscriptionDummy");
    toCreate.setCallbackURL("http://my-callback-url.local");
    toCreate.setRequestMethod(RequestMethod.DELETE);
    toCreate.setSubscribedURL("http://my-subscribed-url.local");

    var actual = given()
      .spec(requestSpecification)
      .body(toCreate)
      .when()
      .post(subscriptionsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(SubscriptionIO.class);
    subscription = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(username);
    assertThat(actual.getName()).isEqualTo("SubscriptionDummy");
    assertThat(actual.getCallbackURL()).isEqualTo("http://my-callback-url.local");
    assertThat(actual.getRequestMethod()).isEqualTo(RequestMethod.DELETE);
    assertThat(actual.getSubscribedURL()).isEqualTo("http://my-subscribed-url.local");
  }

  @Test
  @Order(2)
  public void getSubscriptionTest() {
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(subscriptionsURL + "/" + subscription.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(SubscriptionIO.class);
    assertThat(actual).isEqualTo(subscription);
  }

  @Test
  @Order(3)
  public void getSubscriptionsTest() {
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(subscriptionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SubscriptionIO[].class);
    assertThat(actual).containsExactly(subscription);
  }

  @Test
  @Order(4)
  public void deleteSubscriptionTest() {
    given()
      .spec(requestSpecification)
      .when()
      .delete(subscriptionsURL + "/" + subscription.getId())
      .then()
      .statusCode(204);

    given().spec(requestSpecification).when().get(subscriptionsURL + "/" + subscription.getId()).then().statusCode(404);
  }
}
