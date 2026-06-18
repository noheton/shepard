package de.dlr.shepard.v2.provenance.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.QueryParam;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Regression guard for APISIMP-PROVENANCE-STATS-ID-PARAM. */
class ProvenanceRestStatsTest {

  @Test
  void stats_entityIdParamAnnotationIsEntityId() throws NoSuchMethodException {
    // The @QueryParam on the entity-scope parameter must be "entityId" (not "id").
    // All v2 query params use descriptive names; bare "id" is ambiguous.
    Method method = ProvenanceRest.class.getMethod(
        "stats", String.class, String.class, Long.class, Long.class,
        jakarta.ws.rs.core.SecurityContext.class);
    String actual = Arrays.stream(method.getParameters())
        .filter(p -> {
          QueryParam qp = p.getAnnotation(QueryParam.class);
          return qp != null && (qp.value().equals("entityId") || qp.value().equals("id"));
        })
        .map(p -> p.getAnnotation(QueryParam.class).value())
        .findFirst()
        .orElse("NOT_FOUND");
    assertEquals("entityId", actual, "@QueryParam on scope-entity parameter must be 'entityId', not 'id'");
  }
}
