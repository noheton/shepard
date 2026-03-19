package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
public class GracePeriodIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static String collectionsURL;
  private static String permissionsURL;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("PermissionsTestCollection");
    collectionsURL = "/" + Constants.COLLECTIONS;
    permissionsURL = "/%s/%d/%s".formatted(Constants.COLLECTIONS, collection.getId(), Constants.PERMISSIONS);
  }

  @Test
  public void retrieve() {
    given().spec(requestSpecOfDefaultUser).when().get(collectionsURL + "/" + collection.getId()).then().statusCode(200);

    var permissionsLockingOutUser = new PermissionsIO() {
      {
        setReader(new String[] {});
        setWriter(new String[] {});
        setManager(new String[] {});
      }
    };

    given()
      .spec(requestSpecOfDefaultUser)
      .body(permissionsLockingOutUser)
      .when()
      .put(permissionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(PermissionsIO.class);

    given().spec(requestSpecOfDefaultUser).when().get(collectionsURL + "/" + collection.getId()).then().statusCode(200);
  }
}
