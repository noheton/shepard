package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.util.Constants;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StructuredDataReferenceTest extends BaseTestCaseIT {

  private static CollectionIO collection;
  private static DataObjectIO dataObject;

  private static String referencesURL;
  private static RequestSpecification referencesRequestSpec;
  private static String containerURL;
  private static RequestSpecification containerRequestSpec;

  private static StructuredDataContainerIO container;
  private static StructuredDataReferenceIO reference;
  private static StructuredDataPayload payload;

  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  public static void setUp() {
    collection = createCollection("StructuredDataReferenceTestCollection");
    dataObject = createDataObject("StructuredDataReferenceTestDataObject", collection.getId());

    referencesURL = String.format(
      "%s/%s/%d/%s/%d/%s",
      baseURL,
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATAOBJECTS,
      dataObject.getId(),
      Constants.STRUCTUREDDATA_REFERENCES
    );
    referencesRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri(referencesURL)
      .addHeader("X-API-KEY", jws)
      .build();

    containerURL = String.format("%s/%s", baseURL, Constants.STRUCTUREDDATAS);
    containerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri(containerURL)
      .addHeader("X-API-KEY", jws)
      .build();

    var toCreate = new StructuredDataContainerIO();
    toCreate.setName("StructuredDataContainer");
    container = given()
      .spec(containerRequestSpec)
      .body(toCreate)
      .when()
      .post()
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredDataContainerIO.class);
    var structuredData = new StructuredData();
    structuredData.setName("My Structured Data");
    payload = new StructuredDataPayload(
      structuredData,
      "{\"Hallo\":\"Welt\",\"number\":123,\"list\":[\"a\",\"b\"],\"object\":{\"a\":\"b\"}}"
    );
    var actual = given()
      .spec(containerRequestSpec)
      .body(payload)
      .when()
      .post(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredData.class);
    payload.setStructuredData(actual);
  }

  @Test
  @Order(1)
  public void createStructuredDataReference() {
    var toCreate = new StructuredDataReferenceIO();
    toCreate.setName("StructuredDataReferenceDummy");
    toCreate.setStructuredDataOids(new String[] { payload.getStructuredData().getOid() });
    toCreate.setStructuredDataContainerId(container.getId());

    var actual = given()
      .spec(referencesRequestSpec)
      .body(toCreate)
      .when()
      .post()
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredDataReferenceIO.class);
    reference = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(username);
    assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
    assertThat(actual.getName()).isEqualTo("StructuredDataReferenceDummy");
    assertThat(actual.getStructuredDataContainerId()).isEqualTo(container.getId());
    assertThat(actual.getStructuredDataOids()).containsExactly(payload.getStructuredData().getOid());
    assertThat(actual.getType()).isEqualTo("StructuredDataReference");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getStructuredDataReferences() {
    var actual = given()
      .spec(referencesRequestSpec)
      .when()
      .get()
      .then()
      .statusCode(200)
      .extract()
      .as(StructuredDataReferenceIO[].class);

    assertThat(actual).containsExactly(reference);
  }

  @Test
  @Order(3)
  public void getStructuredDataReference() {
    var actual = given()
      .spec(referencesRequestSpec)
      .when()
      .get(referencesURL + "/" + reference.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(StructuredDataReferenceIO.class);

    assertThat(actual).isEqualTo(reference);
  }

  @Test
  @Order(4)
  @SuppressWarnings("unchecked")
  public void getStructuredDataReferencePayload() throws JsonMappingException, JsonProcessingException {
    var actual = given()
      .spec(referencesRequestSpec)
      .when()
      .get(
        String.format(
          "%s/%d/%s/%s",
          referencesURL,
          reference.getId(),
          Constants.PAYLOAD,
          payload.getStructuredData().getOid()
        )
      )
      .then()
      .statusCode(200)
      .extract()
      .as(StructuredDataPayload.class);
    var payloadMap = objectMapper.readValue(actual.getPayload(), Map.class);
    var expectedMap = objectMapper.readValue(payload.getPayload(), Map.class);

    assertThat(actual.getStructuredData()).isEqualTo(payload.getStructuredData());
    assertThat(payloadMap).containsAllEntriesOf(expectedMap);
    assertThat(actual.getStructuredData()).isEqualTo(payload.getStructuredData());
  }

  @Test
  @Order(5)
  public void deleteReferences() {
    given().spec(referencesRequestSpec).when().delete(referencesURL + "/" + reference.getId()).then().statusCode(204);

    given().spec(referencesRequestSpec).when().get(referencesURL + "/" + reference.getId()).then().statusCode(404);
  }
}
