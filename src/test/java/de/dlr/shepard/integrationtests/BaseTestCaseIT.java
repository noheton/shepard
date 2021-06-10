package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.UserIO;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

public class BaseTestCaseIT {
	protected static String baseURL = "http://127.0.0.1:8080/backend/api";

	protected static String jws;
	protected static String username;
	protected static UUID apiKeyId;
	protected static UserIO userIO;

	@BeforeAll
	public static void init() {
		var credentials = new PrepareDatabase().getUserWithApiKey();
		jws = credentials.getApiKey().getJws();
		username = credentials.getUser().getUsername();
		apiKeyId = credentials.getApiKey().getUid();
		userIO = new UserIO(credentials.getUser());
	}

	protected static CollectionIO createCollection(String name) {
		var collectionsURL = String.format("%s/collections/", baseURL);
		var collectionSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(collectionsURL).addHeader("X-API-KEY", jws).build();
		var collection = given().spec(collectionSpecification).body(Map.of("name", name)).when().post().then()
				.statusCode(201).extract().as(CollectionIO.class);
		return collection;
	}

	protected static DataObjectIO createDataObject(String name, long collectionId) {
		var dataObjectsURL = String.format("%s/collections/%d/dataObjects/", baseURL, collectionId);
		var dataObjectSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(dataObjectsURL).addHeader("X-API-KEY", jws).build();
		var dataObject = given().spec(dataObjectSpecification).body(Map.of("name", name)).when().post().then()
				.statusCode(201).extract().as(DataObjectIO.class);
		return dataObject;
	}

}
