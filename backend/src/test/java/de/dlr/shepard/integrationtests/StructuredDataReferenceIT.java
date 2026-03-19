package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StructuredDataReferenceIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;

  private static String referencesURL;
  private static String containerURL;

  private static StructuredDataContainerIO container;
  private static StructuredDataReferenceIO reference;
  private static StructuredDataPayload payload;

  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  public static void setUp() {
    collection = createCollection("StructuredDataReferenceTestCollection");
    dataObject = createDataObject("StructuredDataReferenceTestDataObject", collection.getId());

    referencesURL = "/%s/%d/%s/%d/%s".formatted(
        Constants.COLLECTIONS,
        collection.getId(),
        Constants.DATA_OBJECTS,
        dataObject.getId(),
        Constants.STRUCTURED_DATA_REFERENCES
      );

    containerURL = "/" + Constants.STRUCTURED_DATA_CONTAINERS;

    var toCreate = new StructuredDataContainerIO();
    toCreate.setName("StructuredDataContainer");
    container = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(containerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredDataContainerIO.class);
    var structuredData = new StructuredData();
    structuredData.setName("My Structured Data");
    payload = new StructuredDataPayload(
      structuredData,
      "{\"Hallo\":\"Welt\",\"number\":123,\"list\":[\"a\",\"b\"],\"object\":{\"a\":\"b\"}}"
    );
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post("%s/%d/%s".formatted(containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredData.class);
    payload.setStructuredData(actual);
  }

  @Test
  @Order(1)
  public void createStructuredDataReference() {
    var toCreate = new StructuredDataReferenceIO();
    toCreate.setName("StructuredDataReferenceDummy");
    toCreate.setStructuredDataOids(new String[] { payload.getStructuredData().getOid() });
    toCreate.setStructuredDataContainerId(container.getId());

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredDataReferenceIO.class);
    reference = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
    assertThat(actual.getName()).isEqualTo("StructuredDataReferenceDummy");
    assertThat(actual.getStructuredDataContainerId()).isEqualTo(container.getId());
    assertThat(actual.getStructuredDataOids()).containsExactly(payload.getStructuredData().getOid());
    assertThat(actual.getType()).isEqualTo("StructuredDataReference");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getStructuredDataReferences() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL)
      .then()
      .statusCode(200)
      .extract()
      .as(StructuredDataReferenceIO[].class);

    assertThat(actual).containsExactly(reference);
  }

  @Test
  @Order(3)
  public void getStructuredDataReference() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/" + reference.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(StructuredDataReferenceIO.class);

    assertThat(actual).isEqualTo(reference);
  }

  @Test
  @Order(4)
  public void getStructuredDataReference_doesNotExist_notFound() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - Structured Data Reference with id 99999 is null or deleted");
  }

  @Test
  @Order(5)
  public void getStructuredDataReference_isIsCollectionId_notFound() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/" + collection.getId())
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo(
      "ID ERROR - Structured Data Reference with id %s is null or deleted".formatted(collection.getId())
    );
  }

  @Test
  @Order(6)
  public void getStructuredDataReference_idBelongsToWrongDataObject_notFound() {
    DataObjectIO otherDataObject = createDataObject("OtherStructuredDataReferenceTestDataObject", collection.getId());

    StructuredDataReferenceIO toCreate = new StructuredDataReferenceIO();
    toCreate.setName("StructuredDataReferenceDummy");
    toCreate.setStructuredDataOids(new String[] { payload.getStructuredData().getOid() });
    toCreate.setStructuredDataContainerId(container.getId());

    StructuredDataReferenceIO otherRef = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(
        "/%s/%d/%s/%d/%s".formatted(
            Constants.COLLECTIONS,
            collection.getId(),
            Constants.DATA_OBJECTS,
            otherDataObject.getId(),
            Constants.STRUCTURED_DATA_REFERENCES
          )
      )
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredDataReferenceIO.class);

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/" + otherRef.getId())
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - There is no association between dataObject and reference");
  }

  @Test
  @Order(7)
  @SuppressWarnings("unchecked")
  public void getStructuredDataReferencePayload() throws JsonMappingException, JsonProcessingException {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(
        "%s/%d/%s/%s".formatted(
            referencesURL,
            reference.getId(),
            Constants.PAYLOAD,
            payload.getStructuredData().getOid()
          )
      )
      .then()
      .statusCode(200)
      .extract()
      .as(StructuredDataPayload.class);
    var payloadMap = objectMapper.readValue(actual.getPayload(), Map.class);
    var expectedMap = objectMapper.readValue(payload.getPayload(), Map.class);

    assertThat(actual.getStructuredData()).isEqualTo(payload.getStructuredData());
    assertThat(payloadMap).containsAllEntriesOf(expectedMap);
    assertThat(actual.getStructuredData()).isEqualTo(payload.getStructuredData());
  }

  @Test
  @Order(8)
  public void getStructuredDataReference_referenceToPrivateContainerOfOtherUser_notAllowed() {
    // This test is a testing the bug described in #475
    CollectionIO otherCollection = createCollection("CollectionContainingReferencingDataObject", otherUser);
    DataObjectIO otherDataObject = createDataObject(
      "ReferencingDataObjectOfOtherUser",
      otherCollection.getId(),
      otherUser
    );

    StructuredDataReferenceIO toCreate = new StructuredDataReferenceIO();
    toCreate.setName("StructuredDataReferenceDummy");
    // create reference to other container with specific data oid
    toCreate.setStructuredDataOids(new String[] { payload.getStructuredData().getOid() });
    toCreate.setStructuredDataContainerId(container.getId());

    // Act + Assert
    ErrorResponse errorResponse = given()
      .spec(requestSpecOfOtherUser)
      .body(toCreate)
      .when()
      .post(
        "/%s/%d/%s/%d/%s".formatted(
            Constants.COLLECTIONS,
            otherCollection.getId(),
            Constants.DATA_OBJECTS,
            otherDataObject.getId(),
            Constants.STRUCTURED_DATA_REFERENCES
          )
      )
      .then()
      .statusCode(403)
      .extract()
      .as(ErrorResponse.class);

    assertThat(errorResponse.getMessage()).isEqualTo(
      "The requested action is forbidden by the permission policies. User has no READ permissions."
    );
  }

  @Test
  @Order(9)
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
