package de.dlr.shepard.auth.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.apikey.services.ApiKeyService;
import de.dlr.shepard.common.util.PKIHelper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiKeyAuthServiceTest {

  private static final String PUBLIC_B64 =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiAxFyffvM0oiga3h2E7XpHtJvu1vTodrn9Y426FOv80YJcMwPkaI5tXY5hnLjgOwsVNSBv9wAhLL4bUfP+TVhdg4dijD2H/3FamheQaPmduimytzQjlHIIfFuZidH12ZyUrOWfDxiHiRFQ3Dd8dlS7MbIsWt/qBIg16ZZazTJTiaSyP/qH305x9iRjrtGRmvE2VMOdc5EhujFMJnQWWgwOnv2C9U9KIchkPCz+TAL4kKJ79BUi4b0+jxL5Cbgyt0bMo27Zx0zQjU7f0ynFIllqZ6new3Q8HYbr4AIkca4pMjfKWrTHkrQBL2cEXHLIHt86C17goKteToqDjphkwImwIDAQAB";

  private static final String PRIVATE_B64 =
    "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCIDEXJ9+8zSiKBreHYTteke0m+7W9Oh2uf1jjboU6/zRglwzA+Rojm1djmGcuOA7CxU1IG/3ACEsvhtR8/5NWF2Dh2KMPYf/cVqaF5Bo+Z26KbK3NCOUcgh8W5mJ0fXZnJSs5Z8PGIeJEVDcN3x2VLsxsixa3+oEiDXpllrNMlOJpLI/+offTnH2JGOu0ZGa8TZUw51zkSG6MUwmdBZaDA6e/YL1T0ohyGQ8LP5MAviQonv0FSLhvT6PEvkJuDK3RsyjbtnHTNCNTt/TKcUiWWpnqd7DdDwdhuvgAiRxrikyN8patMeStAEvZwRccsge3zoLXuCgq15OioOOmGTAibAgMBAAECggEABBqirFIPZDOzUMgnDPhr5ulVMy5EclEBfSPgOTfngT+1n8YAmZBVJumCjoZuro0L8n159v4TqexZPCjTlYDYtB3urhnStqA9muiwF0+MW27Vu+qWooPJ0oBmBZBGBSE0t27LRMlQ7/X7InB02hMoyhzQD7943TqGlXfwFrIc+H1uXN8MrB4boRX71/yEPT8hv8nWB0FLcgfwtl1l+81otJFveMO/RLStHUH3Auomb/Hh4u96H6S6lUZ8TJ6+8jh2LXmg/RpsqHIWhDbZaNQJE1YdySe3bijov3s/PISaE8pRec6l6KaYkUuFUa6RoGP1RnopsFuN+EeLRMXTRtxgEQKBgQDDB1YRjE7YAYUqeuohhMgx9Ms39zsJGrs6KHE4uWtJFR/Jo3Kq093ykGA+IO+DK/IhBXGzy53SIQ9J7WEONpMmaahY6/Bkhn2nRI9biNaPCovHeO+nIpwtIdTUQLg/d+om+jC2My0YLGk71A5TRkIBPBE9NirbITxibo6jwWWOwwKBgQCylI1fx6f1gdEAP0qM7/LVLrZU3Qx+Q9rPcGG2FI1gWYu69o3JBGpSXqKcAc9hxtFVBaOGpaj9sB8+fPfMXWAvM7c808eL0zOmDC6RlQs0N4XmpV/vUeurgkLQfgB4sfUXbVWHQNsAkvB64BVbbmWFEcHzaBMytb2whvU9hcExSQKBgQCDuSjAoWt/KUev8WTBTtWIKDY5jpopBA0AsuAF1/ZGXiYiImsIRiDZ+/mE/OnIRp46/1pUfWoSypFw9Qtgdivc/e/eXzz2KIAlwYCx6jJAWnceOuhiklW5heghk7Td6TgVK1ZLOTVz5ksNRaSHSiS6gL+EAFnhtwj50oI0yCK30QKBgF7k028HADhUYEQaXbogs1AW/2p+/+mEkxxR4opHx4xgaQDTjSo5P2o/wXbW+2VAqfHdCjU9iFwuH5wr+d1N7ROIDqGzA8FIXJSquoA/y/FWY7/ZNu5MAMhlcq2plwSLw+pL/fveOcHHUyRoONEaC7Y3ZnG6ZyE2M/M+88hab/uJAoGARSSJgG3rRz8hcfQEfopo3rzdeAMY0ws+fXlHp6u51PP+238rB0Y+/b/NeHzwwuqeIxqVTcbd5E8Va7KESPuuzfIQtKbGuVwFpZzWHmROt312AoxeSwRDpOQibpfBAF59D40+SCl6N64whiVoEgJvOQGYB6BIcunIhSpLSD2YId4=";

  private static PrivateKey privateKey;
  private static PublicKey publicKey;

  private PKIHelper pkiHelper;
  private ApiKeyService apiKeyService;
  private ApiKeyLastSeenCache apiKeyLastSeenCache;
  private ApiKeyAuthService svc;

  @BeforeAll
  static void loadKeys() throws NoSuchAlgorithmException, InvalidKeySpecException {
    var kf = KeyFactory.getInstance("RSA");
    privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(PRIVATE_B64)));
    publicKey = kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_B64)));
  }

  @BeforeEach
  void setup() {
    pkiHelper = mock(PKIHelper.class);
    when(pkiHelper.getPublicKey()).thenReturn(publicKey);
    apiKeyService = mock(ApiKeyService.class);
    apiKeyLastSeenCache = mock(ApiKeyLastSeenCache.class);

    svc = new ApiKeyAuthService();
    svc.pkiHelper = pkiHelper;
    svc.apiKeyService = apiKeyService;
    svc.apiKeyLastSeenCache = apiKeyLastSeenCache;
  }

  @Test
  void looksLikeJws_acceptsThreePartTokens() {
    assertTrue(ApiKeyAuthService.looksLikeJws("a.b.c"));
    assertFalse(ApiKeyAuthService.looksLikeJws(""));
    assertFalse(ApiKeyAuthService.looksLikeJws(null));
    assertFalse(ApiKeyAuthService.looksLikeJws("a.b"));
    assertFalse(ApiKeyAuthService.looksLikeJws("a.b.c.d"));
  }

  @Test
  void parseApiKey_validJwsAndDbHit_returnsPrincipal() {
    UUID uid = UUID.randomUUID();
    String jws = signedJws(uid, "alice", Set.of("users"));

    ApiKey stored = new ApiKey(uid);
    stored.setJws(jws);
    stored.setRoles(Set.of("users"));
    stored.setBelongsTo(new de.dlr.shepard.auth.users.entities.User("alice"));
    when(apiKeyService.getApiKey(uid)).thenReturn(stored);
    when(apiKeyLastSeenCache.isKeyCached(uid.toString())).thenReturn(false);

    JWTPrincipal p = svc.parseApiKey(jws);

    assertNotNull(p);
    org.junit.jupiter.api.Assertions.assertEquals("alice", p.getUsername());
  }

  @Test
  void parseApiKey_cachedKeySkipsDbCheck() {
    UUID uid = UUID.randomUUID();
    String jws = signedJws(uid, "alice", Set.of());
    when(apiKeyLastSeenCache.isKeyCached(uid.toString())).thenReturn(true);

    JWTPrincipal p = svc.parseApiKey(jws);

    assertNotNull(p);
    org.mockito.Mockito.verify(apiKeyService, org.mockito.Mockito.never()).getApiKey(org.mockito.ArgumentMatchers.any(UUID.class));
  }

  @Test
  void parseApiKey_expiredThrows() {
    UUID uid = UUID.randomUUID();
    Date past = DateUtils.addMinutes(new Date(), -5);
    String jws = Jwts.builder()
      .setSubject("alice")
      .setExpiration(past)
      .setIssuedAt(DateUtils.addMinutes(new Date(), -10))
      .setId(uid.toString())
      .signWith(privateKey)
      .compact();

    assertThrows(ExpiredJwtException.class, () -> svc.parseApiKey(jws));
  }

  @Test
  void parseApiKey_dbMissReturnsNull() {
    UUID uid = UUID.randomUUID();
    String jws = signedJws(uid, "alice", Set.of());
    when(apiKeyLastSeenCache.isKeyCached(uid.toString())).thenReturn(false);
    when(apiKeyService.getApiKey(uid)).thenReturn(null);

    assertNull(svc.parseApiKey(jws));
  }

  @Test
  void parseApiKey_storedJwsMismatchReturnsNull() {
    UUID uid = UUID.randomUUID();
    String jws = signedJws(uid, "alice", Set.of());

    ApiKey stored = new ApiKey(uid);
    stored.setJws("something.else.entirely");
    when(apiKeyService.getApiKey(uid)).thenReturn(stored);

    assertNull(svc.parseApiKey(jws));
  }

  @Test
  void parseApiKey_roleMismatchReturnsNull() {
    UUID uid = UUID.randomUUID();
    String jws = signedJws(uid, "alice", Set.of("admin"));

    ApiKey stored = new ApiKey(uid);
    stored.setJws(jws);
    stored.setRoles(Set.of("users")); // differs from JWT claim
    when(apiKeyService.getApiKey(uid)).thenReturn(stored);

    assertNull(svc.parseApiKey(jws));
  }

  @Test
  void parseApiKey_invalidSignatureReturnsNull() {
    assertNull(svc.parseApiKey("not.a.jwt"));
  }

  private static String signedJws(UUID uid, String subject, Set<String> roles) {
    return Jwts.builder()
      .setSubject(subject)
      .setNotBefore(new Date())
      .setIssuedAt(new Date())
      .setId(uid.toString())
      .claim("roles", roles)
      .signWith(privateKey)
      .compact();
  }
}
