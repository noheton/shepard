package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.influxtimeseries.InfluxPoint;
import de.dlr.shepard.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.influxtimeseries.InfluxTimeseriesPayload;
import de.dlr.shepard.timeseries.io.TimeseriesContainerIO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TimeseriesIT extends BaseTestCaseIT {

  private static String containerURL;
  private static RequestSpecification containerRequestSpec;

  private static TimeseriesContainerIO container;
  private static InfluxTimeseriesPayload payload;
  private static long start;
  private static long end;

  private static int numPoints = 32;

  @BeforeAll
  public static void setUp() {
    containerURL = "/" + Constants.TIMESERIES_CONTAINERS;
    containerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
  }

  @Test
  @Order(1)
  public void createTimeseriesContainer() {
    var toCreate = new TimeseriesContainerIO();
    toCreate.setName("TimeseriesContainer");

    var actual = given()
      .spec(containerRequestSpec)
      .body(toCreate)
      .when()
      .post(containerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(TimeseriesContainerIO.class);
    container = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(username);
    assertThat(actual.getDatabase()).isNotNull();
    assertThat(actual.getName()).isEqualTo("TimeseriesContainer");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getTimeseriesContainers() {
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .get(containerURL)
      .then()
      .statusCode(200)
      .extract()
      .as(TimeseriesContainerIO[].class);

    assertThat(actual).contains(container);
  }

  @Test
  @Order(3)
  public void getTimeseriesContainer() {
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .get(containerURL + "/" + container.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(TimeseriesContainerIO.class);

    assertThat(actual).isEqualTo(container);
  }

  @Test
  @Order(4)
  public void createTimeseries() {
    var currentTime = System.currentTimeMillis() * 1000000;
    var slice = (2f * Math.PI) / (numPoints - 1);

    List<InfluxPoint> points = new ArrayList<>();
    for (int i = 0; i < numPoints; i++) {
      var offset = i * 1000000000L;
      var point = new InfluxPoint(currentTime + offset, Math.sin(slice * i));
      points.add(point);
    }

    start = points.get(0).getTimeInNanoseconds();
    end = points.get(numPoints - 1).getTimeInNanoseconds();

    payload = new InfluxTimeseriesPayload();
    payload.setTimeseries(new InfluxTimeseries("meas", "dev", "loc", "symName", "field"));
    payload.setPoints(points);

    var actual = given()
      .spec(containerRequestSpec)
      .body(payload)
      .when()
      .post(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(201)
      .extract()
      .as(InfluxTimeseries.class);

    assertThat(actual).isEqualTo(payload.getTimeseries());
  }

  @Test
  @Order(5)
  public void getTimeseriesAvailable() {
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .get(containerURL + "/" + container.getId() + "/" + Constants.AVAILABLE)
      .then()
      .statusCode(200)
      .extract()
      .as(InfluxTimeseries[].class);

    assertThat(actual).contains(new InfluxTimeseries("meas", "dev", "loc", "symName", "field"));
  }

  @Test
  @Order(6)
  public void getTimeseriesPayload() {
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .queryParams(
        Map.of(
          "measurement",
          "meas",
          "location",
          "loc",
          "device",
          "dev",
          "symbolic_name",
          "symName",
          "field",
          "field",
          "start",
          start,
          "end",
          end
        )
      )
      .get(containerURL + "/" + container.getId() + "/" + Constants.PAYLOAD)
      .then()
      .statusCode(200)
      .extract()
      .as(InfluxTimeseriesPayload.class);

    assertThat(actual).isEqualTo(payload);
  }

  @Test
  @Order(7)
  public void importTimeseriesPayload()
    throws URISyntaxException, NoSuchAlgorithmException, FileNotFoundException, IOException {
    var file = new File(getClass().getClassLoader().getResource("timeseries_import.csv").toURI());
    given()
      .spec(containerRequestSpec)
      .contentType(ContentType.MULTIPART)
      .multiPart(file)
      .when()
      .post(String.format("%s/%d/%s", containerURL, container.getId(), Constants.IMPORT))
      .then()
      .statusCode(200);
  }

  @Test
  @Order(8)
  public void exportTimeseriesPayload() throws URISyntaxException, IOException {
    var importFile = new File(getClass().getClassLoader().getResource("timeseries_import.csv").toURI());
    var expected = Files.readString(importFile.toPath());
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .queryParam("device", "temp-sensor")
      .queryParam("field", "temperature")
      .queryParam("location", "living-room")
      .queryParam("measurement", "apartment")
      .queryParam("symbolic_name", "my-favorite")
      .queryParam("start", 1708339761809582900L)
      .queryParam("end", 1708339881809582900L)
      .get(String.format("%s/%d/%s", containerURL, container.getId(), Constants.EXPORT))
      .then()
      .statusCode(200)
      .extract()
      .asString();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Order(9)
  public void deleteContainer() {
    given().spec(containerRequestSpec).when().delete(containerURL + "/" + container.getId()).then().statusCode(204);
    given().spec(containerRequestSpec).when().get(containerURL + "/" + container.getId()).then().statusCode(404);
  }
}
