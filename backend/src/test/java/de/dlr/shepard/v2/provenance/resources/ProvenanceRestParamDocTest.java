package de.dlr.shepard.v2.provenance.resources;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for APISIMP-PROVENANCE-V2-PARAMS-UNDOCUMENTED.
 *
 * <p>Asserts that every {@code @QueryParam} and {@code @PathParam} on the
 * content-negotiated variants of the provenance endpoints carries a
 * non-blank {@code @Parameter(description=...)}. The primary JSON variants
 * were already documented; this guard prevents undocumented params from
 * re-appearing in the PROV-JSON / JSON-LD / count overloads.
 */
class ProvenanceRestParamDocTest {

  // ─── helpers ─────────────────────────────────────────────────────────────────

  private static String descriptionFor(Method method, String paramName) {
    return Arrays.stream(method.getParameters())
      .filter(p -> {
        QueryParam qp = p.getAnnotation(QueryParam.class);
        PathParam pp = p.getAnnotation(PathParam.class);
        return (qp != null && paramName.equals(qp.value()))
          || (pp != null && paramName.equals(pp.value()));
      })
      .map(p -> {
        Parameter ann = p.getAnnotation(Parameter.class);
        return ann == null ? null : ann.description();
      })
      .findFirst()
      .orElse(null);
  }

  private static void assertDoc(Method method, String paramName) {
    String desc = descriptionFor(method, paramName);
    assertNotNull(desc,
      method.getName() + "(): @Parameter missing on '" + paramName + "'");
    assertTrue(!desc.isBlank(),
      method.getName() + "(): @Parameter.description blank on '" + paramName + "'");
  }

  // ─── listActivitiesProvJson ────────────────────────────────────────────────

  @Test
  void listActivitiesProvJson_agentParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesProvJson",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, SecurityContext.class);
    assertDoc(m, "agent");
  }

  @Test
  void listActivitiesProvJson_targetKindParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesProvJson",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, SecurityContext.class);
    assertDoc(m, "targetKind");
  }

  @Test
  void listActivitiesProvJson_targetAppIdParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesProvJson",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, SecurityContext.class);
    assertDoc(m, "targetAppId");
  }

  @Test
  void listActivitiesProvJson_sinceParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesProvJson",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, SecurityContext.class);
    assertDoc(m, "since");
  }

  @Test
  void listActivitiesProvJson_untilParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesProvJson",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, SecurityContext.class);
    assertDoc(m, "until");
  }

  @Test
  void listActivitiesProvJson_pageSizeParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesProvJson",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, SecurityContext.class);
    assertDoc(m, "pageSize");
  }

  // ─── listActivitiesJsonLd ─────────────────────────────────────────────────

  @Test
  void listActivitiesJsonLd_agentParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, String.class, SecurityContext.class);
    assertDoc(m, "agent");
  }

  @Test
  void listActivitiesJsonLd_targetKindParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, String.class, SecurityContext.class);
    assertDoc(m, "targetKind");
  }

  @Test
  void listActivitiesJsonLd_targetAppIdParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, String.class, SecurityContext.class);
    assertDoc(m, "targetAppId");
  }

  @Test
  void listActivitiesJsonLd_sinceParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, String.class, SecurityContext.class);
    assertDoc(m, "since");
  }

  @Test
  void listActivitiesJsonLd_untilParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, String.class, SecurityContext.class);
    assertDoc(m, "until");
  }

  @Test
  void listActivitiesJsonLd_pageSizeParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      Integer.class, String.class, SecurityContext.class);
    assertDoc(m, "pageSize");
  }

  // ─── listEntityActivitiesProvJson ────────────────────────────────────────

  @Test
  void listEntityActivitiesProvJson_appIdParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listEntityActivitiesProvJson",
      String.class, Long.class, Long.class, Integer.class, SecurityContext.class);
    assertDoc(m, "appId");
  }

  @Test
  void listEntityActivitiesProvJson_sinceParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listEntityActivitiesProvJson",
      String.class, Long.class, Long.class, Integer.class, SecurityContext.class);
    assertDoc(m, "since");
  }

  @Test
  void listEntityActivitiesProvJson_untilParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listEntityActivitiesProvJson",
      String.class, Long.class, Long.class, Integer.class, SecurityContext.class);
    assertDoc(m, "until");
  }

  @Test
  void listEntityActivitiesProvJson_pageSizeParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listEntityActivitiesProvJson",
      String.class, Long.class, Long.class, Integer.class, SecurityContext.class);
    assertDoc(m, "pageSize");
  }

  // ─── listEntityActivitiesJsonLd ─────────────────────────────────────────

  @Test
  void listEntityActivitiesJsonLd_appIdParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listEntityActivitiesJsonLd",
      String.class, Long.class, Long.class, Integer.class, String.class,
      SecurityContext.class);
    assertDoc(m, "appId");
  }

  @Test
  void listEntityActivitiesJsonLd_sinceParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listEntityActivitiesJsonLd",
      String.class, Long.class, Long.class, Integer.class, String.class,
      SecurityContext.class);
    assertDoc(m, "since");
  }

  @Test
  void listEntityActivitiesJsonLd_untilParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listEntityActivitiesJsonLd",
      String.class, Long.class, Long.class, Integer.class, String.class,
      SecurityContext.class);
    assertDoc(m, "until");
  }

  @Test
  void listEntityActivitiesJsonLd_pageSizeParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("listEntityActivitiesJsonLd",
      String.class, Long.class, Long.class, Integer.class, String.class,
      SecurityContext.class);
    assertDoc(m, "pageSize");
  }

  // ─── countActivities ─────────────────────────────────────────────────────

  @Test
  void countActivities_agentParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("countActivities",
      String.class, String.class, String.class, Long.class, Long.class,
      SecurityContext.class);
    assertDoc(m, "agent");
  }

  @Test
  void countActivities_targetKindParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("countActivities",
      String.class, String.class, String.class, Long.class, Long.class,
      SecurityContext.class);
    assertDoc(m, "targetKind");
  }

  @Test
  void countActivities_targetAppIdParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("countActivities",
      String.class, String.class, String.class, Long.class, Long.class,
      SecurityContext.class);
    assertDoc(m, "targetAppId");
  }

  @Test
  void countActivities_sinceParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("countActivities",
      String.class, String.class, String.class, Long.class, Long.class,
      SecurityContext.class);
    assertDoc(m, "since");
  }

  @Test
  void countActivities_untilParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("countActivities",
      String.class, String.class, String.class, Long.class, Long.class,
      SecurityContext.class);
    assertDoc(m, "until");
  }

  // ─── countActivitiesJsonLd ───────────────────────────────────────────────

  @Test
  void countActivitiesJsonLd_agentParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("countActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      String.class, SecurityContext.class);
    assertDoc(m, "agent");
  }

  @Test
  void countActivitiesJsonLd_targetKindParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("countActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      String.class, SecurityContext.class);
    assertDoc(m, "targetKind");
  }

  @Test
  void countActivitiesJsonLd_targetAppIdParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("countActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      String.class, SecurityContext.class);
    assertDoc(m, "targetAppId");
  }

  @Test
  void countActivitiesJsonLd_sinceParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("countActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      String.class, SecurityContext.class);
    assertDoc(m, "since");
  }

  @Test
  void countActivitiesJsonLd_untilParam_documented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod("countActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      String.class, SecurityContext.class);
    assertDoc(m, "until");
  }
}
