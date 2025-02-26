package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.context.version.io.VersionIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataObjectReferenceIT extends BaseTestCaseIT {

  private static CollectionIO collection1;
  private static CollectionIO collection2;
  private static DataObjectIO dataObject11;
  private static DataObjectIO dataObject12;
  private static DataObjectIO dataObject21;
  private static DataObjectIO dataObject22;

  private static UUID c1v1UID;

  private static String c1d1referencesURL;
  private static String c1d2referencesURL;
  private static String c2d1referencesURL;
  private static RequestSpecification requestSpecification;

  private static DataObjectReferenceIO reference11to12;
  private static DataObjectReferenceIO reference12to21;
  private static DataObjectReferenceIO reference21to12;

  @BeforeAll
  public static void setUp() {
    collection1 = createCollection("1cDataObjectReferenceTestCollection");
    collection2 = createCollection("2cDataObjectReferenceTestCollection");
    dataObject11 = createDataObject("1c1dDataObject", collection1.getId());
    dataObject12 = createDataObject("1c2dDataObject", collection1.getId());
    dataObject21 = createDataObject("2c1dDataObject", collection2.getId());
    dataObject22 = createDataObject("2c2dDataObject", collection2.getId());

    c1d1referencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection1.getId(),
      Constants.DATA_OBJECTS,
      dataObject11.getId(),
      Constants.DATAOBJECT_REFERENCES
    );
    c1d2referencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection1.getId(),
      Constants.DATA_OBJECTS,
      dataObject12.getId(),
      Constants.DATAOBJECT_REFERENCES
    );
    c2d1referencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection2.getId(),
      Constants.DATA_OBJECTS,
      dataObject21.getId(),
      Constants.DATAOBJECT_REFERENCES
    );
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();

    var referenceToCreate = new DataObjectReferenceIO();
    referenceToCreate.setName("reference12to21");
    referenceToCreate.setRelationship("integrationtest");
    referenceToCreate.setReferencedDataObjectId(dataObject21.getId());
    reference12to21 = given()
      .spec(requestSpecification)
      .body(referenceToCreate)
      .when()
      .post(c1d2referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectReferenceIO.class);

    referenceToCreate = new DataObjectReferenceIO();
    referenceToCreate.setName("reference21to12");
    referenceToCreate.setRelationship("integrationtest");
    referenceToCreate.setReferencedDataObjectId(dataObject12.getId());
    reference21to12 = given()
      .spec(requestSpecification)
      .body(referenceToCreate)
      .when()
      .post(c2d1referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectReferenceIO.class);

    referenceToCreate = new DataObjectReferenceIO();
    referenceToCreate.setName("reference21to22");
    referenceToCreate.setRelationship("integrationtest");
    referenceToCreate.setReferencedDataObjectId(dataObject22.getId());
  }

  @Test
  @Order(1)
  public void createDataObjectReferenceTest() {
    var toCreate = new DataObjectReferenceIO();
    toCreate.setName("DataObjectReferenceDummy");
    toCreate.setRelationship("integrationtests");
    toCreate.setReferencedDataObjectId(dataObject12.getId());

    var actual = given()
      .spec(requestSpecification)
      .body(toCreate)
      .when()
      .post(c1d1referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectReferenceIO.class);
    reference11to12 = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(username);
    assertThat(actual.getDataObjectId()).isEqualTo(dataObject11.getId());
    assertThat(actual.getName()).isEqualTo("DataObjectReferenceDummy");
    assertThat(actual.getRelationship()).isEqualTo("integrationtests");
    assertThat(actual.getReferencedDataObjectId()).isEqualTo(dataObject12.getId());
    assertThat(actual.getType()).isEqualTo("DataObjectReference");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void createDataObjectReferenceTest_ReferencedDoesNotExist() {
    var toCreate = new DataObjectReferenceIO();
    toCreate.setName("DataObjectReferenceDummy2");
    toCreate.setRelationship("integrationtests");
    toCreate.setReferencedDataObjectId(-2);

    given().spec(requestSpecification).body(toCreate).when().post(c1d1referencesURL).then().statusCode(400);
  }

  @Test
  @Order(3)
  public void getDataObjectReferenceTest() {
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(c1d1referencesURL + "/" + reference11to12.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectReferenceIO.class);
    assertThat(actual).isEqualTo(reference11to12);
  }

  @Test
  @Order(4)
  public void getDataObjectReferencesTest() {
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(c1d1referencesURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectReferenceIO[].class);
    assertThat(actual).containsExactly(reference11to12);
  }

  @Test
  @Order(5)
  public void getDataObjectReference_referenceDoesNotExist_notFound() {
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(c1d1referencesURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - Reference does not exist");
  }

  @Test
  @Order(6)
  public void getDataObjectReference_idBelongsToWrongDataObject_notFound() {
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(c1d1referencesURL + "/" + reference21to12.getId())
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - There is no association between dataObject and reference");
  }

  @Test
  @Order(7)
  public void getDataObjectReferencedTest() {
    var referencedURL = String.format(
      "/%s/%d/%s/%d",
      Constants.COLLECTIONS,
      collection1.getId(),
      Constants.DATA_OBJECTS,
      dataObject12.getId()
    );
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(referencedURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertEquals(actual.getIncomingIds().length, 2);
    assertThat(actual.getIncomingIds()).contains(reference11to12.getId());
    assertThat(actual.getIncomingIds()).contains(reference21to12.getId());
  }

  @Test
  @Order(8)
  public void getDataObjectReferencePayloadTest() {
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(String.format("%s/%d/%s", c1d1referencesURL, reference11to12.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertThat(actual)
      .usingRecursiveComparison()
      .ignoringFields("incomingIds")
      .ignoringFields("referenceIds")
      .isEqualTo(dataObject12);
    assertEquals(actual.getIncomingIds().length, 2);
    assertThat(actual.getIncomingIds()).contains(reference11to12.getId());
    assertThat(actual.getIncomingIds()).contains(reference21to12.getId());
  }

  @Test
  @Order(9)
  public void deleteDataObjectReferenceTest() {
    given()
      .spec(requestSpecification)
      .when()
      .delete(c1d1referencesURL + "/" + reference11to12.getId())
      .then()
      .statusCode(204);

    given()
      .spec(requestSpecification)
      .when()
      .get(c1d1referencesURL + "/" + reference11to12.getId())
      .then()
      .statusCode(404);
  }

  @Test
  @Order(10)
  @EnabledIf(VersioningFeatureToggle.IS_ENABLED_METHOD_ID)
  public void createNewVersionCollection1Test() {
    String versionizeCollection1URL = "/" + Constants.COLLECTIONS + "/" + collection1.getId() + "/versions";
    VersionIO inputVersion = new VersionIO();
    inputVersion.setName("c1v1");
    inputVersion.setDescription("first version of collection 1");
    VersionIO actual = given()
      .spec(requestSpecification)
      .body(inputVersion)
      .when()
      .post(versionizeCollection1URL)
      .then()
      .statusCode(201)
      .extract()
      .as(VersionIO.class);
    assertEquals(actual.getName(), inputVersion.getName());
    c1v1UID = actual.getUid();
  }

  @Test
  @Order(11)
  @EnabledIf(VersioningFeatureToggle.IS_ENABLED_METHOD_ID)
  public void incomingReferencesToDataObject12InHEADVersionTest() {
    var referencedURL = String.format(
      "/%s/%d/%s/%d",
      Constants.COLLECTIONS,
      collection1.getId(),
      Constants.DATA_OBJECTS,
      dataObject12.getId()
    );
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(referencedURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertThat(actual.getIncomingIds()).containsExactly(reference21to12.getId());
  }

  @Test
  @Order(12)
  @EnabledIf(VersioningFeatureToggle.IS_ENABLED_METHOD_ID)
  public void incomingReferencesToDataObject12InFirstVersionTest() {
    var referencedURL = String.format(
      "/%s/%d/%s/%d",
      Constants.COLLECTIONS,
      collection1.getId(),
      Constants.DATA_OBJECTS,
      dataObject12.getId()
    );
    var actual = given()
      .spec(requestSpecification)
      .queryParam("versionUid", c1v1UID)
      .when()
      .get(referencedURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertEquals(actual.getIncomingIds().length, 0);
  }

  @Test
  @Order(13)
  @EnabledIf(VersioningFeatureToggle.IS_ENABLED_METHOD_ID)
  public void multipleIncomingReferencesToDataObject21InHEADVersion() {
    var referencedURL = String.format(
      "/%s/%d/%s/%d",
      Constants.COLLECTIONS,
      collection2.getId(),
      Constants.DATA_OBJECTS,
      dataObject21.getId()
    );
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(referencedURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertEquals(actual.getIncomingIds().length, 2);
    assertThat(actual.getIncomingIds()).contains(reference12to21.getId());
  }

  @Test
  public void getDataObjectReferencePayload_referencedDataObjectDeleted_returnsForbidden() {
    // Arrange
    CollectionIO collectionWithReferencingDataObject = createCollection("collectionWithReferencingDataObject");
    DataObjectIO referencingDataObject = createDataObject(
      "referencingDataObject",
      collectionWithReferencingDataObject.getId()
    );
    CollectionIO collectionWithReferencedDataObject = createCollection("collectionWithReferencedDataObject");
    DataObjectIO referencedDataObject = createDataObject(
      "referencedDataObject",
      collectionWithReferencedDataObject.getId()
    );

    String referencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collectionWithReferencingDataObject.getId(),
      Constants.DATA_OBJECTS,
      referencingDataObject.getId(),
      Constants.DATAOBJECT_REFERENCES
    );

    DataObjectReferenceIO referenceToCreate = new DataObjectReferenceIO();
    referenceToCreate.setName("reference");
    referenceToCreate.setRelationship("integrationtest");
    referenceToCreate.setReferencedDataObjectId(referencedDataObject.getId());
    DataObjectReferenceIO reference = given()
      .spec(requestSpecification)
      .body(referenceToCreate)
      .when()
      .post(referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectReferenceIO.class);

    // Act
    given()
      .spec(requestSpecification)
      .when()
      .delete(
        String.format(
          "/%s/%d/%s/%d",
          Constants.COLLECTIONS,
          collectionWithReferencedDataObject.getId(),
          Constants.DATA_OBJECTS,
          referencedDataObject.getId()
        )
      )
      .then()
      .statusCode(204);

    // Assert
    given()
      .spec(requestSpecification)
      .when()
      .get(String.format("%s/%d/%s", referencesURL, reference.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(404);
  }
}
