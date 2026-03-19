package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.io.FileContainerIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FileReferenceIT extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;
  private static ShepardFile file;

  private static String referencesURL;
  private static String containerURL;
  private static RequestSpecification fileRequestSpec;

  private static FileContainerIO container;
  private static FileReferenceIO reference;

  @BeforeAll
  public static void setUp() throws URISyntaxException {
    collection = createCollection("FileReferenceTestCollection");
    dataObject = createDataObject("FileReferenceTestDataObject", collection.getId());

    referencesURL = "/%s/%d/%s/%d/%s".formatted(
        Constants.COLLECTIONS,
        collection.getId(),
        Constants.DATA_OBJECTS,
        dataObject.getId(),
        Constants.FILE_REFERENCES
      );

    containerURL = "/" + Constants.FILE_CONTAINERS;
    fileRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.MULTIPART)
      .addHeader("X-API-KEY", defaultUser.getApiKey().getJws())
      .build();

    var toCreate = new FileContainerIO();
    toCreate.setName("FileContainer");
    InputStream targetStream = new ByteArrayInputStream("Hello World!".getBytes());
    container = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(containerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(FileContainerIO.class);
    file = given()
      .spec(fileRequestSpec)
      .multiPart("file", "test.txt", targetStream)
      .when()
      .post("%s/%d/%s".formatted(containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(201)
      .extract()
      .as(ShepardFile.class);
  }

  @Test
  @Order(1)
  public void createFileReference() {
    var toCreate = new FileReferenceIO();
    toCreate.setName("FileReferenceDummy");
    toCreate.setFileOids(new String[] { file.getOid() });
    toCreate.setFileContainerId(container.getId());

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(referencesURL)
      .then()
      .statusCode(201)
      .extract()
      .as(FileReferenceIO.class);
    reference = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
    assertThat(actual.getName()).isEqualTo("FileReferenceDummy");
    assertThat(actual.getFileContainerId()).isEqualTo(container.getId());
    assertThat(actual.getFileOids()).containsExactly(file.getOid());
    assertThat(actual.getType()).isEqualTo("FileReference");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getFileReferences() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL)
      .then()
      .statusCode(200)
      .extract()
      .as(FileReferenceIO[].class);
    assertThat(actual).containsExactly(reference);
  }

  @Test
  @Order(3)
  public void getFileReference() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/" + reference.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(FileReferenceIO.class);

    assertThat(actual).isEqualTo(reference);
  }

  @Test
  @Order(4)
  public void getFileReference_referenceDoesNotExist_notFound() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(referencesURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - File Reference with id 99999 is null or deleted");
  }

  @Test
  @Order(5)
  public void getFileReference_idBelongsToWrongDataObject_notFound() {
    DataObjectIO otherDataObject = createDataObject("OtherStructuredDataReferenceTestDataObject", collection.getId());

    var toCreate = new FileReferenceIO();
    toCreate.setName("FileReferenceDummy");
    toCreate.setFileOids(new String[] { file.getOid() });
    toCreate.setFileContainerId(container.getId());

    FileReferenceIO otherRef = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(
        "/%s/%d/%s/%d/%s".formatted(
            Constants.COLLECTIONS,
            collection.getId(),
            Constants.DATA_OBJECTS,
            otherDataObject.getId(),
            Constants.FILE_REFERENCES
          )
      )
      .then()
      .statusCode(201)
      .extract()
      .as(FileReferenceIO.class);

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
  public void getFileReferencePayload() throws URISyntaxException, IOException {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get("%s/%d/payload/%s".formatted(referencesURL, reference.getId(), file.getOid()))
      .then()
      .statusCode(200)
      .extract()
      .asString();

    assertThat(actual).isEqualTo("Hello World!");
  }

  @Test
  @Order(7)
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
