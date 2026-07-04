package de.dlr.shepard.v2.importer.resources;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.QueryParam;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-IMPORT-DIAGNOSTICS-FILTER-PARAMS + APISIMP-IMPORT-DIAG-EVENTS-BARE-LIST
 * regression tests.
 *
 * <p>Verifies that the {@code ?level=}, {@code ?phase=}, and {@code ?limit=} query
 * parameters on {@link ImportDiagnosticsV2Rest#getEvents} carry the expected
 * annotations.
 */
class ImportDiagnosticsV2RestTest {

  /** Resolve the getEvents method with its updated signature (includes int limit). */
  private static Method getEventsMethod() throws NoSuchMethodException {
    return ImportDiagnosticsV2Rest.class.getMethod(
        "getEvents",
        String.class,
        String.class,
        String.class,
        int.class,
        jakarta.ws.rs.core.SecurityContext.class);
  }

  @Test
  void levelParam_hasParameterAnnotationWithEnumeration() throws NoSuchMethodException {
    Method method = getEventsMethod();

    Parameter param = Arrays.stream(method.getParameters())
        .filter(p -> {
          QueryParam qp = p.getAnnotation(QueryParam.class);
          return qp != null && "level".equals(qp.value());
        })
        .findFirst()
        .orElse(null);

    assertNotNull(param, "level must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "level must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(),
        "@Parameter.description must be non-blank for level");
    assertTrue(ann.description().contains("INFO"),
        "@Parameter.description for level must mention INFO");
  }

  @Test
  void listRuns_methodExists() throws NoSuchMethodException {
    Method method = ImportDiagnosticsV2Rest.class.getMethod(
        "listRuns",
        jakarta.ws.rs.core.SecurityContext.class);
    assertNotNull(method, "listRuns method must exist with (SecurityContext) signature");
  }

  @Test
  void phaseParam_hasParameterAnnotationWithEnumeration() throws NoSuchMethodException {
    Method method = getEventsMethod();

    Parameter param = Arrays.stream(method.getParameters())
        .filter(p -> {
          QueryParam qp = p.getAnnotation(QueryParam.class);
          return qp != null && "phase".equals(qp.value());
        })
        .findFirst()
        .orElse(null);

    assertNotNull(param, "phase must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "phase must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(),
        "@Parameter.description must be non-blank for phase");
    assertTrue(ann.description().contains("DO_CREATE"),
        "@Parameter.description for phase must mention DO_CREATE");
  }

  @Test
  void limitParam_hasQueryParamAndParameterAnnotation() throws NoSuchMethodException {
    Method method = getEventsMethod();

    Parameter param = Arrays.stream(method.getParameters())
        .filter(p -> {
          QueryParam qp = p.getAnnotation(QueryParam.class);
          return qp != null && "limit".equals(qp.value());
        })
        .findFirst()
        .orElse(null);

    assertNotNull(param, "limit must carry @QueryParam(\"limit\")");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "limit must carry @Parameter annotation");
    assertTrue(ann.description() != null && ann.description().contains("X-Truncated"),
        "@Parameter.description for limit must mention X-Truncated header");
  }
}
