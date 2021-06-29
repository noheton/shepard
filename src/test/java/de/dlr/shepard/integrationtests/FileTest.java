package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.mongoDB.File;
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
	private static File file;

	private static String referencesURL;
	private static RequestSpecification referencesRequestSpec;
	private static String containerURL;
	private static RequestSpecification containerRequestSpec;
	private static RequestSpecification fileRequestSpec;

	private static FileContainerIO container;
	private static FileReferenceIO reference;

	@BeforeAll
	public static void setUp() {
		collection = createCollection("FileReferenceTestCollection");
		dataObject = createDataObject("FileReferenceTestDataObject", collection.getId());

		referencesURL = String.format("%s/collections/%d/dataObjects/%d/fileReferences", baseURL, collection.getId(),
				dataObject.getId());
		referencesRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(referencesURL)
				.addHeader("X-API-KEY", jws).build();

		containerURL = String.format("%s/files", baseURL);
		containerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(containerURL)
				.addHeader("X-API-KEY", jws).build();
		fileRequestSpec = new RequestSpecBuilder().setContentType(ContentType.MULTIPART).setBaseUri(containerURL)
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

	@Test
	@Order(4)
	public void uploadFile() throws URISyntaxException {
		var newFile = new java.io.File(getClass().getClassLoader().getResource("test.txt").toURI());
		var actual = given().spec(fileRequestSpec).multiPart(newFile).when()
				.post(String.format("%s/%d/payload", containerURL, container.getId())).then().statusCode(201).extract()
				.as(File.class);
		file = actual;

		assertThat(actual.getOid()).isNotBlank();
		assertThat(actual.getFilename()).isEqualTo("test.txt");
	}

	@Test
	@Order(5)
	public void createFileReference() {
		var toCreate = new FileReferenceIO();
		toCreate.setName("FileReferenceDummy");
		toCreate.setFiles(List.of(file));
		toCreate.setFileContainerId(container.getId());

		var actual = given().spec(referencesRequestSpec).body(toCreate).when().post().then().statusCode(201).extract()
				.as(FileReferenceIO.class);
		reference = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getCreatedBy()).isEqualTo(username);
		assertThat(actual.getDataObjectId()).isEqualTo(dataObject.getId());
		assertThat(actual.getName()).isEqualTo("FileReferenceDummy");
		assertThat(actual.getFileContainerId()).isEqualTo(container.getId());
		assertThat(actual.getFiles()).containsExactly(file);
		assertThat(actual.getType()).isEqualTo("FileReference");
		assertThat(actual.getUpdatedAt()).isNull();
		assertThat(actual.getUpdatedBy()).isNull();
	}

	@Test
	@Order(6)
	public void getFileReferences() {
		var actual = given().spec(referencesRequestSpec).when().get().then().statusCode(200).extract()
				.as(FileReferenceIO[].class);

		assertThat(actual).containsExactly(reference);
	}

	@Test
	@Order(7)
	public void getFileReference() {
		var actual = given().spec(referencesRequestSpec).when().get(referencesURL + "/" + reference.getId()).then()
				.statusCode(200).extract().as(FileReferenceIO.class);

		assertThat(actual).isEqualTo(reference);
	}

	@Test
	@Order(8)
	public void getFileReferencePayload() throws URISyntaxException, IOException {
		var oldFile = new java.io.File(getClass().getClassLoader().getResource("test.txt").toURI());
		var expected = Files.readString(oldFile.toPath());
		var actual = given().spec(referencesRequestSpec).when()
				.get(String.format("%s/%d/files/%s/payload", referencesURL, reference.getId(), file.getOid())).then()
				.statusCode(200).extract().asString();

		assertThat(actual).isEqualTo(expected);
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
