package de.dlr.shepard.spi.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI1a — pins the f(ai)²r constant surface. Predicate names live as
 * {@link String} constants on the SPI (NOT enforced DTO fields).
 *
 * <p>The test asserts the presence of the canonical set and the
 * namespace IRI so a downstream emitter compiles against stable names.
 */
class Fair2rPredicatesTest {

  @Test
  void namespaceUriIsPresent() {
    assertThat(Fair2rPredicates.NAMESPACE_URI).startsWith("https://").endsWith("#");
  }

  @Test
  void canonicalPredicatesAreAllStringConstants() {
    String[] expected = {
      "USED_MODEL", "USED_PROVIDER", "PROMPT_HASH", "PROMPT_TEXT",
      "CAPABILITY", "INPUT_TOKENS", "OUTPUT_TOKENS",
      "GUARDRAILS_VERSION", "INJECTION_FLAGGED",
      "RESULTED_IN_WRITE", "INVOKED_BY", "ASSOCIATED_USER",
      "WAS_STREAMED",
    };

    List<String> found = new ArrayList<>();
    for (Field f : Fair2rPredicates.class.getDeclaredFields()) {
      if (
        Modifier.isPublic(f.getModifiers()) &&
        Modifier.isStatic(f.getModifiers()) &&
        Modifier.isFinal(f.getModifiers()) &&
        f.getType() == String.class
      ) {
        found.add(f.getName());
      }
    }
    for (String name : expected) {
      assertThat(found).as("missing predicate constant: " + name).contains(name);
    }
  }

  @Test
  void predicateNamesAreNonBlank() throws Exception {
    for (Field f : Fair2rPredicates.class.getDeclaredFields()) {
      if (
        Modifier.isPublic(f.getModifiers()) &&
        Modifier.isStatic(f.getModifiers()) &&
        f.getType() == String.class
      ) {
        Object v = f.get(null);
        assertThat(v).isInstanceOf(String.class);
        assertThat((String) v).isNotBlank();
      }
    }
  }
}
