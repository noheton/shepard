package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * v5 wire-fidelity test for {@code /shepard/api/.../{dataObjectId}/structuredDataReferences}.
 *
 * <p>Of the three "Reference" payload kinds, structured-data is the cheapest to set up
 * end-to-end (no multipart file upload, no Timescale row insertion). It exercises the
 * full {@code BasicReferenceIO + VersionableEntity + structuredDataOids[] +
 * structuredDataContainerId} shape — the same {@code revision} addition flagged in
 * {@code aidocs/34} row V2a appears in this fixture.
 */
@QuarkusIntegrationTest
public class StructuredDataReferenceV5WireFidelityIT extends V5WireFidelityTest {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;
  private static StructuredDataContainerIO container;
  private static StructuredData payload;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("StructuredDataReferenceWireFixture_" + System.currentTimeMillis());
    dataObject = createDataObject("StructuredDataReferenceWireFixtureDO", collection.getId());

    var containerIO = new StructuredDataContainerIO();
    containerIO.setName("StructuredDataReferenceWireFixtureContainer");
    container = given()
      .spec(requestSpecOfDefaultUser)
      .body(containerIO)
      .when()
      .post("/" + Constants.STRUCTURED_DATA_CONTAINERS)
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredDataContainerIO.class);

    var sd = new StructuredData();
    sd.setName("WireFixturePayload");
    var sdPayload = new StructuredDataPayload(sd, "{\"a\":\"b\"}");
    payload = given()
      .spec(requestSpecOfDefaultUser)
      .body(sdPayload)
      .when()
      .post("/" + Constants.STRUCTURED_DATA_CONTAINERS + "/" + container.getId() + "/" + Constants.PAYLOAD)
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredData.class);
  }

  @Test
  public void createStructuredDataReference_wireMatchesFixture() {
    var ref = new StructuredDataReferenceIO();
    ref.setName("StructuredDataReferenceWireFixture");
    ref.setStructuredDataContainerId(container.getId());
    ref.setStructuredDataOids(new String[] { payload.getOid() });
    String url = "/%s/%d/%s/%d/%s".formatted(
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATA_OBJECTS,
      dataObject.getId(),
      Constants.STRUCTURED_DATA_REFERENCES
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
    assertWireMatches("structureddatareferences", "create", response);
  }
}
