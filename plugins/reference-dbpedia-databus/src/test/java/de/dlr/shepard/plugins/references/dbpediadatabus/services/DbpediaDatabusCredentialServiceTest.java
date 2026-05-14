package de.dlr.shepard.plugins.references.dbpediadatabus.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Optional;
import javax.crypto.KeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * REF1c — tests for {@link DbpediaDatabusCredentialService}.
 */
class DbpediaDatabusCredentialServiceTest {

  private String base64Key;

  @BeforeEach
  void setUp() throws Exception {
    KeyGenerator kg = KeyGenerator.getInstance("AES");
    kg.init(256);
    base64Key = Base64.getEncoder().encodeToString(kg.generateKey().getEncoded());
  }

  @Test
  void encryptionAvailable_withKey_true() {
    DbpediaDatabusCredentialService svc = new DbpediaDatabusCredentialService(base64Key);
    assertThat(svc.encryptionAvailable()).isTrue();
  }

  @Test
  void encryptionAvailable_withoutKey_false() {
    DbpediaDatabusCredentialService svc = new DbpediaDatabusCredentialService(null);
    assertThat(svc.encryptionAvailable()).isFalse();
  }

  @Test
  void encryptThenDecrypt_roundTrips() {
    DbpediaDatabusCredentialService svc = new DbpediaDatabusCredentialService(base64Key);
    String cipher = svc.encrypt("my-secret");
    Optional<String> decrypted = svc.decrypt(cipher);
    assertThat(decrypted).hasValue("my-secret");
  }

  @Test
  void encrypt_noKey_throwsIllegalState() {
    DbpediaDatabusCredentialService svc = new DbpediaDatabusCredentialService(null);
    assertThatThrownBy(() -> svc.encrypt("secret"))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void decrypt_nullCipher_returnsEmpty() {
    DbpediaDatabusCredentialService svc = new DbpediaDatabusCredentialService(base64Key);
    assertThat(svc.decrypt(null)).isEmpty();
  }

  @Test
  void decrypt_noKey_returnsEmpty() {
    DbpediaDatabusCredentialService svc = new DbpediaDatabusCredentialService(null);
    assertThat(svc.decrypt("cipher")).isEmpty();
  }

  @Test
  void fingerprint_returnsFirst8HexChars() {
    DbpediaDatabusCredentialService svc = new DbpediaDatabusCredentialService(base64Key);
    String fp = svc.fingerprint("someciphertext");
    assertThat(fp).isNotNull().hasSize(DbpediaDatabusCredentialService.FINGERPRINT_LENGTH);
    assertThat(fp).matches("[0-9a-f]{8}");
  }

  @Test
  void fingerprint_nullCipher_returnsNull() {
    DbpediaDatabusCredentialService svc = new DbpediaDatabusCredentialService(base64Key);
    assertThat(svc.fingerprint(null)).isNull();
  }

  @Test
  void fingerprint_blankCipher_returnsNull() {
    DbpediaDatabusCredentialService svc = new DbpediaDatabusCredentialService(base64Key);
    assertThat(svc.fingerprint("  ")).isNull();
  }
}
