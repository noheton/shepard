package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import de.dlr.shepard.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
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
  private static RequestSpecification requestSpecification;

  private static URIReferenceIO reference;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("URIReferenceTestCollection");
    dataObject = createDataObject("URIReferenceDataObject", collection.getId());

    referencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATAOBJECTS,
      dataObject.getId(),
      Constants.URI_REFERENCES
    );
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
  }

  @Test
  @Order(1)
  public void createURIReferenceTest() {
    var toCreate = new URIReferenceIO();
    toCreate.setName("URIReferenceDummy");
    toCreate.setUri("http://MyAwesomeUrl.com");

    var actual = given()
      .spec(requestSpecification)
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
    assertThat(actual.getCreatedBy()).isEqualTo(username);
    assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
    assertThat(actual.getName()).isEqualTo("URIReferenceDummy");
    assertThat(actual.getUri()).isEqualTo("http://MyAwesomeUrl.com");
    assertThat(actual.getType()).isEqualTo("URIReference");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getURIReferenceTest() {
    var actual = given()
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
  public void deleteURIReferenceTest() {
    given().spec(requestSpecification).when().delete(referencesURL + "/" + reference.getId()).then().statusCode(204);

    given().spec(requestSpecification).when().get(referencesURL + "/" + reference.getId()).then().statusCode(404);
  }
}
