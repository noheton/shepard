package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesWithDataPoints;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import de.dlr.shepard.timeseries.services.InstantHelper;
import de.dlr.shepard.util.Constants;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
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
@IfBuildProperty(name = "shepard.experimental-timeseries.enabled", stringValue = "true")
public class ExperimentalTimeseriesIT extends BaseTestCaseIT {

  private static String containerURL;
  private static RequestSpecification containerRequestSpec;

  private static TimeseriesContainerIO container;
  private static ExperimentalTimeseriesWithDataPoints payload;
  private static long start;
  private static long end;

  @BeforeAll
  public static void setUp() {
    containerURL = Constants.EXPERIMENTAL_TIMESERIES_CONTAINERS;
    containerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
  }

  @Test
  @Order(1)
  public void createTimeseriesContainer() {
    var containerName = "ExperimentalTimeseriesContainer";
    var toCreate = new TimeseriesContainerIO();
    toCreate.setName(containerName);

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
    assertThat(actual.getName()).isEqualTo(containerName);
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getAllTimeseriesContainers() {
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
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    start = instantHelper.toNano();
    List<ExperimentalTimeseriesDataPoint> dataPointsIO = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );
    end = instantHelper.addSeconds(2).toNano();

    payload = new ExperimentalTimeseriesWithDataPoints(timeseries, dataPointsIO);

    var actual = given()
      .spec(containerRequestSpec)
      .body(payload)
      .when()
      .post(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(201)
      .extract()
      .as(ExperimentalTimeseries.class);

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
      .as(ExperimentalTimeseries[].class);

    ExperimentalTimeseries expectedTimeseriesIO = new ExperimentalTimeseries(
      "temperature",
      "device",
      "location",
      "symbolicName",
      "field"
    );

    assertThat(actual).contains(expectedTimeseriesIO);
  }

  @Test
  @Order(6)
  public void getTimeseries() {
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .queryParams(
        Map.of(
          Constants.MEASUREMENT,
          "temperature",
          Constants.LOCATION,
          "location",
          Constants.DEVICE,
          "device",
          Constants.SYMBOLICNAME,
          "symbolicName",
          Constants.FIELD,
          "field",
          Constants.START,
          start,
          Constants.END,
          end
        )
      )
      .get(containerURL + "/" + container.getId() + "/" + Constants.PAYLOAD)
      .then()
      .statusCode(200)
      .extract()
      .as(ExperimentalTimeseriesWithDataPoints.class);

    assertThat(actual).isEqualTo(payload);
  }

  @Test
  @Order(7)
  public void deleteContainer() {
    given().spec(containerRequestSpec).when().delete(containerURL + "/" + container.getId()).then().statusCode(204);
    given().spec(containerRequestSpec).when().get(containerURL + "/" + container.getId()).then().statusCode(404);
  }
}
