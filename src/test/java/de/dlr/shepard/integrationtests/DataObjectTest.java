package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
import de.dlr.shepard.util.Constants;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataObjectTest extends BaseTestCaseIT {
	private static CollectionIO collection;
	private static CollectionIO collectionForOrderByTest;
	private static RequestSpecification requestSpecification;
	private static RequestSpecification orderByRequestSpecification;

	private static String dataObjectsURL;
	private static String orderByDataObjectsURL;
	private static DataObjectIO dataObject;
	private static DataObjectIO child;
	private static DataObjectIO successor;
	private static DataObjectIO successorAndChild;
	private static DataObjectIO aDataObject;
	private static DataObjectIO bDataObject;
	private static DataObjectIO cDataObject;
	private static DataObjectIO dDataObject;
	private static DataObjectIO eDataObject;
	private static DataObjectIO fDataObject;

	@BeforeAll
	public static void setUp() {
		collection = createCollection("DataObjectTestCollection");
		collectionForOrderByTest = createCollection("OrderByTestCollection");

		dataObjectsURL = String.format("%s/%s/%d/%s", baseURL, Constants.COLLECTIONS, collection.getId(),
				Constants.DATAOBJECTS);
		orderByDataObjectsURL = String.format("%s/%s/%d/%s", baseURL, Constants.COLLECTIONS,
				collectionForOrderByTest.getId(), Constants.DATAOBJECTS);
		requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(dataObjectsURL)
				.addHeader("X-API-KEY", jws).build();
		orderByRequestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON)
				.setBaseUri(orderByDataObjectsURL).addHeader("X-API-KEY", jws).build();

		var aInput = new DataObjectIO();
		aInput.setName("a");
		aDataObject = given().spec(orderByRequestSpecification).body(aInput).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);
		var bInput = new DataObjectIO();
		bInput.setName("b");
		bDataObject = given().spec(orderByRequestSpecification).body(bInput).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);
		var cInput = new DataObjectIO();
		cInput.setName("c");
		cDataObject = given().spec(orderByRequestSpecification).body(cInput).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);
		var dInput = new DataObjectIO();
		dInput.setName("d");
		dDataObject = given().spec(orderByRequestSpecification).body(dInput).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);
		var eInput = new DataObjectIO();
		eInput.setName("e");
		eDataObject = given().spec(orderByRequestSpecification).body(eInput).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);
		var fInput = new DataObjectIO();
		fInput.setName("f");
		fDataObject = given().spec(orderByRequestSpecification).body(fInput).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);

	}

	@Test
	@Order(1)
	public void postDataObjectTest_Successful() {
		var payload = new DataObjectIO();
		payload.setName("DataObjectDummy");
		payload.setDescription("My Description");
		payload.setAttributes(Map.of("a", "1", "b", "2"));

		DataObjectIO actual = given().spec(requestSpecification).body(payload).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);
		dataObject = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getAttributes()).isEqualTo(Map.of("a", "1", "b", "2"));
		assertThat(actual.getDescription()).isEqualTo("My Description");
		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getCreatedBy()).isEqualTo(username);
		assertThat(actual.getIncomingIds()).isEmpty();
		assertThat(actual.getReferenceIds()).isEmpty();
		assertThat(actual.getChildrenIds()).isEmpty();
		assertThat(actual.getPredecessorIds()).isEmpty();
		assertThat(actual.getSuccessorIds()).isEmpty();
		assertThat(actual.getParentId()).isNull();
		assertThat(actual.getName()).isEqualTo("DataObjectDummy");
		assertThat(actual.getCollectionId()).isEqualTo(collection.getId());
		assertThat(actual.getUpdatedAt()).isNull();
		assertThat(actual.getUpdatedBy()).isNull();
	}

	@Test
	@Order(2)
	public void postDataObjectTest_ParentId() {
		var payload = new DataObjectIO();
		payload.setName("ChildDummy");
		payload.setParentId(dataObject.getId());

		DataObjectIO actual = given().spec(requestSpecification).body(payload).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);
		child = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getChildrenIds()).isEmpty();
		assertThat(actual.getPredecessorIds()).isEmpty();
		assertThat(actual.getSuccessorIds()).isEmpty();
		assertThat(actual.getParentId()).isEqualTo(dataObject.getId());
		assertThat(actual.getCollectionId()).isEqualTo(collection.getId());

		DataObjectIO parent = given().spec(requestSpecification).when().get(dataObjectsURL + "/" + dataObject.getId())
				.then().statusCode(200).extract().as(DataObjectIO.class);
		assertThat(parent).usingRecursiveComparison().ignoringFields("childrenIds").isEqualTo(dataObject);
		assertThat(parent.getChildrenIds()).containsExactlyInAnyOrder(child.getId());
		dataObject = parent;
	}

	@Test
	@Order(3)
	public void postDataObjectTest_PredecessorId() {
		var payload = new DataObjectIO();
		payload.setName("SuccessorDummy");
		payload.setPredecessorIds(new long[] { dataObject.getId() });

		DataObjectIO actual = given().spec(requestSpecification).body(payload).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);
		successor = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getChildrenIds()).isEmpty();
		assertThat(actual.getPredecessorIds()).containsExactlyInAnyOrder(dataObject.getId());
		assertThat(actual.getSuccessorIds()).isEmpty();
		assertThat(actual.getParentId()).isNull();
		assertThat(actual.getCollectionId()).isEqualTo(collection.getId());

		DataObjectIO predecessor = given().spec(requestSpecification).when()
				.get(dataObjectsURL + "/" + dataObject.getId()).then().statusCode(200).extract().as(DataObjectIO.class);
		assertThat(predecessor).usingRecursiveComparison().ignoringFields("successorIds").isEqualTo(dataObject);
		assertThat(predecessor.getSuccessorIds()).containsExactlyInAnyOrder(successor.getId());
		dataObject = predecessor;
	}

	@Test
	@Order(4)
	public void postDataObjectTest_PredecessorIdAndParentId() {
		var payload = new DataObjectIO();
		payload.setName("ChildAndSuccessorDummy");
		payload.setParentId(dataObject.getId());
		payload.setPredecessorIds(new long[] { child.getId() });

		DataObjectIO actual = given().spec(requestSpecification).body(payload).when().post().then().statusCode(201)
				.extract().as(DataObjectIO.class);
		successorAndChild = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getChildrenIds()).isEmpty();
		assertThat(actual.getPredecessorIds()).containsExactlyInAnyOrder(child.getId());
		assertThat(actual.getSuccessorIds()).isEmpty();
		assertThat(actual.getParentId()).isEqualTo(dataObject.getId());
		assertThat(actual.getCollectionId()).isEqualTo(collection.getId());

		DataObjectIO parent = given().spec(requestSpecification).when().get(dataObjectsURL + "/" + dataObject.getId())
				.then().statusCode(200).extract().as(DataObjectIO.class);
		assertThat(parent).usingRecursiveComparison().ignoringFields("childrenIds").isEqualTo(dataObject);
		assertThat(parent.getChildrenIds()).containsExactlyInAnyOrder(child.getId(), successorAndChild.getId());
		dataObject = parent;

		DataObjectIO predecessor = given().spec(requestSpecification).when().get(dataObjectsURL + "/" + child.getId())
				.then().statusCode(200).extract().as(DataObjectIO.class);
		assertThat(predecessor).usingRecursiveComparison().ignoringFields("successorIds").isEqualTo(child);
		assertThat(predecessor.getSuccessorIds()).containsExactlyInAnyOrder(successorAndChild.getId());
		child = predecessor;
	}

	@Test
	@Order(5)
	public void getDataObjectTest_Successful() {
		DataObjectIO actual = given().spec(requestSpecification).when().get(dataObjectsURL + "/" + dataObject.getId())
				.then().statusCode(200).extract().as(DataObjectIO.class);

		assertThat(actual).isEqualTo(dataObject);
	}

	@Test
	@Order(6)
	public void getDataObjectTest_ByName() {
		DataObjectIO[] response = given().spec(requestSpecification).queryParam("name", dataObject.getName()).when()
				.get().then().statusCode(200).extract().as(DataObjectIO[].class);

		assertThat(response).usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds",
				"predecessorIds").containsExactlyInAnyOrder(dataObject);
	}

	@Test
	@Order(7)
	public void getDataObjectsTest_Successful() {
		DataObjectIO[] response = given().spec(requestSpecification).when().get().then().statusCode(200).extract()
				.as(DataObjectIO[].class);

		assertThat(response).usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds",
				"predecessorIds").containsExactlyInAnyOrder(dataObject, child, successor, successorAndChild);
	}

	@Test
	@Order(8)
	public void getDataObjectsTest_ByParent() {
		DataObjectIO[] response = given().spec(requestSpecification).queryParam("parentId", dataObject.getId()).when()
				.get().then().statusCode(200).extract().as(DataObjectIO[].class);

		assertThat(response).usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds",
				"predecessorIds").containsExactlyInAnyOrder(child, successorAndChild);
	}

	@Test
	@Order(9)
	public void getDataObjectsTest_WithoutParent() {
		DataObjectIO[] response = given().spec(requestSpecification).queryParam("parentId", -1).when().get().then()
				.statusCode(200).extract().as(DataObjectIO[].class);

		assertThat(response).usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds",
				"predecessorIds").containsExactlyInAnyOrder(dataObject, successor);
	}

	@Test
	@Order(10)
	public void getDataObjectsTest_ByPredecessor() {
		DataObjectIO[] response = given().spec(requestSpecification).queryParam("predecessorId", dataObject.getId())
				.when().get().then().statusCode(200).extract().as(DataObjectIO[].class);

		assertThat(response).usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds",
				"predecessorIds").containsExactlyInAnyOrder(successor);
	}

	@Test
	@Order(11)
	public void getDataObjectsTest_WithoutPredecessor() {
		DataObjectIO[] response = given().spec(requestSpecification).queryParam("predecessorId", -1).when().get().then()
				.statusCode(200).extract().as(DataObjectIO[].class);

		assertThat(response).usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds",
				"predecessorIds").containsExactlyInAnyOrder(dataObject, child);
	}

	@Test
	@Order(12)
	public void getDataObjectsTest_BySuccessor() {
		DataObjectIO[] response = given().spec(requestSpecification).queryParam("successorId", successor.getId()).when()
				.get().then().statusCode(200).extract().as(DataObjectIO[].class);

		assertThat(response).usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds",
				"predecessorIds").containsExactlyInAnyOrder(dataObject);
	}

	@Test
	@Order(13)
	public void getDataObjectsTest_WithoutSuccessor() {
		DataObjectIO[] response = given().spec(requestSpecification).queryParam("successorId", -1).when().get().then()
				.statusCode(200).extract().as(DataObjectIO[].class);

		assertThat(response).usingRecursiveFieldByFieldElementComparatorIgnoringFields("childrenIds", "successorIds",
				"predecessorIds").containsExactlyInAnyOrder(successor, successorAndChild);
	}

	@Test
	@Order(14)
	public void putDataObjectTest_Successful() {
		dataObject.setName("DataObjectSuccessorChanged");

		DataObjectIO actual = given().spec(requestSpecification).body(dataObject).when()
				.put(dataObjectsURL + "/" + dataObject.getId()).then().statusCode(200).extract().as(DataObjectIO.class);

		assertThat(actual.getUpdatedAt()).isNotNull();
		assertThat(actual.getUpdatedBy()).isEqualTo(username);
		assertThat(actual).usingRecursiveComparison().ignoringFields("updatedBy", "updatedAt").isEqualTo(dataObject);
		dataObject = actual;
	}

	@Test
	@Order(15)
	public void putDataObjectTest_newParent() {
		successorAndChild.setName("DataObjectChildChanged");
		successorAndChild.setParentId(successor.getId());

		DataObjectIO actual = given().spec(requestSpecification).body(successorAndChild).when()
				.put(dataObjectsURL + "/" + successorAndChild.getId()).then().statusCode(200).extract()
				.as(DataObjectIO.class);

		assertThat(actual).usingRecursiveComparison().ignoringFields("updatedBy", "updatedAt")
				.isEqualTo(successorAndChild);
		successorAndChild = actual;

		dataObject.setChildrenIds(new long[] { child.getId() });
		DataObjectIO oldParent = given().spec(requestSpecification).when()
				.get(dataObjectsURL + "/" + dataObject.getId()).then().statusCode(200).extract().as(DataObjectIO.class);
		assertThat(oldParent).isEqualTo(dataObject);

		successor.setChildrenIds(new long[] { actual.getId() });
		DataObjectIO newParent = given().spec(requestSpecification).when().get(dataObjectsURL + "/" + successor.getId())
				.then().statusCode(200).extract().as(DataObjectIO.class);
		assertThat(newParent).isEqualTo(successor);
	}

	@Test
	@Order(16)
	public void putDataObjectTest_newPredecessor() {
		child.setName("DataObjectSuccessorChanged");
		child.setPredecessorIds(new long[] { dataObject.getId() });

		DataObjectIO actual = given().spec(requestSpecification).body(child).when()
				.put(dataObjectsURL + "/" + child.getId()).then().statusCode(200).extract().as(DataObjectIO.class);

		assertThat(actual).usingRecursiveComparison().ignoringFields("updatedBy", "updatedAt").isEqualTo(child);
		child = actual;

		DataObjectIO newPredecessor = given().spec(requestSpecification).when()
				.get(dataObjectsURL + "/" + dataObject.getId()).then().statusCode(200).extract().as(DataObjectIO.class);
		assertThat(newPredecessor).usingRecursiveComparison().ignoringFields("successorIds").isEqualTo(dataObject);
		assertThat(newPredecessor.getSuccessorIds()).containsExactlyInAnyOrder(successor.getId(), child.getId());
		dataObject = newPredecessor;
	}

	@Test
	@Order(17)
	public void deleteDataObjectTest_Successful() {
		given().spec(requestSpecification).when().delete(dataObjectsURL + "/" + dataObject.getId()).then()
				.statusCode(204);

		given().spec(requestSpecification).when().get(dataObjectsURL + "/" + dataObject.getId()).then().statusCode(404);
	}

	@Test
	@Order(18)
	public void getOrderByName() {
		DataObjectIO[] response = given().spec(orderByRequestSpecification)
				.queryParam("orderBy", DataObjectAttributes.name).when().get().then().statusCode(200).extract()
				.as(DataObjectIO[].class);
		assertThat(response).containsExactly(aDataObject, bDataObject, cDataObject, dDataObject, eDataObject,
				fDataObject);
	}

	@Test
	@Order(19)
	public void getOrderByNameDesc() {
		DataObjectIO[] response = given().spec(orderByRequestSpecification)
				.queryParam("orderBy", DataObjectAttributes.name).queryParam("orderDesc", true).when().get().then()
				.statusCode(200).extract().as(DataObjectIO[].class);
		assertThat(response).containsExactly(fDataObject, eDataObject, dDataObject, cDataObject, bDataObject,
				aDataObject);
	}

	@Test
	@Order(19)
	public void getOrderByCreatedAt() {
		DataObjectIO[] response = given().spec(orderByRequestSpecification)
				.queryParam("orderBy", DataObjectAttributes.createdAt).queryParam("orderDesc", false).when().get()
				.then().statusCode(200).extract().as(DataObjectIO[].class);
		assertThat(response).containsExactly(aDataObject, bDataObject, cDataObject, dDataObject, eDataObject,
				fDataObject);
	}

	@Test
	@Order(20)
	public void getOrderByCreatedFirstPage() {
		DataObjectIO[] response = given().spec(orderByRequestSpecification)
				.queryParam("orderBy", DataObjectAttributes.createdAt).queryParam("orderDesc", false)
				.queryParam("page", 0).queryParam("size", 3).when().get().then().statusCode(200).extract()
				.as(DataObjectIO[].class);
		assertThat(response).containsExactly(aDataObject, bDataObject, cDataObject);
	}

	@Test
	@Order(21)
	public void getOrderByCreatedSecondPage() {
		DataObjectIO[] response = given().spec(orderByRequestSpecification)
				.queryParam("orderBy", DataObjectAttributes.createdAt).queryParam("orderDesc", false)
				.queryParam("page", 1).queryParam("size", 3).when().get().then().statusCode(200).extract()
				.as(DataObjectIO[].class);
		assertThat(response).containsExactly(dDataObject, eDataObject, fDataObject);
	}

	@Test
	@Order(22)
	public void getOrderByCreatedDescSecondPage() {
		DataObjectIO[] response = given().spec(orderByRequestSpecification)
				.queryParam("orderBy", DataObjectAttributes.createdAt).queryParam("orderDesc", true)
				.queryParam("page", 1).queryParam("size", 3).when().get().then().statusCode(200).extract()
				.as(DataObjectIO[].class);
		assertThat(response).containsExactly(cDataObject, bDataObject, aDataObject);
	}

	@Test
	@Order(23)
	public void getOrderByCreatedDescFirstPage() {
		DataObjectIO[] response = given().spec(orderByRequestSpecification)
				.queryParam("orderBy", DataObjectAttributes.createdAt).queryParam("orderDesc", true)
				.queryParam("page", 0).queryParam("size", 3).when().get().then().statusCode(200).extract()
				.as(DataObjectIO[].class);
		assertThat(response).containsExactly(fDataObject, eDataObject, dDataObject);
	}

}
