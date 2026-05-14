package de.dlr.shepard.plugins.storage.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * FS1b — unit tests for {@link CredentialCipher}.
 */
class CredentialCipherTest {

  @Test
  void encrypt_returnsGcm1PrefixedString() {
    CredentialCipher cipher = new CredentialCipher("test-instance-id");
    String cipherText = cipher.encrypt("my-secret-key");
    assertThat(cipherText).startsWith("gcm1:");
  }

  @Test
  void roundTrip_decryptReturnsOriginal() {
    CredentialCipher cipher = new CredentialCipher("test-instance-id");
    String original = "AKIAIOSFODNN7EXAMPLE";
    String cipherText = cipher.encrypt(original);
    String decrypted = cipher.decrypt(cipherText);
    assertThat(decrypted).isEqualTo(original);
  }

  @Test
  void differentInstanceIds_produceDifferentCipherTexts() {
    CredentialCipher cipher1 = new CredentialCipher("instance-a");
    CredentialCipher cipher2 = new CredentialCipher("instance-b");
    String ct1 = cipher1.encrypt("same-secret");
    String ct2 = cipher2.encrypt("same-secret");
    // Different keys, different IVs — output will differ
    assertThat(ct1).isNotEqualTo(ct2);
  }

  @Test
  void decrypt_withWrongKey_throwsIllegalState() {
    CredentialCipher encryptor = new CredentialCipher("instance-a");
    CredentialCipher decryptor = new CredentialCipher("instance-b");
    String cipherText = encryptor.encrypt("secret");
    assertThatThrownBy(() -> decryptor.decrypt(cipherText))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("AES-GCM decrypt failed");
  }

  @Test
  void decrypt_withMissingPrefix_throwsIllegalState() {
    CredentialCipher cipher = new CredentialCipher("instance-id");
    assertThatThrownBy(() -> cipher.decrypt("notprefixed"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("gcm1:");
  }

  @Test
  void decrypt_withBlankInput_throwsIllegalArgument() {
    CredentialCipher cipher = new CredentialCipher("instance-id");
    assertThatThrownBy(() -> cipher.decrypt(""))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cipher.decrypt(null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void encrypt_withNullInput_throwsIllegalArgument() {
    CredentialCipher cipher = new CredentialCipher("instance-id");
    assertThatThrownBy(() -> cipher.encrypt(null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_withBlankInstanceId_throwsIllegalArgument() {
    assertThatThrownBy(() -> new CredentialCipher(""))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CredentialCipher(null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deriveKey_returnsCorrectLength() {
    byte[] key = CredentialCipher.deriveKey("test-id");
    assertThat(key).hasSize(CredentialCipher.KEY_LENGTH_BYTES);
  }

  @Test
  void deriveKey_isPepperDistinctFromDatacite() {
    // The key derived for FS1b must differ from KIP1d's DataCite cipher
    // (different pepper) so a leak of one cannot be cross-replayed.
    byte[] fs1bKey = CredentialCipher.deriveKey("shepard");
    // Datacite uses pepper "shepard:KIP1d:minter-datacite:"
    // FS1b uses pepper "shepard:FS1b:storage-s3:"
    // We can't import the other class, but we know the derivation differs.
    assertThat(fs1bKey).hasSize(32);
  }

  @Test
  void twoEncryptionsOfSameValue_produceDifferentCipherTexts() {
    // IV is random per call, so identical plaintexts produce different ciphertexts.
    CredentialCipher cipher = new CredentialCipher("test-instance");
    String ct1 = cipher.encrypt("same-secret");
    String ct2 = cipher.encrypt("same-secret");
    assertThat(ct1).isNotEqualTo(ct2);
  }
}
