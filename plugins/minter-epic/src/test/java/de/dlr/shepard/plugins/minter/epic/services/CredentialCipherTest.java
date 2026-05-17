package de.dlr.shepard.plugins.minter.epic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * KIP1c — unit tests for the AES-GCM cipher used to encrypt the
 * ePIC credential at rest. Mirrors the KIP1d CredentialCipherTest
 * but with the KIP1c pepper.
 */
class CredentialCipherTest {

  // ─── round-trip ────────────────────────────────────────────────────

  @Test
  void encrypt_then_decrypt_returnsOriginalPlaintext() {
    CredentialCipher cipher = new CredentialCipher("shepard-prod");

    String original = "user:s3cret-epic-password!";
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
    CredentialCipher cipher = new CredentialCipher("inst-1");

    String first = cipher.encrypt("same-input");
    String second = cipher.encrypt("same-input");

    assertThat(first).isNotEqualTo(second);
  }

  // ─── instance-id binding ───────────────────────────────────────────

  @Test
  void decrypt_failsLoudly_whenInstanceIdDiffers() {
    CredentialCipher original = new CredentialCipher("instance-a");
    String encrypted = original.encrypt("the-credential");

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

  // ─── pepper isolation ─────────────────────────────────────────────
  // The KIP1c epic cipher uses a different pepper than KIP1d datacite,
  // so a cipher text produced by one cannot be decrypted by the other's key.
  // This is verified indirectly: the deriveKey output for the same instanceId
  // must differ between the two cipher implementations (different peppers).

  @Test
  void deriveKey_epicPepperDiffersFromDatacitePepper() {
    // The KIP1d datacite cipher uses "shepard:KIP1d:datacite:" as pepper.
    // The KIP1c epic cipher uses "shepard:KIP1c:epic:" as pepper.
    // Verify the resulting keys are different for the same instanceId.
    byte[] epicKey = CredentialCipher.deriveKey("shared-instance");

    // Simulate what datacite's deriveKey would produce using the same logic
    // but with its pepper — done manually here to avoid importing the other module.
    java.security.MessageDigest md;
    try {
      md = java.security.MessageDigest.getInstance("SHA-256");
      md.update("shepard:KIP1d:datacite:".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      md.update("shared-instance".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      byte[] dataciteKey = new byte[32];
      System.arraycopy(md.digest(), 0, dataciteKey, 0, 32);
      assertThat(epicKey).isNotEqualTo(dataciteKey);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // ─── unicode + edge cases ──────────────────────────────────────────

  @Test
  void roundTrip_handlesUnicodeAndLongCredentials() {
    CredentialCipher cipher = new CredentialCipher("any");

    String unicode = "user:pâsswörd-üñîcödé-ßØ123-中文-🔑";
    assertThat(cipher.decrypt(cipher.encrypt(unicode))).isEqualTo(unicode);

    String longCred = "user:" + "x".repeat(2043);
    assertThat(cipher.decrypt(cipher.encrypt(longCred))).isEqualTo(longCred);
  }
}
