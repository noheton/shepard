package de.dlr.shepard.v2.provenance.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.QueryParam;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Regression guard for APISIMP-PROVENANCE-STATS-ID-PARAM / APISIMP-PROV-STATS-ENTITYID-RENAME. */
class ProvenanceRestStatsTest {

  @Test
  void stats_subjectParamAnnotationIsSubject() throws NoSuchMethodException {
    // The @QueryParam on the entity-scope parameter must be "subject" (APISIMP-PROV-STATS-ENTITYID-RENAME).
    // APISIMP-PROVENANCE-ENTITYID-TOMBSTONE-DROP: legacyEntityId param removed; stats() now has
    // 4 String params (scope, subject, sinceRaw, untilRaw) + SecurityContext.
    Method method = ProvenanceRest.class.getMethod(
        "stats", String.class, String.class, String.class, String.class,
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
}
