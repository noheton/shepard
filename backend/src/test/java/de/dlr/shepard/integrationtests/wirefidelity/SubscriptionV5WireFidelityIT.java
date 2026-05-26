package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.common.subscription.io.SubscriptionIO;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.RequestMethod;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * v5 wire-fidelity test for {@code /shepard/api/users/{username}/subscriptions}.
 *
 * <p>Exercises the standalone {@code SubscriptionIO} shape, which does NOT extend
 * {@code BasicEntityIO} and therefore carries no {@code appId}, {@code updatedAt},
 * {@code updatedBy}, or {@code revision} fields. The custom {@link #dynamicFields()}
 * override ensures those absent fields are not expected by the normaliser.
 *
 * <p>The fixture captures the exact v5 upstream wire shape for Subscription creation —
 * upstream clients see: {@code id}, {@code name}, {@code callbackURL},
 * {@code subscribedURL}, {@code requestMethod}, {@code createdBy}, {@code createdAt}.
 */
@QuarkusIntegrationTest
public class SubscriptionV5WireFidelityIT extends V5WireFidelityTest {

  private static final String SLUG = "subscriptions";

  /**
   * SubscriptionIO does not extend BasicEntityIO — override to only redact the fields
   * that are actually present in the response.
   */
  @Override
  protected Map<String, String> dynamicFields() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("id", V5JsonNormalizer.ANY_LONG);
    m.put("createdAt", V5JsonNormalizer.ANY_STRING);
    m.put("createdBy", V5JsonNormalizer.ANY_STRING);
    return m;
  }

  @Test
  public void createSubscription_wireMatchesFixture() {
    var sub = new SubscriptionIO();
    sub.setName("SubscriptionWireFixture");
    sub.setCallbackURL("http://my-callback-url.local");
    sub.setSubscribedURL("http://my-subscribed-url.local");
    sub.setRequestMethod(RequestMethod.POST);

    String url = "/%s/%s/%s".formatted(Constants.USERS, nameOfDefaultUser, Constants.SUBSCRIPTIONS);

    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .body(sub)
      .when()
      .post(url)
      .then()
      .statusCode(201)
      .extract()
      .response();

    assertWireMatches(SLUG, "create", response);
  }
}
