package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class URIReferenceIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;

  private static String referencesURL;

  private static URIReferenceIO reference;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("URIReferenceTestCollection");
    dataObject = createDataObject("URIReferenceDataObject", collection.getId());

    referencesURL = "/%s/%d/%s/%d/%s".formatted(
        Constants.COLLECTIONS,
        collection.getId(),
        Constants.DATA_OBJECTS,
        dataObject.getId(),
        Constants.URI_REFERENCES
      );
  }

  @Test
  @Order(1)
  public void createURIReferenceTest() {
    var toCreate = new URIReferenceIO();
    toCreate.setName("URIReferenceDummy");
    toCreate.setUri("http://MyAwesomeUrl.com");
    toCreate.setRelationship("test-relationship");

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(URIReferenceIO.class);
    reference = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
    assertThat(actual.getName()).isEqualTo("URIReferenceDummy");
    assertThat(actual.getUri()).isEqualTo("http://MyAwesomeUrl.com");
    assertThat(actual.getType()).isEqualTo("URIReference");
    assertThat(actual.getRelationship()).isEqualTo("test-relationship");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getURIReferenceTest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/" + reference.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(URIReferenceIO.class);
    assertThat(actual).isEqualTo(reference);
  }

  @Test
  @Order(3)
  public void getURIReferencesTest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL)
      .then()
      .statusCode(200)
      .extract()
      .as(URIReferenceIO[].class);
    assertThat(actual).containsExactly(reference);
  }

  @Test
  @Order(4)
  public void getURIReference_referenceDoesNotExist_notFound() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - URI Reference with id 99999 is null or deleted");
  }

  @Test
  @Order(5)
  public void getURIReference_idBelongsToWrongDataObject_notFound() {
    DataObjectIO otherDataObject = createDataObject("OtherStructuredDataReferenceTestDataObject", collection.getId());

    var toCreate = new URIReferenceIO();
    toCreate.setName("URIReferenceDummy");
    toCreate.setUri("http://MyAwesomeUrl.com");

    URIReferenceIO otherRef = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(
        "/%s/%d/%s/%d/%s".formatted(
            Constants.COLLECTIONS,
            collection.getId(),
            Constants.DATA_OBJECTS,
            otherDataObject.getId(),
            Constants.URI_REFERENCES
          )
      )
      .then()
      .statusCode(201)
      .extract()
      .as(URIReferenceIO.class);

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
  @Order(6)
  public void deleteURIReferenceTest() {
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
