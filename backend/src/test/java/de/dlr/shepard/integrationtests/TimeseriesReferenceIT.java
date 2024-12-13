package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.timeseries.model.Timeseries;
import de.dlr.shepard.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TimeseriesReferenceIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;

  private static String referencesURL;
  private static RequestSpecification referencesRequestSpec;
  private static String containerURL;
  private static RequestSpecification containerRequestSpec;

  private static TimeseriesContainerIO container;
  private static TimeseriesReferenceIO reference;
  private static TimeseriesWithDataPoints timeseriesWithDataPoints;

  private static int numPoints = 32;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("TimeseriesReferenceTestCollection");
    dataObject = createDataObject("TimeseriesReferenceTestDataObject", collection.getId());

    referencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATA_OBJECTS,
      dataObject.getId(),
      Constants.TIMESERIES_REFERENCES
    );
    referencesRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();

    containerURL = "/" + Constants.TIMESERIES_CONTAINERS;
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

    var currentTime = System.currentTimeMillis() * 1000000;
    var slice = (2f * Math.PI) / (numPoints - 1);

    List<TimeseriesDataPoint> dataPoints = new ArrayList<>();
    for (int i = 0; i < numPoints; i++) {
      var offset = i * 1000000000L;
      var point = new TimeseriesDataPoint(currentTime + offset, Math.sin(slice * i));
      dataPoints.add(point);
    }

    timeseriesWithDataPoints = new TimeseriesWithDataPoints(
      new Timeseries("meas", "dev", "loc", "symName", "field"),
      dataPoints
    );

    given()
      .spec(containerRequestSpec)
      .body(timeseriesWithDataPoints)
      .when()
      .post(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(201);
  }

  @Test
  @Order(1)
  public void createTimeseriesReference() {
    var nanos = timeseriesWithDataPoints.getPoints().get(0).getTimestamp();
    var toCreate = new TimeseriesReferenceIO();
    toCreate.setName("TimeseriesReferenceDummy");
    toCreate.setStart(nanos - 1000000000L);
    toCreate.setEnd(nanos + 1000000000L * numPoints);
    toCreate.setReferencedTimeseriesList(
      List.of(new ReferencedTimeseriesNodeEntity(timeseriesWithDataPoints.getTimeseries()))
    );
    toCreate.setTimeseriesContainerId(container.getId());

    var actual = given()
      .spec(referencesRequestSpec)
      .body(toCreate)
      .when()
      .post(referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(TimeseriesReferenceIO.class);
    reference = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(username);
    assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
    assertThat(actual.getStart()).isEqualTo(nanos - 1000000000L);
    assertThat(actual.getEnd()).isEqualTo(nanos + 1000000000L * numPoints);
    assertThat(actual.getName()).isEqualTo("TimeseriesReferenceDummy");
    assertThat(actual.getReferencedTimeseriesList()).isEqualTo(
      List.of(new ReferencedTimeseriesNodeEntity(timeseriesWithDataPoints.getTimeseries()))
    );
    assertThat(actual.getType()).isEqualTo("TimeseriesReference");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getTimeseriesReferences() {
    var actual = given()
      .spec(referencesRequestSpec)
      .when()
      .get(referencesURL)
      .then()
      .statusCode(200)
      .extract()
      .as(TimeseriesReferenceIO[].class);

    assertThat(actual).containsExactly(reference);
  }

  @Test
  @Order(3)
  public void getTimeseriesReference() {
    var actual = given()
      .spec(referencesRequestSpec)
      .when()
      .get(referencesURL + "/" + reference.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(TimeseriesReferenceIO.class);

    assertThat(actual).isEqualTo(reference);
  }

  @Test
  @Order(4)
  public void getTimeseriesReferencePayload() {
    var actual = given()
      .spec(referencesRequestSpec)
      .when()
      .get(String.format("%s/%d/%s", referencesURL, reference.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(200)
      .extract()
      .as(TimeseriesWithDataPoints[].class);

    assertThat(actual).containsExactly(timeseriesWithDataPoints);
  }

  @Test
  @Order(5)
  public void deleteReferences() {
    given().spec(referencesRequestSpec).when().delete(referencesURL + "/" + reference.getId()).then().statusCode(204);

    given().spec(referencesRequestSpec).when().get(referencesURL + "/" + reference.getId()).then().statusCode(404);
  }
}
