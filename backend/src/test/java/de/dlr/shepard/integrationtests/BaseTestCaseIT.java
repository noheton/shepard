package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
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

    var credentials = new UserWithApiKeyBuilder().withUser().withApiKey().build();
    jws = credentials.getApiKey().getJws();
    username = credentials.getUser().getUsername();
    apiKeyId = credentials.getApiKey().getUid();
    userIO = new UserIO(credentials.getUser());
  }

  protected static UserWithApiKey getNewUserWithApiKey(String username) {
    return new UserWithApiKeyBuilder().withUser(username).withApiKey().build();
  }

  protected static User getNewUser(String username) {
    return new UserWithApiKeyBuilder().withUser(username).build().getUser();
  }

  protected static CollectionIO createCollection(String name) {
    var collectionSpecification = new RequestSpecBuilder()
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
    var collectionSpecification = new RequestSpecBuilder()
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
    var dataObjectsURL = String.format("/%s/%d/%s/", Constants.COLLECTIONS, collectionId, Constants.DATA_OBJECTS);
    var dataObjectSpecification = new RequestSpecBuilder()
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

  protected static DataObjectIO createDataObject(String name, long collectionId, ApiKey apiKey) {
    var dataObjectsURL = String.format("/%s/%d/%s/", Constants.COLLECTIONS, collectionId, Constants.DATA_OBJECTS);
    var dataObjectSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", apiKey.getJws())
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

  protected static DataObjectReferenceIO createDataObjectReference(
    long collectionId,
    long dataObjectId,
    long setReferencedDataObjectId
  ) {
    var dataObjectReferenceUrl =
      "/" +
      Constants.COLLECTIONS +
      "/" +
      collectionId +
      "/" +
      Constants.DATA_OBJECTS +
      "/" +
      dataObjectId +
      "/" +
      Constants.DATAOBJECT_REFERENCES +
      "/";
    var dataObjectReference = new DataObjectReferenceIO() {
      {
        setName("DataObjectReference");
        setReferencedDataObjectId(setReferencedDataObjectId);
        setRelationship("self_reference");
      }
    };
    var specification = new RequestSpecBuilder().setContentType(ContentType.JSON).addHeader("X-API-KEY", jws).build();
    var created = given()
      .spec(specification)
      .body(dataObjectReference)
      .when()
      .post(dataObjectReferenceUrl)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectReferenceIO.class);
    return created;
  }
}
