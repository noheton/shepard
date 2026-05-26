package de.dlr.shepard.auth.users.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * NEO-AUDIT-010: unit tests for {@link UserDAO#findByEmail(String)}.
 *
 * <p>The V81 uniqueness constraint guarantees at most one :User per email address.
 * These tests verify that {@code findByEmail} returns at most one result and
 * handles edge cases (null, blank, no match) gracefully.
 */
public class UserDAOFindByEmailTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private UserDAO dao;

  // -----------------------------------------------------------------------
  // findByEmail — normal cases
  // -----------------------------------------------------------------------

  @Test
  public void findByEmail_returnsUser_whenExactlyOneMatch() {
    User alice = new User("alice", "Alice", "Demo", "alice@demo.shepard.local");
    when(session.loadAll(eq(User.class), any(Filter.class), anyInt()))
      .thenReturn(List.of(alice));

    Optional<User> result = dao.findByEmail("alice@demo.shepard.local");

    assertTrue(result.isPresent());
    assertEquals("alice", result.get().getUsername());
  }

  @Test
  public void findByEmail_returnsEmpty_whenNoMatch() {
    when(session.loadAll(eq(User.class), any(Filter.class), anyInt()))
      .thenReturn(Collections.emptyList());

    Optional<User> result = dao.findByEmail("unknown@nowhere.example");

    assertFalse(result.isPresent());
  }

  // -----------------------------------------------------------------------
  // findByEmail — at-most-one guarantee
  //
  // Post-V81 constraint, the DB cannot return two results. But if (hypothetically,
  // pre-migration) the session returned two nodes, findByEmail returns only one —
  // confirming the method's "at most one" contract regardless of DB state.
  // -----------------------------------------------------------------------

  @Test
  public void findByEmail_returnsAtMostOne_evenIfSessionReturnsTwoNodes() {
    // Simulates a pre-V81 state where the constraint is absent and two users
    // share the same email. The method must not throw and must return exactly one.
    User admin1 = new User("admin", "Admin", "One", "admin@demo.shepard.local");
    User admin2 = new User("admin-copy", "Admin", "Copy", "admin@demo.shepard.local");
    when(session.loadAll(eq(User.class), any(Filter.class), anyInt()))
      .thenReturn(List.of(admin1, admin2));

    Optional<User> result = dao.findByEmail("admin@demo.shepard.local");

    assertTrue(result.isPresent()); // exactly one returned
  }

  // -----------------------------------------------------------------------
  // findByEmail — null / blank guards (should not hit the DB)
  // -----------------------------------------------------------------------

  @Test
  public void findByEmail_returnsEmpty_forNullEmail() {
    Optional<User> result = dao.findByEmail(null);
    assertFalse(result.isPresent());
  }

  @Test
  public void findByEmail_returnsEmpty_forBlankEmail() {
    Optional<User> result = dao.findByEmail("   ");
    assertFalse(result.isPresent());
  }

  @Test
  public void findByEmail_returnsEmpty_forEmptyString() {
    Optional<User> result = dao.findByEmail("");
    assertFalse(result.isPresent());
  }

  // -----------------------------------------------------------------------
  // findByEmail — null return from session (defensive)
  // -----------------------------------------------------------------------

  @Test
  public void findByEmail_returnsEmpty_whenSessionReturnsNull() {
    Collection<User> nullResult = null;
    when(session.loadAll(eq(User.class), any(Filter.class), anyInt()))
      .thenReturn(nullResult);

    Optional<User> result = dao.findByEmail("someone@example.com");
    assertFalse(result.isPresent());
  }
}
