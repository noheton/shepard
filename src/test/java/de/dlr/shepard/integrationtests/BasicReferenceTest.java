package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BasicReferenceTest extends BaseTestCaseIT {
	private static CollectionIO collection;
	private static DataObjectIO dataObject;
	private static RequestSpecification requestSpecification;

	private static String referencesURL;
	private static BasicReferenceIO dataObjectReference;
	private static BasicReferenceIO uriReference;

	@BeforeAll
	public static void setUp() {
		collection = createCollection("BasicReferenceTestCollection");
		dataObject = createDataObject("BasicReferenceTestDataObject", collection.getId());

		dataObjectReference = createDataObjectReference(collection.getId(), dataObject.getId());
		uriReference = createUriReference(collection.getId(), dataObject.getId());

		referencesURL = String.format("%s/collections/%d/dataObjects/%d/references", baseURL, collection.getId(),
				dataObject.getId());
		requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(referencesURL)
				.addHeader("X-API-KEY", jws).build();
	}

	@Test
	@Order(1)
	public void getFirstReference_Successful() {
		BasicReferenceIO actual = given().spec(requestSpecification).when()
				.get(referencesURL + "/" + dataObjectReference.getId()).then().statusCode(200).extract()
				.as(BasicReferenceIO.class);

		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getCreatedBy()).isEqualTo(username);
		assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
		assertThat(actual.getId()).isEqualTo(dataObjectReference.getId());
		assertThat(actual.getName()).isEqualTo("DataObjectReference");
		assertThat(actual.getType()).isEqualTo("DataObjectReference");
		assertThat(actual.getUpdatedAt()).isNull();
		assertThat(actual.getUpdatedBy()).isNull();

		dataObjectReference = actual;
	}

	@Test
	@Order(2)
	public void getSecondReference_Successful() {
		BasicReferenceIO actual = given().spec(requestSpecification).when()
				.get(referencesURL + "/" + uriReference.getId()).then().statusCode(200).extract()
				.as(BasicReferenceIO.class);

		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getCreatedBy()).isEqualTo(username);
		assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
		assertThat(actual.getId()).isEqualTo(uriReference.getId());
		assertThat(actual.getName()).isEqualTo("UriReference");
		assertThat(actual.getType()).isEqualTo("URIReference");
		assertThat(actual.getUpdatedAt()).isNull();
		assertThat(actual.getUpdatedBy()).isNull();

		uriReference = actual;
	}

	@Test
	@Order(3)
	public void getAllReferences_Successful() {
		BasicReferenceIO[] actual = given().spec(requestSpecification).when().get().then().statusCode(200).extract()
				.as(BasicReferenceIO[].class);

		assertThat(actual).containsExactlyInAnyOrder(dataObjectReference, uriReference);
	}

	@Test
	@Order(4)
	public void deleteReferences_Successful() {
		given().spec(requestSpecification).when().delete(referencesURL + "/" + uriReference.getId()).then()
				.statusCode(204);

		BasicReferenceIO[] actual = given().spec(requestSpecification).when().get().then().statusCode(200).extract()
				.as(BasicReferenceIO[].class);

		assertThat(actual).containsExactly(dataObjectReference);

		given().spec(requestSpecification).when().get(referencesURL + "/" + uriReference.getId()).then()
				.statusCode(404);
	}

	private static URIReferenceIO createUriReference(long collectionId, long dataObjectId) {
		var uriReferenceUrl = baseURL + "/collections/" + collectionId + "/dataObjects/" + dataObjectId
				+ "/uriReferences/";
		var uriReference = new URIReferenceIO() {
			{
				setName("UriReference");
				setUri("http://www.example.com");
			}
		};
		var specification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(uriReferenceUrl)
				.addHeader("X-API-KEY", jws).build();
		var created = given().spec(specification).body(uriReference).when().post().then().statusCode(201).extract()
				.as(URIReferenceIO.class);
		return created;
	}

	private static DataObjectReferenceIO createDataObjectReference(long collectionId, long dataObjectId) {
		var dataObjectReferenceUrl = baseURL + "/collections/" + collectionId + "/dataObjects/" + dataObjectId
				+ "/dataObjectReferences/";
		var dataObjectReference = new DataObjectReferenceIO() {
			{
				setName("DataObjectReference");
				setReferencedDataObjectId(dataObject.getId());
				setRelationship("self_reference");
			}
		};
		var specification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(dataObjectReferenceUrl)
				.addHeader("X-API-KEY", jws).build();
		var created = given().spec(specification).body(dataObjectReference).when().post().then().statusCode(201)
				.extract().as(DataObjectReferenceIO.class);
		return created;
	}
}
