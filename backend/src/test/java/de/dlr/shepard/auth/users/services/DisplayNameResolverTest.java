package de.dlr.shepard.auth.users.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import org.junit.jupiter.api.Test;

class DisplayNameResolverTest {

  @Test
  void overrideWins() {
    var u = new User("alice", "Alice", "Anderson", "alice@example.org");
    u.setDisplayName("Dr. A");
    assertEquals("Dr. A", DisplayNameResolver.effectiveDisplayName(u));
  }

  @Test
  void overrideIsTrimmed() {
    var u = new User("alice", "Alice", "Anderson", "alice@example.org");
    u.setDisplayName("   Dr. A   ");
    assertEquals("Dr. A", DisplayNameResolver.effectiveDisplayName(u));
  }

  @Test
  void blankOverrideFallsBackToFullName() {
    var u = new User("alice", "Alice", "Anderson", "alice@example.org");
    u.setDisplayName("   ");
    assertEquals("Alice Anderson", DisplayNameResolver.effectiveDisplayName(u));
  }

  @Test
  void firstAndLastNameComposeWhenNoOverride() {
    var u = new User("alice", "Alice", "Anderson", "alice@example.org");
    assertEquals("Alice Anderson", DisplayNameResolver.effectiveDisplayName(u));
  }

  @Test
  void firstNameOnlyIsUsed() {
    var u = new User("alice", "Alice", "", "alice@example.org");
    assertEquals("Alice", DisplayNameResolver.effectiveDisplayName(u));
  }

  @Test
  void lastNameOnlyIsUsed() {
    var u = new User("alice", "", "Anderson", "alice@example.org");
    assertEquals("Anderson", DisplayNameResolver.effectiveDisplayName(u));
  }

  @Test
  void usernameFallbackWhenNoNameSet() {
    var u = new User("alice", "", "", "");
    assertEquals("alice", DisplayNameResolver.effectiveDisplayName(u));
  }

  @Test
  void crypticKeycloakUsernameIsRedacted() {
    // 36-char UUID — the redaction trims to first 8 + ellipsis.
    var u = new User("c8d4e2a1-f3b6-4c7e-9d2f-1a3b5c7d9e0f", "", "", "");
    assertEquals("c8d4e2a1…", DisplayNameResolver.effectiveDisplayName(u));
  }

  @Test
  void trailingSegmentExtractedFromColonPrefix() {
    // Some IdPs put a realm prefix on the subject: realm:uuid.
    var u = new User("realm:c8d4e2a1-f3b6-4c7e-9d2f-1a3b5c7d9e0f", "", "", "");
    assertEquals("c8d4e2a1…", DisplayNameResolver.effectiveDisplayName(u));
  }

  @Test
  void trailingSegmentExtractedFromSlashPrefix() {
    var u = new User("subject/c8d4e2a1-f3b6-4c7e-9d2f-1a3b5c7d9e0f", "", "", "");
    assertEquals("c8d4e2a1…", DisplayNameResolver.effectiveDisplayName(u));
  }

  @Test
  void shortNonUuidUsernameReturnedAsIs() {
    var u = new User("alice42", "", "", "");
    assertEquals("alice42", DisplayNameResolver.effectiveDisplayName(u));
  }

  @Test
  void nullUserReturnsAnonymous() {
    assertEquals("(anonymous)", DisplayNameResolver.effectiveDisplayName(null));
  }

  @Test
  void redactBlankUsernameReturnsAnonymous() {
    assertEquals("(anonymous)", DisplayNameResolver.redactUsername(""));
    assertEquals("(anonymous)", DisplayNameResolver.redactUsername(null));
  }
}
