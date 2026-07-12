package de.dlr.shepard.v2.provenance.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.QueryParam;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Regression guard for APISIMP-PROVENANCE-STATS-ID-PARAM / APISIMP-PROV-STATS-ENTITYID-RENAME. */
class ProvenanceRestStatsTest {

  @Test
  void stats_subjectParamAnnotationIsSubject() throws NoSuchMethodException {
    // The @QueryParam on the entity-scope parameter must be "subject" (APISIMP-PROV-STATS-ENTITYID-RENAME).
    // APISIMP-PROV-ISO8601-TIMESTAMPS: since/until changed to String.
    // stats() now has 6 String params: scope, subject, legacyEntityId, sinceRaw, untilRaw + SecurityContext.
    Method method = ProvenanceRest.class.getMethod(
        "stats", String.class, String.class, String.class, String.class, String.class,
        jakarta.ws.rs.core.SecurityContext.class);
    String actual = Arrays.stream(method.getParameters())
        .filter(p -> {
          QueryParam qp = p.getAnnotation(QueryParam.class);
          return qp != null && qp.value().equals("subject");
        })
        .map(p -> p.getAnnotation(QueryParam.class).value())
        .findFirst()
        .orElse("NOT_FOUND");
    assertEquals("subject", actual, "@QueryParam on scope-entity parameter must be 'subject'");
  }

  @Test
  void stats_legacyEntityIdParamPresent() throws NoSuchMethodException {
    // A @QueryParam("entityId") parameter must still exist so the handler can detect
    // its use and return 400 with a migration hint (APISIMP-PROV-STATS-ENTITYID-RENAME AC).
    Method method = ProvenanceRest.class.getMethod(
        "stats", String.class, String.class, String.class, String.class, String.class,
        jakarta.ws.rs.core.SecurityContext.class);
    boolean legacyPresent = Arrays.stream(method.getParameters()).anyMatch(p -> {
      QueryParam qp = p.getAnnotation(QueryParam.class);
      return qp != null && qp.value().equals("entityId");
    });
    assertTrue(legacyPresent, "stats() must bind @QueryParam('entityId') to return a 400 migration hint");
  }
}
