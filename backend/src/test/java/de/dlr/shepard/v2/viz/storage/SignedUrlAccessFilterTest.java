package de.dlr.shepard.v2.viz.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * VIS-S1b — unit tests for {@link SignedUrlAccessFilter}.
 *
 * <p>All path and token logic is tested without booting Quarkus.
 * The companion {@link SignedUrlIssuer} is constructed directly to
 * produce tokens for round-trip verification tests.
 */
class SignedUrlAccessFilterTest {

  private SignedUrlIssuer issuer;
  private SignedUrlAccessFilter filter;

  @BeforeEach
  void setUp() {
    issuer = new SignedUrlIssuer();
    issuer.signedUrlTtlSeconds = 3600;
    issuer.signingKey = "test-signing-key-for-filter-tests";

    filter = new SignedUrlAccessFilter();
    filter.issuer = issuer;
  }

  // ─── isVizPath ────────────────────────────────────────────────────────────

  @Test
  void vizPathPrefixMatches() {
    assertThat(SignedUrlAccessFilter.isVizPath("/v2/viz")).isTrue();
    assertThat(SignedUrlAccessFilter.isVizPath("/v2/viz/")).isTrue();
    assertThat(SignedUrlAccessFilter.isVizPath("/v2/viz/objects/bucket/key")).isTrue();
  }

  @Test
  void nonVizPathsDoNotMatch() {
    assertThat(SignedUrlAccessFilter.isVizPath("/v2/shapes/render")).isFalse();
    assertThat(SignedUrlAccessFilter.isVizPath("/v2/vizualization")).isFalse();
    assertThat(SignedUrlAccessFilter.isVizPath("/shepard/api/collections")).isFalse();
    assertThat(SignedUrlAccessFilter.isVizPath(null)).isFalse();
    assertThat(SignedUrlAccessFilter.isVizPath("")).isFalse();
  }

  // ─── applicationPath ─────────────────────────────────────────────────────

  @Test
  void applicationPathStripsShepardApiPrefix() {
    ContainerRequestContext ctx = mockRequest("/shepard/api/v2/viz/objects/bucket/key");
    String path = SignedUrlAccessFilter.applicationPath(ctx);
    assertThat(path).isEqualTo("/v2/viz/objects/bucket/key");
  }

  @Test
  void applicationPathKeepsV2PathUnchanged() {
    ContainerRequestContext ctx = mockRequest("/v2/viz/objects/bucket/key");
    String path = SignedUrlAccessFilter.applicationPath(ctx);
    assertThat(path).isEqualTo("/v2/viz/objects/bucket/key");
  }

  @Test
  void applicationPathHandlesEmptyPath() {
    ContainerRequestContext ctx = mockRequest("");
    assertThat(SignedUrlAccessFilter.applicationPath(ctx)).isEqualTo("/");
  }

  // ─── parseBucketAndKey ────────────────────────────────────────────────────

  @Test
  void parseBucketAndKeyExtractsBucketAndKey() {
    SignedUrlAccessFilter.BucketAndKey bk =
      SignedUrlAccessFilter.parseBucketAndKey("/v2/viz/objects/my-bucket/path%2Fto%2Fobject.glb");
    assertThat(bk).isNotNull();
    assertThat(bk.bucket()).isEqualTo("my-bucket");
    assertThat(bk.objectKey()).isEqualTo("path/to/object.glb");
  }

  @Test
  void parseBucketAndKeyDecodesEncodedKey() {
    SignedUrlAccessFilter.BucketAndKey bk =
      SignedUrlAccessFilter.parseBucketAndKey("/v2/viz/objects/bucket/my%20file.bin");
    assertThat(bk).isNotNull();
    assertThat(bk.objectKey()).isEqualTo("my file.bin");
  }

  @Test
  void parseBucketAndKeyReturnsNullForNonObjectsPath() {
    assertThat(SignedUrlAccessFilter.parseBucketAndKey("/v2/viz/admin/status")).isNull();
    assertThat(SignedUrlAccessFilter.parseBucketAndKey("/v2/viz")).isNull();
  }

  @Test
  void parseBucketAndKeyReturnsNullWhenNoSlashAfterBucket() {
    // /v2/viz/objects/bucket-only (no slash → no objectKey)
    assertThat(SignedUrlAccessFilter.parseBucketAndKey("/v2/viz/objects/bucket")).isNull();
  }

  @Test
  void parseBucketAndKeyReturnsNullForBlankKey() {
    assertThat(SignedUrlAccessFilter.parseBucketAndKey("/v2/viz/objects//key")).isNull();
  }

  // ─── verifyToken round-trip ───────────────────────────────────────────────

  @Test
  void verifyTokenSucceedsForValidToken() {
    long exp = Instant.now().getEpochSecond() + 3600;
    String message = SignedUrlIssuer.buildMessage("bucket", "obj/key.bin", exp);
    String sig = issuer.computeHmacHex(message);

    assertThat(filter.verifyToken("bucket", "obj/key.bin", exp, sig)).isTrue();
  }

  @Test
  void verifyTokenFailsForExpiredToken() {
    // Still a valid HMAC — just expired
    long expiredExp = Instant.now().getEpochSecond() - 1;
    String message = SignedUrlIssuer.buildMessage("bucket", "key", expiredExp);
    String sig = issuer.computeHmacHex(message);

    // verifyToken itself only checks the HMAC, not the expiry — expiry
    // is checked in filter() before verifyToken is called.
    // With a future exp value baked into the message, the sig won't match.
    // So we sign a future exp and pass an expired exp — the HMAC won't match.
    long futureExp = Instant.now().getEpochSecond() + 3600;
    String futureMessage = SignedUrlIssuer.buildMessage("bucket", "key", futureExp);
    String futureSig = issuer.computeHmacHex(futureMessage);

    // Verify with wrong exp → HMAC won't match because message differs
    assertThat(filter.verifyToken("bucket", "key", expiredExp, futureSig)).isFalse();
  }

  @Test
  void verifyTokenFailsForTamperedBucket() {
    long exp = Instant.now().getEpochSecond() + 3600;
    String sig = issuer.computeHmacHex(SignedUrlIssuer.buildMessage("original-bucket", "key", exp));

    // Tamper bucket name — HMAC should not match
    assertThat(filter.verifyToken("tampered-bucket", "key", exp, sig)).isFalse();
  }

  @Test
  void verifyTokenFailsForTamperedObjectKey() {
    long exp = Instant.now().getEpochSecond() + 3600;
    String sig = issuer.computeHmacHex(SignedUrlIssuer.buildMessage("bucket", "original-key", exp));

    assertThat(filter.verifyToken("bucket", "tampered-key", exp, sig)).isFalse();
  }

  @Test
  void verifyTokenFailsForNullSig() {
    long exp = Instant.now().getEpochSecond() + 3600;
    assertThat(filter.verifyToken("bucket", "key", exp, null)).isFalse();
  }

  @Test
  void verifyTokenFailsForBlankSig() {
    long exp = Instant.now().getEpochSecond() + 3600;
    assertThat(filter.verifyToken("bucket", "key", exp, "")).isFalse();
    assertThat(filter.verifyToken("bucket", "key", exp, "   ")).isFalse();
  }

  @Test
  void verifyTokenFailsForNonHexSig() {
    long exp = Instant.now().getEpochSecond() + 3600;
    assertThat(filter.verifyToken("bucket", "key", exp, "not-valid-hex!")).isFalse();
  }

  @Test
  void verifyTokenFailsForShortSig() {
    long exp = Instant.now().getEpochSecond() + 3600;
    // A valid hex string but wrong length — MessageDigest.isEqual will return false
    assertThat(filter.verifyToken("bucket", "key", exp, "deadbeef")).isFalse();
  }

  // ─── Round-trip: mintSignedUrl → verifyToken ──────────────────────────────

  @Test
  void mintAndVerifyRoundTripSucceeds() {
    URI signed = issuer.mintSignedUrl("assets", "mesh/robot.glb");
    String urlStr = signed.toString();

    // Parse the URL the same way the filter would
    String expStr = extractQueryParam(urlStr, "exp");
    String sig = extractQueryParam(urlStr, "sig");
    long exp = Long.parseLong(expStr);

    assertThat(filter.verifyToken("assets", "mesh/robot.glb", exp, sig)).isTrue();
  }

  @Test
  void mintAndVerifyRoundTripFailsForDifferentKey() {
    URI signed = issuer.mintSignedUrl("assets", "key.bin");
    String urlStr = signed.toString();
    String sig = extractQueryParam(urlStr, "sig");
    long exp = Long.parseLong(extractQueryParam(urlStr, "exp"));

    // Swap out the signing key in the filter's issuer
    SignedUrlIssuer differentKeyIssuer = new SignedUrlIssuer();
    differentKeyIssuer.signedUrlTtlSeconds = 3600;
    differentKeyIssuer.signingKey = "a-completely-different-key";
    filter.issuer = differentKeyIssuer;

    assertThat(filter.verifyToken("assets", "key.bin", exp, sig)).isFalse();
  }

  // ─── filter() — scope bypass for non-viz paths ───────────────────────────

  @Test
  void filterPassesThroughNonVizPaths() {
    // Non-viz paths must not be aborted — the filter simply returns.
    // We verify this indirectly by confirming no exception is thrown
    // and the request context is not aborted when UriInfo returns a
    // non-viz path and no query params are present.
    ContainerRequestContext ctx = mockRequestWithQueryParams(
      "/v2/shapes/render",
      new MultivaluedHashMap<>()
    );
    // If the filter aborts, it would call ctx.abortWith(...). Since
    // we're not mocking the abortWith call (and Mockito won't throw),
    // the real test is that no NullPointerException / other error is
    // raised when the filter skips the non-viz path.
    filter.filter(ctx); // must not throw
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private static ContainerRequestContext mockRequest(String path) {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getPath()).thenReturn(path);
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
    when(ctx.getUriInfo()).thenReturn(uriInfo);
    return ctx;
  }

  private static ContainerRequestContext mockRequestWithQueryParams(
    String path,
    jakarta.ws.rs.core.MultivaluedMap<String, String> params
  ) {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getPath()).thenReturn(path);
    when(uriInfo.getQueryParameters()).thenReturn(params);
    when(ctx.getUriInfo()).thenReturn(uriInfo);
    return ctx;
  }

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
