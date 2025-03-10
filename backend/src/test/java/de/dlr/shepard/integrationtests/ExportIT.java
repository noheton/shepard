package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExportIT extends BaseTestCaseIT {

  private static String collectionsURL;
  private static CollectionIO collection;
  private static DataObjectIO dataObject;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("ExportTestCollection");
    dataObject = createDataObject("ExportTestDataObject", collection.getId());

    collectionsURL = "/" + Constants.COLLECTIONS + "/" + collection.getId() + "/export";
  }

  @Test
  @Order(1)
  public void exportCollection_successful() throws IOException {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(collectionsURL)
      .then()
      .statusCode(200)
      .extract()
      .asInputStream();
    var zis = new ZipInputStream(actual);
    var filenames = new ArrayList<String>();

    var zipEntry = zis.getNextEntry();
    while (zipEntry != null) {
      filenames.add(zipEntry.getName());
      zipEntry = zis.getNextEntry();
    }

    assertThat(filenames).containsExactlyInAnyOrder(
      collection.getId() + ".json",
      dataObject.getId() + ".json",
      "ro-crate-metadata.json"
    );
  }
}
