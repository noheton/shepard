package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StructuredDataTest extends BaseTestCaseIT {
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

		referencesURL = String.format("%s/collections/%d/dataObjects/%d/structureddataReferences", baseURL,
				collection.getId(), dataObject.getId());
		referencesRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(referencesURL)
				.addHeader("X-API-KEY", jws).build();

		containerURL = String.format("%s/structureddatas", baseURL);
		containerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(containerURL)
				.addHeader("X-API-KEY", jws).build();
	}

	@Test
	@Order(1)
	public void createStructuredDataContainer() {
		var toCreate = new StructuredDataContainerIO();
		toCreate.setName("StructuredDataContainer");

		var actual = given().spec(containerRequestSpec).body(toCreate).when().post().then().statusCode(201).extract()
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
		var actual = given().spec(containerRequestSpec).when().get().then().statusCode(200).extract()
				.as(StructuredDataContainerIO[].class);

		assertThat(actual).contains(container);
	}

	@Test
	@Order(3)
	public void getStructuredDataContainer() {
		var actual = given().spec(containerRequestSpec).when().get(containerURL + "/" + container.getId()).then()
				.statusCode(200).extract().as(StructuredDataContainerIO.class);

		assertThat(actual).isEqualTo(container);
	}

	@Test
	@Order(4)
	public void createStructuredData() throws JsonProcessingException {
		var payloadMap = Map.of("Hallo", "Welt", "number", 123, "object", Map.of("a", "b"), "list", List.of("a", "b"));

		payload = new StructuredDataPayload(null, objectMapper.writeValueAsString(payloadMap));

		var actual = given().spec(containerRequestSpec).body(payload).when()
				.post(String.format("%s/%d/payload", containerURL, container.getId())).then().statusCode(201).extract()
				.as(StructuredData.class);

		assertThat(actual.getOid()).isNotBlank();
		payload.setStructuredData(actual);
	}

	@Test
	@Order(5)
	public void createStructuredDataReference() {
		var toCreate = new StructuredDataReferenceIO();
		toCreate.setName("StructuredDataReferenceDummy");
		toCreate.setStructuredDatas(List.of(payload.getStructuredData()));
		toCreate.setStructuredDataContainerId(container.getId());

		var actual = given().spec(referencesRequestSpec).body(toCreate).when().post().then().statusCode(201).extract()
				.as(StructuredDataReferenceIO.class);
		reference = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getCreatedBy()).isEqualTo(username);
		assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
		assertThat(actual.getName()).isEqualTo("StructuredDataReferenceDummy");
		assertThat(actual.getStructuredDataContainerId()).isEqualTo(container.getId());
		assertThat(actual.getStructuredDatas()).containsExactly(payload.getStructuredData());
		assertThat(actual.getType()).isEqualTo("StructuredDataReference");
		assertThat(actual.getUpdatedAt()).isNull();
		assertThat(actual.getUpdatedBy()).isNull();
	}

	@Test
	@Order(6)
	public void getStructuredDataReferences() {
		var actual = given().spec(referencesRequestSpec).when().get().then().statusCode(200).extract()
				.as(StructuredDataReferenceIO[].class);

		assertThat(actual).containsExactly(reference);
	}

	@Test
	@Order(7)
	public void getStructuredDataReference() {
		var actual = given().spec(referencesRequestSpec).when().get(referencesURL + "/" + reference.getId()).then()
				.statusCode(200).extract().as(StructuredDataReferenceIO.class);

		assertThat(actual).isEqualTo(reference);
	}

	@Test
	@Order(8)
	@SuppressWarnings("unchecked")
	public void getStructuredDataReferencePayload() throws JsonMappingException, JsonProcessingException {
		var actual = given().spec(referencesRequestSpec).when()
				.get(String.format("%s/%d/payload", referencesURL, reference.getId())).then().statusCode(200).extract()
				.as(StructuredDataPayload[].class);
		var payloadMap = objectMapper.readValue(actual[0].getPayload(), Map.class);
		var expectedMap = objectMapper.readValue(payload.getPayload(), Map.class);

		assertThat(actual).hasSize(1);
		assertThat(actual[0].getStructuredData()).isEqualTo(payload.getStructuredData());
		assertThat(payloadMap).containsAllEntriesOf(expectedMap);
	}

	@Test
	@Order(9)
	public void deleteReferences() {
		given().spec(referencesRequestSpec).when().delete(referencesURL + "/" + reference.getId()).then()
				.statusCode(204);

		given().spec(referencesRequestSpec).when().get(referencesURL + "/" + reference.getId()).then().statusCode(404);
	}

	@Test
	@Order(10)
	public void deleteContainer() {
		given().spec(containerRequestSpec).when().delete(containerURL + "/" + container.getId()).then().statusCode(204);

		given().spec(containerRequestSpec).when().get(containerURL + "/" + container.getId()).then().statusCode(404);
	}

}
