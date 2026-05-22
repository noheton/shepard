package de.dlr.shepard.auth.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.role.daos.RoleDAO;
import de.dlr.shepard.auth.security.RolesList;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JwtTokenAuthServiceTest {

  private static PrivateKey privateKey;
  private static PublicKey publicKey;

  private static final String PUBLIC_B64 =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiAxFyffvM0oiga3h2E7XpHtJvu1vTodrn9Y426FOv80YJcMwPkaI5tXY5hnLjgOwsVNSBv9wAhLL4bUfP+TVhdg4dijD2H/3FamheQaPmduimytzQjlHIIfFuZidH12ZyUrOWfDxiHiRFQ3Dd8dlS7MbIsWt/qBIg16ZZazTJTiaSyP/qH305x9iRjrtGRmvE2VMOdc5EhujFMJnQWWgwOnv2C9U9KIchkPCz+TAL4kKJ79BUi4b0+jxL5Cbgyt0bMo27Zx0zQjU7f0ynFIllqZ6new3Q8HYbr4AIkca4pMjfKWrTHkrQBL2cEXHLIHt86C17goKteToqDjphkwImwIDAQAB";

  private static final String PRIVATE_B64 =
    "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCIDEXJ9+8zSiKBreHYTteke0m+7W9Oh2uf1jjboU6/zRglwzA+Rojm1djmGcuOA7CxU1IG/3ACEsvhtR8/5NWF2Dh2KMPYf/cVqaF5Bo+Z26KbK3NCOUcgh8W5mJ0fXZnJSs5Z8PGIeJEVDcN3x2VLsxsixa3+oEiDXpllrNMlOJpLI/+offTnH2JGOu0ZGa8TZUw51zkSG6MUwmdBZaDA6e/YL1T0ohyGQ8LP5MAviQonv0FSLhvT6PEvkJuDK3RsyjbtnHTNCNTt/TKcUiWWpnqd7DdDwdhuvgAiRxrikyN8patMeStAEvZwRccsge3zoLXuCgq15OioOOmGTAibAgMBAAECggEABBqirFIPZDOzUMgnDPhr5ulVMy5EclEBfSPgOTfngT+1n8YAmZBVJumCjoZuro0L8n159v4TqexZPCjTlYDYtB3urhnStqA9muiwF0+MW27Vu+qWooPJ0oBmBZBGBSE0t27LRMlQ7/X7InB02hMoyhzQD7943TqGlXfwFrIc+H1uXN8MrB4boRX71/yEPT8hv8nWB0FLcgfwtl1l+81otJFveMO/RLStHUH3Auomb/Hh4u96H6S6lUZ8TJ6+8jh2LXmg/RpsqHIWhDbZaNQJE1YdySe3bijov3s/PISaE8pRec6l6KaYkUuFUa6RoGP1RnopsFuN+EeLRMXTRtxgEQKBgQDDB1YRjE7YAYUqeuohhMgx9Ms39zsJGrs6KHE4uWtJFR/Jo3Kq093ykGA+IO+DK/IhBXGzy53SIQ9J7WEONpMmaahY6/Bkhn2nRI9biNaPCovHeO+nIpwtIdTUQLg/d+om+jC2My0YLGk71A5TRkIBPBE9NirbITxibo6jwWWOwwKBgQCylI1fx6f1gdEAP0qM7/LVLrZU3Qx+Q9rPcGG2FI1gWYu69o3JBGpSXqKcAc9hxtFVBaOGpaj9sB8+fPfMXWAvM7c808eL0zOmDC6RlQs0N4XmpV/vUeurgkLQfgB4sfUXbVWHQNsAkvB64BVbbmWFEcHzaBMytb2whvU9hcExSQKBgQCDuSjAoWt/KUev8WTBTtWIKDY5jpopBA0AsuAF1/ZGXiYiImsIRiDZ+/mE/OnIRp46/1pUfWoSypFw9Qtgdivc/e/eXzz2KIAlwYCx6jJAWnceOuhiklW5heghk7Td6TgVK1ZLOTVz5ksNRaSHSiS6gL+EAFnhtwj50oI0yCK30QKBgF7k028HADhUYEQaXbogs1AW/2p+/+mEkxxR4opHx4xgaQDTjSo5P2o/wXbW+2VAqfHdCjU9iFwuH5wr+d1N7ROIDqGzA8FIXJSquoA/y/FWY7/ZNu5MAMhlcq2plwSLw+pL/fveOcHHUyRoONEaC7Y3ZnG6ZyE2M/M+88hab/uJAoGARSSJgG3rRz8hcfQEfopo3rzdeAMY0ws+fXlHp6u51PP+238rB0Y+/b/NeHzwwuqeIxqVTcbd5E8Va7KESPuuzfIQtKbGuVwFpZzWHmROt312AoxeSwRDpOQibpfBAF59D40+SCl6N64whiVoEgJvOQGYB6BIcunIhSpLSD2YId4=";

  @BeforeAll
  static void loadKeys() throws NoSuchAlgorithmException, InvalidKeySpecException {
    var kf = KeyFactory.getInstance("RSA");
    privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(PRIVATE_B64)));
    publicKey = kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_B64)));
  }

  private static JwtTokenAuthService service(String requiredRole, String claimPath, String instanceAdminClaim, RoleDAO roleDAO) {
    return new JwtTokenAuthService(
      publicKey,
      requiredRole,
      JwtTokenAuthService.parseClaimPath(claimPath),
      instanceAdminClaim,
      roleDAO
    );
  }

  private static String validJws(String subject, String[] realmRoles) {
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    var builder = Jwts.builder()
      .setSubject(subject)
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(now)
      .setId(UUID.randomUUID().toString())
      .claim("azp", "testcase");
    if (realmRoles != null) {
      builder.claim("realm_access", new RolesList(realmRoles));
    }
    return builder.signWith(privateKey).compact();
  }

  @Test
  void parseClaimPath_splits() {
    assertArrayEquals(new String[] { "realm_access", "roles" }, JwtTokenAuthService.parseClaimPath("realm_access.roles"));
    assertArrayEquals(new String[0], JwtTokenAuthService.parseClaimPath(""));
    assertArrayEquals(new String[] { "groups" }, JwtTokenAuthService.parseClaimPath(" groups "));
  }

  @Test
  void parseBearerToken_returnsNullForNull() {
    var svc = service("", "realm_access.roles", "", mockRoleDAO());
    assertNull(svc.parseBearerToken(null));
  }

  @Test
  void parseBearerToken_returnsNullForUnsignedGarbage() {
    var svc = service("", "realm_access.roles", "", mockRoleDAO());
    assertNull(svc.parseBearerToken("Bearer not.a.jwt"));
  }

  @Test
  void parseBearerToken_validToken_setsUsername() {
    var roleDAO = mockRoleDAO();
    when(roleDAO.rolesForUser("alice")).thenReturn(Collections.emptyList());
    var svc = service("", "realm_access.roles", "", roleDAO);

    String jws = validJws("alice", new String[] { "users" });
    JWTPrincipal principal = svc.parseBearerToken("Bearer " + jws);

    assertNotNull(principal);
    assertEquals("alice", principal.getUsername());
  }

  @Test
  void parseBearerToken_missingRequiredRole_returnsNull() {
    var svc = service("required_role", "realm_access.roles", "", mockRoleDAO());
    String jws = validJws("bob", new String[] { "other_role" });
    assertNull(svc.parseBearerToken("Bearer " + jws));
  }

  @Test
  void parseBearerToken_subjectColonSplits() {
    var roleDAO = mockRoleDAO();
    when(roleDAO.rolesForUser("alice")).thenReturn(Collections.emptyList());
    var svc = service("", "realm_access.roles", "", roleDAO);

    // Some IdPs prefix subjects with realm IDs (e.g. realm:user). Filter keeps last segment.
    String jws = validJws("realm:alice", new String[] { "users" });
    JWTPrincipal principal = svc.parseBearerToken("Bearer " + jws);

    assertNotNull(principal);
    assertEquals("alice", principal.getUsername());
  }

  @Test
  void parseBearerToken_idpClaimGrantsInstanceAdmin() {
    var roleDAO = mockRoleDAO();
    when(roleDAO.rolesForUser("alice")).thenReturn(Collections.emptyList());
    var svc = service("", "groups", "shepard-admin", roleDAO);

    String jws = Jwts.builder()
      .setSubject("alice")
      .setAudience("account")
      .setExpiration(DateUtils.addMinutes(new Date(), 5))
      .setIssuedAt(new Date())
      .setId(UUID.randomUUID().toString())
      .claim("groups", List.of("shepard-admin", "users"))
      .signWith(privateKey)
      .compact();

    JWTPrincipal principal = svc.parseBearerToken("Bearer " + jws);
    assertNotNull(principal);
    assertTrue(principal.hasRole("instance-admin"));
  }

  @Test
  void parseBearerToken_neo4jOnlyGrantsInstanceAdmin() {
    var roleDAO = mockRoleDAO();
    when(roleDAO.rolesForUser("bob")).thenReturn(List.of("instance-admin"));
    var svc = service("", "realm_access.roles", "shepard-admin", roleDAO);

    String jws = validJws("bob", new String[] { "users" });
    JWTPrincipal principal = svc.parseBearerToken("Bearer " + jws);

    assertNotNull(principal);
    assertTrue(principal.hasRole("instance-admin"));
  }

  @Test
  void parseBearerToken_nestedClaimPath() {
    var roleDAO = mockRoleDAO();
    when(roleDAO.rolesForUser("eve")).thenReturn(Collections.emptyList());
    var svc = service("", "resource_access.shepard.roles", "instance-admin", roleDAO);

    String jws = Jwts.builder()
      .setSubject("eve")
      .setAudience("account")
      .setExpiration(DateUtils.addMinutes(new Date(), 5))
      .setIssuedAt(new Date())
      .setId(UUID.randomUUID().toString())
      .claim("resource_access", java.util.Map.of("shepard", java.util.Map.of("roles", List.of("instance-admin"))))
      .signWith(privateKey)
      .compact();

    JWTPrincipal principal = svc.parseBearerToken("Bearer " + jws);
    assertNotNull(principal);
    assertTrue(principal.hasRole("instance-admin"));
  }

  @Test
  void parseBearerToken_expiredTokenReturnsNull() {
    var svc = service("", "realm_access.roles", "", mockRoleDAO());
    Date past = DateUtils.addMinutes(new Date(), -5);
    String jws = Jwts.builder()
      .setSubject("bob")
      .setExpiration(past)
      .setIssuedAt(DateUtils.addMinutes(new Date(), -10))
      .setId(UUID.randomUUID().toString())
      .signWith(privateKey)
      .compact();

    assertNull(svc.parseBearerToken("Bearer " + jws));
  }

  private static RoleDAO mockRoleDAO() {
    RoleDAO r = mock(RoleDAO.class);
    when(r.rolesForUser(org.mockito.ArgumentMatchers.anyString())).thenReturn(Collections.emptyList());
    return r;
  }
}
