package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.CollectionReferenceIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.util.Constants;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CollectionReferenceTest extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;
  private static CollectionIO referenced;

  private static String referencesURL;
  private static RequestSpecification requestSpecification;

  private static CollectionReferenceIO reference;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("CollectionReferenceTestCollection");
    dataObject = createDataObject("CollectionReference", collection.getId());
    referenced = createCollection("ReferencedCollection");

    referencesURL = String.format(
      "%s/%s/%d/%s/%d/%s",
      baseURL,
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATAOBJECTS,
      dataObject.getId(),
      Constants.COLLECTION_REFERENCES
    );
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri(referencesURL)
      .addHeader("X-API-KEY", jws)
      .build();
  }

  @Test
  @Order(1)
  public void createCollectionReferenceTest() {
    var toCreate = new CollectionReferenceIO();
    toCreate.setName("CollectionReferenceDummy");
    toCreate.setRelationship("integrationtests");
    toCreate.setReferencedCollectionId(referenced.getId());

    var actual = given()
      .spec(requestSpecification)
      .body(toCreate)
      .when()
      .post()
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionReferenceIO.class);
    reference = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(username);
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

    given().spec(requestSpecification).body(toCreate).when().post().then().statusCode(400);
  }

  @Test
  @Order(3)
  public void getCollectionReferenceTest() {
    var actual = given()
      .spec(requestSpecification)
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
      .spec(requestSpecification)
      .when()
      .get()
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionReferenceIO[].class);
    assertThat(actual).containsExactly(reference);
  }

  @Test
  @Order(5)
  public void getCollectionReferencedTest() {
    var referencedURL = String.format("%s/%s/%d", baseURL, Constants.COLLECTIONS, referenced.getId());
    var actual = given()
      .spec(requestSpecification)
      .when()
      .get(referencedURL)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO.class);
    assertThat(actual.getIncomingIds()).containsExactly(reference.getId());
  }

  @Test
  @Order(6)
  public void getCollectionReferencePayloadTest() {
    var actual = given()
      .spec(requestSpecification)
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
  @Order(7)
  public void deleteCollectionReferenceTest() {
    given().spec(requestSpecification).when().delete(referencesURL + "/" + reference.getId()).then().statusCode(204);

    given().spec(requestSpecification).when().get(referencesURL + "/" + reference.getId()).then().statusCode(404);
  }
}
