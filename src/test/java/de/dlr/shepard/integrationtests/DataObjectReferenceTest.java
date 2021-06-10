package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.neo4Core.io.AbstractDataObjectIO;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import lombok.NoArgsConstructor;

@NoArgsConstructor
class BasicDataObjectIO extends AbstractDataObjectIO {

}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataObjectReferenceTest extends BaseTestCaseIT {

	private static CollectionIO collection;
	private static DataObjectIO dataObject;
	private static DataObjectIO referenced;

	private static String referencesURL;
	private static RequestSpecification requestSpecification;

	private static DataObjectReferenceIO reference;

	@BeforeAll
	public static void setUp() {
		collection = createCollection("DataObjectReferenceTestCollection");
		dataObject = createDataObject("DataObjectReference", collection.getId());
		referenced = createDataObject("ReferencedDataObject", collection.getId());

		referencesURL = String.format("%s/collections/%d/dataObjects/%d/dataObjectReferences", baseURL,
				collection.getId(), dataObject.getId());
		requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(referencesURL)
				.addHeader("X-API-KEY", jws).build();
	}

	@Test
	@Order(1)
	public void createDataObjectReferenceTest() {
		var toCreate = new DataObjectReferenceIO();
		toCreate.setName("DataObjectReferenceDummy");
		toCreate.setRelationship("integrationtests");
		toCreate.setReferencedDataObjectId(referenced.getId());

		var actual = given().spec(requestSpecification).body(toCreate).when().post().then().statusCode(201).extract()
				.as(DataObjectReferenceIO.class);
		reference = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getCreatedBy()).isEqualTo(username);
		assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
		assertThat(actual.getName()).isEqualTo("DataObjectReferenceDummy");
		assertThat(actual.getRelationship()).isEqualTo("integrationtests");
		assertThat(actual.getReferencedDataObjectId()).isEqualTo(referenced.getId());
		assertThat(actual.getType()).isEqualTo("DataObjectReference");
		assertThat(actual.getUpdatedAt()).isNull();
		assertThat(actual.getUpdatedBy()).isNull();
	}

	@Test
	@Order(2)
	public void createDataObjectReferenceTest_ReferencedDoesNotExist() {
		var toCreate = new DataObjectReferenceIO();
		toCreate.setName("DataObjectReferenceDummy2");
		toCreate.setRelationship("integrationtests");
		toCreate.setReferencedDataObjectId(-2);

		given().spec(requestSpecification).body(toCreate).when().post().then().statusCode(400);
	}

	@Test
	@Order(3)
	public void getDataObjectReferenceTest() {
		var actual = given().spec(requestSpecification).when().get(referencesURL + "/" + reference.getId()).then()
				.statusCode(200).extract().as(DataObjectReferenceIO.class);
		assertThat(actual).isEqualTo(reference);
	}

	@Test
	@Order(4)
	public void getDataObjectReferencesTest() {
		var actual = given().spec(requestSpecification).when().get().then().statusCode(200).extract()
				.as(DataObjectReferenceIO[].class);
		assertThat(actual).containsExactly(reference);
	}

	@Test
	@Order(5)
	public void getDataObjectReferencedTest() {
		var referencedURL = String.format("%s/collections/%d/dataObjects/%d", baseURL, collection.getId(),
				referenced.getId());
		var actual = given().spec(requestSpecification).when().get(referencedURL).then().statusCode(200).extract()
				.as(DataObjectIO.class);
		assertThat(actual.getIncomingIds()).containsExactly(reference.getId());
	}

	@Test
	@Order(6)
	public void getDataObjectReferencePayloadTest() {
		var actual = given().spec(requestSpecification).when()
				.get(String.format("%s/%d/payload", referencesURL, reference.getId())).then().statusCode(200).extract()
				.as(BasicDataObjectIO.class);

		assertThat(actual).usingRecursiveComparison().ignoringFields("incomingIds").isEqualTo(referenced);
		assertThat(actual.getIncomingIds()).containsExactly(reference.getId());
	}

	@Test
	@Order(7)
	public void deleteDataObjectReferenceTest() {
		given().spec(requestSpecification).when().delete(referencesURL + "/" + reference.getId()).then()
				.statusCode(204);

		given().spec(requestSpecification).when().get(referencesURL + "/" + reference.getId()).then().statusCode(404);
	}

}
