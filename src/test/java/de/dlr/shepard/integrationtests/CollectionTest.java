package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CollectionTest extends BaseTestCaseIT {
	private static String collectionsURL;
	private static CollectionIO collection;
	private static RequestSpecification requestSpecification;

	@BeforeAll
	public static void setUp() {
		collectionsURL = baseURL.concat("/collections");
		requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionsURL)
				.addHeader("X-API-KEY", jws).build();
	}

	@Test
	@Order(1)
	public void postCollectionTest_Successful() {
		var payload = new CollectionIO();
		payload.setName("CollectionDummy");
		payload.setDescription("My Description");
		payload.setAttributes(Map.of("a", "1", "b", "2"));

		CollectionIO actual = given().spec(requestSpecification).body(payload).when().post().then().statusCode(201)
				.extract().as(CollectionIO.class);
		collection = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getAttributes()).isEqualTo(Map.of("a", "1", "b", "2"));
		assertThat(actual.getDescription()).isEqualTo("My Description");
		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getCreatedBy()).isEqualTo(username);
		assertThat(actual.getName()).isEqualTo("CollectionDummy");
		assertThat(actual.getDataObjectIds()).isEmpty();
		assertThat(actual.getUpdatedAt()).isNull();
		assertThat(actual.getUpdatedBy()).isNull();
	}

	@Test
	@Order(2)
	public void postCollectionTest_WithoutAuth() {
		var payload = new CollectionIO();
		payload.setName("CollectionDummy");

		var wrongSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionsURL)
				.build();
		given().spec(wrongSpecification).body(payload).when().post().then().statusCode(401);
	}

	@Test
	@Order(3)
	public void postCollectionTest_BadJson() {
		String payload = "{,}";
		given().spec(requestSpecification).body(payload).when().post().then().statusCode(400);
	}

	@Test
	@Order(4)
	public void postCollectionTest_BadBody() {
		String payload = "{\"attribute\":\"value\"}";
		given().spec(requestSpecification).body(payload).when().post().then().statusCode(400);
	}

	@Test
	@Order(5)
	public void getCollectionTest_Successful() {
		CollectionIO actual = given().spec(requestSpecification).when().get(collectionsURL + "/" + collection.getId())
				.then().statusCode(200).extract().as(CollectionIO.class);

		assertThat(actual).isEqualTo(collection);
	}

	@Test
	@Order(6)
	public void getCollectionTest_withDataObject() {
		var payload = new DataObjectIO();
		payload.setName("CollectionDummy");

		DataObjectIO dataObject = given().spec(requestSpecification).body(payload).when()
				.post(collectionsURL + "/" + collection.getId() + "/dataObjects").then().statusCode(201).extract()
				.as(DataObjectIO.class);

		CollectionIO actual = given().spec(requestSpecification).when().get(collectionsURL + "/" + collection.getId())
				.then().statusCode(200).extract().as(CollectionIO.class);
		collection = actual;

		assertThat(actual.getDataObjectIds()).contains(dataObject.getId());
	}

	@Test
	@Order(7)
	public void getCollectionTest_QueryParamNameSuccessful() {
		CollectionIO[] response = given().spec(requestSpecification).queryParam("name", collection.getName()).when()
				.get().then().statusCode(200).extract().as(CollectionIO[].class);

		assertThat(response).containsExactly(collection);
	}

	@Test
	@Order(8)
	public void getCollectionsTest_Successful() {
		CollectionIO[] response = given().spec(requestSpecification).when().get().then().statusCode(200).extract()
				.as(CollectionIO[].class);

		assertThat(response).contains(collection);
	}

	@Test
	@Order(9)
	public void getCollectionTest_WrongId() {
		given().spec(requestSpecification).when().get("now_the_request_should_result_in_404").then().statusCode(404);
	}

	@Test
	@Order(10)
	public void putCollectionTest_Successful() {
		collection.setName("CollectionDummyChanged");

		CollectionIO actualResponse = given().spec(requestSpecification).body(collection).when()
				.put(collectionsURL + "/" + collection.getId()).then().statusCode(200).extract().as(CollectionIO.class);

		assertThat(actualResponse.getUpdatedAt()).isNotNull();
		assertThat(actualResponse.getUpdatedBy()).isEqualTo(username);
		assertThat(actualResponse).usingRecursiveComparison().ignoringFields("updatedBy", "updatedAt")
				.isEqualTo(collection);
	}

	@Test
	@Order(11)
	public void deleteCollectionTest_Successful() {
		given().spec(requestSpecification).when().delete(collectionsURL + "/" + collection.getId()).then()
				.statusCode(204);

		given().spec(requestSpecification).when().get(collectionsURL + "/" + collection.getId()).then().statusCode(404);
	}
}
