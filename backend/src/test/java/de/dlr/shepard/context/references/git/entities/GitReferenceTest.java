package de.dlr.shepard.context.references.git.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GitReferenceTest {

  @Test
  void modeAConvenienceConstructorPopulatesLooseLinkFields() {
    var gr = new GitReference("https://gitlab.dlr.de/group/repo", "main", "src/foo.py");
    assertEquals("https://gitlab.dlr.de/group/repo", gr.getRepoUrl());
    assertEquals("main", gr.getRef());
    assertEquals("src/foo.py", gr.getPath());
  }

  @Test
  void modeBcFieldsDefaultToNull() {
    var gr = new GitReference("https://gitlab.dlr.de/group/repo", null, null);
    assertNull(gr.getSha(), "mode-(a) GitReference must not carry a pinned sha");
    assertNull(gr.getResolvedSha(), "mode-(a) GitReference must not carry a resolved sha");
    assertNull(gr.getResolvedAtMillis(), "mode-(a) GitReference must not carry a resolved timestamp");
  }

  @Test
  void emptyConstructorYieldsAllNullFields() {
    var gr = new GitReference();
    assertNull(gr.getRepoUrl());
    assertNull(gr.getRef());
    assertNull(gr.getPath());
    assertNull(gr.getSha());
    assertNull(gr.getResolvedSha());
    assertNull(gr.getResolvedAtMillis());
  }
}
