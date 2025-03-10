package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BasicReferenceIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;

  private static String referencesURL;
  private static BasicReferenceIO dataObjectReference;
  private static BasicReferenceIO uriReference;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("BasicReferenceTestCollection");
    dataObject = createDataObject("BasicReferenceTestDataObject", collection.getId());

    dataObjectReference = createDataObjectReference(collection.getId(), dataObject.getId(), dataObject.getId());
    uriReference = createUriReference(collection.getId(), dataObject.getId());

    referencesURL = String.format(
      "/%s/%d/%s/%d/references",
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATA_OBJECTS,
      dataObject.getId(),
      Constants.BASIC_REFERENCES
    );
  }

  @Test
  @Order(1)
  public void getFirstReference_Successful() {
    BasicReferenceIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/" + dataObjectReference.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(BasicReferenceIO.class);

    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
    assertThat(actual.getId()).isEqualTo(dataObjectReference.getId());
    assertThat(actual.getName()).isEqualTo("DataObjectReference");
    assertThat(actual.getType()).isEqualTo("DataObjectReference");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();

    dataObjectReference = actual;
  }

  @Test
  @Order(2)
  public void getReference_doesNotExist_notFound() {
    ErrorResponse actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - Reference does not exist");
  }

  @Test
  @Order(3)
  public void getReference_idBelongsToWrongDataObject_notFound() {
    DataObjectIO otherDataObject = createDataObject("OtherStructuredDataReferenceTestDataObject", collection.getId());
    DataObjectReferenceIO otherRef = createDataObjectReference(
      collection.getId(),
      otherDataObject.getId(),
      dataObject.getId()
    );

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
  @Order(4)
  public void getSecondReference_Successful() {
    BasicReferenceIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/" + uriReference.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(BasicReferenceIO.class);

    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
    assertThat(actual.getId()).isEqualTo(uriReference.getId());
    assertThat(actual.getName()).isEqualTo("UriReference");
    assertThat(actual.getType()).isEqualTo("URIReference");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();

    uriReference = actual;
  }

  @Test
  @Order(5)
  public void getAllReferences_Successful() {
    BasicReferenceIO[] actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL)
      .then()
      .statusCode(200)
      .extract()
      .as(BasicReferenceIO[].class);

    assertThat(actual).containsExactlyInAnyOrder(dataObjectReference, uriReference);
  }

  @Test
  @Order(6)
  public void deleteReferences_Successful() {
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(referencesURL + "/" + uriReference.getId())
      .then()
      .statusCode(204);

    BasicReferenceIO[] actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL)
      .then()
      .statusCode(200)
      .extract()
      .as(BasicReferenceIO[].class);

    assertThat(actual).containsExactly(dataObjectReference);

    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/" + uriReference.getId())
      .then()
      .statusCode(404);
  }

  private static URIReferenceIO createUriReference(long collectionId, long dataObjectId) {
    var uriReferenceUrl =
      "/" +
      Constants.COLLECTIONS +
      "/" +
      collectionId +
      "/" +
      Constants.DATA_OBJECTS +
      "/" +
      dataObjectId +
      "/" +
      Constants.URI_REFERENCES +
      "/";
    var uriReference = new URIReferenceIO() {
      {
        setName("UriReference");
        setUri("http://www.example.com");
      }
    };
    var created = given()
      .spec(requestSpecOfDefaultUser)
      .body(uriReference)
      .when()
      .post(uriReferenceUrl)
      .then()
      .statusCode(201)
      .extract()
      .as(URIReferenceIO.class);
    return created;
  }
}
