package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import de.dlr.shepard.data.structureddata.io.StructuredDataContainerIO;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StructuredDataIT extends BaseTestCaseIT {

  private static String containerURL;

  private static StructuredDataContainerIO container;
  private static StructuredDataPayload payload;

  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  public static void setUp() {
    containerURL = "/" + Constants.STRUCTURED_DATA_CONTAINERS;
  }

  @Test
  @Order(1)
  public void createStructuredDataContainer() {
    var toCreate = new StructuredDataContainerIO();
    toCreate.setName("StructuredDataContainer");

    var actual = given()
      .spec(requestSpecOfDefaultUser)
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
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getOid()).isNotBlank();
    assertThat(actual.getName()).isEqualTo("StructuredDataContainer");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getStructuredDataContainers() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
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
      .spec(requestSpecOfDefaultUser)
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
      .spec(requestSpecOfDefaultUser)
      .body(payload)
      .when()
      .post("%s/%d/%s".formatted(containerURL, container.getId(), Constants.PAYLOAD))
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
      .spec(requestSpecOfDefaultUser)
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
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(
        "%s/%d/%s/%s".formatted(
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
  public void getStructuredDataPayload_userHasNoPermissions_NotAllowed() {
    given()
      .spec(requestSpecOfOtherUser)
      .when()
      .get(
        "%s/%d/%s/%s".formatted(
            containerURL,
            container.getId(),
            Constants.PAYLOAD,
            payload.getStructuredData().getOid()
          )
      )
      .then()
      .statusCode(403)
      .extract()
      .as(ErrorResponse.class);
  }

  @Test
  @Order(8)
  public void getStructuredDataPayload_nonExistingOid() throws JsonMappingException, JsonProcessingException {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get("%s/%d/%s/%s".formatted(containerURL, container.getId(), Constants.PAYLOAD, 1234321))
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    assertEquals("Could not find document with oid: 1234321", actual.getMessage());
  }

  @Test
  @Order(9)
  public void deleteStructuredData() {
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(
        "%s/%d/%s/%s".formatted(
            containerURL,
            container.getId(),
            Constants.PAYLOAD,
            payload.getStructuredData().getOid()
          )
      )
      .then()
      .statusCode(204);

    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(
        "%s/%d/%s/%s".formatted(
            containerURL,
            container.getId(),
            Constants.PAYLOAD,
            payload.getStructuredData().getOid()
          )
      )
      .then()
      .statusCode(404);

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(containerURL + "/" + container.getId() + "/" + Constants.PAYLOAD)
      .then()
      .statusCode(200)
      .extract()
      .as(StructuredData[].class);
    assertThat(actual).isEmpty();
  }

  @Test
  @Order(10)
  public void deleteContainer() {
    given().spec(requestSpecOfDefaultUser).when().delete(containerURL + "/" + container.getId()).then().statusCode(204);

    given().spec(requestSpecOfDefaultUser).when().get(containerURL + "/" + container.getId()).then().statusCode(404);
  }

  @Test
  public void getStructuredDataContainer_doesNotExist_notFound() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(containerURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - Structured Data Container with id 99999 is null or deleted");
  }
}
