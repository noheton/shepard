package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.util.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StructuredDataTest extends BaseTestCaseIT {

  private static String containerURL;
  private static RequestSpecification containerRequestSpec;

  private static StructuredDataContainerIO container;
  private static StructuredDataPayload payload;

  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  public static void setUp() {
    containerURL = "/" + Constants.STRUCTUREDDATAS;
    containerRequestSpec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
  }

  @Test
  @Order(1)
  public void createStructuredDataContainer() {
    var toCreate = new StructuredDataContainerIO();
    toCreate.setName("StructuredDataContainer");

    var actual = given()
      .spec(containerRequestSpec)
      .body(toCreate)
      .when()
      .post(containerURL)
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredDataContainerIO.class);
    container = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(username);
    assertThat(actual.getOid()).isNotBlank();
    assertThat(actual.getName()).isEqualTo("StructuredDataContainer");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getStructuredDataContainers() {
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .get(containerURL)
      .then()
      .statusCode(200)
      .extract()
      .as(StructuredDataContainerIO[].class);

    assertThat(actual).contains(container);
  }

  @Test
  @Order(3)
  public void getStructuredDataContainer() {
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .get(containerURL + "/" + container.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(StructuredDataContainerIO.class);

    assertThat(actual).isEqualTo(container);
  }

  @Test
  @Order(4)
  public void createStructuredData() throws JsonProcessingException {
    var payloadMap = Map.of("Hallo", "Welt", "number", 123, "object", Map.of("a", "b"), "list", List.of("a", "b"));
    var structuredData = new StructuredData();
    structuredData.setName("My Structured Data");

    payload = new StructuredDataPayload(structuredData, objectMapper.writeValueAsString(payloadMap));

    var actual = given()
      .spec(containerRequestSpec)
      .body(payload)
      .when()
      .post(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD))
      .then()
      .statusCode(201)
      .extract()
      .as(StructuredData.class);

    assertThat(actual.getOid()).isNotBlank();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getName()).isEqualTo("My Structured Data");
    payload.setStructuredData(actual);
  }

  @Test
  @Order(5)
  public void getStructuredDatas() {
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .get(containerURL + "/" + container.getId() + "/" + Constants.PAYLOAD)
      .then()
      .statusCode(200)
      .extract()
      .as(StructuredData[].class);

    assertThat(actual).containsExactly(payload.getStructuredData());
  }

  @Test
  @Order(6)
  @SuppressWarnings("unchecked")
  public void getStructuredDataPayload() throws JsonMappingException, JsonProcessingException {
    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .get(
        String.format(
          "%s/%d/%s/%s",
          containerURL,
          container.getId(),
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
  @Order(7)
  public void deleteStructuredData() {
    given()
      .spec(containerRequestSpec)
      .when()
      .delete(
        String.format(
          "%s/%d/%s/%s",
          containerURL,
          container.getId(),
          Constants.PAYLOAD,
          payload.getStructuredData().getOid()
        )
      )
      .then()
      .statusCode(204);

    given()
      .spec(containerRequestSpec)
      .when()
      .get(
        String.format(
          "%s/%d/%s/%s",
          containerURL,
          container.getId(),
          Constants.PAYLOAD,
          payload.getStructuredData().getOid()
        )
      )
      .then()
      .statusCode(404);

    var actual = given()
      .spec(containerRequestSpec)
      .when()
      .get(containerURL + "/" + container.getId() + "/" + Constants.PAYLOAD)
      .then()
      .statusCode(200)
      .extract()
      .as(StructuredData[].class);
    assertThat(actual).isEmpty();
  }

  @Test
  @Order(8)
  public void deleteContainer() {
    given().spec(containerRequestSpec).when().delete(containerURL + "/" + container.getId()).then().statusCode(204);

    given().spec(containerRequestSpec).when().get(containerURL + "/" + container.getId()).then().statusCode(404);
  }
}
