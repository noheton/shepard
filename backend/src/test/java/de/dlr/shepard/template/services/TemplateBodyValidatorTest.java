package de.dlr.shepard.template.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.template.services.TemplateBodyValidator.InvalidTemplateBodyException;
import org.junit.jupiter.api.Test;

class TemplateBodyValidatorTest {

  TemplateBodyValidator validator = new TemplateBodyValidator();

  @Test
  void blankBodyRejected() {
    var errs = validator.collectErrors(null, "COLLECTION_RECIPE");
    assertFalse(errs.isEmpty());
    assertTrue(errs.get(0).contains("required"));
    assertFalse(validator.collectErrors("", "COLLECTION_RECIPE").isEmpty());
    assertFalse(validator.collectErrors("   ", "COLLECTION_RECIPE").isEmpty());
  }

  @Test
  void malformedJsonRejected() {
    var errs = validator.collectErrors("{ not valid json", "COLLECTION_RECIPE");
    assertFalse(errs.isEmpty());
    assertTrue(errs.get(0).contains("not valid JSON"));
  }

  @Test
  void nonObjectTopLevelRejected() {
    assertTrue(validator.collectErrors("[]", "COLLECTION_RECIPE").get(0).contains("must be a JSON object"));
    assertTrue(validator.collectErrors("\"a string\"", "COLLECTION_RECIPE").get(0).contains("must be a JSON object"));
    assertTrue(validator.collectErrors("42", "COLLECTION_RECIPE").get(0).contains("must be a JSON object"));
    assertTrue(validator.collectErrors("null", "COLLECTION_RECIPE").get(0).contains("must be a JSON object"));
  }

  @Test
  void collectionRecipeRequiresCollectionKey() {
    assertTrue(validator.collectErrors("{}", "COLLECTION_RECIPE").get(0).contains("collection"));
    assertTrue(validator.collectErrors("{\"foo\": 1}", "COLLECTION_RECIPE").get(0).contains("collection"));
    assertTrue(validator.collectErrors("{\"collection\": {}}", "COLLECTION_RECIPE").isEmpty());
  }

  @Test
  void dataobjectRecipeAcceptsEitherKey() {
    assertTrue(validator.collectErrors("{\"dataObject\": {}}", "DATAOBJECT_RECIPE").isEmpty());
    assertTrue(validator.collectErrors("{\"dataobjects\": []}", "DATAOBJECT_RECIPE").isEmpty());
    assertFalse(validator.collectErrors("{\"collection\": {}}", "DATAOBJECT_RECIPE").isEmpty());
  }

  @Test
  void experimentRecipeAcceptsAnyOfThreeKeys() {
    assertTrue(validator.collectErrors("{\"experiment\": {}}", "EXPERIMENT_RECIPE").isEmpty());
    assertTrue(validator.collectErrors("{\"steps\": []}", "EXPERIMENT_RECIPE").isEmpty());
    assertTrue(validator.collectErrors("{\"phases\": []}", "EXPERIMENT_RECIPE").isEmpty());
    assertFalse(validator.collectErrors("{\"collection\": {}}", "EXPERIMENT_RECIPE").isEmpty());
  }

  @Test
  void aasSubmodelTemplateAcceptsEitherKey() {
    assertTrue(validator.collectErrors("{\"submodel\": {}}", "AAS_SUBMODEL_TEMPLATE").isEmpty());
    assertTrue(validator.collectErrors("{\"submodelElements\": []}", "AAS_SUBMODEL_TEMPLATE").isEmpty());
  }

  @Test
  void unknownTemplateKindPermissive() {
    // Permissive: any well-formed JSON object passes when the kind is unknown.
    assertTrue(validator.collectErrors("{}", "FUTURE_KIND_42").isEmpty());
    assertTrue(validator.collectErrors("{\"whatever\": true}", null).isEmpty());
  }

  @Test
  void validateThrowsOnError() {
    assertThrows(InvalidTemplateBodyException.class, () -> validator.validate("not json", "COLLECTION_RECIPE"));
  }

  @Test
  void validateSilentOnSuccess() {
    validator.validate("{\"collection\": {\"name\": \"hot-fire\"}}", "COLLECTION_RECIPE");
  }

  @Test
  void invalidExceptionCarriesAllErrors() {
    var ex = assertThrows(InvalidTemplateBodyException.class, () -> validator.validate("[]", "COLLECTION_RECIPE"));
    assertEquals(1, ex.getErrors().size());
    assertTrue(ex.getMessage().contains("must be a JSON object"));
  }

  @Test
  void unknownTopLevelKeysAreToleratedV1() {
    // The DSL is open for plugin-supplied extensions; unknown keys do NOT fail v1.
    assertTrue(validator.collectErrors("{\"collection\": {}, \"futurePlugin\": {\"x\":1}}", "COLLECTION_RECIPE").isEmpty());
  }

  @Test
  void expectedKeysTableExposed() {
    assertTrue(TemplateBodyValidator.expectedKeys("COLLECTION_RECIPE").contains("collection"));
    assertTrue(TemplateBodyValidator.expectedKeys(null).isEmpty());
  }
}
