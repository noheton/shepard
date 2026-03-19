package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.spatialdata.io.SpatialDataReferenceIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataContainerIO;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SpatialDataReferenceIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;

  private static String referencesURL;
  private static String containerURL;

  private static SpatialDataContainerIO container;
  private static SpatialDataReferenceIO reference;
  private static List<SpatialDataPointIO> spatialDataPoints;

  @BeforeAll
  public static void setUp() throws URISyntaxException {
    collection = createCollection("SpatialDataReferenceTestCollection");
    dataObject = createDataObject("SpatialDataReferenceTestDataObject", collection.getId());

    referencesURL = "/%s/%d/%s/%d/%s".formatted(
        Constants.COLLECTIONS,
        collection.getId(),
        Constants.DATA_OBJECTS,
        dataObject.getId(),
        Constants.SPATIAL_DATA_REFERENCES
      );

    containerURL = "/" + Constants.SPATIAL_DATA_CONTAINERS;

    var toCreate = new SpatialDataContainerIO();
    toCreate.setName("SpatialDataContainer");

    spatialDataPoints = new ArrayList<>();

    Map<String, Object> measurement = Map.of("temperature", 20);
    for (int i = 0; i < 10; i++) {
      spatialDataPoints.add(
        new SpatialDataPointIO((long) i, (double) i, (double) i, (double) i, Collections.emptyMap(), measurement)
      );
    }

    container = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(containerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(SpatialDataContainerIO.class);

    given()
      .spec(requestSpecOfDefaultUser)
      .body(spatialDataPoints)
      .when()
      .post("%s/%d/%s".formatted(containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(204);
  }

  @Test
  @Order(1)
  public void createSpatialDataReference() {
    var toCreate = new SpatialDataReferenceIO();
    toCreate.setName("SpatialDataReferenceDummy");
    toCreate.setSpatialDataContainerId(container.getId());

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(SpatialDataReferenceIO.class);
    reference = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
    assertThat(actual.getName()).isEqualTo("SpatialDataReferenceDummy");
    assertThat(actual.getSpatialDataContainerId()).isEqualTo(container.getId());
    assertThat(actual.getType()).isEqualTo("SpatialDataReference");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
    assertThat(actual.getGeometryFilter()).isNull();
    assertThat(actual.getMeasurementsFilter()).isEqualTo(Collections.emptyList());
    assertThat(actual.getMetadataFilter()).isEqualTo(Collections.emptyMap());
    assertThat(actual.getStartTime()).isNull();
    assertThat(actual.getEndTime()).isNull();
    assertThat(actual.getLimit()).isNull();
    assertThat(actual.getSkip()).isNull();
  }

  @Test
  @Order(2)
  public void getSpatialDataReferences() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataReferenceIO[].class);
    assertThat(actual).containsExactly(reference);
  }

  @Test
  @Order(3)
  public void getSpatialDataReference() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/" + reference.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataReferenceIO.class);

    assertThat(actual).isEqualTo(reference);
  }

  @Test
  @Order(4)
  public void getSpatialDataReference_doesNotExist_notFound() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - SpatialData Reference with id 99999 is null or deleted");
  }

  @Test
  @Order(5)
  public void getSpatialDataReference_referenceBelongsToDifferentCollection_notFound() {
    CollectionIO otherCollection = createCollection("otherCollection");
    DataObjectIO otherDataObject = createDataObject("otherDataObject", otherCollection.getId());
    var otherReferenceToCreate = new SpatialDataReferenceIO();
    otherReferenceToCreate.setName("SpatialDataReferenceDummy");
    otherReferenceToCreate.setSpatialDataContainerId(container.getId());

    SpatialDataReferenceIO otherReference = given()
      .spec(requestSpecOfDefaultUser)
      .body(otherReferenceToCreate)
      .when()
      .post(
        "/%s/%d/%s/%d/%s".formatted(
            Constants.COLLECTIONS,
            otherCollection.getId(),
            Constants.DATA_OBJECTS,
            otherDataObject.getId(),
            Constants.SPATIAL_DATA_REFERENCES
          )
      )
      .then()
      .statusCode(201)
      .extract()
      .as(SpatialDataReferenceIO.class);

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/" + otherReference.getId())
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - There is no association between dataObject and reference");
  }

  @Test
  @Order(6)
  public void getSpatialDataReferencePayload() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get("%s/%d/%s".formatted(referencesURL, reference.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(200)
      .extract()
      .as(SpatialDataPointIO[].class);

    SpatialDataPointIO[] expected = spatialDataPoints.toArray(SpatialDataPointIO[]::new);
    assertThat(actual).containsExactly(expected);
  }

  @Test
  @Order(7)
  public void deleteReferences() {
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(referencesURL + "/" + reference.getId())
      .then()
      .statusCode(204);
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(referencesURL + "/" + reference.getId())
      .then()
      .statusCode(404);
    given().spec(requestSpecOfDefaultUser).when().get(referencesURL + "/" + reference.getId()).then().statusCode(404);
  }
}
