package de.dlr.shepard.plugins.minter.datacite.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import org.junit.jupiter.api.Test;

/**
 * KIP1d — wire-shape tests for the admin IO records.
 *
 * <p>Critical invariants enforced here:
 *
 * <ul>
 *   <li>{@link DataciteMinterConfigIO} never serialises
 *       {@code passwordCipher} or {@code passwordHash}.</li>
 *   <li>{@link DataciteMinterConfigPatchIO} fires the read-only
 *       sentinel when {@code passwordHash} / {@code passwordCipher} /
 *       {@code password} are deserialised.</li>
 *   <li>The {@code touched}-flag idiom distinguishes "absent" from
 *       "explicit null" on string fields.</li>
 * </ul>
 */
class DataciteIoTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void configIO_maskedShape_omitsCredentialFields() throws Exception {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    cfg.setEnabled(true);
    cfg.setHandlePrefix("10.5072");
    cfg.setPasswordCipher("gcm1:very-secret-cipher");
    cfg.setPasswordHash("0123456789abcdef".repeat(4));
    cfg.setRepositoryId("DLR");

    DataciteMinterConfigIO io = DataciteMinterConfigIO.from(cfg);
    String json = mapper.writeValueAsString(io);

    assertThat(json).doesNotContain("very-secret-cipher");
    assertThat(json).doesNotContain("passwordCipher");
    assertThat(json).doesNotContain("passwordHash");
    // The fingerprint surfaces but only first 8.
    assertThat(json).contains("\"passwordFingerprint\":\"01234567\"");
    assertThat(json).contains("\"passwordSet\":true");
  }

  @Test
  void configIO_passwordSet_falseWhenNoHash() {
    DataciteMinterConfig cfg = new DataciteMinterConfig();
    DataciteMinterConfigIO io = DataciteMinterConfigIO.from(cfg);
    assertThat(io.passwordSet()).isFalse();
    assertThat(io.passwordFingerprint()).isNull();
  }

  @Test
  void patchIO_touchedFlagsReflectExplicitNulls() throws Exception {
    String json = """
        { "handlePrefix": null, "publisher": "X" }
        """;

    DataciteMinterConfigPatchIO patch = mapper.readValue(json, DataciteMinterConfigPatchIO.class);

    assertThat(patch.isHandlePrefixTouched()).isTrue();
    assertThat(patch.getHandlePrefix()).isNull();
    assertThat(patch.isPublisherTouched()).isTrue();
    assertThat(patch.getPublisher()).isEqualTo("X");
    // unmentioned fields stay untouched
    assertThat(patch.isApiBaseUrlTouched()).isFalse();
    assertThat(patch.isRepositoryIdTouched()).isFalse();
  }

  @Test
  void patchIO_passwordSentinelsFireOnAnyVariant() throws Exception {
    String json = """
        { "passwordHash": "shouldnt-set" }
        """;
    DataciteMinterConfigPatchIO patch = mapper.readValue(json, DataciteMinterConfigPatchIO.class);
    assertThat(patch.isPasswordHashTouched()).isTrue();

    DataciteMinterConfigPatchIO p2 = mapper.readValue(
      "{ \"passwordCipher\": \"x\" }",
      DataciteMinterConfigPatchIO.class
    );
    assertThat(p2.isPasswordCipherTouched()).isTrue();

    DataciteMinterConfigPatchIO p3 = mapper.readValue(
      "{ \"password\": \"x\" }",
      DataciteMinterConfigPatchIO.class
    );
    assertThat(p3.isPasswordCipherTouched()).isTrue();
  }

  @Test
  void credentialIO_carriesPasswordOnly() throws Exception {
    DataciteCredentialIO io = new DataciteCredentialIO("the-pwd");
    String json = mapper.writeValueAsString(io);
    JsonNode tree = mapper.readTree(json);
    assertThat(tree.path("password").asText()).isEqualTo("the-pwd");
    // No accidental fields leak through.
    assertThat(tree.fieldNames()).toIterable().containsExactly("password");
  }

  @Test
  void credentialSetIO_masksPlaintextResponse() throws Exception {
    DataciteCredentialSetIO io = new DataciteCredentialSetIO(true, "01234567");
    String json = mapper.writeValueAsString(io);
    assertThat(json).contains("\"passwordSet\":true");
    assertThat(json).contains("\"fingerprint\":\"01234567\"");
    // The plaintext field is intentionally not in the record.
    assertThat(json).doesNotContain("password\":\"");
  }

  @Test
  void testConnectionIO_serialisesAllFields() throws Exception {
    DataciteTestConnectionIO io = new DataciteTestConnectionIO(true, 200, 42L, "https://api.test.datacite.org", null);
    String json = mapper.writeValueAsString(io);
    JsonNode tree = mapper.readTree(json);
    assertThat(tree.path("reachable").asBoolean()).isTrue();
    assertThat(tree.path("statusCode").asInt()).isEqualTo(200);
    assertThat(tree.path("latencyMs").asLong()).isEqualTo(42L);
    assertThat(tree.path("apiBaseUrl").asText()).isEqualTo("https://api.test.datacite.org");
    // NON_NULL — `detail` is omitted entirely.
    assertThat(tree.has("detail")).isFalse();
  }
}
