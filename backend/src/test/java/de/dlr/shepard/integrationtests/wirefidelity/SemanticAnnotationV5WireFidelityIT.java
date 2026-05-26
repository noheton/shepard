package de.dlr.shepard.integrationtests.wirefidelity;

import static io.restassured.RestAssured.given;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.io.SemanticRepositoryIO;
import de.dlr.shepard.integrationtests.WireMockResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * v5 wire-fidelity test for {@code /shepard/api/collections/{id}/semanticAnnotations}.
 *
 * <p>Exercises the standalone {@code SemanticAnnotationIO} shape which carries no
 * {@code appId}, {@code updatedAt}, {@code updatedBy}, or {@code revision} fields.
 * The custom {@link #dynamicFields()} override ensures those absent fields are not
 * expected.
 *
 * <p>A WireMock SPARQL stub is required so that the server can resolve the
 * {@code propertyIRI} and {@code valueIRI} labels when constructing the auto-computed
 * {@code name}, {@code propertyName}, and {@code valueName} fields. The stubs return
 * deterministic English labels, so those values are fixed in the fixture.
 *
 * <p>Fork-additive nullable fields ({@code numericValue}, {@code unitIRI}) are serialised
 * as {@code null} when unset — they appear in the fixture as {@code null}.
 */
@QuarkusIntegrationTest
@WithTestResource(WireMockResource.class)
public class SemanticAnnotationV5WireFidelityIT extends V5WireFidelityTest {

  private static final String SLUG = "semanticannotations";

  private static CollectionIO collection;
  private static SemanticRepositoryIO repository;

  @BeforeAll
  public static void setUp() {
    collection = createCollection("SemanticAnnotationWireFixture_" + System.currentTimeMillis());

    var repoIO = new SemanticRepositoryIO();
    repoIO.setName("SemanticAnnotationWireFixtureRepo");
    repoIO.setType(SemanticRepositoryType.SPARQL);
    repoIO.setEndpoint(WireMockResource.getWireMockServerURlWithPath("/sparql"));

    repository = given()
      .spec(requestSpecOfDefaultUser)
      .body(repoIO)
      .when()
      .post("/" + Constants.SEMANTIC_REPOSITORIES)
      .then()
      .statusCode(201)
      .extract()
      .as(SemanticRepositoryIO.class);
  }

  /**
   * SemanticAnnotationIO does not extend BasicEntityIO. Only {@code id},
   * {@code propertyRepositoryId}, and {@code valueRepositoryId} are dynamic.
   * All other fields are either caller-supplied constants or WireMock-determined
   * labels and are therefore byte-stable.
   */
  @Override
  protected Map<String, String> dynamicFields() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("id", V5JsonNormalizer.ANY_LONG);
    m.put("propertyRepositoryId", V5JsonNormalizer.ANY_LONG);
    m.put("valueRepositoryId", V5JsonNormalizer.ANY_LONG);
    return m;
  }

  @Test
  public void createSemanticAnnotation_wireMatchesFixture() {
    var annotation = new SemanticAnnotationIO();
    annotation.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
    annotation.setPropertyRepositoryId(repository.getId());
    annotation.setValueIRI("http://dbpedia.org/resource/Almond_milk");
    annotation.setValueRepositoryId(repository.getId());

    String url = "/%s/%d/%s".formatted(Constants.COLLECTIONS, collection.getId(), Constants.SEMANTIC_ANNOTATIONS);

    Response response = given()
      .spec(requestSpecOfDefaultUser)
      .body(annotation)
      .when()
      .post(url)
      .then()
      .statusCode(201)
      .extract()
      .response();

    assertWireMatches(SLUG, "create", response);
  }
}
