package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.data.file.io.FileContainerIO;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;

public class BaseTestCaseIT {

  protected static String host = "http://127.0.0.1";
  protected static int port = 8083;
  protected static String basePath = "/shepard/api";

  protected static UserWithApiKey defaultUser;
  protected static String nameOfDefaultUser;
  protected static RequestSpecification requestSpecOfDefaultUser;

  protected static UserWithApiKey otherUser;
  protected static RequestSpecification requestSpecOfOtherUser;

  protected static RequestSpecification requestSpecNoUser;

  @BeforeAll
  public static void init() {
    RestAssured.baseURI = host;
    RestAssured.port = port;
    RestAssured.basePath = basePath;

    defaultUser = getNewUserWithApiKey("test_it");
    nameOfDefaultUser = defaultUser.getUser().getUsername();
    requestSpecOfDefaultUser = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", defaultUser.getApiKey().getJws())
      .build();

    otherUser = getNewUserWithApiKey("other_user");
    requestSpecOfOtherUser = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", otherUser.getApiKey().getJws())
      .build();

    requestSpecNoUser = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
  }

  protected static UserWithApiKey getNewUserWithApiKey(String username) {
    return new UserWithApiKeyBuilder().withUser(username).withApiKey().build();
  }

  protected static User getNewUser(String username) {
    return new UserWithApiKeyBuilder().withUser(username).build().getUser();
  }

  protected static UserGroup getNewUserGroup(String name) {
    return new UserGroupBuilder().withUserGroup(name).build();
  }

  protected static CollectionIO createCollection(String name) {
    return createCollection(name, defaultUser);
  }

  protected static CollectionIO createCollection(String name, UserWithApiKey user) {
    var collectionSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", user.getApiKey().getJws())
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
    return createDataObject(name, collectionId, defaultUser);
  }

  protected static DataObjectIO createDataObject(String name, long collectionId, UserWithApiKey user) {
    var dataObjectsURL = "/%s/%d/%s/".formatted(Constants.COLLECTIONS, collectionId, Constants.DATA_OBJECTS);
    var dataObjectSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader("X-API-KEY", user.getApiKey().getJws())
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
    var created = given()
      .spec(requestSpecOfDefaultUser)
      .body(dataObjectReference)
      .when()
      .post(dataObjectReferenceUrl)
      .then()
      .statusCode(201)
      .extract()
      .as(DataObjectReferenceIO.class);
    return created;
  }

  protected static FileContainerIO createFileContainer(String containerName) {
    return createFileContainer(containerName, defaultUser);
  }

  protected static FileContainerIO createFileContainer(String name, UserWithApiKey user) {
    FileContainerIO fileContainerIO = new FileContainerIO();
    fileContainerIO.setName(name);

    var fileContainer = given()
      .spec(requestSpecOfDefaultUser)
      .body(fileContainerIO)
      .when()
      .post("/" + Constants.FILE_CONTAINERS)
      .then()
      .statusCode(201)
      .extract()
      .as(FileContainerIO.class);
    return fileContainer;
  }
}
