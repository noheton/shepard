package de.dlr.shepard.v2.provenance.resources;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-PROVENANCE-PROV-VARIANT-PARAMS — reflection regression tests
 * asserting that every {@code @QueryParam} on the five content-negotiation
 * variant methods in {@link ProvenanceRest} carries a non-blank
 * {@code @Parameter} description.
 *
 * <p>The primary JSON {@code listActivities()} method was already documented;
 * these tests guard the PROV-JSON and JSON-LD variants that were missing the
 * same annotations.
 */
class ProvenanceRestParamDocTest {

  /** Find the first method parameter annotated {@code @QueryParam(name)}. */
  private static java.lang.reflect.Parameter queryParam(Method m, String name) {
    return Arrays.stream(m.getParameters())
      .filter(p -> {
        QueryParam qp = p.getAnnotation(QueryParam.class);
        return qp != null && name.equals(qp.value());
      })
      .findFirst()
      .orElse(null);
  }

  private static void assertDocumented(Method m, String paramName) {
    java.lang.reflect.Parameter p = queryParam(m, paramName);
    assertNotNull(p, "@QueryParam(\"" + paramName + "\") not found on " + m.getName());
    Parameter ann = p.getAnnotation(Parameter.class);
    assertNotNull(ann, "@Parameter missing on '" + paramName + "' in " + m.getName());
    assertTrue(
      ann.description() != null && !ann.description().isBlank(),
      "@Parameter.description must be non-blank for '" + paramName + "' in " + m.getName()
    );
  }

  // ── listActivitiesProvJson ────────────────────────────────────────────────

  @Test
  void listActivitiesProvJson_agentParam_isDocumented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "listActivitiesProvJson",
      String.class, String.class, String.class, Long.class, Long.class, Integer.class,
      SecurityContext.class
    );
    assertDocumented(m, "agent");
  }

  // ── listActivitiesJsonLd ──────────────────────────────────────────────────

  @Test
  void listActivitiesJsonLd_pageSize_isDocumented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "listActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class, Integer.class,
      String.class, SecurityContext.class
    );
    assertDocumented(m, "pageSize");
  }

  // ── listEntityActivitiesProvJson ──────────────────────────────────────────

  @Test
  void listEntityActivitiesProvJson_since_isDocumented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "listEntityActivitiesProvJson",
      String.class, Long.class, Long.class, Integer.class, SecurityContext.class
    );
    assertDocumented(m, "since");
  }

  // ── listEntityActivitiesJsonLd ────────────────────────────────────────────

  @Test
  void listEntityActivitiesJsonLd_pageSize_isDocumented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "listEntityActivitiesJsonLd",
      String.class, Long.class, Long.class, Integer.class, String.class, SecurityContext.class
    );
    assertDocumented(m, "pageSize");
  }

  // ── countActivitiesJsonLd ─────────────────────────────────────────────────

  @Test
  void countActivitiesJsonLd_targetKind_isDocumented() throws NoSuchMethodException {
    Method m = ProvenanceRest.class.getMethod(
      "countActivitiesJsonLd",
      String.class, String.class, String.class, Long.class, Long.class,
      String.class, SecurityContext.class
    );
    assertDocumented(m, "targetKind");
  }
}
