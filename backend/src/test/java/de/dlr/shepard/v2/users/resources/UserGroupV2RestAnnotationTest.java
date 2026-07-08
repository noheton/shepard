package de.dlr.shepard.v2.users.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import de.dlr.shepard.auth.users.endpoints.UserGroupAttributes;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-USERGROUP-LIST-SCHEMA-MISMATCH — reflection guard ensuring that
 * {@code GET /v2/user-groups} advertises {@link PagedResponseIO} as its 200
 * response schema, not a bare array of {@code UserGroupV2IO}.
 *
 * <p>Root cause of the mismatch: fire-444 set {@code SchemaType.ARRAY} when
 * the {@code ?q=} path returned a bare list; fire-456 wrapped that path in
 * {@link PagedResponseIO} but forgot to update the {@code @APIResponse}
 * annotation. This test prevents the same regression.
 */
class UserGroupV2RestAnnotationTest {

  private static Method listUserGroups() throws NoSuchMethodException {
    return UserGroupV2Rest.class.getMethod(
      "listUserGroups",
      String.class,
      int.class,
      int.class,
      UserGroupAttributes.class,
      Boolean.class
    );
  }

  @Test
  void listUserGroups_200_schema_isPagedResponseIO() throws NoSuchMethodException {
    Method m = listUserGroups();
    APIResponse[] responses = m.getAnnotationsByType(APIResponse.class);
    APIResponse ok = Arrays.stream(responses)
      .filter(r -> "200".equals(r.responseCode()))
      .findFirst()
      .orElse(null);
    assertNotNull(ok, "GET /v2/user-groups must have an @APIResponse(responseCode=\"200\")");
    assertNotNull(ok.content(), "200 response must have content");
    assertEquals(1, ok.content().length, "200 response must have exactly one @Content");
    assertSame(
      PagedResponseIO.class,
      ok.content()[0].schema().implementation(),
      "200 schema implementation must be PagedResponseIO — not UserGroupV2IO or a raw array"
    );
  }

  @Test
  void listUserGroups_200_schema_hasNoArrayType() throws NoSuchMethodException {
    Method m = listUserGroups();
    APIResponse ok = Arrays.stream(m.getAnnotationsByType(APIResponse.class))
      .filter(r -> "200".equals(r.responseCode()))
      .findFirst()
      .orElseThrow();
    SchemaType actualType = ok.content()[0].schema().type();
    assertEquals(
      SchemaType.DEFAULT,
      actualType,
      "200 schema must NOT set type=ARRAY — the envelope already implies an object type"
    );
  }
}
