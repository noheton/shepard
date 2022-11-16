package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.util.Constants;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.xml.bind.DatatypeConverter;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FileTest extends BaseTestCaseIT {
	private static ShepardFile file;

	private static String containerURL;
	private static RequestSpecification containerRequestSpec;
	private static RequestSpecification fileRequestSpec;

	private static FileContainerIO container;

	@BeforeAll
	public static void setUp() {
		containerURL = String.format("%s/%s", baseURL, Constants.FILES);
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
	public void uploadFile() throws URISyntaxException, NoSuchAlgorithmException, FileNotFoundException, IOException {
		var newFile = new File(getClass().getClassLoader().getResource("test.txt").toURI());
		MessageDigest md = MessageDigest.getInstance("MD5");
		try (var stream = new FileInputStream(newFile)) {
			md.update(stream.readAllBytes());
		}
		var md5 = DatatypeConverter.printHexBinary(md.digest());
		var actual = given().spec(fileRequestSpec).multiPart(newFile).when()
				.post(String.format("%s/%d/%s", containerURL, container.getId(), Constants.PAYLOAD)).then()
				.statusCode(201).extract().as(ShepardFile.class);
		file = actual;

		assertThat(actual.getOid()).isNotBlank();
		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getFilename()).isEqualTo("test.txt");
		assertThat(actual.getMd5()).isEqualToIgnoringCase(md5);
	}

	@Test
	@Order(5)
	public void getFiles() {
		var actual = given().spec(containerRequestSpec).when()
				.get(containerURL + "/" + container.getId() + "/" + Constants.PAYLOAD).then().statusCode(200).extract()
				.as(ShepardFile[].class);

		assertThat(actual).containsExactly(file);
	}

	@Test
	@Order(6)
	public void getFilePayload() throws URISyntaxException, IOException {
		var oldFile = new File(getClass().getClassLoader().getResource("test.txt").toURI());
		var expected = Files.readString(oldFile.toPath());
		var actual = given().spec(containerRequestSpec).when()
				.get(String.format("%s/%d/%s/%s", containerURL, container.getId(), Constants.PAYLOAD, file.getOid()))
				.then().statusCode(200).extract().asString();

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	@Order(7)
	public void deleteFile() {
		given().spec(containerRequestSpec).when()
				.delete(String.format("%s/%d/%s/%s", containerURL, container.getId(), Constants.PAYLOAD, file.getOid()))
				.then().statusCode(204);

		given().spec(containerRequestSpec).when()
				.get(String.format("%s/%d/%s/%s", containerURL, container.getId(), Constants.PAYLOAD, file.getOid()))
				.then().statusCode(404);

		var actual = given().spec(containerRequestSpec).when()
				.get(containerURL + "/" + container.getId() + "/" + Constants.PAYLOAD).then().statusCode(200).extract()
				.as(ShepardFile[].class);
		assertThat(actual).isEmpty();
	}

	@Test
	@Order(8)
	public void deleteContainer() {
		given().spec(containerRequestSpec).when().delete(containerURL + "/" + container.getId()).then().statusCode(204);

		given().spec(containerRequestSpec).when().get(containerURL + "/" + container.getId()).then().statusCode(404);
	}

}
