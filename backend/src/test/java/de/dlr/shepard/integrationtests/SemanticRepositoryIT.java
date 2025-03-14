package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.ErrorResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.io.SemanticRepositoryIO;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusIntegrationTest
@WithTestResource(WireMockResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SemanticRepositoryIT extends BaseTestCaseIT {

  private static String repositoryURL;

  private static SemanticRepositoryIO repository;
  private static SemanticAnnotationIO collectionAnnotation;
  private static SemanticAnnotationIO dataObjectAnnotation;
  private static SemanticAnnotationIO referenceAnnotation;

  private static CollectionIO collection;
  private static DataObjectIO dataObject;
  private static BasicReferenceIO dataObjectReference;
  private static String collectionAnnotationURL;
  private static String dataObjectAnnotationURL;
  private static String referenceAnnotationURL;

  @BeforeAll
  public static void setUp() {
    repositoryURL = "/" + Constants.SEMANTIC_REPOSITORIES;

    collection = createCollection("SemanticsCollection");
    dataObject = createDataObject("SemanticDataObject", collection.getId());
    dataObjectReference = createDataObjectReference(collection.getId(), dataObject.getId(), dataObject.getId());

    collectionAnnotationURL = String.format("/%s/%d/semanticAnnotations", Constants.COLLECTIONS, collection.getId());
    dataObjectAnnotationURL = String.format(
      "/%s/%d/%s/%d/semanticAnnotations",
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATA_OBJECTS,
      dataObject.getId()
    );
    referenceAnnotationURL = String.format(
      "/%s/%d/%s/%d/%s/%d/semanticAnnotations",
      Constants.COLLECTIONS,
      collection.getId(),
      Constants.DATA_OBJECTS,
      dataObject.getId(),
      Constants.BASIC_REFERENCES,
      dataObjectReference.getId()
    );
  }

  @Test
  @Order(1)
  public void createSemanticRepository() {
    var toCreate = new SemanticRepositoryIO();
    toCreate.setName("SemanticRepository");
    toCreate.setType(SemanticRepositoryType.SPARQL);
    toCreate.setEndpoint(WireMockResource.getWireMockServerURlWithPath("/sparql"));

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(repositoryURL)
      .then()
      .statusCode(201)
      .extract()
      .as(SemanticRepositoryIO.class);
    repository = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getCreatedBy()).isEqualTo(nameOfDefaultUser);
    assertThat(actual.getType()).isEqualTo(SemanticRepositoryType.SPARQL);
    assertThat(actual.getEndpoint()).isEqualTo(WireMockResource.getWireMockServerURlWithPath("/sparql"));
    assertThat(actual.getName()).isEqualTo("SemanticRepository");
    assertThat(actual.getUpdatedAt()).isNull();
    assertThat(actual.getUpdatedBy()).isNull();
  }

  @Test
  @Order(2)
  public void getSemanticRepositories() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(repositoryURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SemanticRepositoryIO[].class);

    assertThat(actual).contains(repository);
  }

  @Test
  @Order(3)
  public void getSemanticRepository() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(repositoryURL + "/" + repository.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(SemanticRepositoryIO.class);

    assertThat(actual).isEqualTo(repository);
  }

  @Test
  @Order(4)
  public void getSemanticRepository_doesNotExist_notFound() {
    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .get(repositoryURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actual.getMessage()).isEqualTo("ID ERROR - SemanticRepository does not exist");
  }

  @Test
  @Order(5)
  public void createCollectionSemanticAnnotation() {
    var toCreate = new SemanticAnnotationIO();
    toCreate.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
    toCreate.setPropertyRepositoryId(repository.getId());
    toCreate.setValueIRI("http://dbpedia.org/resource/Almond_milk");
    toCreate.setValueRepositoryId(repository.getId());

    collectionAnnotation = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(collectionAnnotationURL)
      .then()
      .statusCode(201)
      .extract()
      .as(SemanticAnnotationIO.class);

    assertThat(collectionAnnotation.getId()).isNotNull();
    assertThat(collectionAnnotation.getName()).isEqualTo("ingredient::Almond milk");
    assertThat(collectionAnnotation.getPropertyIRI()).isEqualTo("http://dbpedia.org/ontology/ingredient");
    assertThat(collectionAnnotation.getPropertyRepositoryId()).isEqualTo(repository.getId());
    assertThat(collectionAnnotation.getValueIRI()).isEqualTo("http://dbpedia.org/resource/Almond_milk");
    assertThat(collectionAnnotation.getValueRepositoryId()).isEqualTo(repository.getId());
  }

  @Test
  @Order(6)
  public void createDataObjectSemanticAnnotation() {
    var toCreate = new SemanticAnnotationIO();
    toCreate.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
    toCreate.setPropertyRepositoryId(repository.getId());
    toCreate.setValueIRI("http://dbpedia.org/resource/Almond_milk");
    toCreate.setValueRepositoryId(repository.getId());

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(dataObjectAnnotationURL)
      .then()
      .statusCode(201)
      .extract()
      .as(SemanticAnnotationIO.class);
    dataObjectAnnotation = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getName()).isEqualTo("ingredient::Almond milk");
    assertThat(actual.getPropertyIRI()).isEqualTo("http://dbpedia.org/ontology/ingredient");
    assertThat(actual.getPropertyRepositoryId()).isEqualTo(repository.getId());
    assertThat(actual.getValueIRI()).isEqualTo("http://dbpedia.org/resource/Almond_milk");
    assertThat(actual.getValueRepositoryId()).isEqualTo(repository.getId());
  }

  @Test
  @Order(7)
  public void createReferenceSemanticAnnotation() {
    var toCreate = new SemanticAnnotationIO();
    toCreate.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
    toCreate.setPropertyRepositoryId(repository.getId());
    toCreate.setValueIRI("http://dbpedia.org/resource/Almond_milk");
    toCreate.setValueRepositoryId(repository.getId());

    var actual = given()
      .spec(requestSpecOfDefaultUser)
      .body(toCreate)
      .when()
      .post(referenceAnnotationURL)
      .then()
      .statusCode(201)
      .extract()
      .as(SemanticAnnotationIO.class);
    referenceAnnotation = actual;

    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getName()).isEqualTo("ingredient::Almond milk");
    assertThat(actual.getPropertyIRI()).isEqualTo("http://dbpedia.org/ontology/ingredient");
    assertThat(actual.getPropertyRepositoryId()).isEqualTo(repository.getId());
    assertThat(actual.getValueIRI()).isEqualTo("http://dbpedia.org/resource/Almond_milk");
    assertThat(actual.getValueRepositoryId()).isEqualTo(repository.getId());
  }

  @Test
  @Order(8)
  public void getSemanticAnnotations() {
    var actualCollectionAnnotations = given()
      .spec(requestSpecOfDefaultUser)
      .get(collectionAnnotationURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SemanticAnnotationIO[].class);
    var actualDataObjectAnnotations = given()
      .spec(requestSpecOfDefaultUser)
      .get(dataObjectAnnotationURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SemanticAnnotationIO[].class);
    var actualReferenceAnnotations = given()
      .spec(requestSpecOfDefaultUser)
      .get(referenceAnnotationURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SemanticAnnotationIO[].class);

    assertThat(actualCollectionAnnotations).contains(collectionAnnotation);
    assertThat(actualDataObjectAnnotations).contains(dataObjectAnnotation);
    assertThat(actualReferenceAnnotations).contains(referenceAnnotation);
  }

  @Test
  @Order(9)
  public void getSemanticAnnotation() {
    var actualCollectionAnnotation = given()
      .spec(requestSpecOfDefaultUser)
      .get(collectionAnnotationURL + "/" + collectionAnnotation.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(SemanticAnnotationIO.class);
    var actualDataObjectAnnotation = given()
      .spec(requestSpecOfDefaultUser)
      .get(dataObjectAnnotationURL + "/" + dataObjectAnnotation.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(SemanticAnnotationIO.class);
    var actualReferenceAnnotation = given()
      .spec(requestSpecOfDefaultUser)
      .get(referenceAnnotationURL + "/" + referenceAnnotation.getId())
      .then()
      .statusCode(200)
      .extract()
      .as(SemanticAnnotationIO.class);

    assertThat(actualCollectionAnnotation).isEqualTo(collectionAnnotation);
    assertThat(actualDataObjectAnnotation).isEqualTo(dataObjectAnnotation);
    assertThat(actualReferenceAnnotation).isEqualTo(referenceAnnotation);
  }

  @Test
  @Order(10)
  public void getSemanticAnnotation_doesNotExist_notFound() {
    var actualCollectionAnnotation = given()
      .spec(requestSpecOfDefaultUser)
      .get(collectionAnnotationURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    var actualDataObjectAnnotation = given()
      .spec(requestSpecOfDefaultUser)
      .get(dataObjectAnnotationURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    var actualReferenceAnnotation = given()
      .spec(requestSpecOfDefaultUser)
      .get(referenceAnnotationURL + "/99999")
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actualCollectionAnnotation.getMessage()).isEqualTo("ID ERROR - SemanticAnnotation does not exist");
    assertThat(actualDataObjectAnnotation.getMessage()).isEqualTo("ID ERROR - SemanticAnnotation does not exist");
    assertThat(actualReferenceAnnotation.getMessage()).isEqualTo("ID ERROR - SemanticAnnotation does not exist");
  }

  @Test
  @Order(11)
  public void getSemanticAnnotation_annotationOfSomethingElse_notFound() {
    var actualCollectionAnnotation = given()
      .spec(requestSpecOfDefaultUser)
      .get(collectionAnnotationURL + "/" + dataObjectAnnotation.getId())
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    var actualDataObjectAnnotation = given()
      .spec(requestSpecOfDefaultUser)
      .get(dataObjectAnnotationURL + "/" + collectionAnnotation.getId())
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);
    var actualReferenceAnnotation = given()
      .spec(requestSpecOfDefaultUser)
      .get(referenceAnnotationURL + "/" + collectionAnnotation.getId())
      .then()
      .statusCode(404)
      .extract()
      .as(ErrorResponse.class);

    assertThat(actualCollectionAnnotation.getMessage()).isEqualTo(
      "ID ERROR - There is no association between annotation and collection"
    );
    assertThat(actualDataObjectAnnotation.getMessage()).isEqualTo(
      "ID ERROR - There is no association between annotation and dataObject"
    );
    assertThat(actualReferenceAnnotation.getMessage()).isEqualTo(
      "ID ERROR - There is no association between annotation and reference"
    );
  }

  @Test
  @Order(12)
  public void deleteSemanticAnnotation() {
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(collectionAnnotationURL + "/" + collectionAnnotation.getId())
      .then()
      .statusCode(204);
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(dataObjectAnnotationURL + "/" + dataObjectAnnotation.getId())
      .then()
      .statusCode(204);
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(referenceAnnotationURL + "/" + referenceAnnotation.getId())
      .then()
      .statusCode(204);
    var actualCollectionAnnotations = given()
      .spec(requestSpecOfDefaultUser)
      .get(collectionAnnotationURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SemanticAnnotationIO[].class);
    assertThat(actualCollectionAnnotations).isEmpty();
    var actualDataObjectAnnotation = given()
      .spec(requestSpecOfDefaultUser)
      .get(dataObjectAnnotationURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SemanticAnnotationIO[].class);
    assertThat(actualDataObjectAnnotation).isEmpty();
    var actualReferenceAnnotations = given()
      .spec(requestSpecOfDefaultUser)
      .get(referenceAnnotationURL)
      .then()
      .statusCode(200)
      .extract()
      .as(SemanticAnnotationIO[].class);
    assertThat(actualReferenceAnnotations).isEmpty();
  }

  @Test
  @Order(13)
  public void getIllegalPath_notFound() {
    given()
      .spec(requestSpecOfDefaultUser)
      .get("/semanticAnnotations/" + referenceAnnotation.getId())
      .then()
      .statusCode(404);
  }

  @Test
  @Order(14)
  public void deleteSemanticRepository() {
    given()
      .spec(requestSpecOfDefaultUser)
      .when()
      .delete(repositoryURL + "/" + repository.getId())
      .then()
      .statusCode(204);
    given().spec(requestSpecOfDefaultUser).when().get(repositoryURL + "/" + repository.getId()).then().statusCode(404);
  }
}
