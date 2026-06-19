package de.dlr.shepard.v2.spatial.promote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.ws.rs.QueryParam;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.junit.jupiter.api.Test;

class SpatialPromoteRestTest {

  // ─── APISIMP-PLUGIN-V2-PARAMS-UNDOCUMENTED regression ────────────────────

  @Test
  void promote_fileReferenceAppIdParamHasParameterAnnotation() throws NoSuchMethodException {
    Method method = Arrays.stream(SpatialPromoteRest.class.getMethods())
        .filter(m -> m.getName().equals("promote"))
        .findFirst()
        .orElseThrow(() -> new NoSuchMethodException("promote() not found on SpatialPromoteRest"));
    String desc = Arrays.stream(method.getParameters())
        .filter(p -> p.getAnnotation(QueryParam.class) != null
            && "fileReferenceAppId".equals(p.getAnnotation(QueryParam.class).value()))
        .map(p -> {
          Parameter ann = p.getAnnotation(Parameter.class);
          return ann != null ? ann.description() : "";
        })
        .findFirst()
        .orElse("");
    assertNotNull(desc, "fileReferenceAppId must have a @Parameter annotation");
    assertFalse(
        desc.isBlank(),
        "fileReferenceAppId @Parameter description must be non-blank — got: '" + desc + "'");
  }
}
