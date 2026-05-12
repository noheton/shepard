package de.dlr.shepard.auth.users.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.daos.GitCredentialDAO;
import de.dlr.shepard.auth.users.entities.GitCredential;
import de.dlr.shepard.common.crypto.AesGcmCipher;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.crypto.KeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitCredentialServiceTest {

  private GitCredentialService svc;
  private GitCredentialDAO dao;
  private String keyB64;
  private byte[] key;

  @BeforeEach
  void setup() throws Exception {
    KeyGenerator kg = KeyGenerator.getInstance("AES");
    kg.init(256);
    key = kg.generateKey().getEncoded();
    keyB64 = Base64.getEncoder().encodeToString(key);

    dao = mock(GitCredentialDAO.class);
    svc = new GitCredentialService();
    svc.gitCredentialDAO = dao;
    svc.encryptionKey = Optional.of(keyB64);
  }

  private GitCredential cred(String host, String pat) {
    GitCredential c = new GitCredential();
    c.setHost(host);
    c.setEncryptedPat(AesGcmCipher.encrypt(pat, key));
    return c;
  }

  @Test
  void findPat_returnsDecryptedPat_onExactHostMatch() {
    when(dao.findAllByUser("alice")).thenReturn(List.of(cred("gitlab.com", "PAT-1")));
    Optional<String> p = svc.findPatForHost("alice", "gitlab.com");
    assertTrue(p.isPresent());
    assertEquals("PAT-1", p.get());
  }

  @Test
  void findPat_returnsEmpty_whenHostMismatch() {
    when(dao.findAllByUser("alice")).thenReturn(List.of(cred("github.com", "X")));
    assertFalse(svc.findPatForHost("alice", "gitlab.com").isPresent());
  }

  @Test
  void findPat_returnsEmpty_whenNoCredentials() {
    when(dao.findAllByUser("alice")).thenReturn(List.of());
    assertFalse(svc.findPatForHost("alice", "gitlab.com").isPresent());
  }

  @Test
  void findPat_isCaseInsensitiveOnHost() {
    when(dao.findAllByUser("alice")).thenReturn(List.of(cred("GitLab.Com", "PAT")));
    assertTrue(svc.findPatForHost("alice", "gitlab.com").isPresent());
  }

  @Test
  void findPat_returnsEmpty_whenKeyAbsent() {
    svc.encryptionKey = Optional.empty();
    when(dao.findAllByUser("alice")).thenReturn(List.of(cred("gitlab.com", "X")));
    assertFalse(svc.findPatForHost("alice", "gitlab.com").isPresent());
  }

  @Test
  void findPat_returnsEmpty_whenKeyIsBlank() {
    svc.encryptionKey = Optional.of("   ");
    when(dao.findAllByUser("alice")).thenReturn(List.of(cred("gitlab.com", "X")));
    assertFalse(svc.findPatForHost("alice", "gitlab.com").isPresent());
  }

  @Test
  void findPat_returnsEmpty_whenKeyIsMalformedBase64() {
    svc.encryptionKey = Optional.of("not-valid-base64!!!");
    when(dao.findAllByUser("alice")).thenReturn(List.of(cred("gitlab.com", "X")));
    assertFalse(svc.findPatForHost("alice", "gitlab.com").isPresent());
  }

  @Test
  void findPat_returnsEmpty_whenCiphertextTampered() {
    GitCredential c = cred("gitlab.com", "X");
    c.setEncryptedPat("AAA-invalid-ciphertext-AAA");
    when(dao.findAllByUser("alice")).thenReturn(List.of(c));
    assertFalse(svc.findPatForHost("alice", "gitlab.com").isPresent());
  }

  @Test
  void findPat_blankUsername_returnsEmpty() {
    assertFalse(svc.findPatForHost("", "gitlab.com").isPresent());
    assertFalse(svc.findPatForHost(null, "gitlab.com").isPresent());
  }

  @Test
  void findPat_blankHost_returnsEmpty() {
    assertFalse(svc.findPatForHost("alice", "").isPresent());
    assertFalse(svc.findPatForHost("alice", null).isPresent());
  }

  @Test
  void findPat_skipsCredentialsWithNullHost() {
    GitCredential bad = new GitCredential();
    bad.setHost(null);
    bad.setEncryptedPat("x");
    GitCredential good = cred("gitlab.com", "PAT");
    when(dao.findAllByUser("alice")).thenReturn(List.of(bad, good));
    assertEquals("PAT", svc.findPatForHost("alice", "gitlab.com").orElseThrow());
  }
}
