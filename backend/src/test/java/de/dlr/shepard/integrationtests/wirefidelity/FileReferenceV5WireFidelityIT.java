package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.io.FileContainerIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * v5 wire-fidelity test for {@code /shepard/api/.../fileReferences}.
 *
 * <p>Exercises the full {@code BasicReferenceIO + VersionableEntity + fileOids[] +
 * fileContainerId} shape. As noted in {@code docs/reference/v5-cross-instance-quirks.md},
 * FileReference requires a multipart file upload to create a valid {@code oid};
 * this test uses a small in-memory byte array to avoid the "needs multipart setup"
 * blocker documented in the fixture gap list.
 */
@QuarkusIntegrationTest
public class FileReferenceV5WireFidelityIT extends V5WireFidelityTest {

  private static final String SLUG = "filereferences";

  private static CollectionIO collection;
  private static DataObjectIO dataObject;
  private static FileContainerIO container;
  private static ShepardFile uploadedFile;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("FileReferenceWireFixture_" + System.currentTimeMillis());
    dataObject = createDataObject("FileReferenceWireFixtureDO", collection.getId());

    var containerIO = new FileContainerIO();
    containerIO.setName("FileReferenceWireFixtureContainer");
    container = given()
      .spec(requestSpecOfDefaultUser)
      .body(containerIO)
      .when()
      .post("/" + Constants.FILE_CONTAINERS)
      .then()
      .statusCode(201)
      .extract()
      .as(FileContainerIO.class);

    RequestSpecification multipartSpec = new RequestSpecBuilder()
      .setContentType(ContentType.MULTIPART)
      .addHeader("X-API-KEY", defaultUser.getApiKey().getJws())
      .build();

    uploadedFile = given()
      .spec(multipartSpec)
      .multiPart("file", "wire-fixture.txt", new ByteArrayInputStream("v5-wire-fixture".getBytes()))
      .when()
      .post("/" + Constants.FILE_CONTAINERS + "/" + container.getId() + "/" + Constants.PAYLOAD)
      .then()
      .statusCode(201)
      .extract()
      .as(ShepardFile.class);
  }

  /**
   * Additional dynamic fields for FileReferenceIO:
   * {@code fileContainerId} — server-assigned long, already in DEFAULT but explicit here for
   * documentation; {@code fileOids} — OIDs are deterministic MinIO/Garage object names minted
   * at upload time and must not be compared literally.
   */
  @Override
  protected Map<String, String> dynamicFields() {
    Map<String, String> m = new LinkedHashMap<>(DEFAULT_DYNAMIC_FIELDS);
    m.put("fileContainerId", V5JsonNormalizer.ANY_LONG);
    m.put("fileOids", V5JsonNormalizer.ANY_STRING_ARRAY);
    return m;
  }

  @Test
  public void createFileReference_wireMatchesFixture() {
    var ref = new FileReferenceIO();
    ref.setName("FileReferenceWireFixture");
    ref.setFileContainerId(container.getId());
    ref.setFileOids(new String[] { uploadedFile.getOid() });

    String url = "/%s/%d/%s/%d/%s".formatted(
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATA_OBJECTS,
      dataObject.getId(),
      Constants.FILE_REFERENCES
    );

    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .body(ref)
      .when()
      .post(url)
      .then()
      .statusCode(201)
      .extract()
      .response();

    assertWireMatches(SLUG, "create", response);
  }
}
