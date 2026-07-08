package de.dlr.shepard.v2.admin.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.daos.GitCredentialDAO;
import de.dlr.shepard.auth.users.entities.GitCredential;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.v2.admin.users.AdminUserGitCredentialRest.AdminGitCredentialIO;
import de.dlr.shepard.v2.admin.users.AdminUserGitCredentialRest.AdminGitCredentialListItemIO;
import de.dlr.shepard.v2.admin.users.AdminUserGitCredentialRest.AdminGitCredentialResultIO;
import de.dlr.shepard.v2.admin.users.AdminUserGitCredentialRest.AdminGitCredentialRotateIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.ws.rs.core.Response;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.KeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ADM-USR-GIT-BACKEND-1 — unit tests for {@link AdminUserGitCredentialRest}.
 *
 * <p>Covers the new GET list endpoint, the dedicated rotate endpoint, and the
 * backwards-compat behaviour of the existing POST (idempotent create-or-replace
 * by host, lastRotatedAt stamping).
 */
class AdminUserGitCredentialRestTest {

  private static final String USER = "flodemo";
  private static final String CRED_APP_ID = "01900000-0000-7000-8000-000000000001";

  private UserService userService;
  private GitCredentialDAO dao;
  private AdminUserGitCredentialRest rest;
  private String keyB64;

  @BeforeEach
  void setUp() throws Exception {
    KeyGenerator kg = KeyGenerator.getInstance("AES");
    kg.init(256);
    keyB64 = Base64.getEncoder().encodeToString(kg.generateKey().getEncoded());

    userService = mock(UserService.class);
    dao = mock(GitCredentialDAO.class);

    rest = new AdminUserGitCredentialRest();
    rest.userService = userService;
    rest.gitCredentialDAO = dao;
    rest.encryptionKey = Optional.of(keyB64);

    // Default: user exists
    when(userService.getUserOptional(USER)).thenReturn(Optional.of(new User()));
  }

  private GitCredential cred(String appId, String host, String username, Date rotated) {
    GitCredential c = new GitCredential();
    c.setAppId(appId);
    c.setHost(host);
    c.setUsername(username);
    c.setDisplayName(host);
    c.setEncryptedPat("dummy-cipher");
    c.setCreatedAt(new Date(1000L));
    c.setLastRotatedAt(rotated);
    return c;
  }

  // ── GET list ─────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  @Test
  void list_returns200_withEmptyList_whenNoCredentials() {
    when(dao.findAllByUser(USER)).thenReturn(List.of());
    Response r = rest.list(USER);
    assertThat(r.getStatus()).isEqualTo(200);
    PagedResponseIO<AdminGitCredentialListItemIO> body =
        (PagedResponseIO<AdminGitCredentialListItemIO>) r.getEntity();
    assertThat(body.items()).isEmpty();
    assertThat(body.total()).isEqualTo(0);
  }

  @SuppressWarnings("unchecked")
  @Test
  void list_returns200_withItems_andOmitsPat() {
    Date rotated = new Date(2000L);
    GitCredential c = cred(CRED_APP_ID, "gitlab.com", "alice", rotated);
    when(dao.findAllByUser(USER)).thenReturn(List.of(c));

    Response r = rest.list(USER);
    assertThat(r.getStatus()).isEqualTo(200);
    PagedResponseIO<AdminGitCredentialListItemIO> body =
        (PagedResponseIO<AdminGitCredentialListItemIO>) r.getEntity();
    assertThat(body.items()).hasSize(1);
    assertThat(body.total()).isEqualTo(1);
    var item = body.items().get(0);
    assertThat(item.appId()).isEqualTo(CRED_APP_ID);
    assertThat(item.host()).isEqualTo("gitlab.com");
    assertThat(item.username()).isEqualTo("alice");
    assertThat(item.displayName()).isEqualTo("gitlab.com");
    assertThat(item.lastRotatedAt()).isEqualTo(rotated.toInstant());

    // PAT must never appear on the wire — the IO record literally has no
    // pat/encryptedPat field, so the type itself enforces this.
    assertThat(item.getClass().getRecordComponents())
      .extracting(rc -> rc.getName())
      .doesNotContain("pat", "encryptedPat");
  }

  @SuppressWarnings("unchecked")
  @Test
  void list_returns200_withNullLastRotatedAt_forLegacyRows() {
    GitCredential legacy = cred(CRED_APP_ID, "github.com", "bob", null);
    when(dao.findAllByUser(USER)).thenReturn(List.of(legacy));
    Response r = rest.list(USER);
    assertThat(r.getStatus()).isEqualTo(200);
    PagedResponseIO<AdminGitCredentialListItemIO> body =
        (PagedResponseIO<AdminGitCredentialListItemIO>) r.getEntity();
    assertThat(body.items().get(0).lastRotatedAt()).isNull();
  }

  @Test
  void list_returns404_whenUserMissing() {
    when(userService.getUserOptional(USER)).thenReturn(Optional.empty());
    Response r = rest.list(USER);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── POST rotate ──────────────────────────────────────────────────────────

  @Test
  void rotate_returns204_andCallsDaoWithEncryptedPat() {
    GitCredential existing = cred(CRED_APP_ID, "gitlab.com", "alice", new Date(1000L));
    when(dao.findByUserAndAppId(USER, CRED_APP_ID)).thenReturn(existing);
    when(dao.rotateByUserAndAppId(eq(USER), eq(CRED_APP_ID), any())).thenReturn(existing);

    Response r = rest.rotate(USER, CRED_APP_ID, new AdminGitCredentialRotateIO("fresh-PAT-123"));
    assertThat(r.getStatus()).isEqualTo(204);
    // The DAO sees ciphertext, never the cleartext.
    verify(dao).rotateByUserAndAppId(
      eq(USER), eq(CRED_APP_ID),
      org.mockito.ArgumentMatchers.argThat(s -> s != null && !s.contains("fresh-PAT-123"))
    );
  }

  @Test
  void rotate_returns400_whenBodyMissingPat() {
    Response r = rest.rotate(USER, CRED_APP_ID, new AdminGitCredentialRotateIO(""));
    assertThat(r.getStatus()).isEqualTo(400);
    verify(dao, never()).rotateByUserAndAppId(any(), any(), any());
  }

  @Test
  void rotate_returns400_whenBodyNull() {
    Response r = rest.rotate(USER, CRED_APP_ID, null);
    assertThat(r.getStatus()).isEqualTo(400);
  }

  @Test
  void rotate_returns404_whenUserMissing() {
    when(userService.getUserOptional(USER)).thenReturn(Optional.empty());
    Response r = rest.rotate(USER, CRED_APP_ID, new AdminGitCredentialRotateIO("fresh"));
    assertThat(r.getStatus()).isEqualTo(404);
    verify(dao, never()).rotateByUserAndAppId(any(), any(), any());
  }

  @Test
  void rotate_returns404_whenCredAppIdMissing() {
    when(dao.findByUserAndAppId(USER, CRED_APP_ID)).thenReturn(null);
    Response r = rest.rotate(USER, CRED_APP_ID, new AdminGitCredentialRotateIO("fresh"));
    assertThat(r.getStatus()).isEqualTo(404);
    verify(dao, never()).rotateByUserAndAppId(any(), any(), any());
  }

  @Test
  void rotate_returns503_whenEncryptionKeyAbsent() {
    rest.encryptionKey = Optional.empty();
    GitCredential existing = cred(CRED_APP_ID, "gitlab.com", "alice", new Date(1000L));
    when(dao.findByUserAndAppId(USER, CRED_APP_ID)).thenReturn(existing);

    Response r = rest.rotate(USER, CRED_APP_ID, new AdminGitCredentialRotateIO("fresh"));
    assertThat(r.getStatus()).isEqualTo(503);
  }

  // ── POST (backwards-compat) ──────────────────────────────────────────────

  @Test
  void post_create_returns201_andStampsLastRotatedAt() {
    when(dao.findAllByUser(USER)).thenReturn(List.of());
    GitCredential created = cred(CRED_APP_ID, "gitlab.com", "alice", new Date());
    when(dao.createForUser(eq(USER), any(GitCredential.class))).thenReturn(created);

    AdminGitCredentialIO body = new AdminGitCredentialIO("gitlab.com", "alice", "PAT-123", "GitLab");
    Response r = rest.post(USER, body);
    assertThat(r.getStatus()).isEqualTo(201);
    AdminGitCredentialResultIO out = (AdminGitCredentialResultIO) r.getEntity();
    assertThat(out.appId()).isEqualTo(CRED_APP_ID);
    assertThat(out.host()).isEqualTo("gitlab.com");
    // The DAO stamps lastRotatedAt internally on create (see DAO test).
    verify(dao).createForUser(eq(USER), any(GitCredential.class));
  }

  @Test
  void post_replace_returns201_andRoutesThroughRotate() {
    GitCredential existing = cred(CRED_APP_ID, "gitlab.com", "alice", new Date(1000L));
    when(dao.findAllByUser(USER)).thenReturn(List.of(existing));
    when(dao.rotateByUserAndAppId(eq(USER), eq(CRED_APP_ID), any())).thenReturn(existing);

    // Same username — should skip the updateByUserAndAppId hop.
    AdminGitCredentialIO body = new AdminGitCredentialIO("gitlab.com", "alice", "NEW-PAT", "GitLab");
    Response r = rest.post(USER, body);
    assertThat(r.getStatus()).isEqualTo(201);
    verify(dao).rotateByUserAndAppId(eq(USER), eq(CRED_APP_ID), any());
    verify(dao, never()).updateByUserAndAppId(any(), any(), any());
  }

  @Test
  void post_replace_withUsernameChange_alsoUpdatesUsername() {
    GitCredential existing = cred(CRED_APP_ID, "gitlab.com", "alice", new Date(1000L));
    when(dao.findAllByUser(USER)).thenReturn(List.of(existing));
    when(dao.rotateByUserAndAppId(eq(USER), eq(CRED_APP_ID), any())).thenReturn(existing);

    AdminGitCredentialIO body = new AdminGitCredentialIO("gitlab.com", "bob", "NEW-PAT", "GitLab");
    Response r = rest.post(USER, body);
    assertThat(r.getStatus()).isEqualTo(201);
    verify(dao).updateByUserAndAppId(eq(USER), eq(CRED_APP_ID), any(GitCredential.class));
    verify(dao).rotateByUserAndAppId(eq(USER), eq(CRED_APP_ID), any());
  }
}
