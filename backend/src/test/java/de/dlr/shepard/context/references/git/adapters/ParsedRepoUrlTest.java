package de.dlr.shepard.context.references.git.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParsedRepoUrlTest {

  @Test
  void parse_gitlabCom() {
    var p = ParsedRepoUrl.parse("https://gitlab.com/foo/bar");
    assertEquals("gitlab.com", p.host());
    assertEquals("foo/bar", p.projectPath());
  }

  @Test
  void parse_selfHostedGitlab() {
    var p = ParsedRepoUrl.parse("https://gitlab.example.dlr.de/group/sub/proj");
    assertEquals("gitlab.example.dlr.de", p.host());
    assertEquals("group/sub/proj", p.projectPath());
  }

  @Test
  void parse_stripsTrailingDotGit() {
    var p = ParsedRepoUrl.parse("https://gitlab.com/foo/bar.git");
    assertEquals("foo/bar", p.projectPath());
  }

  @Test
  void parse_stripsTrailingSlashes() {
    var p = ParsedRepoUrl.parse("https://gitlab.com/foo/bar/");
    assertEquals("foo/bar", p.projectPath());
  }

  @Test
  void parse_lowercaseHost() {
    var p = ParsedRepoUrl.parse("https://GitLab.Example.DLR.de/foo/bar");
    assertEquals("gitlab.example.dlr.de", p.host());
  }

  @Test
  void parse_blank_throws() {
    var ex = assertThrows(GitAdapterException.class, () -> ParsedRepoUrl.parse(""));
    assertEquals(400, ex.getStatus());
  }

  @Test
  void parse_null_throws() {
    assertThrows(GitAdapterException.class, () -> ParsedRepoUrl.parse(null));
  }

  @Test
  void parse_malformedUri_throws() {
    var ex = assertThrows(GitAdapterException.class, () -> ParsedRepoUrl.parse("not a uri at all !!!"));
    assertEquals(400, ex.getStatus());
  }

  @Test
  void parse_missingHost_throws() {
    var ex = assertThrows(GitAdapterException.class, () -> ParsedRepoUrl.parse("file:///foo/bar"));
    assertEquals(400, ex.getStatus());
    assertTrue(ex.getMessage().contains("host"));
  }

  @Test
  void parse_emptyPath_throws() {
    var ex = assertThrows(GitAdapterException.class, () -> ParsedRepoUrl.parse("https://gitlab.com/"));
    assertEquals(400, ex.getStatus());
  }
}
