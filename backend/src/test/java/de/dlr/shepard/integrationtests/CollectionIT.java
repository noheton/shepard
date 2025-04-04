package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.version.io.VersionIO;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CollectionIT extends BaseTestCaseIT {

  private static String collectionsURL;
  private static CollectionIO collection;
  private static VersionIO firstVersion;
  private static VersionIO secondVersion;
  private static String name;
  private static long VersionizedCollectionShepardId;
  private static String VersionizedCollectionName;
  private static String newVersionizedCollectionName;

  @BeforeAll
  public static void setUp() {
    collectionsURL = "/" + Constants.COLLECTIONS;
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
      .spec(requestSpecOfDefaultUser)
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
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
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
    given().spec(requestSpecOfDefaultUser).body(payload).when().post(collectionsURL).then().statusCode(400);
  }

  @Test
  @Order(4)
  public void postCollectionTest_BadBody() {
    String payload = "{\"attribute\":\"value\"}";
    given().spec(requestSpecOfDefaultUser).body(payload).when().post(collectionsURL).then().statusCode(400);
  }

  @Test
  @Order(5)
  public void getCollectionTest_Successful() {
    CollectionIO actual = given()
      .spec(requestSpecOfDefaultUser)
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
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post(collectionsURL + "/" + collection.getId() + "/" + Constants.DATA_OBJECTS)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    CollectionIO actual = given()
      .spec(requestSpecOfDefaultUser)
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
      .spec(requestSpecOfDefaultUser)
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
  public void getCollectionTest_wrongId_notFound() {
    ErrorResponse response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(collectionsURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    assertThat(response.getMessage()).isEqualTo("ID ERROR - Collection with id 99999 is null or deleted");
  }

  @Test
  @Order(9)
  public void getCollectionTest_privateCollection_forbidden() {
    RequestSpecification otherUserRequestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", otherUser.getApiKey().getJws())
      .build();

    ErrorResponse response = given()
      .spec(otherUserRequestSpecification)
      .when()
      .get(collectionsURL + "/" + collection.getId())
      .then()
      .statusCode(403)
      .extract()
      .as(ErrorResponse.class);
    assertThat(response.getMessage()).isEqualTo("The requested action is forbidden by the permission policies");
  }

  @Test
  @Order(10)
  public void getCollectionsTest_Successful() {
    CollectionIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(collectionsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO[].class);

    assertThat(response).contains(collection);
  }

  @Test
  @Order(11)
  public void putCollectionTest_Successful() {
    collection.setName("CollectionDummyChanged");

    CollectionIO actualResponse = given()
      .spec(requestSpecOfDefaultUser)
      .body(collection)
      .when()
      .put(collectionsURL + "/" + collection.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO.class);

    Log.warn(collection);
    Log.warn(actualResponse);

    assertThat(actualResponse.getUpdatedAt()).isNotNull();
    assertThat(actualResponse.getUpdatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actualResponse)
      .usingRecursiveComparison()
      .ignoringFields("updatedBy", "updatedAt")
      .isEqualTo(collection);
  }

  @Test
  @Order(12)
  public void deleteCollectionTest_Successful() {
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(collectionsURL + "/" + collection.getId())
      .then()
      .statusCode(204);
    given().spec(requestSpecOfDefaultUser).when().get(collectionsURL + "/" + collection.getId()).then().statusCode(404);
  }

  @Test
  @Order(13)
  public void getinitialHEADVersion() {
    if (VersioningFeatureToggle.isEnabled()) {
      var payload = new CollectionIO();
      VersionizedCollectionName = "VersionizedCollection";
      payload.setName(VersionizedCollectionName);
      payload.setDescription(VersionizedCollectionName);
      CollectionIO actual = given()
        .spec(requestSpecOfDefaultUser)
        .body(payload)
        .when()
        .post(collectionsURL)
        .then()
        .statusCode(201)
        .extract()
        .as(CollectionIO.class);
      VersionizedCollectionShepardId = actual.getId();
      VersionIO[] versions = given()
        .spec(requestSpecOfDefaultUser)
        .when()
        .get(collectionsURL + "/" + VersionizedCollectionShepardId + "/versions")
        .then()
        .statusCode(200)
        .extract()
        .as(VersionIO[].class);
      assertEquals(versions.length, 1);
      VersionIO HEADVersion = versions[0];
      assertEquals(HEADVersion.getName(), "HEAD");
      assertEquals(HEADVersion.getDescription(), "HEAD version");
    }
  }

  @Test
  @Order(14)
  public void createNewVersion() {
    if (VersioningFeatureToggle.isEnabled()) {
      VersionIO newVersion = new VersionIO();
      newVersion.setName("first version");
      newVersion.setDescription("first version of versionized collection");
      firstVersion = given()
        .spec(requestSpecOfDefaultUser)
        .body(newVersion)
        .when()
        .post(collectionsURL + "/" + VersionizedCollectionShepardId + "/versions")
        .then()
        .statusCode(201)
        .extract()
        .as(VersionIO.class);
      assertEquals(firstVersion.getName(), newVersion.getName());
      assertEquals(firstVersion.getDescription(), newVersion.getDescription());
    }
  }

  @Test
  @Order(15)
  public void createSecondVersion() {
    if (VersioningFeatureToggle.isEnabled()) {
      VersionIO newVersion = new VersionIO();
      newVersion.setName("second version");
      newVersion.setDescription("second version of versionized collection");
      secondVersion = given()
        .spec(requestSpecOfDefaultUser)
        .body(newVersion)
        .when()
        .post(collectionsURL + "/" + VersionizedCollectionShepardId + "/versions")
        .then()
        .statusCode(201)
        .extract()
        .as(VersionIO.class);
      assertEquals(secondVersion.getName(), newVersion.getName());
      assertEquals(secondVersion.getDescription(), newVersion.getDescription());
    }
  }

  @Test
  @Order(16)
  public void getAllVersions() {
    if (VersioningFeatureToggle.isEnabled()) {
      VersionIO[] versions = given()
        .spec(requestSpecOfDefaultUser)
        .when()
        .get(collectionsURL + "/" + VersionizedCollectionShepardId + "/versions")
        .then()
        .statusCode(200)
        .extract()
        .as(VersionIO[].class);
      assertEquals(versions.length, 3);
      VersionIO HEADVersionCandidate = null;
      VersionIO firstVersionCandidate = null;
      VersionIO secondVersionCandidate = null;
      for (int i = 0; i < 3; i++) {
        if (versions[i].getName().equals("HEAD")) HEADVersionCandidate = versions[i];
        if (versions[i].getUid().equals(firstVersion.getUid())) firstVersionCandidate = versions[i];
        if (versions[i].getUid().equals(secondVersion.getUid())) secondVersionCandidate = versions[i];
      }
      assertNotNull(HEADVersionCandidate);
      assertNotNull(firstVersionCandidate);
      assertNotNull(secondVersionCandidate);
      assertEquals(HEADVersionCandidate.getPredecessorUUID(), secondVersionCandidate.getUid());
      assertEquals(secondVersionCandidate.getPredecessorUUID(), firstVersionCandidate.getUid());
      assertNull(firstVersionCandidate.getPredecessorUUID());
    }
  }

  @Test
  @Order(17)
  public void modifyHEADCollection() {
    if (VersioningFeatureToggle.isEnabled()) {
      CollectionIO newVersionizedCollection = new CollectionIO();
      newVersionizedCollectionName = "updated versionized collection";
      newVersionizedCollection.setName(newVersionizedCollectionName);
      CollectionIO actual = given()
        .spec(requestSpecOfDefaultUser)
        .body(newVersionizedCollection)
        .when()
        .put(collectionsURL + "/" + VersionizedCollectionShepardId)
        .then()
        .statusCode(200)
        .extract()
        .as(CollectionIO.class);
      assertEquals(actual.getName(), newVersionizedCollectionName);
      assertEquals(actual.getId(), VersionizedCollectionShepardId);
    }
  }

  @Test
  @Order(18)
  public void retrieveModifiedHEADCollection() {
    if (VersioningFeatureToggle.isEnabled()) {
      CollectionIO actual = given()
        .spec(requestSpecOfDefaultUser)
        .when()
        .get(collectionsURL + "/" + VersionizedCollectionShepardId)
        .then()
        .statusCode(200)
        .extract()
        .as(CollectionIO.class);
      assertEquals(actual.getName(), newVersionizedCollectionName);
    }
  }

  @Test
  @Order(19)
  public void retrieveSecondVersion() {
    if (VersioningFeatureToggle.isEnabled()) {
      VersionIO actual = given()
        .spec(requestSpecOfDefaultUser)
        .when()
        .get(collectionsURL + "/" + VersionizedCollectionShepardId + "/versions/" + secondVersion.getUid().toString())
        .then()
        .statusCode(200)
        .extract()
        .as(VersionIO.class);
      assertEquals(actual, secondVersion);
    }
  }

  @Test
  @Order(20)
  public void retrieveSecondVersionOfCollection() {
    if (VersioningFeatureToggle.isEnabled()) {
      CollectionIO actual = given()
        .spec(requestSpecOfDefaultUser)
        .queryParam("versionUid", secondVersion.getUid().toString())
        .when()
        .get(collectionsURL + "/" + VersionizedCollectionShepardId)
        .then()
        .statusCode(200)
        .extract()
        .as(CollectionIO.class);
      assertEquals(actual.getName(), VersionizedCollectionName);
    }
  }

  @Test
  @Order(21)
  public void updateCollection_deleteAttribute_successfullyDeleteAttribute() {
    // Arrange
    CollectionIO collectionIO = new CollectionIO();
    collectionIO.setName("Some name");
    collectionIO.setAttributes(Map.of("name", "my data object"));
    CollectionIO collection = given()
      .spec(requestSpecOfDefaultUser)
      .body(collectionIO)
      .when()
      .post(collectionsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionIO.class);

    // Act
    collectionIO.setAttributes(Map.of());
    given()
      .spec(requestSpecOfDefaultUser)
      .body(collectionIO)
      .when()
      .put(collectionsURL + "/" + collection.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO.class);

    // Assert
    CollectionIO updatedCollection = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(collectionsURL + "/" + collection.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(CollectionIO.class);
    assertEquals(Map.of(), updatedCollection.getAttributes());
  }
}
