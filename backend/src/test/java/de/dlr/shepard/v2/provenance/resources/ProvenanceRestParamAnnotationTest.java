package de.dlr.shepard.v2.provenance.resources;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.QueryParam;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-PROVENANCE-PROV-VARIANT-PARAMS — reflection guards ensuring that
 * {@code @Parameter} annotations are present on the five content-negotiation
 * variants of {@link ProvenanceRest}.  The primary JSON method was already
 * annotated; these tests prevent the variant params from silently regressing.
 *
 * <p>APISIMP-PROV-ISO8601-TIMESTAMPS: since/until params are now {@code String}
 * (accept both ISO 8601 and epoch-ms); reflection signatures updated accordingly.
 */
class ProvenanceRestParamAnnotationTest {

  private static java.lang.reflect.Parameter queryParam(Method m, String name) {
    return Arrays.stream(m.getParameters())
      .filter(p -> {
        var qp = p.getAnnotation(QueryParam.class);
        return qp != null && name.equals(qp.value());
      })
      .findFirst()
      .orElse(null);
  }

  private static void assertDocumented(java.lang.reflect.Parameter p, String label) {
    assertNotNull(p, label + " must carry @QueryParam");
    var ann = p.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, label + " must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(),
      "@Parameter.description must be non-blank for " + label);
  }

  @Test
  void listActivitiesProvJson_pageSizeParam_hasParameterAnnotation() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "listActivitiesProvJson",
      String.class, String.class, String.class, String.class, String.class,
      Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    assertDocumented(queryParam(m, "pageSize"), "listActivitiesProvJson.pageSize");
  }

  @Test
  void listActivitiesJsonLd_pageSizeParam_hasParameterAnnotation() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "listActivitiesJsonLd",
      String.class, String.class, String.class, String.class, String.class,
      Integer.class, String.class, jakarta.ws.rs.core.SecurityContext.class);
    assertDocumented(queryParam(m, "pageSize"), "listActivitiesJsonLd.pageSize");
  }

  @Test
  void listEntityActivitiesProvJson_sinceParam_hasParameterAnnotation() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "listEntityActivitiesProvJson",
      String.class, String.class, String.class, Integer.class,
      jakarta.ws.rs.core.SecurityContext.class);
    assertDocumented(queryParam(m, "since"), "listEntityActivitiesProvJson.since");
  }

  @Test
  void listEntityActivitiesJsonLd_sinceParam_hasParameterAnnotation() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "listEntityActivitiesJsonLd",
      String.class, String.class, String.class, Integer.class, String.class,
      jakarta.ws.rs.core.SecurityContext.class);
    assertDocumented(queryParam(m, "since"), "listEntityActivitiesJsonLd.since");
  }

  @Test
  void countActivitiesJsonLd_targetKindParam_hasParameterAnnotation() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "countActivitiesJsonLd",
      String.class, String.class, String.class, String.class, String.class,
      String.class, jakarta.ws.rs.core.SecurityContext.class);
    assertDocumented(queryParam(m, "targetKind"), "countActivitiesJsonLd.targetKind");
  }

  // ─── APISIMP-REMAINING-PARAMS: countActivities (plain-JSON) ───────────────

  @Test
  void countActivities_agentParam_hasParameterAnnotation() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "countActivities",
      String.class, String.class, String.class, String.class, String.class,
      jakarta.ws.rs.core.SecurityContext.class);
    assertDocumented(queryParam(m, "agent"), "countActivities.agent");
  }

  @Test
  void countActivities_targetKindParam_hasParameterAnnotation() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "countActivities",
      String.class, String.class, String.class, String.class, String.class,
      jakarta.ws.rs.core.SecurityContext.class);
    assertDocumented(queryParam(m, "targetKind"), "countActivities.targetKind");
  }

  @Test
  void countActivities_sinceParam_hasParameterAnnotation() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "countActivities",
      String.class, String.class, String.class, String.class, String.class,
      jakarta.ws.rs.core.SecurityContext.class);
    assertDocumented(queryParam(m, "since"), "countActivities.since");
  }
}
