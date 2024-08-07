package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TimeseriesCsvIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;

  private static String referencesURL;
  private static RequestSpecification referencesRequestSpec;
  private static String containerURL;
  private static RequestSpecification containerRequestSpec;

  private static TimeseriesContainerIO container;
  private static TimeseriesReferenceIO reference;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("TimeseriesReferenceTestCollection");
    dataObject = createDataObject("TimeseriesReferenceTestDataObject", collection.getId());

    referencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATAOBJECTS,
      dataObject.getId(),
      Constants.TIMESERIES_REFERENCES
    );
    referencesRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();

    containerURL = "/" + Constants.TIMESERIES;
    containerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();

    var toCreate = new TimeseriesContainerIO();
    toCreate.setName("TimeseriesContainer");
    container = given()
      .spec(containerRequestSpec)
      .body(toCreate)
      .when()
      .post(containerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(TimeseriesContainerIO.class);
  }

  @Test
  @Order(1)
  public void importTimeseriesCsv()
    throws URISyntaxException, NoSuchAlgorithmException, FileNotFoundException, IOException {
    var newFile = new File(getClass().getClassLoader().getResource("timeseries_export.csv").toURI());
    given()
      .spec(containerRequestSpec)
      .contentType(ContentType.MULTIPART)
      .multiPart(newFile)
      .when()
      .post(String.format("%s/%d/%s", containerURL, container.getId(), Constants.IMPORT))
      .then()
      .statusCode(200);
  }

  @Test
  @Order(2)
  public void createReference() {
    var ts1 = new Timeseries("MyMeas", "MyDev", "MyLoc", "MySymName", "value");
    var ts2 = new Timeseries("Different", "Just", "For", "Testing", "Purposes");
    var timeseries = new Timeseries[] { ts1, ts2 };

    var toCreate = new TimeseriesReferenceIO();
    toCreate.setName("TimeseriesReferenceDummy");
    toCreate.setStart(1708067683056000000L);
    toCreate.setEnd(1708068043057000000L);
    toCreate.setTimeseries(timeseries);
    toCreate.setTimeseriesContainerId(container.getId());
    reference = given()
      .spec(referencesRequestSpec)
      .body(toCreate)
      .when()
      .post(referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(TimeseriesReferenceIO.class);
  }

  @Test
  @Order(3)
  public void exportTimeseriesCsv() throws URISyntaxException, IOException {
    var oldFile = new File(getClass().getClassLoader().getResource("timeseries_export.csv").toURI());
    var expected = Files.readString(oldFile.toPath());
    var actual = given()
      .spec(referencesRequestSpec)
      .when()
      .get(String.format("%s/%d/%s", referencesURL, reference.getId(), Constants.EXPORT))
      .then()
      .statusCode(200)
      .extract()
      .asString();

    assertThat(actual).isEqualTo(expected);
  }
}
