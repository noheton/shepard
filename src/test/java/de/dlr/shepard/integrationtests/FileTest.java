package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FileTest extends BaseTestCaseIT {
	private static CollectionIO collection;
	private static DataObjectIO dataObject;

	private static String referencesURL;
	private static RequestSpecification referencesRequestSpec;
	private static String containerURL;
	private static RequestSpecification containerRequestSpec;

	private static FileContainerIO container;
	private static FileReferenceIO reference;

	@BeforeAll
	public static void setUp() {
		collection = createCollection("FileReferenceTestCollection");
		dataObject = createDataObject("FileReferenceTestDataObject", collection.getId());

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
	public void createFileContainer() {
		var toCreate = new FileContainerIO();
		toCreate.setName("FileContainer");

		var actual = given().spec(containerRequestSpec).body(toCreate).when().post().then().statusCode(201).extract()
				.as(FileContainerIO.class);
		container = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getCreatedBy()).isEqualTo(username);
		assertThat(actual.getOid()).isNotBlank();
		assertThat(actual.getName()).isEqualTo("FileContainer");
		assertThat(actual.getUpdatedAt()).isNull();
		assertThat(actual.getUpdatedBy()).isNull();
	}

	@Test
	@Order(2)
	public void getFileContainers() {
		var actual = given().spec(containerRequestSpec).when().get().then().statusCode(200).extract()
				.as(FileContainerIO[].class);

		assertThat(actual).contains(container);
	}

	@Test
	@Order(3)
	public void getFileContainer() {
		var actual = given().spec(containerRequestSpec).when().get(containerURL + "/" + container.getId()).then()
				.statusCode(200).extract().as(FileContainerIO.class);

		assertThat(actual).isEqualTo(container);
	}

	// TODO: Upload and download files

	@Test
	@Order(10)
	public void deleteContainer() {
		given().spec(containerRequestSpec).when().delete(containerURL + "/" + container.getId()).then().statusCode(204);

		given().spec(containerRequestSpec).when().get(containerURL + "/" + container.getId()).then().statusCode(404);
	}

}
