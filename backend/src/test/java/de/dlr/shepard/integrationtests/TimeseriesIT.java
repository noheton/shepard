package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.data.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.services.InstantHelper;
import io.quarkus.test.junit.QuarkusIntegrationTest;
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

  private static TimeseriesContainerIO container;
  private static TimeseriesWithDataPoints payload;
  private static long start;
  private static long end;

  @BeforeAll
  public static void setUp() {
    containerURL = Constants.TIMESERIES_CONTAINERS;
  }

  @Test
  @Order(1)
  public void createTimeseriesContainer() {
    var containerName = "TimeseriesContainer";
    var toCreate = new TimeseriesContainerIO();
    toCreate.setName(containerName);

    var actual = given()
      .spec(requestSpecOfDefaultUser)
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
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getName()).isEqualTo(containerName);
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getAllTimeseriesContainers() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
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
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(containerURL + "/" + container.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(TimeseriesContainerIO.class);

    assertThat(actual).isEqualTo(container);
  }

  @Test
  public void getTimeseriesContainer_doesNotExist_notFound() {
    ErrorResponse actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(containerURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    assertThat(actual.getMessage()).isEqualTo("ID ERROR - Timeseries Container with id 99999 is null or deleted");
  }

  @Test
  public void getTimeseriesContainer_doesNotExistNegativeId_badRequest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(containerURL + "/-1")
      .then()
      .statusCode(400)
      .extract();
    assertThat(actual.body().asString()).isEqualTo(
      "{\"title\":\"Constraint Violation\",\"status\":400,\"violations\":[{\"field\":\"getTimeseriesContainer.timeseriesContainerId\",\"message\":\"must be greater than or equal to 0\"}]}"
    );
  }

  @Test
  @Order(4)
  public void createTimeseries() {
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    start = instantHelper.toNano();
    List<TimeseriesDataPoint> dataPointsIO = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );
    end = instantHelper.addSeconds(2).toNano();

    payload = new TimeseriesWithDataPoints(timeseries, dataPointsIO);

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(201)
      .extract()
      .as(Timeseries.class);

    assertThat(actual).isEqualTo(payload.getTimeseries());
  }

  @Test
  @Order(5)
  public void getTimeseriesAvailable() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(containerURL + "/" + container.getId() + "/" + Constants.AVAILABLE)
      .then()
      .statusCode(200)
      .extract()
      .as(Timeseries[].class);

    Timeseries expectedTimeseriesIO = new Timeseries("temperature", "device", "location", "symbolicName", "field");

    assertThat(actual).contains(expectedTimeseriesIO);
  }

  @Test
  @Order(6)
  public void getTimeseries() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
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
      .as(TimeseriesWithDataPoints.class);

    assertThat(actual).isEqualTo(payload);
  }

  @Test
  @Order(6)
  public void getTimeseries_startNotSet_badRequest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
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
          Constants.END,
          end
        )
      )
      .get(containerURL + "/" + container.getId() + "/" + Constants.PAYLOAD)
      .then()
      .statusCode(400)
      .extract();

    assertThat(actual.body().asString()).isEqualTo(
      "{\"title\":\"Constraint Violation\",\"status\":400,\"violations\":[{\"field\":\"getTimeseries.start\",\"message\":\"must not be null\"}]}"
    );
  }

  @Test
  @Order(6)
  public void getTimeseries_measurementIsBlank_badRequest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .queryParams(
        Map.of(
          Constants.MEASUREMENT,
          "   ",
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
      .statusCode(400)
      .extract();

    assertThat(actual.body().asString()).isEqualTo(
      "{\"title\":\"Constraint Violation\",\"status\":400,\"violations\":[{\"field\":\"getTimeseries.measurement\",\"message\":\"must not be blank\"}]}"
    );
  }

  @Test
  @Order(7)
  public void deleteContainer() {
    given().spec(requestSpecOfDefaultUser).when().delete(containerURL + "/" + container.getId()).then().statusCode(204);
    given().spec(requestSpecOfDefaultUser).when().get(containerURL + "/" + container.getId()).then().statusCode(404);
  }
}
