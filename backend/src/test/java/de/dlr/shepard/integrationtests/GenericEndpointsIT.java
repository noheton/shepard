package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
class EndpointTestCase {

  private String url;
  private List<String> methods;
  private Map<String, String> validPathParams;
  private Map<String, String> invalidPathParams;
  private Map<String, String> notFoundPathParams;
  private Map<String, Map<String, Integer>> expectedStatusCodes;
  private Map<String, Object> requestBody;
  private Map<String, Object> invalidRequestBody;
}

@QuarkusIntegrationTest
public class GenericEndpointsIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;

  @BeforeAll
  public static void setup() {
    collection = createCollection("A Collection");
    dataObject = createDataObject("A dataobject", collection.getId());
  }

  /**
   * This parametrized test will perform all the needed tests on each test case.
   * The covered test cases:
   * - Requests with valid path
   * - Requests with invalid path
   * - Requests with valid body (POST, PUT)
   * - Requests with invalid body (POST, PUT)
   * - Requests for not found resource
   * - Unauthorized access
   * - Unauthorized public access (no user)
   *
   * Hint: Keep delete the last test case as it will delete the tested resource.
   */
  @ParameterizedTest
  @MethodSource("getEndpoints")
  public void testEndpoints(EndpointTestCase testCase) {
    for (String method : testCase.getMethods()) {
      if (method.equals("POST") || method.equals("PUT")) {
        testValidRequestsWithBody(testCase, method);
        testInvalidRequestsWithInvalidBody(testCase, method);
      } else {
        testValidRequests(testCase, method);
        testInvalidPathParams(testCase, method);
      }
      testNotFound(testCase, method);
      testUnauthorizedAccess(testCase, method);
      testUnauthenticatedAccess(testCase, method);
    }
  }

  private void testValidRequests(EndpointTestCase testCase, String method) {
    given()
      .spec(requestSpecOfDefaultUser)
      .pathParams(testCase.getValidPathParams())
      .when()
      .request(method, testCase.getUrl())
      .then()
      .statusCode(testCase.getExpectedStatusCodes().get(method).get("valid"));
  }

  private void testInvalidPathParams(EndpointTestCase testCase, String method) {
    if (testCase.getInvalidPathParams() == null) return;
    given()
      .spec(requestSpecOfDefaultUser)
      .pathParams(testCase.getInvalidPathParams())
      .when()
      .request(method, testCase.getUrl())
      .then()
      .statusCode(testCase.getExpectedStatusCodes().get(method).get("invalid"));
  }

  private void testValidRequestsWithBody(EndpointTestCase testCase, String method) {
    given()
      .spec(requestSpecOfDefaultUser)
      .pathParams(testCase.getValidPathParams())
      .when()
      .body(testCase.getRequestBody())
      .request(method, testCase.getUrl())
      .then()
      .statusCode(testCase.getExpectedStatusCodes().get(method).get("valid"));
  }

  private void testInvalidRequestsWithInvalidBody(EndpointTestCase testCase, String method) {
    given()
      .spec(requestSpecOfDefaultUser)
      .pathParams(testCase.getValidPathParams())
      .when()
      .body(testCase.getInvalidRequestBody())
      .request(method, testCase.getUrl())
      .then()
      .statusCode(testCase.getExpectedStatusCodes().get(method).get("invalid"));
  }

  private void testUnauthorizedAccess(EndpointTestCase testCase, String method) {
    if (testCase.getExpectedStatusCodes().get(method).get("unauthorized") == null) return;
    given()
      .spec(requestSpecOfOtherUser)
      .pathParams(testCase.getValidPathParams())
      .when()
      .request(method, testCase.getUrl())
      .then()
      .statusCode(testCase.getExpectedStatusCodes().get(method).get("unauthorized"));
  }

  private void testUnauthenticatedAccess(EndpointTestCase testCase, String method) {
    if (testCase.getExpectedStatusCodes().get(method).get("unauthenticated") == null) return;
    given()
      .spec(requestSpecNoUser)
      .pathParams(testCase.getValidPathParams())
      .when()
      .request(method, testCase.getUrl())
      .then()
      .statusCode(testCase.getExpectedStatusCodes().get(method).get("unauthenticated"));
  }

  private void testNotFound(EndpointTestCase testCase, String method) {
    if (testCase.getExpectedStatusCodes().get(method).get("notFound") == null) return;
    given()
      .spec(requestSpecOfDefaultUser)
      .pathParams(testCase.getNotFoundPathParams())
      .when()
      .request(method, testCase.getUrl())
      .then()
      .statusCode(testCase.getExpectedStatusCodes().get(method).get("notFound"));
  }

  public static List<EndpointTestCase> loadTestCases() {
    return List.of(
      new EndpointTestCase(
        "/collections",
        List.of("GET", "POST"),
        Map.of(),
        null,
        null,
        Map.of(
          "GET",
          Map.of("valid", 200, "unauthenticated", 401),
          "POST",
          Map.of("valid", 201, "invalid", 400, "unauthenticated", 401)
        ),
        Map.of("name", "coll name"),
        Map.of()
      ),
      new EndpointTestCase(
        "/collections/{collectionId}",
        List.of("GET", "PUT"),
        Map.of("collectionId", Long.toString(collection.getId())),
        Map.of("collectionId", "abc"),
        Map.of("collectionId", "999999999"),
        Map.of(
          "GET",
          Map.of("valid", 200, "invalid", 403, "unauthorized", 403, "unauthenticated", 401, "notFound", 404),
          "PUT",
          Map.of("valid", 200, "invalid", 400, "unauthorized", 403, "unauthenticated", 401, "notFound", 404)
        ),
        Map.of("name", "coll name"),
        Map.of()
      ),
      new EndpointTestCase(
        "/collections/{collectionId}/permissions",
        List.of("GET", "PUT"),
        Map.of("collectionId", Long.toString(collection.getId())),
        Map.of("collectionId", "abc"),
        Map.of("collectionId", "999999999"),
        Map.of(
          "GET",
          Map.of("valid", 200, "invalid", 403, "unauthorized", 403, "unauthenticated", 401, "notFound", 404),
          "PUT",
          Map.of("valid", 200, "invalid", 400, "unauthorized", 403, "unauthenticated", 401, "notFound", 404)
        ),
        Map.of(
          "owner",
          nameOfDefaultUser,
          "permissionType",
          "Private",
          "reader",
          List.of(),
          "writer",
          List.of(),
          "readerGroupIds",
          List.of(),
          "writerGroupIds",
          List.of(),
          "manager",
          List.of()
        ),
        Map.of()
      ),
      new EndpointTestCase(
        "/collections/{collectionId}/roles",
        List.of("GET"),
        Map.of("collectionId", Long.toString(collection.getId())),
        Map.of("collectionId", "abc"),
        Map.of("collectionId", "999999999"),
        Map.of(
          "GET",
          Map.of("valid", 200, "invalid", 403, "unauthorized", 403, "unauthenticated", 401, "notFound", 404)
        ),
        Map.of(),
        Map.of()
      ),
      new EndpointTestCase(
        "/collections/{collectionId}/export",
        List.of("GET"),
        Map.of("collectionId", Long.toString(collection.getId())),
        Map.of("collectionId", "abc"),
        Map.of("collectionId", "999999999"),
        Map.of(
          "GET",
          Map.of("valid", 200, "invalid", 403, "unauthorized", 403, "unauthenticated", 401, "notFound", 404)
        ),
        Map.of(),
        Map.of()
      ),
      new EndpointTestCase(
        "/collections/{collectionId}/dataObjects/{dataObjectId}",
        List.of("GET"),
        Map.of("collectionId", Long.toString(collection.getId()), "dataObjectId", Long.toString(dataObject.getId())),
        Map.of("collectionId", Long.toString(collection.getId()), "dataObjectId", "abc"),
        Map.of("collectionId", Long.toString(collection.getId()), "dataObjectId", "9999999"),
        Map.of(
          "GET",
          Map.of("valid", 200, "invalid", 404, "unauthorized", 403, "unauthenticated", 401, "notFound", 404)
        ),
        Map.of("name", "coll name"),
        Map.of()
      ),
      new EndpointTestCase(
        "/collections/{collectionId}",
        List.of("DELETE"),
        Map.of("collectionId", Long.toString(collection.getId())),
        Map.of("collectionId", "abc"),
        Map.of("collectionId", "999999999"),
        Map.of(
          "DELETE",
          Map.of("valid", 204, "invalid", 403, "unauthorized", 403, "unauthenticated", 401, "notFound", 404)
        ),
        Map.of(),
        Map.of()
      )
    );
  }

  private static Stream<EndpointTestCase> getEndpoints() {
    return loadTestCases().stream();
  }
}
