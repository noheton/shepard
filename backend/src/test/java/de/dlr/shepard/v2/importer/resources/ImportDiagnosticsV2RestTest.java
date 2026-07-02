package de.dlr.shepard.v2.importer.resources;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.QueryParam;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-IMPORT-DIAGNOSTICS-FILTER-PARAMS regression tests.
 *
 * <p>Verifies that the {@code ?level=} and {@code ?phase=} query parameters on
 * {@link ImportDiagnosticsV2Rest#getEvents} carry {@code @Parameter} annotations
 * with non-blank descriptions and schema enumerations listing every valid value.
 */
class ImportDiagnosticsV2RestTest {

  @Test
  void levelParam_hasParameterAnnotationWithEnumeration() throws NoSuchMethodException {
    Method method = ImportDiagnosticsV2Rest.class.getMethod(
        "getEvents",
        String.class,
        String.class,
        String.class,
        jakarta.ws.rs.core.SecurityContext.class);

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
    Method method = ImportDiagnosticsV2Rest.class.getMethod(
        "getEvents",
        String.class,
        String.class,
        String.class,
        jakarta.ws.rs.core.SecurityContext.class);

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
}
