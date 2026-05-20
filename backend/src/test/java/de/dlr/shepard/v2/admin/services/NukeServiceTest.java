package de.dlr.shepard.v2.admin.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NukeService#confirmPhraseValid(String)}.
 *
 * <p>The nuke() method itself depends on Neo4j session + MongoDB + Postgres;
 * integration coverage is provided by the instance-reset smoke test in
 * the E2E suite. Here we cover the pure guard logic.
 */
class NukeServiceTest {

  private final NukeService service = new NukeService();

  @Test
  void exactPhrase_accepted() {
    assertTrue(service.confirmPhraseValid("yes drop everything"));
  }

  @Test
  void wrongPhrase_rejected() {
    assertFalse(service.confirmPhraseValid("yes drop everything!"));
    assertFalse(service.confirmPhraseValid("YES DROP EVERYTHING"));
    assertFalse(service.confirmPhraseValid("yes drop everything "));
    assertFalse(service.confirmPhraseValid(" yes drop everything"));
    assertFalse(service.confirmPhraseValid("drop everything"));
    assertFalse(service.confirmPhraseValid(""));
  }

  @Test
  void nullPhrase_rejected() {
    assertFalse(service.confirmPhraseValid(null));
  }
}
