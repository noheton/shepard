package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.util.Constants;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CollectionTest extends BaseTestCaseIT {

  private static String collectionsURL;
  private static CollectionIO collection;
  private static RequestSpecification requestSpecification;
  private static String name;

  @BeforeAll
  public static void setUp() {
    collectionsURL = "/" + Constants.COLLECTIONS;
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
  }

  @Test
  @Order(1)
  public void postCollectionTest_Successful() {
    var payload = new CollectionIO();
    name = "CollectionDummy" + System.currentTimeMillis();
    payload.setName(name);
    payload.setDescription("My Description");
    payload.setAttributes(Map.of("a", "1", "b", "2"));
    CollectionIO actual = given()
      .spec(requestSpecification)
      .body(payload)
      .when()
      .post(collectionsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionIO.class);
    collection = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getAttributes()).isEqualTo(Map.of("a", "1", "b", "2"));
    assertThat(actual.getDescription()).isEqualTo("My Description");
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(username);
    assertThat(actual.getName()).isEqualTo(name);
    assertThat(actual.getDataObjectIds()).isEmpty();
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void postCollectionTest_WithoutAuth() {
    var payload = new CollectionIO();
    payload.setName(name);

    var wrongSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
    given().spec(wrongSpecification).body(payload).when().post(collectionsURL).then().statusCode(401);
  }

  @Test
  @Order(3)
  public void postCollectionTest_BadJson() {
    String payload = "{,}";
    given().spec(requestSpecification).body(payload).when().post(collectionsURL).then().statusCode(400);
  }

  @Test
  @Order(4)
  public void postCollectionTest_BadBody() {
    String payload = "{\"attribute\":\"value\"}";
    given().spec(requestSpecification).body(payload).when().post(collectionsURL).then().statusCode(400);
  }

  @Test
  @Order(5)
  public void getCollectionTest_Successful() {
    CollectionIO actual = given()
      .spec(requestSpecification)
      .when()
      .get(collectionsURL + "/" + collection.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO.class);

    assertThat(actual).isEqualTo(collection);
  }

  @Test
  @Order(6)
  public void getCollectionTest_withDataObject() {
    var payload = new DataObjectIO();
    payload.setName(name);

    DataObjectIO dataObject = given()
      .spec(requestSpecification)
      .body(payload)
      .when()
      .post(collectionsURL + "/" + collection.getId() + "/" + Constants.DATAOBJECTS)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    CollectionIO actual = given()
      .spec(requestSpecification)
      .when()
      .get(collectionsURL + "/" + collection.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO.class);
    collection = actual;

    assertThat(actual.getDataObjectIds()).contains(dataObject.getId());
  }

  @Test
  @Order(7)
  public void getCollectionTest_QueryParamNameSuccessful() {
    CollectionIO[] response = given()
      .spec(requestSpecification)
      .queryParam("name", collection.getName())
      .when()
      .get(collectionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO[].class);

    assertThat(response).containsExactly(collection);
  }

  @Test
  @Order(8)
  public void getCollectionsTest_Successful() {
    CollectionIO[] response = given()
      .spec(requestSpecification)
      .when()
      .get(collectionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO[].class);

    assertThat(response).contains(collection);
  }

  @Test
  @Order(9)
  public void getCollectionTest_WrongId() {
    given().spec(requestSpecification).when().get(collectionsURL + "/9999").then().statusCode(404);
  }

  @Test
  @Order(10)
  public void putCollectionTest_Successful() {
    collection.setName("CollectionDummyChanged");

    CollectionIO actualResponse = given()
      .spec(requestSpecification)
      .body(collection)
      .when()
      .put(collectionsURL + "/" + collection.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO.class);

    assertThat(actualResponse.getUpdatedAt()).isNotNull();
    assertThat(actualResponse.getUpdatedBy()).isEqualTo(username);
    assertThat(actualResponse)
      .usingRecursiveComparison()
      .ignoringFields("updatedBy", "updatedAt")
      .isEqualTo(collection);
  }

  @Test
  @Order(11)
  public void deleteCollectionTest_Successful() {
    given().spec(requestSpecification).when().delete(collectionsURL + "/" + collection.getId()).then().statusCode(204);
    given().spec(requestSpecification).when().get(collectionsURL + "/" + collection.getId()).then().statusCode(404);
  }
}
