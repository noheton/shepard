package de.dlr.shepard.v2.viz.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * VIS-S1b — unit tests for {@link SignedUrlIssuer}.
 *
 * <p>Tests construct the bean directly, bypassing CDI, following the same
 * pattern as {@link de.dlr.shepard.storage.PresignTtlValidatorTest}.
 */
class SignedUrlIssuerTest {

  private SignedUrlIssuer issuer;

  @BeforeEach
  void setUp() {
    issuer = new SignedUrlIssuer();
    issuer.signedUrlTtlSeconds = 3600;
    issuer.signingKey = "test-signing-key-for-unit-tests";
  }

  // ─── mintSignedUrl happy path ────────────────────────────────────────────

  @Test
  void mintedUrlContainsVizObjectsPath() {
    URI url = issuer.mintSignedUrl("my-bucket", "path/to/object.glb");
    assertThat(url.toString()).contains("/v2/viz/objects/my-bucket/");
  }

  @Test
  void mintedUrlContainsExpQueryParam() {
    long before = Instant.now().getEpochSecond();
    URI url = issuer.mintSignedUrl("bucket", "key");
    long after = Instant.now().getEpochSecond();

    String urlStr = url.toString();
    assertThat(urlStr).contains("exp=");

    // Extract exp value and verify it falls within the expected window
    String expStr = extractQueryParam(urlStr, "exp");
    long exp = Long.parseLong(expStr);
    assertThat(exp).isGreaterThanOrEqualTo(before + issuer.getSignedUrlTtlSeconds());
    assertThat(exp).isLessThanOrEqualTo(after + issuer.getSignedUrlTtlSeconds());
  }

  @Test
  void mintedUrlContainsSigQueryParam() {
    URI url = issuer.mintSignedUrl("bucket", "key");
    String urlStr = url.toString();

    assertThat(urlStr).contains("sig=");
    String sig = extractQueryParam(urlStr, "sig");
    // HMAC-SHA256 hex is 64 characters
    assertThat(sig).hasSize(64);
    assertThat(sig).matches("[0-9a-f]+");
  }

  @Test
  void mintedUrlTtlMatchesConfiguredSeconds() {
    issuer.signedUrlTtlSeconds = 1800;
    long before = Instant.now().getEpochSecond();
    URI url = issuer.mintSignedUrl("bucket", "key");
    long after = Instant.now().getEpochSecond();

    String expStr = extractQueryParam(url.toString(), "exp");
    long exp = Long.parseLong(expStr);
    assertThat(exp).isGreaterThanOrEqualTo(before + 1800);
    assertThat(exp).isLessThanOrEqualTo(after + 1800);
  }

  @Test
  void mintedUrlObjectKeyIsUrlEncoded() {
    URI url = issuer.mintSignedUrl("bucket", "path/to/my file.glb");
    // Space should be encoded as %20 (not +)
    assertThat(url.toString()).contains("%20");
    assertThat(url.toString()).doesNotContain(" ");
  }

  @Test
  void mintedUrlObjectKeySlashesAreEncoded() {
    URI url = issuer.mintSignedUrl("bucket", "nested/path/object.bin");
    // The entire objectKey segment after bucket is encoded
    String urlStr = url.toString();
    // key slashes get encoded via URLEncoder
    assertThat(urlStr).contains("%2F");
  }

  @Test
  void differentKeysProduceDifferentSigs() {
    URI url1 = issuer.mintSignedUrl("bucket", "key1");
    URI url2 = issuer.mintSignedUrl("bucket", "key2");

    String sig1 = extractQueryParam(url1.toString(), "sig");
    String sig2 = extractQueryParam(url2.toString(), "sig");
    assertThat(sig1).isNotEqualTo(sig2);
  }

  @Test
  void differentBucketsProduceDifferentSigs() {
    URI url1 = issuer.mintSignedUrl("bucket1", "key");
    URI url2 = issuer.mintSignedUrl("bucket2", "key");

    String sig1 = extractQueryParam(url1.toString(), "sig");
    String sig2 = extractQueryParam(url2.toString(), "sig");
    assertThat(sig1).isNotEqualTo(sig2);
  }

  // ─── mintSignedUrl input validation ──────────────────────────────────────

  @Test
  void nullBucketThrowsIllegalArgument() {
    assertThatThrownBy(() -> issuer.mintSignedUrl(null, "key"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("bucketName");
  }

  @Test
  void blankBucketThrowsIllegalArgument() {
    assertThatThrownBy(() -> issuer.mintSignedUrl("   ", "key"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("bucketName");
  }

  @Test
  void nullObjectKeyThrowsIllegalArgument() {
    assertThatThrownBy(() -> issuer.mintSignedUrl("bucket", null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("objectKey");
  }

  @Test
  void blankObjectKeyThrowsIllegalArgument() {
    assertThatThrownBy(() -> issuer.mintSignedUrl("bucket", ""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("objectKey");
  }

  // ─── buildMessage ─────────────────────────────────────────────────────────

  @Test
  void buildMessageConcatenatesFieldsWithPipeSeparator() {
    String msg = SignedUrlIssuer.buildMessage("my-bucket", "my/key", 1234567890L);
    assertThat(msg).isEqualTo("my-bucket|my/key|1234567890");
  }

  @Test
  void buildMessageDifferentBucketsDifferentMessages() {
    String m1 = SignedUrlIssuer.buildMessage("bucket-a", "key", 100L);
    String m2 = SignedUrlIssuer.buildMessage("bucket-b", "key", 100L);
    assertThat(m1).isNotEqualTo(m2);
  }

  @Test
  void buildMessageDifferentExpsDifferentMessages() {
    String m1 = SignedUrlIssuer.buildMessage("bucket", "key", 100L);
    String m2 = SignedUrlIssuer.buildMessage("bucket", "key", 200L);
    assertThat(m1).isNotEqualTo(m2);
  }

  // ─── computeHmacHex ──────────────────────────────────────────────────────

  @Test
  void computeHmacHexProduces64CharLowercaseHex() {
    String hex = issuer.computeHmacHex("test-message");
    assertThat(hex).hasSize(64);
    assertThat(hex).matches("[0-9a-f]+");
  }

  @Test
  void computeHmacHexIsDeterministic() {
    String h1 = issuer.computeHmacHex("same-message");
    String h2 = issuer.computeHmacHex("same-message");
    assertThat(h1).isEqualTo(h2);
  }

  @Test
  void computeHmacHexDifferentMessagesProduceDifferentDigests() {
    String h1 = issuer.computeHmacHex("message-a");
    String h2 = issuer.computeHmacHex("message-b");
    assertThat(h1).isNotEqualTo(h2);
  }

  @Test
  void computeHmacBytesMatchesHexDecoded() {
    String msg = "test-message";
    String hex = issuer.computeHmacHex(msg);
    byte[] bytes = issuer.computeHmacBytes(msg);
    assertThat(bytes).hasSize(32); // HMAC-SHA256 = 32 bytes
    assertThat(java.util.HexFormat.of().formatHex(bytes)).isEqualTo(hex);
  }

  // ─── constantTimeEq ──────────────────────────────────────────────────────

  @Test
  void constantTimeEqReturnsTrueForEqualArrays() {
    byte[] a = {1, 2, 3, 4};
    byte[] b = {1, 2, 3, 4};
    assertThat(SignedUrlIssuer.constantTimeEq(a, b)).isTrue();
  }

  @Test
  void constantTimeEqReturnsFalseForDifferentArrays() {
    byte[] a = {1, 2, 3, 4};
    byte[] b = {1, 2, 3, 5};
    assertThat(SignedUrlIssuer.constantTimeEq(a, b)).isFalse();
  }

  @Test
  void constantTimeEqReturnsFalseForDifferentLengths() {
    byte[] a = {1, 2, 3};
    byte[] b = {1, 2, 3, 4};
    assertThat(SignedUrlIssuer.constantTimeEq(a, b)).isFalse();
  }

  @Test
  void constantTimeEqReturnsTrueForEmptyArrays() {
    assertThat(SignedUrlIssuer.constantTimeEq(new byte[0], new byte[0])).isTrue();
  }

  // ─── getTtl ───────────────────────────────────────────────────────────────

  @Test
  void getTtlReturnsConfiguredSeconds() {
    issuer.signedUrlTtlSeconds = 7200;
    assertThat(issuer.getSignedUrlTtlSeconds()).isEqualTo(7200L);
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  /** Extract a query parameter value from a URL string. */
  private static String extractQueryParam(String urlStr, String name) {
    String query = urlStr.substring(urlStr.indexOf('?') + 1);
    for (String part : query.split("&")) {
      if (part.startsWith(name + "=")) {
        return part.substring(name.length() + 1);
      }
    }
    throw new AssertionError("Query param '" + name + "' not found in: " + urlStr);
  }
}
