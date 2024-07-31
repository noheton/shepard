package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.util.Constants;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;

public class BaseTestCaseIT {

  protected static String host = "http://127.0.0.1";
  protected static int port = 8083;
  protected static String basePath = "/shepard/api";

  protected static String jws;
  protected static String username;
  protected static UUID apiKeyId;
  protected static UserIO userIO;

  @BeforeAll
  public static void init() {
    RestAssured.baseURI = host;
    RestAssured.port = port;
    RestAssured.basePath = basePath;

    var credentials = new PrepareDatabase().withUser().withApiKey().build();
    jws = credentials.getApiKey().getJws();
    username = credentials.getUser().getUsername();
    apiKeyId = credentials.getApiKey().getUid();
    userIO = new UserIO(credentials.getUser());
  }

  protected static RequestSpecBuilder getNewRequestSpecBuilderWithBasePath() {
    return new RequestSpecBuilder().setBaseUri(host).setPort(port).setBasePath(basePath);
  }

  protected static UserWithApiKey getNewUserWithApiKey(String username) {
    return new PrepareDatabase().withUser(username).withApiKey().build();
  }

  protected static User getNewUser(String username) {
    return new PrepareDatabase().withUser(username).build().getUser();
  }

  protected static CollectionIO createCollection(String name) {
    var collectionSpecification = getNewRequestSpecBuilderWithBasePath()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    var collection = given()
      .spec(collectionSpecification)
      .body(Map.of("name", name))
      .when()
      .post("/" + Constants.COLLECTIONS)
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionIO.class);
    return collection;
  }

  protected static CollectionIO createCollection(String name, ApiKey apiKey) {
    var collectionSpecification = getNewRequestSpecBuilderWithBasePath()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", apiKey.getJws())
      .build();
    var collection = given()
      .spec(collectionSpecification)
      .body(Map.of("name", name))
      .when()
      .post("/" + Constants.COLLECTIONS)
      .then()
      .statusCode(201)
      .extract()
      .as(CollectionIO.class);
    return collection;
  }

  protected static DataObjectIO createDataObject(String name, long collectionId) {
    var dataObjectsURL = String.format("/%s/%d/%s/", Constants.COLLECTIONS, collectionId, Constants.DATAOBJECTS);
    var dataObjectSpecification = getNewRequestSpecBuilderWithBasePath()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", jws)
      .build();
    DataObjectIO dataObjectIO = new DataObjectIO();
    dataObjectIO.setName(name);
    var dataObjectIOToReturn = given()
      .spec(dataObjectSpecification)
      .body(dataObjectIO)
      .when()
      .post(dataObjectsURL)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectIO.class);
    return dataObjectIOToReturn;
  }
}
