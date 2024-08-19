package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
public class GracePeriodIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static String collectionsURL;
  private static String permissionsURL;
  private static RequestSpecification requestSpecification;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("PermissionsTestCollection");
    collectionsURL = "/" + Constants.COLLECTIONS;
    permissionsURL = String.format("/%s/%d/%s", Constants.COLLECTIONS, collection.getId(), Constants.PERMISSIONS);
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
  }

  @Test
  public void retrieve() {
    given().spec(requestSpecification).when().get(collectionsURL + "/" + collection.getId()).then().statusCode(200);

    var permissionsLockingOutUser = new PermissionsIO() {
      {
        setReader(new String[] {});
        setWriter(new String[] {});
        setManager(new String[] {});
      }
    };

    given()
      .spec(requestSpecification)
      .body(permissionsLockingOutUser)
      .when()
      .put(permissionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(PermissionsIO.class);

    given().spec(requestSpecification).when().get(collectionsURL + "/" + collection.getId()).then().statusCode(200);
  }
}
