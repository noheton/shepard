package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.subscription.io.SubscriptionIO;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.RequestMethod;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SubscriptionIT extends BaseTestCaseIT {

  private static String subscriptionsURL;

  private static SubscriptionIO subscription;

  @BeforeAll
  public static void setUp() {
    subscriptionsURL = String.format("/%s/%s/%s", Constants.USERS, nameOfDefaultUser, Constants.SUBSCRIPTIONS);
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
      .spec(requestSpecOfDefaultUser)
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
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getName()).isEqualTo("SubscriptionDummy");
    assertThat(actual.getCallbackURL()).isEqualTo("http://my-callback-url.local");
    assertThat(actual.getRequestMethod()).isEqualTo(RequestMethod.DELETE);
    assertThat(actual.getSubscribedURL()).isEqualTo("http://my-subscribed-url.local");
  }

  @Test
  @Order(2)
  public void getSubscriptionTest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
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
  public void getSubscriptionTest_doesNotExist_notFound() {
    ErrorResponse actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(subscriptionsURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    assertThat(actual.getMessage()).isEqualTo("ID ERROR - Subscription does not exist");
  }

  @Test
  @Order(4)
  public void getSubscriptionTest_requestingSubscriptionOfOtherUser_forbidden() {
    ErrorResponse actual = given()
      .spec(requestSpecOfOtherUser)
      .when()
      .get(subscriptionsURL + "/" + subscription.getId())
      .then()
      .statusCode(403)
      .extract()
      .as(ErrorResponse.class);
    assertThat(actual.getMessage()).isEqualTo("The requested action is forbidden by the permission policies");
  }

  @Test
  @Order(5)
  public void getSubscriptionTest_requestingIdOfOtherUser_notFound() {
    ErrorResponse actual = given()
      .spec(requestSpecOfOtherUser)
      .when()
      .get(
        String.format(
          "/%s/%s/%s/%s",
          Constants.USERS,
          otherUser.getUser().getUsername(),
          Constants.SUBSCRIPTIONS,
          subscription.getId()
        )
      )
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    assertThat(actual.getMessage()).isEqualTo("ID ERROR - There is no association between subscription and user");
  }

  @Test
  @Order(6)
  public void getSubscriptionsTest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(subscriptionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SubscriptionIO[].class);
    assertThat(actual).containsExactly(subscription);
  }

  @Test
  @Order(7)
  public void deleteSubscriptionTest() {
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(subscriptionsURL + "/" + subscription.getId())
      .then()
      .statusCode(204);

    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(subscriptionsURL + "/" + subscription.getId())
      .then()
      .statusCode(404);
  }
}
