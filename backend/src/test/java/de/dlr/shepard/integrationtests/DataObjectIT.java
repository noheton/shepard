package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.endpoints.DataObjectAttributes;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.version.io.VersionIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
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
  private static RequestSpecification requestSpecification;
  private static RequestSpecification orderByRequestSpecification;
  private static RequestSpecification versioningRequestSpecification;
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

    dataObjectsURL = String.format("/%s/%d/%s", Constants.COLLECTIONS, collection.getId(), Constants.DATA_OBJECTS);
    orderByDataObjectsURL = String.format(
      "/%s/%d/%s",
      Constants.COLLECTIONS,
      collectionForOrderByTest.getId(),
      Constants.DATA_OBJECTS
    );
    versioningURL = String.format(
      "/%s/%d/%s",
      Constants.COLLECTIONS,
      versionizedCollection.getId(),
      Constants.DATA_OBJECTS
    );
    versionizedCollectionURL = String.format("/%s/%d", Constants.COLLECTIONS, versionizedCollection.getId());

    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();

    orderByRequestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();

    versioningRequestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    var aInput = new DataObjectIO();
    aInput.setName("a");
    aDataObject = given()
      .spec(orderByRequestSpecification)
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
      .spec(orderByRequestSpecification)
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
      .spec(orderByRequestSpecification)
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
      .spec(orderByRequestSpecification)
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
      .spec(orderByRequestSpecification)
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
      .spec(orderByRequestSpecification)
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
      .spec(versioningRequestSpecification)
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
      .spec(versioningRequestSpecification)
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
      .spec(versioningRequestSpecification)
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
    // TODO: try out different attribute keys that should be valid
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
      .spec(requestSpecification)
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
    assertThat(actual.getCreatedBy()).isEqualTo(username);
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

    given().spec(requestSpecification).body(payload).when().post(dataObjectsURL).then().statusCode(400);
  }

  @Test
  @Order(3)
  public void postDataObjectTest_ParentId() {
    var payload = new DataObjectIO();
    payload.setName("ChildDummy");
    payload.setParentId(dataObject.getId());

    DataObjectIO actual = given()
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
  @Order(15)
  public void putDataObjectTest_Successful() {
    dataObject.setName("DataObjectSuccessorChanged");

    DataObjectIO actual = given()
      .spec(requestSpecification)
      .body(dataObject)
      .when()
      .put(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertThat(actual.getUpdatedAt()).isNotNull();
    assertThat(actual.getUpdatedBy()).isEqualTo(username);
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
    given().spec(requestSpecification).when().delete(dataObjectsURL + "/" + dataObject.getId()).then().statusCode(204);

    given().spec(requestSpecification).when().get(dataObjectsURL + "/" + dataObject.getId()).then().statusCode(404);
  }

  @Test
  @Order(19)
  public void getOrderByName() {
    DataObjectIO[] response = given()
      .spec(orderByRequestSpecification)
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
      .spec(orderByRequestSpecification)
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
      .spec(orderByRequestSpecification)
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
      .spec(orderByRequestSpecification)
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
      .spec(orderByRequestSpecification)
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
      .spec(orderByRequestSpecification)
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
      .spec(orderByRequestSpecification)
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
        .spec(versioningRequestSpecification)
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
        .spec(versioningRequestSpecification)
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
        .spec(versioningRequestSpecification)
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
        .spec(versioningRequestSpecification)
        .queryParam("versionUid", firstVersionUID.toString())
        .when()
        .get(versioningURL + "/" + firstVersionizedDataobject.getId())
        .then()
        .statusCode(200)
        .extract()
        .as(DataObjectIO.class);
      DataObjectIO firstDataObjectExpliciteHEADVersion = given()
        .spec(versioningRequestSpecification)
        .queryParam("versionUid", HEADVersionUID.toString())
        .when()
        .get(versioningURL + "/" + firstVersionizedDataobject.getId())
        .then()
        .statusCode(200)
        .extract()
        .as(DataObjectIO.class);
      DataObjectIO firstDataObjectImpliciteHEADVersion = given()
        .spec(versioningRequestSpecification)
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
        .spec(versioningRequestSpecification)
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
        .spec(versioningRequestSpecification)
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
        .spec(versioningRequestSpecification)
        .when()
        .delete(versioningURL + "/" + firstVersionizedDataobject.getId())
        .then()
        .statusCode(204);
      DataObjectIO[] response = given()
        .spec(versioningRequestSpecification)
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
        .spec(versioningRequestSpecification)
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
  public void putDataObjectTest_RemoveParentAndPredecessor() {
    DataObjectIO parent = new DataObjectIO();
    parent.setName("10001 Parent");
    parent = given()
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
      .body(dataObject)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);

    dataObject.setPredecessorIds(new long[] {});

    dataObject = given()
      .spec(requestSpecification)
      .body(dataObject)
      .when()
      .put(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    dataObject = given()
      .spec(requestSpecification)
      .when()
      .get(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertThat(dataObject.getPredecessorIds()).isEmpty();

    dataObject.setParentId(null);
    dataObject = given()
      .spec(requestSpecification)
      .body(dataObject)
      .when()
      .put(dataObjectsURL + "/" + dataObject.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    dataObject = given()
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
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
      .spec(requestSpecification)
      .body(dataObjectWithParentAndPredecessor)
      .when()
      .put(dataObjectsURL + "/" + dataObjectWithParentAndPredecessor.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    // Assert
    DataObjectIO updatedParent = given()
      .spec(requestSpecification)
      .when()
      .get(dataObjectsURL + "/" + dataObjectWithParentAndPredecessor.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(DataObjectIO.class);

    assertEquals(null, updatedParent.getParentId());
  }
}
