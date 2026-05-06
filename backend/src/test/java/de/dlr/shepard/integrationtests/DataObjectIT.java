package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.endpoints.DataObjectAttributes;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.DataObjectIOBuilder;
import de.dlr.shepard.context.version.io.VersionIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataObjectIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static CollectionIO collectionForOrderByTest;
  private static CollectionIO versionizedCollection;

  private static String dataObjectsURL;
  private static String orderByDataObjectsURL;
  private static String versioningURL;
  private static String versionizedCollectionURL;
  private static DataObjectIO dataObject;
  private static DataObjectIO child;
  private static DataObjectIO successor;
  private static DataObjectIO successorAndChild;
  private static DataObjectIO aDataObject;
  private static DataObjectIO bDataObject;
  private static DataObjectIO cDataObject;
  private static DataObjectIO dDataObject;
  private static DataObjectIO eDataObject;
  private static DataObjectIO fDataObject;
  private static DataObjectIO firstVersionizedDataobject;
  private static DataObjectIO secondVersionizedDataObject;
  private static DataObjectIO thirdVersionizedDataObject;
  private static UUID HEADVersionUID;
  private static UUID firstVersionUID;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("DataObjectTestCollection");
    collectionForOrderByTest = createCollection("OrderByTestCollection");
    versionizedCollection = createCollection("collection for versioning");

    dataObjectsURL = "/%s/%d/%s".formatted(Constants.COLLECTIONS, collection.getId(), Constants.DATA_OBJECTS);
    orderByDataObjectsURL = "/%s/%d/%s".formatted(
        Constants.COLLECTIONS,
        collectionForOrderByTest.getId(),
        Constants.DATA_OBJECTS
      );
    versioningURL = "/%s/%d/%s".formatted(Constants.COLLECTIONS, versionizedCollection.getId(), Constants.DATA_OBJECTS);
    versionizedCollectionURL = "/%s/%d".formatted(Constants.COLLECTIONS, versionizedCollection.getId());

    var aInput = new DataObjectIO();
    aInput.setName("a");
    aDataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(aInput)
      .when()
      .post(orderByDataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    var bInput = new DataObjectIO();
    bInput.setName("b");
    bDataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(bInput)
      .when()
      .post(orderByDataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    var cInput = new DataObjectIO();
    cInput.setName("c");
    cDataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(cInput)
      .when()
      .post(orderByDataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    var dInput = new DataObjectIO();
    dInput.setName("d");
    dDataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(dInput)
      .when()
      .post(orderByDataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    var eInput = new DataObjectIO();
    eInput.setName("e");
    eDataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(eInput)
      .when()
      .post(orderByDataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    var fInput = new DataObjectIO();
    fInput.setName("f");
    fDataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(fInput)
      .when()
      .post(orderByDataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    DataObjectIO firstVersionizedDataObjectInput = new DataObjectIO();
    firstVersionizedDataObjectInput.setName("first versionized DataObject");
    firstVersionizedDataobject = given()
      .spec(requestSpecOfDefaultUser)
      .body(firstVersionizedDataObjectInput)
      .when()
      .post(versioningURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    DataObjectIO secondVersionizedDataObjectInput = new DataObjectIO();
    secondVersionizedDataObjectInput.setName("second versionized DataObject");
    secondVersionizedDataObjectInput.setParentId(firstVersionizedDataobject.getId());
    secondVersionizedDataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(secondVersionizedDataObjectInput)
      .when()
      .post(versioningURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    DataObjectIO thirdVersionizedDataObjectInput = new DataObjectIO();
    thirdVersionizedDataObjectInput.setName("third versionized DataoObject");
    long[] thirdVersionizedDataObjectPredecessorIds = { secondVersionizedDataObject.getId() };
    thirdVersionizedDataObjectInput.setPredecessorIds(thirdVersionizedDataObjectPredecessorIds);
    thirdVersionizedDataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(thirdVersionizedDataObjectInput)
      .when()
      .post(versioningURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
  }

  @Test
  @Order(1)
  public void postDataObjectTest_Successful() {
    var attributes = Map.of(
      "a",
      "1",
      "b",
      "2",
      "key.abc",
      "value",
      "key|abc",
      "value",
      "key..abc",
      "value",
      "key/\\abc",
      "value",
      "key\"abc",
      "value"
    );
    var payload = new DataObjectIO();
    payload.setName("DataObjectDummy");
    payload.setDescription("My Description");
    payload.setAttributes(attributes);

    DataObjectIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    dataObject = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getAttributes()).isEqualTo(attributes);
    assertThat(actual.getDescription()).isEqualTo("My Description");
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getIncomingIds()).isEmpty();
    assertThat(actual.getReferenceIds()).isEmpty();
    assertThat(actual.getChildrenIds()).isEmpty();
    assertThat(actual.getPredecessorIds()).isEmpty();
    assertThat(actual.getSuccessorIds()).isEmpty();
    assertThat(actual.getParentId()).isNull();
    assertThat(actual.getName()).isEqualTo("DataObjectDummy");
    assertThat(actual.getCollectionId()).isEqualTo(collection.getId());
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void postDataObjectTest_ValidationError() {
    // use attribute key that contains parsing delimiter ('||') and therefore is invalid
    var attributes = Map.of("key||abc", "value");
    var payload = new DataObjectIO();
    payload.setName("DataObjectDummy");
    payload.setDescription("My Description");
    payload.setAttributes(attributes);

    given().spec(requestSpecOfDefaultUser).body(payload).when().post(dataObjectsURL).then().statusCode(400);
  }

  @Test
  @Order(3)
  public void postDataObjectTest_ParentId() {
    var payload = new DataObjectIO();
    payload.setName("ChildDummy");
    payload.setParentId(dataObject.getId());

    DataObjectIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    child = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getChildrenIds()).isEmpty();
    assertThat(actual.getPredecessorIds()).isEmpty();
    assertThat(actual.getSuccessorIds()).isEmpty();
    assertThat(actual.getParentId()).isEqualTo(dataObject.getId());
    assertThat(actual.getCollectionId()).isEqualTo(collection.getId());

    DataObjectIO parent = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertThat(parent).usingRecursiveComparison().ignoringFields("childrenIds").isEqualTo(dataObject);
    assertThat(parent.getChildrenIds()).containsExactlyInAnyOrder(child.getId());
    dataObject = parent;
  }

  @Test
  @Order(4)
  public void postDataObjectTest_PredecessorId() {
    var payload = new DataObjectIO();
    payload.setName("SuccessorDummy");
    payload.setPredecessorIds(new long[] { dataObject.getId() });

    DataObjectIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    successor = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getChildrenIds()).isEmpty();
    assertThat(actual.getPredecessorIds()).containsExactlyInAnyOrder(dataObject.getId());
    assertThat(actual.getSuccessorIds()).isEmpty();
    assertThat(actual.getParentId()).isNull();
    assertThat(actual.getCollectionId()).isEqualTo(collection.getId());

    DataObjectIO predecessor = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertThat(predecessor).usingRecursiveComparison().ignoringFields("successorIds").isEqualTo(dataObject);
    assertThat(predecessor.getSuccessorIds()).containsExactlyInAnyOrder(successor.getId());
    dataObject = predecessor;
  }

  @Test
  @Order(5)
  public void postDataObjectTest_PredecessorIdAndParentId() {
    var payload = new DataObjectIO();
    payload.setName("ChildAndSuccessorDummy");
    payload.setParentId(dataObject.getId());
    payload.setPredecessorIds(new long[] { child.getId() });

    DataObjectIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    successorAndChild = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getChildrenIds()).isEmpty();
    assertThat(actual.getPredecessorIds()).containsExactlyInAnyOrder(child.getId());
    assertThat(actual.getSuccessorIds()).isEmpty();
    assertThat(actual.getParentId()).isEqualTo(dataObject.getId());
    assertThat(actual.getCollectionId()).isEqualTo(collection.getId());

    DataObjectIO parent = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertThat(parent).usingRecursiveComparison().ignoringFields("childrenIds").isEqualTo(dataObject);
    assertThat(parent.getChildrenIds()).containsExactlyInAnyOrder(child.getId(), successorAndChild.getId());
    dataObject = parent;

    DataObjectIO predecessor = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + child.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertThat(predecessor).usingRecursiveComparison().ignoringFields("successorIds").isEqualTo(child);
    assertThat(predecessor.getSuccessorIds()).containsExactlyInAnyOrder(successorAndChild.getId());
    child = predecessor;
  }

  @Test
  @Order(6)
  public void getDataObjectTest_Successful() {
    DataObjectIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertThat(actual).isEqualTo(dataObject);
  }

  @Test
  @Order(7)
  public void getDataObjectTest_ByName() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("name", dataObject.getName())
      .when()
      .get(dataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);

    assertThat(response)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds", "predecessorIds")
      .containsExactlyInAnyOrder(dataObject);
  }

  @Test
  @Order(8)
  public void getDataObjectsTest_Successful() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);

    assertThat(response)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds", "predecessorIds")
      .containsExactlyInAnyOrder(dataObject, child, successor, successorAndChild);
  }

  @Test
  @Order(9)
  public void getDataObjectsTest_ByParent() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("parentId", dataObject.getId())
      .when()
      .get(dataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);

    assertThat(response)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds", "predecessorIds")
      .containsExactlyInAnyOrder(child, successorAndChild);
  }

  @Test
  @Order(10)
  public void getDataObjectsTest_WithoutParent() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("parentId", -1)
      .when()
      .get(dataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);

    assertThat(response)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds", "predecessorIds")
      .containsExactlyInAnyOrder(dataObject, successor);
  }

  @Test
  @Order(11)
  public void getDataObjectsTest_ByPredecessor() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("predecessorId", dataObject.getId())
      .when()
      .get(dataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);

    assertThat(response)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds", "predecessorIds")
      .containsExactlyInAnyOrder(successor);
  }

  @Test
  @Order(12)
  public void getDataObjectsTest_WithoutPredecessor() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("predecessorId", -1)
      .when()
      .get(dataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);

    assertThat(response)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds", "predecessorIds")
      .containsExactlyInAnyOrder(dataObject, child);
  }

  @Test
  @Order(13)
  public void getDataObjectsTest_BySuccessor() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("successorId", successor.getId())
      .when()
      .get(dataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);

    assertThat(response)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds", "predecessorIds")
      .containsExactlyInAnyOrder(dataObject);
  }

  @Test
  @Order(14)
  public void getDataObjectsTest_WithoutSuccessor() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("successorId", -1)
      .when()
      .get(dataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);

    assertThat(response)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds", "predecessorIds")
      .containsExactlyInAnyOrder(successor, successorAndChild);
  }

  @Test
  @Order(14)
  public void getDataObjectsTest_negativeId_BadRequest() {
    given().spec(requestSpecOfDefaultUser).when().get(dataObjectsURL + "/-1").then().statusCode(400);
  }

  @Test
  @Order(14)
  public void getDataObjectsTest_wrongVersionUUIDFormat_BadRequest() {
    // correct UUID: 00000000-0000-0000-0000-000000000000
    // false UUID:   000000-0000-0000-0000-000000000000
    given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("versionUId", "000000-0000-0000-0000-000000000000")
      .when()
      .get(dataObjectsURL)
      .then()
      .statusCode(400);
  }

  @Test
  @Order(15)
  public void putDataObjectTest_Successful() {
    dataObject.setName("DataObjectSuccessorChanged");

    DataObjectIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObject)
      .when()
      .put(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertThat(actual.getUpdatedAt()).isNotNull();
    assertThat(actual.getUpdatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual)
      .usingRecursiveComparison()
      .ignoringFields("updatedBy", "updatedAt", "childrenIds")
      .isEqualTo(dataObject);
    HashSet<Long> childrenActual = new HashSet<Long>();
    for (int i = 0; i < actual.getChildrenIds().length; i++) childrenActual.add(actual.getChildrenIds()[i]);
    HashSet<Long> childrenDataObject = new HashSet<Long>();
    for (int i = 0; i < dataObject.getChildrenIds().length; i++) childrenDataObject.add(dataObject.getChildrenIds()[i]);
    assertEquals(childrenActual, childrenDataObject);
    dataObject = actual;
  }

  @Test
  @Order(16)
  public void putDataObjectTest_newParent() {
    successorAndChild.setName("DataObjectChildChanged");
    successorAndChild.setParentId(successor.getId());

    DataObjectIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(successorAndChild)
      .when()
      .put(dataObjectsURL + "/" + successorAndChild.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertThat(actual).usingRecursiveComparison().ignoringFields("updatedBy", "updatedAt").isEqualTo(successorAndChild);
    successorAndChild = actual;

    DataObjectIO oldParent = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertThat(oldParent).usingRecursiveComparison().ignoringFields("childrenIds").isEqualTo(dataObject);
    assertThat(oldParent.getChildrenIds()).doesNotContain(successorAndChild.getId());
    dataObject = oldParent;

    successor.setChildrenIds(new long[] { actual.getId() });
    DataObjectIO newParent = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + successor.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertThat(newParent).isEqualTo(successor);
  }

  @Test
  @Order(17)
  public void putDataObjectTest_newPredecessor() {
    child.setName("DataObjectSuccessorChanged");
    child.setPredecessorIds(new long[] { dataObject.getId() });
    DataObjectIO actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(child)
      .when()
      .put(dataObjectsURL + "/" + child.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertThat(actual).usingRecursiveComparison().ignoringFields("updatedBy", "updatedAt").isEqualTo(child);
    child = actual;

    DataObjectIO newPredecessor = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertThat(newPredecessor)
      .usingRecursiveComparison()
      .ignoringFields("successorIds", "childrenIds")
      .isEqualTo(dataObject);
    assertThat(newPredecessor.getChildrenIds()).containsExactlyInAnyOrder(dataObject.getChildrenIds());
    assertThat(newPredecessor.getSuccessorIds()).containsExactlyInAnyOrder(successor.getId(), child.getId());
    dataObject = newPredecessor;
  }

  @Test
  @Order(18)
  public void deleteDataObjectTest_Successful() {
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(204);
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(404);
    given().spec(requestSpecOfDefaultUser).when().get(dataObjectsURL + "/" + dataObject.getId()).then().statusCode(404);
  }

  @Test
  @Order(19)
  public void getOrderByName() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("orderBy", DataObjectAttributes.name)
      .when()
      .get(orderByDataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);
    assertThat(response).containsExactly(aDataObject, bDataObject, cDataObject, dDataObject, eDataObject, fDataObject);
  }

  @Test
  @Order(20)
  public void getOrderByNameDesc() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("orderBy", DataObjectAttributes.name)
      .queryParam("orderDesc", true)
      .when()
      .get(orderByDataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);
    assertThat(response).containsExactly(fDataObject, eDataObject, dDataObject, cDataObject, bDataObject, aDataObject);
  }

  @Test
  @Order(21)
  public void getOrderByCreatedAt() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("orderBy", DataObjectAttributes.createdAt)
      .queryParam("orderDesc", false)
      .when()
      .get(orderByDataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);
    assertThat(response).containsExactly(aDataObject, bDataObject, cDataObject, dDataObject, eDataObject, fDataObject);
  }

  @Test
  @Order(22)
  public void getOrderByCreatedFirstPage() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("orderBy", DataObjectAttributes.createdAt)
      .queryParam("orderDesc", false)
      .queryParam("page", 0)
      .queryParam("size", 3)
      .when()
      .get(orderByDataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);
    assertThat(response).containsExactly(aDataObject, bDataObject, cDataObject);
  }

  @Test
  @Order(23)
  public void getOrderByCreatedSecondPage() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("orderBy", DataObjectAttributes.createdAt)
      .queryParam("orderDesc", false)
      .queryParam("page", 1)
      .queryParam("size", 3)
      .when()
      .get(orderByDataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);
    assertThat(response).containsExactly(dDataObject, eDataObject, fDataObject);
  }

  @Test
  @Order(24)
  public void getOrderByCreatedDescSecondPage() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("orderBy", DataObjectAttributes.createdAt)
      .queryParam("orderDesc", true)
      .queryParam("page", 1)
      .queryParam("size", 3)
      .when()
      .get(orderByDataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);
    assertThat(response).containsExactly(cDataObject, bDataObject, aDataObject);
  }

  @Test
  @Order(25)
  public void getOrderByCreatedDescFirstPage() {
    DataObjectIO[] response = given()
      .spec(requestSpecOfDefaultUser)
      .queryParam("orderBy", DataObjectAttributes.createdAt)
      .queryParam("orderDesc", true)
      .queryParam("page", 0)
      .queryParam("size", 3)
      .when()
      .get(orderByDataObjectsURL)
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO[].class);
    assertThat(response).containsExactly(fDataObject, eDataObject, dDataObject);
  }

  @Test
  @Order(25)
  public void getHEADVersion() {
    if (VersioningFeatureToggle.isEnabled()) {
      VersionIO[] HEADVersion = given()
        .spec(requestSpecOfDefaultUser)
        .when()
        .get(versionizedCollectionURL + "/versions")
        .then()
        .statusCode(200)
        .extract()
        .as(VersionIO[].class);
      assertEquals(HEADVersion.length, 1);
      HEADVersionUID = HEADVersion[0].getUid();
    }
  }

  @Test
  @Order(26)
  public void createFirstVersion() {
    if (VersioningFeatureToggle.isEnabled()) {
      VersionIO firstVersionInput = new VersionIO();
      firstVersionInput.setName("first version");
      firstVersionInput.setDescription("first version");
      VersionIO firstVersion = given()
        .spec(requestSpecOfDefaultUser)
        .body(firstVersionInput)
        .when()
        .post(versionizedCollectionURL + "/versions")
        .then()
        .statusCode(201)
        .extract()
        .as(VersionIO.class);
      assertEquals(firstVersionInput.getName(), firstVersion.getName());
      assertEquals(firstVersionInput.getDescription(), firstVersion.getDescription());
      firstVersionUID = firstVersion.getUid();
    }
  }

  @Test
  @Order(27)
  public void putFirstVersionizedDataObject() {
    if (VersioningFeatureToggle.isEnabled()) {
      String newName = "first versionized DataObject with new name";
      firstVersionizedDataobject.setName(newName);
      DataObjectIO actual = given()
        .spec(requestSpecOfDefaultUser)
        .body(firstVersionizedDataobject)
        .when()
        .put(versioningURL + "/" + firstVersionizedDataobject.getId())
        .then()
        .statusCode(200)
        .extract()
        .as(DataObjectIO.class);
      assertEquals(newName, actual.getName());
    }
  }

  @Test
  @Order(28)
  public void differentFirstVersionizedDataObjects() {
    if (VersioningFeatureToggle.isEnabled()) {
      DataObjectIO firstDataObjectFirstVersion = given()
        .spec(requestSpecOfDefaultUser)
        .queryParam("versionUid", firstVersionUID.toString())
        .when()
        .get(versioningURL + "/" + firstVersionizedDataobject.getId())
        .then()
        .statusCode(200)
        .extract()
        .as(DataObjectIO.class);
      DataObjectIO firstDataObjectExpliciteHEADVersion = given()
        .spec(requestSpecOfDefaultUser)
        .queryParam("versionUid", HEADVersionUID.toString())
        .when()
        .get(versioningURL + "/" + firstVersionizedDataobject.getId())
        .then()
        .statusCode(200)
        .extract()
        .as(DataObjectIO.class);
      DataObjectIO firstDataObjectImpliciteHEADVersion = given()
        .spec(requestSpecOfDefaultUser)
        .when()
        .get(versioningURL + "/" + firstVersionizedDataobject.getId())
        .then()
        .statusCode(200)
        .extract()
        .as(DataObjectIO.class);
      assertEquals(firstDataObjectExpliciteHEADVersion.getName(), firstDataObjectImpliciteHEADVersion.getName());
      assertNotEquals(firstDataObjectExpliciteHEADVersion.getName(), firstDataObjectFirstVersion.getName());
    }
  }

  @Test
  @Order(29)
  public void predecessorsInFirstVersion() {
    if (VersioningFeatureToggle.isEnabled()) {
      DataObjectIO thirdDataObjectFirstVersion = given()
        .spec(requestSpecOfDefaultUser)
        .queryParam("versionUid", firstVersionUID.toString())
        .when()
        .get(versioningURL + "/" + thirdVersionizedDataObject.getId())
        .then()
        .statusCode(200)
        .extract()
        .as(DataObjectIO.class);
      assertEquals(1, thirdDataObjectFirstVersion.getPredecessorIds().length);
      long actualPredecessorId = thirdDataObjectFirstVersion.getPredecessorIds()[0];
      long expectedPredecessorId = secondVersionizedDataObject.getId();
      assertEquals(actualPredecessorId, expectedPredecessorId);
    }
  }

  @Test
  @Order(30)
  public void parentInFirstVersion() {
    if (VersioningFeatureToggle.isEnabled()) {
      DataObjectIO secondDataObjectFirstVersion = given()
        .spec(requestSpecOfDefaultUser)
        .queryParam("versionUid", firstVersionUID.toString())
        .when()
        .get(versioningURL + "/" + secondVersionizedDataObject.getId())
        .then()
        .statusCode(200)
        .extract()
        .as(DataObjectIO.class);
      long actualParentId = secondDataObjectFirstVersion.getParentId();
      long expectedParentId = firstVersionizedDataobject.getId();
      assertEquals(actualParentId, expectedParentId);
    }
  }

  @Test
  @Order(31)
  public void deleteFirstVersionizedDataObject() {
    if (VersioningFeatureToggle.isEnabled()) {
      given()
        .spec(requestSpecOfDefaultUser)
        .when()
        .delete(versioningURL + "/" + firstVersionizedDataobject.getId())
        .then()
        .statusCode(204);
      DataObjectIO[] response = given()
        .spec(requestSpecOfDefaultUser)
        .when()
        .get(versioningURL)
        .then()
        .statusCode(200)
        .extract()
        .as(DataObjectIO[].class);
      if (VersioningFeatureToggle.isEnabled()) assertEquals(2, response.length);
      else assertEquals(5, response.length);
    }
  }

  @Test
  @Order(32)
  public void getDataObjectsOfFirstVersion() {
    if (VersioningFeatureToggle.isEnabled()) {
      DataObjectIO[] response = given()
        .spec(requestSpecOfDefaultUser)
        .queryParam("versionUid", firstVersionUID)
        .when()
        .get(versioningURL)
        .then()
        .statusCode(200)
        .extract()
        .as(DataObjectIO[].class);
      assertEquals(3, response.length);
    }
  }

  @Test
  public void getDataObject_wrongId_notFound() {
    ErrorResponse response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    assertThat(response.getMessage()).startsWith("ID ERROR - DataObject");
  }

  @Test
  public void getDataObject_doesNotExistNegativeId_badRequest() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/-1")
      .then()
      .statusCode(400)
      .extract();
    assertThat(actual.body().asString()).isEqualTo(
      "{\"title\":\"Constraint Violation\",\"status\":400,\"violations\":[{\"field\":\"getDataObject.dataObjectId\",\"message\":\"must be greater than or equal to 0\"}]}"
    );
  }

  @Test
  public void getDataObject_privateCollection_forbidden() {
    // This is a test implementation for the Bug described in #475
    CollectionIO privateCollection = createCollection("private collection", otherUser);
    DataObjectIO privateDataObject = createDataObject("private data object", privateCollection.getId(), otherUser);

    ErrorResponse response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(
        "/%s/%d/%s/%d".formatted(
            Constants.COLLECTIONS,
            privateCollection.getId(),
            Constants.DATA_OBJECTS,
            privateDataObject.getId()
          )
      )
      .then()
      .statusCode(403)
      .extract()
      .as(ErrorResponse.class);
    assertThat(response.getMessage()).isEqualTo("The requested action is forbidden by the permission policies");
  }

  @Test
  public void getDataObject_wrongCollection_notFound() {
    CollectionIO thisCollection = createCollection("private collection");
    DataObjectIO thisDataObject = createDataObject("private data object", thisCollection.getId());
    CollectionIO otherCollection = createCollection("private collection");

    ErrorResponse response = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(
        "/%s/%d/%s/%d".formatted(
            Constants.COLLECTIONS,
            otherCollection.getId(),
            Constants.DATA_OBJECTS,
            thisDataObject.getId()
          )
      )
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    assertThat(response.getMessage()).isEqualTo("ID ERROR - There is no association between collection and dataObject");
  }

  @Test
  public void putDataObjectTest_RemoveParentAndPredecessor() {
    DataObjectIO parent = new DataObjectIO();
    parent.setName("10001 Parent");
    parent = given()
      .spec(requestSpecOfDefaultUser)
      .body(parent)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    DataObjectIO predecessor = new DataObjectIO();
    predecessor.setName("10001 predecessor");
    predecessor = given()
      .spec(requestSpecOfDefaultUser)
      .body(predecessor)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    DataObjectIO dataObject = new DataObjectIO();
    dataObject.setName("ChildAndSuccessorDummy");
    dataObject.setParentId(parent.getId());
    dataObject.setPredecessorIds(new long[] { predecessor.getId() });

    dataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObject)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    dataObject.setPredecessorIds(new long[] {});

    dataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObject)
      .when()
      .put(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    dataObject = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertThat(dataObject.getPredecessorIds()).isEmpty();

    dataObject.setParentId(null);
    dataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObject)
      .when()
      .put(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    dataObject = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertThat(dataObject.getParentId()).isEqualTo(null);
  }

  @Test
  public void updateDataObject_deleteParentWhenPredecessorIsPresent_successfullyDeleteParent() {
    // Arrange
    var dataObjectAsParentAndPredecessorCreationPayload = new DataObjectIO();
    dataObjectAsParentAndPredecessorCreationPayload.setName("dataObjectWithParentAndPredecessor");

    DataObjectIO dataObjectAsParentAndPredecessor = given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObjectAsParentAndPredecessorCreationPayload)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    var dataObjectWithParentAndPredecessorCreationPayload = new DataObjectIO();
    dataObjectWithParentAndPredecessorCreationPayload.setName("dataObjectWithParentAndPredecessor");
    dataObjectWithParentAndPredecessorCreationPayload.setParentId(dataObjectAsParentAndPredecessor.getId());
    dataObjectWithParentAndPredecessorCreationPayload.setPredecessorIds(
      new long[] { dataObjectAsParentAndPredecessor.getId() }
    );

    DataObjectIO dataObjectWithParentAndPredecessor = given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObjectWithParentAndPredecessorCreationPayload)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    // Act
    dataObjectWithParentAndPredecessor.setParentId(null);

    given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObjectWithParentAndPredecessor)
      .when()
      .put(dataObjectsURL + "/" + dataObjectWithParentAndPredecessor.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    // Assert
    DataObjectIO updatedParent = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + dataObjectWithParentAndPredecessor.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertEquals(null, updatedParent.getParentId());
  }

  @Test
  public void updateDataObject_deleteAttribute_successfullyDeleteAttribute() {
    // Arrange
    DataObjectIO dataObjectIO = new DataObjectIOBuilder().setAttributes(Map.of("name", "my data object")).build();
    DataObjectIO dataObject = given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObjectIO)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    // Act
    dataObjectIO.setAttributes(Map.of());
    given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObjectIO)
      .when()
      .put(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    // Assert
    DataObjectIO updatedDataObject = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    assertEquals(Map.of(), updatedDataObject.getAttributes());
  }

  @Test
  public void createDataObjectWithSuccessors() {
    // Arrange
    var dataObjectWithSuccessors = new DataObjectIO();
    dataObjectWithSuccessors.setName("dows");
    long[] successorIds = { 0L };
    dataObjectWithSuccessors.setSuccessorIds(successorIds);
    //Act
    ErrorResponse res = given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObjectWithSuccessors)
      .when()
      .post(orderByDataObjectsURL)
      .then()
      .statusCode(400)
      .extract()
      .as(ErrorResponse.class);
    //Assert
    assertEquals("InvalidBodyException", res.getException());
  }

  @Test
  public void updateDataObjectWithIncorrectSuccessors() {
    // Arrange
    var do1IO = new DataObjectIO();
    do1IO.setName("do1");
    DataObjectIO do1 = given()
      .spec(requestSpecOfDefaultUser)
      .body(do1IO)
      .when()
      .post(orderByDataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    var do2IO = new DataObjectIO();
    do2IO.setName("do2");
    long[] predecessors = { do1.getId() };
    do2IO.setPredecessorIds(predecessors);
    DataObjectIO do2 = given()
      .spec(requestSpecOfDefaultUser)
      .body(do2IO)
      .when()
      .post(orderByDataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    //Act
    var do1New = new DataObjectIO();
    do1New.setName("do1New");
    long[] successors = { do2.getId() + 1 };
    do1New.setSuccessorIds(successors);
    System.out.println("PUTURL: " + dataObjectsURL + "/" + do1.getId());
    ErrorResponse res = given()
      .spec(requestSpecOfDefaultUser)
      .body(do1New)
      .when()
      .put(orderByDataObjectsURL + "/" + do1.getId())
      .then()
      .statusCode(400)
      .extract()
      .as(ErrorResponse.class);
    //Assert
    assertEquals("InvalidBodyException", res.getException());
  }

  @Test
  public void updateDataObjectWithCorrectSuccessors() {
    // Arrange
    var do1IO = new DataObjectIO();
    do1IO.setName("do1");
    DataObjectIO do1 = given()
      .spec(requestSpecOfDefaultUser)
      .body(do1IO)
      .when()
      .post(orderByDataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    var do2IO = new DataObjectIO();
    do2IO.setName("do2");
    long[] predecessors = { do1.getId() };
    do2IO.setPredecessorIds(predecessors);
    DataObjectIO do2 = given()
      .spec(requestSpecOfDefaultUser)
      .body(do2IO)
      .when()
      .post(orderByDataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    //Act
    var do1New = new DataObjectIO();
    do1New.setName("do1New");
    long[] successors = { do2.getId() };
    do1New.setSuccessorIds(successors);
    System.out.println("PUTURL: " + dataObjectsURL + "/" + do1.getId());
    DataObjectIO res = given()
      .spec(requestSpecOfDefaultUser)
      .body(do1New)
      .when()
      .put(orderByDataObjectsURL + "/" + do1.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);
    //Assert
    assertEquals(do1New.getName(), res.getName());
  }

  // -- P21: RFC 7396 JSON Merge Patch on /collections/{cid}/dataObjects/{did} -
  // See aidocs/16-dispatcher-backlog.md P21; aidocs/26-crud-consistency.md
  // finding #1. PATCH ships additively in /v1/ alongside the existing PUT
  // (which keeps full-replace semantics). Mirrors the P21 Collection pilot.

  @Test
  @Order(33)
  public void patchDataObjectTest_happyPath_updatesOnlyDescription() {
    // Arrange: create a fresh data object so other tests are unaffected.
    DataObjectIO seed = new DataObjectIO();
    String seedName = "PatchDataObjectHappy" + System.currentTimeMillis();
    seed.setName(seedName);
    seed.setDescription("original");
    seed.setAttributes(Map.of("k", "v"));
    DataObjectIO created = given()
      .spec(requestSpecOfDefaultUser)
      .body(seed)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    // Act: PATCH only the description.
    String mergePatch = "{\"description\":\"new\"}";
    DataObjectIO patched = given()
      .spec(requestSpecOfDefaultUser)
      .contentType("application/merge-patch+json")
      .body(mergePatch)
      .when()
      .patch(dataObjectsURL + "/" + created.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    // Assert: description changed; everything else preserved.
    assertThat(patched.getDescription()).isEqualTo("new");
    assertThat(patched.getName()).isEqualTo(seedName);
    assertThat(patched.getAttributes()).isEqualTo(Map.of("k", "v"));
    assertThat(patched.getId()).isEqualTo(created.getId());
    assertThat(patched.getUpdatedAt()).isNotNull();
    assertThat(patched.getUpdatedBy()).isEqualTo(nameOfDefaultUser);
  }

  @Test
  @Order(33)
  public void patchDataObjectTest_explicitNull_clearsField() {
    // Arrange: data object with a non-null description.
    DataObjectIO seed = new DataObjectIO();
    seed.setName("PatchDataObjectNull" + System.currentTimeMillis());
    seed.setDescription("will be cleared");
    DataObjectIO created = given()
      .spec(requestSpecOfDefaultUser)
      .body(seed)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    // Act: PATCH with explicit null per RFC 7396.
    String mergePatch = "{\"description\":null}";
    DataObjectIO patched = given()
      .spec(requestSpecOfDefaultUser)
      .contentType("application/merge-patch+json")
      .body(mergePatch)
      .when()
      .patch(dataObjectsURL + "/" + created.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    // Assert: description set to null; name preserved.
    assertThat(patched.getDescription()).isNull();
    assertThat(patched.getName()).isEqualTo(seed.getName());
  }

  @Test
  @Order(33)
  public void patchDataObjectTest_emptyBody_isNoOp() {
    // Arrange.
    DataObjectIO seed = new DataObjectIO();
    seed.setName("PatchDataObjectNoOp" + System.currentTimeMillis());
    seed.setDescription("unchanged");
    seed.setAttributes(Map.of("a", "1"));
    DataObjectIO created = given()
      .spec(requestSpecOfDefaultUser)
      .body(seed)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    // Act: empty merge patch.
    DataObjectIO patched = given()
      .spec(requestSpecOfDefaultUser)
      .contentType("application/merge-patch+json")
      .body("{}")
      .when()
      .patch(dataObjectsURL + "/" + created.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    // Assert: every domain field preserved (updatedAt/By legitimately change).
    assertThat(patched)
      .usingRecursiveComparison()
      .ignoringFields("updatedBy", "updatedAt")
      .isEqualTo(created);
  }

  @Test
  @Order(33)
  public void patchDataObjectTest_mergeViolatesValidation_returns400() {
    // Arrange.
    DataObjectIO seed = new DataObjectIO();
    seed.setName("PatchDataObjectValidation" + System.currentTimeMillis());
    DataObjectIO created = given()
      .spec(requestSpecOfDefaultUser)
      .body(seed)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    // Act: name is @NotBlank on the merged result, blanking it must fail validation.
    String mergePatch = "{\"name\":\"\"}";
    given()
      .spec(requestSpecOfDefaultUser)
      .contentType("application/merge-patch+json")
      .body(mergePatch)
      .when()
      .patch(dataObjectsURL + "/" + created.getId())
      .then()
      .statusCode(400);
  }

  @Test
  @Order(33)
  public void patchDataObjectTest_withoutWritePermission_returns403() {
    // Arrange: defaultUser owns the data object; otherUser has no rights.
    DataObjectIO seed = new DataObjectIO();
    seed.setName("PatchDataObjectPerm" + System.currentTimeMillis());
    seed.setDescription("private");
    DataObjectIO created = given()
      .spec(requestSpecOfDefaultUser)
      .body(seed)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    // Act + Assert: otherUser cannot PATCH.
    given()
      .spec(requestSpecOfOtherUser)
      .contentType("application/merge-patch+json")
      .body("{\"description\":\"hacked\"}")
      .when()
      .patch(dataObjectsURL + "/" + created.getId())
      .then()
      .statusCode(403);

    // And without auth at all -> 401.
    var noAuth = new RequestSpecBuilder().setContentType("application/merge-patch+json").build();
    given()
      .spec(noAuth)
      .body("{\"description\":\"hacked\"}")
      .when()
      .patch(dataObjectsURL + "/" + created.getId())
      .then()
      .statusCode(401);
  }

  @Test
  @Order(33)
  public void patchDataObjectTest_acceptsApplicationJsonContentType() {
    // Pilot decision: the /v1/ PATCH accepts both application/merge-patch+json
    // (RFC 7396 preferred) and application/json so existing callers and SDK
    // generators that don't yet emit the merge-patch media type aren't broken.
    // Future /v2/ will require application/merge-patch+json.
    DataObjectIO seed = new DataObjectIO();
    seed.setName("PatchDataObjectCT" + System.currentTimeMillis());
    seed.setDescription("orig");
    DataObjectIO created = given()
      .spec(requestSpecOfDefaultUser)
      .body(seed)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    DataObjectIO patched = given()
      .spec(requestSpecOfDefaultUser) // ContentType.JSON ("application/json")
      .body("{\"description\":\"via-json\"}")
      .when()
      .patch(dataObjectsURL + "/" + created.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertThat(patched.getDescription()).isEqualTo("via-json");
    assertThat(patched.getName()).isEqualTo(seed.getName());
  }
}
