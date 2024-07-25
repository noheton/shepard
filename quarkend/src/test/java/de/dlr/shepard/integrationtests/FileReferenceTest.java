package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.util.Constants;
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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FileReferenceTest extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;
  private static ShepardFile file;

  private static String referencesURL;
  private static RequestSpecification referencesRequestSpec;
  private static String containerURL;
  private static RequestSpecification containerRequestSpec;
  private static RequestSpecification fileRequestSpec;

  private static FileContainerIO container;
  private static FileReferenceIO reference;

  @BeforeAll
  public static void setUp() throws URISyntaxException {
    collection = createCollection("FileReferenceTestCollection");
    dataObject = createDataObject("FileReferenceTestDataObject", collection.getId());

    referencesURL = String.format(
      "/%s/%d/%s/%d/%s",
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATAOBJECTS,
      dataObject.getId(),
      Constants.FILE_REFERENCES
    );
    referencesRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();

    containerURL = "/" + Constants.FILES;
    containerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    fileRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.MULTIPART)
      .addHeader("X-API-KEY", jws)
      .build();

    var toCreate = new FileContainerIO();
    toCreate.setName("FileContainer");
    InputStream targetStream = new ByteArrayInputStream("Hello World!".getBytes());
    container = given()
      .spec(containerRequestSpec)
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
      .post(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
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
      .spec(referencesRequestSpec)
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
    assertThat(actual.getCreatedBy()).isEqualTo(username);
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
      .spec(referencesRequestSpec)
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
      .spec(referencesRequestSpec)
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
  public void getFileReferencePayload() throws URISyntaxException, IOException {
    var actual = given()
      .spec(referencesRequestSpec)
      .when()
      .get(String.format("%s/%d/payload/%s", referencesURL, reference.getId(), file.getOid()))
      .then()
      .statusCode(200)
      .extract()
      .asString();

    assertThat(actual).isEqualTo("Hello World!");
  }

  @Test
  @Order(5)
  public void deleteReferences() {
    given().spec(referencesRequestSpec).when().delete(referencesURL + "/" + reference.getId()).then().statusCode(204);

    given().spec(referencesRequestSpec).when().get(referencesURL + "/" + reference.getId()).then().statusCode(404);
  }
}
