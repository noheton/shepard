package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.util.Constants;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExportTest extends BaseTestCaseIT {
	private static String collectionsURL;
	private static CollectionIO collection;
	private static DataObjectIO dataObject;
	private static RequestSpecification requestSpecification;

	@BeforeAll
	public static void setUp() {
		collection = createCollection("ExportTestCollection");
		dataObject = createDataObject("ExportTestDataObject", collection.getId());

		collectionsURL = baseURL.concat("/" + Constants.COLLECTIONS + "/" + collection.getId() + "/export");
		requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionsURL)
				.addHeader("X-API-KEY", jws).build();
	}

	@Test
	@Order(1)
	public void exportCollection_successful() throws IOException {
		var actual = given().spec(requestSpecification).when().get().then().statusCode(200).extract().asInputStream();
		var zis = new ZipInputStream(actual);
		var filenames = new ArrayList<String>();

		var zipEntry = zis.getNextEntry();
		while (zipEntry != null) {
			filenames.add(zipEntry.getName());
			zipEntry = zis.getNextEntry();
		}

		assertThat(filenames).containsExactlyInAnyOrder(collection.getId() + ".json", dataObject.getId() + ".json",
				"ro-crate-metadata.json");
	}

}
