package de.dlr.shepard.v2.snapshot.resources;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for {@link CollectionSnapshotRest} — verifies that every
 * {@code @QueryParam} on {@code list()} carries a non-blank {@code @Parameter}
 * description so the OpenAPI schema is usable by callers.
 */
class CollectionSnapshotRestTest {

  private static java.lang.reflect.Parameter listParam(String qpName) throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionSnapshotRest.class.getMethod(
        "list", String.class, int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    return java.util.Arrays.stream(m.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && qpName.equals(qp.value());
        })
        .findFirst()
        .orElseThrow(() -> new AssertionError("No @QueryParam(\"" + qpName + "\") on list()"));
  }

  private static void assertParamDocumented(java.lang.reflect.Parameter param, String label) {
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, label + " must carry @Parameter");
    assertTrue(ann.description() != null && !ann.description().isBlank(),
        label + " @Parameter.description must be non-blank");
  }

  @Test
  void list_pageParamIsDocumented() throws NoSuchMethodException {
    assertParamDocumented(listParam("page"), "list.page");
  }

  @Test
  void list_pageSizeParamIsDocumented() throws NoSuchMethodException {
    assertParamDocumented(listParam("pageSize"), "list.pageSize");
  }
}
