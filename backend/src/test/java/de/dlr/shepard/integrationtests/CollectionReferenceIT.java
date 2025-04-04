package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.dataobject.io.CollectionReferenceIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CollectionReferenceIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;
  private static CollectionIO referenced;

  private static String referencesURL;

  private static CollectionReferenceIO reference;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("CollectionReferenceTestCollection");
    dataObject = createDataObject("CollectionReference", collection.getId());
    referenced = createCollection("ReferencedCollection");

    referencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATA_OBJECTS,
      dataObject.getId(),
      Constants.COLLECTION_REFERENCES
    );
  }

  @Test
  @Order(1)
  public void createCollectionReferenceTest() {
    var toCreate = new CollectionReferenceIO();
    toCreate.setName("CollectionReferenceDummy");
    toCreate.setRelationship("integrationtests");
    toCreate.setReferencedCollectionId(referenced.getId());

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionReferenceIO.class);
    reference = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
    assertThat(actual.getName()).isEqualTo("CollectionReferenceDummy");
    assertThat(actual.getRelationship()).isEqualTo("integrationtests");
    assertThat(actual.getReferencedCollectionId()).isEqualTo(referenced.getId());
    assertThat(actual.getType()).isEqualTo("CollectionReference");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void createCollectionReferenceTest_ReferencedDoesNotExist() {
    var toCreate = new CollectionReferenceIO();
    toCreate.setName("CollectionReferenceDummy2");
    toCreate.setRelationship("integrationtests");
    toCreate.setReferencedCollectionId(-2);

    given().spec(requestSpecOfDefaultUser).body(toCreate).when().post(referencesURL).then().statusCode(400);
  }

  @Test
  @Order(3)
  public void getCollectionReferenceTest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/" + reference.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionReferenceIO.class);
    assertThat(actual).isEqualTo(reference);
  }

  @Test
  @Order(4)
  public void getCollectionReferencesTest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionReferenceIO[].class);
    assertThat(actual).containsExactly(reference);
  }

  @Test
  @Order(5)
  public void getCollectionReference_referenceDoesNotExist_notFound() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - Collection Reference with id 99999 is null or deleted");
  }

  @Test
  @Order(6)
  public void getCollectionReferencedTest() {
    var referencedURL = String.format("/%s/%d", Constants.COLLECTIONS, referenced.getId());
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencedURL)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO.class);

    assertThat(actual).usingRecursiveComparison().ignoringFields("incomingIds").isEqualTo(referenced);
    assertThat(actual.getIncomingIds()).containsExactly(reference.getId());
  }

  @Test
  @Order(7)
  public void getCollectionReferencePayloadTest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(String.format("%s/%d/%s", referencesURL, reference.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO.class);

    assertThat(actual).usingRecursiveComparison().ignoringFields("incomingIds").isEqualTo(referenced);
    assertThat(actual.getIncomingIds()).containsExactly(reference.getId());
  }

  @Test
  @Order(8)
  public void getCollectionReference_idBelongsToWrongDataObject_notFound() {
    DataObjectIO otherDataObject = createDataObject("OtherStructuredDataReferenceTestDataObject", collection.getId());

    var toCreate = new CollectionReferenceIO();
    toCreate.setName("CollectionReferenceDummy");
    toCreate.setRelationship("integrationtests");
    toCreate.setReferencedCollectionId(referenced.getId());

    CollectionReferenceIO otherRef = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(
        String.format(
          "/%s/%d/%s/%d/%s",
          Constants.COLLECTIONS,
          collection.getId(),
          Constants.DATA_OBJECTS,
          otherDataObject.getId(),
          Constants.COLLECTION_REFERENCES
        )
      )
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionReferenceIO.class);

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
  @Order(9)
  public void deleteCollectionReferenceTest() {
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
