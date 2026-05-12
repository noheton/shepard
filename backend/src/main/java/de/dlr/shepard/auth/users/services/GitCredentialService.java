package de.dlr.shepard.auth.users.services;

import de.dlr.shepard.auth.users.daos.GitCredentialDAO;
import de.dlr.shepard.auth.users.entities.GitCredential;
import de.dlr.shepard.common.crypto.AesGcmCipher;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Reads encrypted PATs for the G1b tracked-artifact preview path. Operates
 * over the storage layer from G1-cred ({@link GitCredentialDAO}) — never
 * persists, never logs the cleartext PAT.
 *
 * <p>Lookup strategy: match a credential by **exact host** first; if no
 * exact host match, fall back to the first credential the user owns whose
 * host is a suffix-match of the requested host (so a credential for
 * {@code gitlab.com} matches a request for any sub-tenant). v1 keeps the
 * matching strict — a credential's host must match the GitReference's
 * repo URL host exactly for the typical use case.
 */
@RequestScoped
public class GitCredentialService {

  @Inject
  GitCredentialDAO gitCredentialDAO;

  @ConfigProperty(name = "shepard.secrets.encryption-key")
  Optional<String> encryptionKey;

  /**
   * @param username the caller's username (Neo4j @Id on User).
   * @param host     the host of the repo URL (no scheme, no path).
   * @return the decrypted PAT string, or empty when the user has no
   *         matching credential or when the encryption key is absent.
   */
  public Optional<String> findPatForHost(String username, String host) {
    if (username == null || username.isBlank() || host == null || host.isBlank()) {
      return Optional.empty();
    }
    byte[] key = resolveKey();
    if (key == null) {
      Log.debug("Encryption key absent — git credential lookup disabled");
      return Optional.empty();
    }
    List<GitCredential> all = gitCredentialDAO.findAllByUser(username);
    if (all == null || all.isEmpty()) return Optional.empty();
    String hostLc = host.toLowerCase(java.util.Locale.ROOT);
    GitCredential match = null;
    for (GitCredential c : all) {
      if (c.getHost() == null) continue;
      if (c.getHost().toLowerCase(java.util.Locale.ROOT).equals(hostLc)) {
        match = c;
        break;
      }
    }
    if (match == null) return Optional.empty();
    if (match.getEncryptedPat() == null || match.getEncryptedPat().isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(AesGcmCipher.decrypt(match.getEncryptedPat(), key));
    } catch (IllegalArgumentException e) {
      // Tampered ciphertext or wrong key — log and treat as "no credential"
      // so the caller surfaces "no-credential" rather than 500-ing.
      Log.warnf(e, "Failed to decrypt git credential %s for user %s", match.getAppId(), username);
      return Optional.empty();
    }
  }

  private byte[] resolveKey() {
    if (encryptionKey == null || encryptionKey.isEmpty() || encryptionKey.get().isBlank()) return null;
    try {
      return Base64.getDecoder().decode(encryptionKey.get().trim());
    } catch (IllegalArgumentException e) {
      Log.error("shepard.secrets.encryption-key is present but not valid base64 — treating as absent");
      return null;
    }
  }
}
