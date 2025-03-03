package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.RandomGenerator;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.JsonConverter;
import de.dlr.shepard.data.spatialdata.io.FilterCondition;
import de.dlr.shepard.data.spatialdata.io.Operator;
import de.dlr.shepard.data.spatialdata.io.SpatialDataContainerIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.AxisAlignedBoundingBox;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.BoundingSphere;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.KNearestNeighbor;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SpatialDataIT extends BaseTestCaseIT {

  private static String containerURL;
  private static RequestSpecification containerRequestSpec;
  private static SpatialDataContainerIO container;
  private static ArrayList<SpatialDataContainerIO> existingContainers = new ArrayList<>();

  @BeforeAll
  public static void setUp() {
    containerURL = Constants.SPATIAL_DATA_CONTAINERS;
    containerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();

    IntStream.range(0, 50).forEach(index -> {
      var containerName = "container_" + index + RandomGenerator.generateString(20);
      var toCreate = new SpatialDataContainerIO();
      toCreate.setName(containerName);

      var actual = given()
        .spec(containerRequestSpec)
        .body(toCreate)
        .when()
        .post(containerURL)
        .then()
        .statusCode(201)
        .extract()
        .as(SpatialDataContainerIO.class);
      existingContainers.add(actual);
    });
  }

  @AfterAll
  public static void tearDown() {
    existingContainers.forEach(container ->
      given().spec(containerRequestSpec).when().delete(containerURL + "/" + container.getId())
    );
  }

  @Test
  @Order(1)
  public void createSpatialDataContainer() {
    var containerName = "SpatialContainer";
    var toCreate = new SpatialDataContainerIO();
    toCreate.setName(containerName);

    var actual = given()
      .spec(containerRequestSpec)
      .body(toCreate)
      .when()
      .post(containerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(SpatialDataContainerIO.class);
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
  public void getAllSpatialDataContainers_success() {
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .get(containerURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataContainerIO[].class);

    assertThat(actual).contains(container);
  }

  @Test
  @Order(2)
  public void getAllSpatialDataContainers_filterByName_success() {
    var actual = given()
      .spec(containerRequestSpec)
      .queryParam("name", container.getName())
      .when()
      .get(containerURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataContainerIO[].class);

    assertThat(actual).contains(container);

    actual = given()
      .spec(containerRequestSpec)
      .queryParam("name", RandomGenerator.generateString(20))
      .when()
      .get(containerURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataContainerIO[].class);

    assertThat(actual).doesNotContain(container);
  }

  @Test
  @Order(2)
  public void getAllSpatialDataContainers_requestPage_success() {
    var allContainers = given()
      .spec(containerRequestSpec)
      .when()
      .get(containerURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataContainerIO[].class);
    var firstPage = given()
      .spec(containerRequestSpec)
      .queryParam("page", 0)
      .queryParam("size", 3)
      .when()
      .get(containerURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataContainerIO[].class);

    var thirdPage = given()
      .spec(containerRequestSpec)
      .queryParam("page", 2)
      .queryParam("size", 3)
      .when()
      .get(containerURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataContainerIO[].class);

    assertTrue(firstPage.length == 3);
    assertTrue(thirdPage.length == 3);

    SpatialDataContainerIO[] expectedFirstPge = { allContainers[0], allContainers[1], allContainers[2] };
    SpatialDataContainerIO[] expectedThirdPge = { allContainers[6], allContainers[7], allContainers[8] };
    assertArrayEquals(expectedFirstPge, firstPage);
    assertArrayEquals(expectedThirdPge, thirdPage);
  }

  @Test
  @Order(2)
  public void getAllSpatialDataContainers_orderBy_success() {
    var allContainersDesc = given()
      .spec(containerRequestSpec)
      .queryParam("orderBy", "createdAt")
      .queryParam("orderDesc", true)
      .when()
      .get(containerURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataContainerIO[].class);

    var allContainersAsc = given()
      .spec(containerRequestSpec)
      .queryParam("orderBy", "createdAt")
      .queryParam("orderDesc", false)
      .when()
      .get(containerURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataContainerIO[].class);
    // Check for reverse order
    IntStream.range(0, allContainersAsc.length).forEach(index ->
      assertTrue(
        allContainersAsc[index].getCreatedAt()
          .before(allContainersDesc[allContainersAsc.length - 1 - index].getCreatedAt()) ||
        allContainersAsc[index].getCreatedAt()
          .equals(allContainersDesc[allContainersAsc.length - 1 - index].getCreatedAt())
      )
    );
  }

  @Test
  @Order(4)
  public void getSpatialDataContainer_success() {
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .get(containerURL + "/" + container.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataContainerIO.class);

    assertThat(actual).isEqualTo(container);
  }

  @Test
  @Order(5)
  public void getSpatialDataContainer_doesNotExist_notFound() {
    ErrorResponse actual = given()
      .spec(containerRequestSpec)
      .when()
      .get(containerURL + "/999999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    assertThat(actual.getMessage()).isEqualTo("Spatial data container with id 999999 not found.");
  }

  @Test
  @Order(6)
  public void addSpatialDataPoints_success() {
    var dataPoints = IntStream.range(0, 10)
      .mapToObj(index ->
        new SpatialDataPointIO(
          Long.valueOf(index),
          index * 10.0,
          index * 10.0,
          index * 10.0,
          Map.of("a_measurement", index),
          Map.of("a_meta_data", "metadata_%s".formatted(index))
        )
      )
      .collect(Collectors.toList());
    given()
      .spec(containerRequestSpec)
      .body(dataPoints)
      .when()
      .patch(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(200);
  }

  @Test
  @Order(7)
  public void getSpatialData_geometryFilterBoundingSphere_success() {
    BoundingSphere boundingSphere = new BoundingSphere();
    boundingSphere.set(49, 0, 0, 0);

    String geometryFilter = JsonConverter.convertToString(boundingSphere);
    var dataPoints = given()
      .spec(containerRequestSpec)
      .queryParam("geometryFilter", geometryFilter)
      .when()
      .get(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .extract()
      .as(SpatialDataPointIO[].class);
    // Sphere of radius 49 will include the points [0, 0, 0] [10, 10, 10] and [20, 20, 20]
    assertEquals(dataPoints.length, 3);
  }

  @Test
  @Order(7)
  public void getSpatialData_geometryFilterAxisAlignedBoundingBox_success() {
    AxisAlignedBoundingBox axisAlignedBoundingBox = new AxisAlignedBoundingBox();
    axisAlignedBoundingBox.set(-1, -1, -1, 49, 49, 49);

    String geometryFilter = JsonConverter.convertToString(axisAlignedBoundingBox);
    var dataPoints = given()
      .spec(containerRequestSpec)
      .queryParam("geometryFilter", geometryFilter)
      .when()
      .get(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .extract()
      .as(SpatialDataPointIO[].class);
    assertEquals(dataPoints.length, 5);
  }

  @Test
  @Order(7)
  public void getSpatialData_geometryFilterKNN_success() {
    KNearestNeighbor kNearestNeighbor = new KNearestNeighbor();
    kNearestNeighbor.set(3, 1, 1, 1);

    String geometryFilter = JsonConverter.convertToString(kNearestNeighbor);

    var dataPoints = given()
      .spec(containerRequestSpec)
      .queryParam("geometryFilter", geometryFilter)
      .when()
      .get(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .extract()
      .as(SpatialDataPointIO[].class);
    assertEquals(dataPoints.length, 3);
  }

  @Test
  @Order(7)
  public void getSpatialData_geometryFilterKNNWithMetadataFilter_success() {
    KNearestNeighbor kNearestNeighbor = new KNearestNeighbor();
    kNearestNeighbor.set(3, 1, 1, 1);

    String geometryFilter = JsonConverter.convertToString(kNearestNeighbor);
    String metadataFilter = JsonConverter.convertToString(Map.of("a_meta_data", "metadata_1"));
    var dataPoints = given()
      .spec(containerRequestSpec)
      .queryParam("geometryFilter", geometryFilter)
      .queryParam("metadataFilter", metadataFilter)
      .when()
      .get(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .extract()
      .as(SpatialDataPointIO[].class);
    assertEquals(dataPoints.length, 1);
    assertEquals(dataPoints[0].getMetadata().get("a_meta_data"), "metadata_1");
  }

  @Test
  @Order(7)
  public void getSpatialData_geometryFilterKNNWithMeasurementsFilter_success() {
    KNearestNeighbor kNearestNeighbor = new KNearestNeighbor();
    kNearestNeighbor.set(3, 1, 1, 1);

    String geometryFilter = JsonConverter.convertToString(kNearestNeighbor);
    List<FilterCondition> measurementsFilters = List.of(new FilterCondition("a_measurement", Operator.EQUALS, 1));
    String measurementsFilter = JsonConverter.convertToString(measurementsFilters);
    var dataPoints = given()
      .spec(containerRequestSpec)
      .queryParam("geometryFilter", geometryFilter)
      .queryParam("measurementsFilter", measurementsFilter)
      .when()
      .get(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .extract()
      .as(SpatialDataPointIO[].class);
    assertEquals(dataPoints.length, 1);
    assertEquals(dataPoints[0].getMeasurements().get("a_measurement"), 1);
  }

  @Test
  @Order(7)
  public void getSpatialData_geometryFilterKNNWithTimeDuration_success() {
    KNearestNeighbor kNearestNeighbor = new KNearestNeighbor();
    kNearestNeighbor.set(50, 1, 1, 1);

    String geometryFilter = JsonConverter.convertToString(kNearestNeighbor);
    var dataPoints = given()
      .spec(containerRequestSpec)
      .queryParam("geometryFilter", geometryFilter)
      .queryParam("startTime", -1)
      .queryParam("endTime", 5)
      .when()
      .get(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .extract()
      .as(SpatialDataPointIO[].class);
    assertEquals(dataPoints.length, 5);
  }

  @Test
  @Order(7)
  public void getSpatialData_geometryFilterBoundingSphereWithLimit_success() {
    BoundingSphere boundingSphere = new BoundingSphere();
    boundingSphere.set(110, 0, 0, 0);

    String geometryFilter = JsonConverter.convertToString(boundingSphere);
    var dataPoints = given()
      .spec(containerRequestSpec)
      .queryParam("geometryFilter", geometryFilter)
      .queryParam("limit", 5)
      .when()
      .get(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .extract()
      .as(SpatialDataPointIO[].class);
    assertEquals(dataPoints.length, 5);
  }

  @Test
  @Order(7)
  public void getSpatialData_geometryFilterKNNWithSkip_success() {
    BoundingSphere boundingSphere = new BoundingSphere();
    boundingSphere.set(200, 0, 0, 0);

    String geometryFilter = JsonConverter.convertToString(boundingSphere);
    var dataPoints = given()
      .spec(containerRequestSpec)
      .queryParam("geometryFilter", geometryFilter)
      .queryParam("skip", 2)
      .when()
      .get(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .extract()
      .as(SpatialDataPointIO[].class);
    assertEquals(dataPoints.length, 5);
  }

  @Test
  @Order(8)
  public void deleteContainer() {
    given().spec(containerRequestSpec).when().delete(containerURL + "/" + container.getId()).then().statusCode(200);
    given().spec(containerRequestSpec).when().get(containerURL + "/" + container.getId()).then().statusCode(404);
  }
}
