package de.dlr.shepard.v2.spatial.promote;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.QueryParam;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-REMAINING-PARAMS — reflection guard ensuring that {@code @Parameter} is
 * present on the {@code fileReferenceAppId} query param in
 * {@link SpatialPromoteRest#promote(String, jakarta.ws.rs.core.SecurityContext)}.
 */
class SpatialPromoteRestTest {

  @Test
  void promote_fileReferenceAppIdParam_hasParameterAnnotationWithDescription()
      throws NoSuchMethodException {
    Method m = SpatialPromoteRest.class.getMethod(
        "promote", String.class, jakarta.ws.rs.core.SecurityContext.class);
    java.lang.reflect.Parameter param = Arrays.stream(m.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(QueryParam.class);
          return qp != null && "fileReferenceAppId".equals(qp.value());
        })
        .findFirst()
        .orElse(null);
    assertNotNull(param, "promote.fileReferenceAppId must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "promote.fileReferenceAppId must carry @Parameter annotation");
    assertTrue(
        ann.description() != null && !ann.description().isBlank(),
        "@Parameter.description must be non-blank for promote.fileReferenceAppId");
  }
}
