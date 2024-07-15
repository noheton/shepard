package de.dlr.shepard.integrationtests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.neo4Core.io.SemanticRepositoryIO;
import de.dlr.shepard.semantics.SemanticRepositoryType;
import de.dlr.shepard.util.Constants;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SemanticRepositoryTest extends BaseTestCaseIT {
	private static String repositoryURL;
	private static RequestSpecification repositoryRequestSpec;

	private static SemanticRepositoryIO repository;
	private static SemanticAnnotationIO annotation;

	private static CollectionIO collection;
	private static String collectionURL;
	private static RequestSpecification collectionRequestSpec;

	@BeforeAll
	public static void setUp() {
		repositoryURL = baseURL + "/" + Constants.SEMANTIC_REPOSITORIES;
		repositoryRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(repositoryURL)
				.addHeader("X-API-KEY", jws).build();
		collection = createCollection("SemanticsCollection");
		collectionURL = String.format("%s/%s/%d/semanticAnnotations", baseURL, Constants.COLLECTIONS,
				collection.getId());
		collectionRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).setBaseUri(collectionURL)
				.addHeader("X-API-KEY", jws).build();
	}

	@Test
	@Order(1)
	public void createSemanticRepository() {
		var toCreate = new SemanticRepositoryIO();
		toCreate.setName("SemanticRepository");
		toCreate.setType(SemanticRepositoryType.SPARQL);
		toCreate.setEndpoint("https://dbpedia.org/sparql/");

		var actual = given().spec(repositoryRequestSpec).body(toCreate).when().post().then().statusCode(201).extract()
				.as(SemanticRepositoryIO.class);
		repository = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getCreatedAt()).isNotNull();
		assertThat(actual.getCreatedBy()).isEqualTo(username);
		assertThat(actual.getType()).isEqualTo(SemanticRepositoryType.SPARQL);
		assertThat(actual.getEndpoint()).isEqualTo("https://dbpedia.org/sparql/");
		assertThat(actual.getName()).isEqualTo("SemanticRepository");
		assertThat(actual.getUpdatedAt()).isNull();
		assertThat(actual.getUpdatedBy()).isNull();
	}

	@Test
	@Order(2)
	public void getSemanticRepositories() {
		var actual = given().spec(repositoryRequestSpec).when().get().then().statusCode(200).extract()
				.as(SemanticRepositoryIO[].class);

		assertThat(actual).contains(repository);
	}

	@Test
	@Order(3)
	public void getSemanticRepository() {
		var actual = given().spec(repositoryRequestSpec).when().get(repositoryURL + "/" + repository.getId()).then()
				.statusCode(200).extract().as(SemanticRepositoryIO.class);

		assertThat(actual).isEqualTo(repository);
	}

	@Test
	@Order(4)
	public void createSemanticAnnotation() {
		var toCreate = new SemanticAnnotationIO();
		toCreate.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
		toCreate.setPropertyRepositoryId(repository.getId());
		toCreate.setValueIRI("http://dbpedia.org/resource/Almond_milk");
		toCreate.setValueRepositoryId(repository.getId());

		var actual = given().spec(collectionRequestSpec).body(toCreate).when().post().then().statusCode(201).extract()
				.as(SemanticAnnotationIO.class);
		annotation = actual;

		assertThat(actual.getId()).isNotNull();
		assertThat(actual.getName()).isEqualTo("ingredient::Almond milk");
		assertThat(actual.getPropertyIRI()).isEqualTo("http://dbpedia.org/ontology/ingredient");
		assertThat(actual.getPropertyRepositoryId()).isEqualTo(repository.getId());
		assertThat(actual.getValueIRI()).isEqualTo("http://dbpedia.org/resource/Almond_milk");
		assertThat(actual.getValueRepositoryId()).isEqualTo(repository.getId());
	}

	@Test
	@Order(5)
	public void getSemanticAnnotations() {
		var actual = given().spec(collectionRequestSpec).get().then().statusCode(200).extract()
				.as(SemanticAnnotationIO[].class);

		assertThat(actual).contains(annotation);
	}

	@Test
	@Order(6)
	public void getSemanticAnnotation() {
		var actual = given().spec(collectionRequestSpec).get(collectionURL + "/" + annotation.getId()).then()
				.statusCode(200).extract().as(SemanticAnnotationIO.class);

		assertThat(actual).isEqualTo(annotation);
	}

	@Test
	@Order(7)
	public void deleteSemanticAnnotation() {
		given().spec(collectionRequestSpec).when().delete(collectionURL + "/" + annotation.getId()).then()
				.statusCode(204);
		given().spec(collectionRequestSpec).get(collectionURL + "/" + annotation.getId()).then().statusCode(404);
		var actual = given().spec(collectionRequestSpec).get().then().statusCode(200).extract()
				.as(SemanticAnnotationIO[].class);
		assertThat(actual).isEmpty();
	}

	@Test
	@Order(8)
	public void deleteSemanticRepository() {
		given().spec(repositoryRequestSpec).when().delete(repositoryURL + "/" + repository.getId()).then()
				.statusCode(204);
		given().spec(repositoryRequestSpec).when().get(repositoryURL + "/" + repository.getId()).then().statusCode(404);
	}

}
