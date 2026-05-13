package de.dlr.shepard.plugins.minter.datacite.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * KIP1d — unit tests for the AES-GCM cipher used to encrypt the
 * DataCite Member password at rest.
 *
 * <p>The cipher is operator-managed-secret level — not a KMS — but
 * the encrypt/decrypt round-trip needs to be solid, and the
 * tamper-detection (instance-id change, truncated cipher text) needs
 * to fail loudly rather than return garbage.
 */
class CredentialCipherTest {

  // ─── round-trip ────────────────────────────────────────────────────

  @Test
  void encrypt_then_decrypt_returnsOriginalPlaintext() {
    CredentialCipher cipher = new CredentialCipher("shepard-prod");

    String original = "s3cret-datacite-password!";
    String encrypted = cipher.encrypt(original);
    String roundTrip = cipher.decrypt(encrypted);

    assertThat(roundTrip).isEqualTo(original);
  }

  @Test
  void encrypt_includesGcm1Prefix() {
    CredentialCipher cipher = new CredentialCipher("any-instance");

    String encrypted = cipher.encrypt("a");

    assertThat(encrypted).startsWith("gcm1:");
  }

  @Test
  void encrypt_producesDifferentCipherTextOnRepeatedCalls() {
    // AES-GCM is randomised per-IV; the same plaintext should never
    // serialise twice. (This is also the test that catches a future
    // bug introducing a static IV.)
    CredentialCipher cipher = new CredentialCipher("inst-1");

    String first = cipher.encrypt("same-input");
    String second = cipher.encrypt("same-input");

    assertThat(first).isNotEqualTo(second);
  }

  // ─── instance-id binding ───────────────────────────────────────────

  @Test
  void decrypt_failsLoudly_whenInstanceIdDiffers() {
    CredentialCipher original = new CredentialCipher("instance-a");
    String encrypted = original.encrypt("the-password");

    CredentialCipher impostor = new CredentialCipher("instance-b");

    assertThatThrownBy(() -> impostor.decrypt(encrypted))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("AES-GCM decrypt failed");
  }

  // ─── tamper detection ──────────────────────────────────────────────

  @Test
  void decrypt_failsLoudly_whenCipherTextMissingPrefix() {
    CredentialCipher cipher = new CredentialCipher("instance");

    assertThatThrownBy(() -> cipher.decrypt("just-some-base64-no-prefix"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("unrecognised cipher format");
  }

  @Test
  void decrypt_failsLoudly_whenCipherTextTruncated() {
    CredentialCipher cipher = new CredentialCipher("inst");

    assertThatThrownBy(() -> cipher.decrypt("gcm1:AAAA"))
      .isInstanceOf(IllegalStateException.class);
  }

  // ─── argument validation ───────────────────────────────────────────

  @Test
  void constructor_rejectsBlankInstanceId() {
    assertThatThrownBy(() -> new CredentialCipher(""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("instanceId");
    assertThatThrownBy(() -> new CredentialCipher("   "))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CredentialCipher(null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void encrypt_rejectsNullPlaintext() {
    CredentialCipher cipher = new CredentialCipher("x");
    assertThatThrownBy(() -> cipher.encrypt(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("plaintext");
  }

  @Test
  void decrypt_rejectsNullOrBlankCipherText() {
    CredentialCipher cipher = new CredentialCipher("x");
    assertThatThrownBy(() -> cipher.decrypt(null))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cipher.decrypt(""))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cipher.decrypt("   "))
      .isInstanceOf(IllegalArgumentException.class);
  }

  // ─── key derivation ────────────────────────────────────────────────

  @Test
  void deriveKey_isDeterministicForSameInstanceId() {
    byte[] keyA = CredentialCipher.deriveKey("same-instance");
    byte[] keyB = CredentialCipher.deriveKey("same-instance");

    assertThat(keyA).isEqualTo(keyB);
    assertThat(keyA).hasSize(CredentialCipher.KEY_LENGTH_BYTES);
  }

  @Test
  void deriveKey_differsAcrossInstanceIds() {
    byte[] keyA = CredentialCipher.deriveKey("instance-prod");
    byte[] keyB = CredentialCipher.deriveKey("instance-stage");

    assertThat(keyA).isNotEqualTo(keyB);
  }

  // ─── unicode + edge cases ──────────────────────────────────────────

  @Test
  void roundTrip_handlesUnicodeAndLongPasswords() {
    CredentialCipher cipher = new CredentialCipher("any");

    String unicode = "pâsswörd-üñîcödé-ßØ123-中文-🔑";
    assertThat(cipher.decrypt(cipher.encrypt(unicode))).isEqualTo(unicode);

    String longPwd = "x".repeat(2048);
    assertThat(cipher.decrypt(cipher.encrypt(longPwd))).isEqualTo(longPwd);
  }
}
