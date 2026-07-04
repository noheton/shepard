package de.dlr.shepard.v2.git.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PLUGIN-REF-HANDLER-GIT — unit tests for {@link GitReferenceKindHandler}.
 * Covers {@link GitReferenceKindHandler#kind()} and
 * {@link GitReferenceKindHandler#owns(de.dlr.shepard.context.references.basicreference.entities.BasicReference)}.
 */
class GitReferenceKindHandlerTest {

  GitReferenceKindHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GitReferenceKindHandler();
  }

  @Test
  void kindIsGit() {
    assertEquals("git", handler.kind());
  }

  @Test
  void ownsGitReference() {
    assertTrue(handler.owns(new GitReference()));
  }

  @Test
  void doesNotOwnOtherReference() {
    assertFalse(handler.owns(new URIReference()));
  }
}
