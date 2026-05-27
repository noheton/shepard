package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.auth.apikey.io.ApiKeyIO;
import de.dlr.shepard.common.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * v5 wire-fidelity test for {@code POST /shepard/api/users/{username}/apikeys}.
 *
 * <p>Surfaces drift on the ApiKey creation shape — particularly the fork-added
 * {@code roles} field (A0 §4.2) and the {@code jwt} field that is only returned
 * on creation (never on subsequent GETs). If a future PR renames, removes, or
 * type-changes any of these fields without an upstream-compat policy entry, this
 * fixture breaks the build.
 *
 * <p>The test uses a deterministic name ({@code "ApiKeyWireFixture"}) so the
 * recorded shape is byte-stable modulo the well-known dynamic fields
 * ({@code uid}, {@code createdAt}, {@code belongsTo}, {@code jwt}).
 *
 * <p>GET /apikeys list coverage is handled by {@link
 * de.dlr.shepard.integrationtests.ApiKeyIT} — list length is not predictable
 * across integration-test ordering, so a list fixture would be fragile.
 *
 * @see V5WireFidelityTest
 */
@QuarkusIntegrationTest
public class ApiKeyV5WireFidelityIT extends V5WireFidelityTest {

  private static final String SLUG = "apikeys";
  private static final String API_KEYS_URL =
    "/" + Constants.USERS + "/" + nameOfDefaultUser + "/" + Constants.APIKEYS;

  /** ApiKey-specific dynamic fields (in addition to {@link #DEFAULT_DYNAMIC_FIELDS}). */
  @Override
  protected Map<String, String> dynamicFields() {
    Map<String, String> m = new LinkedHashMap<>(DEFAULT_DYNAMIC_FIELDS);
    // uid — UUID v4 minted by the service on creation
    m.put("uid", V5JsonNormalizer.ANY_STRING);
    // jwt — the signed key token, always dynamic
    m.put("jwt", V5JsonNormalizer.ANY_STRING);
    // belongsTo — the username of the authenticated caller
    m.put("belongsTo", V5JsonNormalizer.ANY_STRING);
    return Map.copyOf(m);
  }

  @Test
  public void createApiKey_wireMatchesFixture() {
    var toCreate = new ApiKeyIO();
    toCreate.setName("ApiKeyWireFixture");

    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(API_KEYS_URL)
      .then()
      .statusCode(201)
      .extract()
      .response();

    assertWireMatches(SLUG, "create", response);
  }
}
