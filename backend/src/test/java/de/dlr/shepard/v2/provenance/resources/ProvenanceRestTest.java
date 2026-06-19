package de.dlr.shepard.v2.provenance.resources;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.QueryParam;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for APISIMP-PROVENANCE-CONTENT-NEG-PARAMS.
 *
 * <p>Verifies that the 6 content-negotiated variants of provenance
 * endpoints carry {@code @Parameter(description=...)} on every
 * {@code @QueryParam}, matching parity with the plain-JSON sibling
 * methods that were already documented.
 *
 * <p>Uses reflection to detect missing annotations so that future
 * copy-paste of a new content-type variant cannot silently drop
 * the descriptions.
 */
class ProvenanceRestTest {

  // ─── listActivitiesProvJson ───────────────────────────────────────────────

  @Test
  void listActivitiesProvJson_agentParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "listActivitiesProvJson",
        String.class, String.class, String.class, Long.class, Long.class,
        Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "agent");
  }

  @Test
  void listActivitiesProvJson_pageSizeParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "listActivitiesProvJson",
        String.class, String.class, String.class, Long.class, Long.class,
        Integer.class, jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "pageSize");
  }

  // ─── listActivitiesJsonLd ─────────────────────────────────────────────────

  @Test
  void listActivitiesJsonLd_agentParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "listActivitiesJsonLd",
        String.class, String.class, String.class, Long.class, Long.class,
        Integer.class, String.class, jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "agent");
  }

  @Test
  void listActivitiesJsonLd_sinceParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "listActivitiesJsonLd",
        String.class, String.class, String.class, Long.class, Long.class,
        Integer.class, String.class, jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "since");
  }

  // ─── listEntityActivitiesProvJson ─────────────────────────────────────────

  @Test
  void listEntityActivitiesProvJson_sinceParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "listEntityActivitiesProvJson",
        String.class, Long.class, Long.class, Integer.class,
        jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "since");
  }

  @Test
  void listEntityActivitiesProvJson_pageSizeParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "listEntityActivitiesProvJson",
        String.class, Long.class, Long.class, Integer.class,
        jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "pageSize");
  }

  // ─── listEntityActivitiesJsonLd ───────────────────────────────────────────

  @Test
  void listEntityActivitiesJsonLd_sinceParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "listEntityActivitiesJsonLd",
        String.class, Long.class, Long.class, Integer.class, String.class,
        jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "since");
  }

  @Test
  void listEntityActivitiesJsonLd_untilParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "listEntityActivitiesJsonLd",
        String.class, Long.class, Long.class, Integer.class, String.class,
        jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "until");
  }

  // ─── countActivities ──────────────────────────────────────────────────────

  @Test
  void countActivities_agentParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "countActivities",
        String.class, String.class, String.class, Long.class, Long.class,
        jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "agent");
  }

  @Test
  void countActivities_targetKindParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "countActivities",
        String.class, String.class, String.class, Long.class, Long.class,
        jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "targetKind");
  }

  // ─── countActivitiesJsonLd ────────────────────────────────────────────────

  @Test
  void countActivitiesJsonLd_agentParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "countActivitiesJsonLd",
        String.class, String.class, String.class, Long.class, Long.class,
        String.class, jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "agent");
  }

  @Test
  void countActivitiesJsonLd_sinceParamIsDocumented() throws NoSuchMethodException {
    Method method = ProvenanceRest.class.getMethod(
        "countActivitiesJsonLd",
        String.class, String.class, String.class, Long.class, Long.class,
        String.class, jakarta.ws.rs.core.SecurityContext.class);
    assertQueryParamHasDescription(method, "since");
  }

  // ─── helper ───────────────────────────────────────────────────────────────

  /**
   * Finds the first method parameter annotated with {@code @QueryParam(queryParamName)}
   * and asserts it also carries a non-blank {@code @Parameter(description=...)}.
   */
  private static void assertQueryParamHasDescription(Method method, String queryParamName) {
    java.lang.reflect.Parameter param = Arrays.stream(method.getParameters())
        .filter(p -> {
          QueryParam qp = p.getAnnotation(QueryParam.class);
          return qp != null && queryParamName.equals(qp.value());
        })
        .findFirst()
        .orElse(null);
    assertNotNull(param,
        "Method " + method.getName() + " must have @QueryParam(\"" + queryParamName + "\")");
    Parameter ann = param.getAnnotation(Parameter.class);
    assertNotNull(ann,
        "Method " + method.getName() + " @QueryParam(\"" + queryParamName + "\") must carry @Parameter");
    assertTrue(ann.description() != null && !ann.description().isBlank(),
        "Method " + method.getName() + " @QueryParam(\"" + queryParamName + "\") @Parameter.description must be non-blank");
  }
}
