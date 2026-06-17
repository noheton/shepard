package de.dlr.shepard.common.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.apikey.services.ApiKeyService;
import de.dlr.shepard.auth.role.daos.RoleDAO;
import de.dlr.shepard.auth.security.ApiKeyLastSeenCache;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.security.JWTSecurityContext;
import de.dlr.shepard.auth.security.JwtTokenAuthService;
import de.dlr.shepard.auth.security.RolesList;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.PKIHelper;
import io.jsonwebtoken.Jwts;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@QuarkusComponentTest({ JwtTokenAuthService.class, de.dlr.shepard.auth.security.ApiKeyAuthService.class })
@TestConfigProperty(
  key = "oidc.public",
  value = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiAxFyffvM0oiga3h2E7XpHtJvu1vTodrn9Y426FOv80YJcMwPkaI5tXY5hnLjgOwsVNSBv9wAhLL4bUfP+TVhdg4dijD2H/3FamheQaPmduimytzQjlHIIfFuZidH12ZyUrOWfDxiHiRFQ3Dd8dlS7MbIsWt/qBIg16ZZazTJTiaSyP/qH305x9iRjrtGRmvE2VMOdc5EhujFMJnQWWgwOnv2C9U9KIchkPCz+TAL4kKJ79BUi4b0+jxL5Cbgyt0bMo27Zx0zQjU7f0ynFIllqZ6new3Q8HYbr4AIkca4pMjfKWrTHkrQBL2cEXHLIHt86C17goKteToqDjphkwImwIDAQAB"
)
@TestConfigProperty(key = "oidc.role", value = "test_role")
public class JWTFilterTest extends BaseTestCase {

  private static PrivateKey privateKey;
  private static PublicKey publicKey;

  @InjectMock
  ContainerRequestContext context;

  @InjectMock
  UriInfo uriInfo;

  @InjectMock
  PKIHelper pkiHelper;

  @InjectMock
  ApiKeyService apiKeyService;

  @InjectMock
  ApiKeyLastSeenCache apiKeyLastSeenCache;

  @InjectMock
  RoleDAO roleDAO;

  @InjectMock
  UserDAO userDAO;

  @InjectMock
  AuthenticationContext authenticationContext;

  @Inject
  JWTFilter filter;

  @Captor
  ArgumentCaptor<Response> responseCaptor;

  @Captor
  ArgumentCaptor<SecurityContext> scCaptor;

  @BeforeAll
  public static void createKeys() throws NoSuchAlgorithmException, InvalidKeySpecException {
    var privateString =
      "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCIDEXJ9+8zSiKBreHYTteke0m+7W9Oh2uf1jjboU6/zRglwzA+Rojm1djmGcuOA7CxU1IG/3ACEsvhtR8/5NWF2Dh2KMPYf/cVqaF5Bo+Z26KbK3NCOUcgh8W5mJ0fXZnJSs5Z8PGIeJEVDcN3x2VLsxsixa3+oEiDXpllrNMlOJpLI/+offTnH2JGOu0ZGa8TZUw51zkSG6MUwmdBZaDA6e/YL1T0ohyGQ8LP5MAviQonv0FSLhvT6PEvkJuDK3RsyjbtnHTNCNTt/TKcUiWWpnqd7DdDwdhuvgAiRxrikyN8patMeStAEvZwRccsge3zoLXuCgq15OioOOmGTAibAgMBAAECggEABBqirFIPZDOzUMgnDPhr5ulVMy5EclEBfSPgOTfngT+1n8YAmZBVJumCjoZuro0L8n159v4TqexZPCjTlYDYtB3urhnStqA9muiwF0+MW27Vu+qWooPJ0oBmBZBGBSE0t27LRMlQ7/X7InB02hMoyhzQD7943TqGlXfwFrIc+H1uXN8MrB4boRX71/yEPT8hv8nWB0FLcgfwtl1l+81otJFveMO/RLStHUH3Auomb/Hh4u96H6S6lUZ8TJ6+8jh2LXmg/RpsqHIWhDbZaNQJE1YdySe3bijov3s/PISaE8pRec6l6KaYkUuFUa6RoGP1RnopsFuN+EeLRMXTRtxgEQKBgQDDB1YRjE7YAYUqeuohhMgx9Ms39zsJGrs6KHE4uWtJFR/Jo3Kq093ykGA+IO+DK/IhBXGzy53SIQ9J7WEONpMmaahY6/Bkhn2nRI9biNaPCovHeO+nIpwtIdTUQLg/d+om+jC2My0YLGk71A5TRkIBPBE9NirbITxibo6jwWWOwwKBgQCylI1fx6f1gdEAP0qM7/LVLrZU3Qx+Q9rPcGG2FI1gWYu69o3JBGpSXqKcAc9hxtFVBaOGpaj9sB8+fPfMXWAvM7c808eL0zOmDC6RlQs0N4XmpV/vUeurgkLQfgB4sfUXbVWHQNsAkvB64BVbbmWFEcHzaBMytb2whvU9hcExSQKBgQCDuSjAoWt/KUev8WTBTtWIKDY5jpopBA0AsuAF1/ZGXiYiImsIRiDZ+/mE/OnIRp46/1pUfWoSypFw9Qtgdivc/e/eXzz2KIAlwYCx6jJAWnceOuhiklW5heghk7Td6TgVK1ZLOTVz5ksNRaSHSiS6gL+EAFnhtwj50oI0yCK30QKBgF7k028HADhUYEQaXbogs1AW/2p+/+mEkxxR4opHx4xgaQDTjSo5P2o/wXbW+2VAqfHdCjU9iFwuH5wr+d1N7ROIDqGzA8FIXJSquoA/y/FWY7/ZNu5MAMhlcq2plwSLw+pL/fveOcHHUyRoONEaC7Y3ZnG6ZyE2M/M+88hab/uJAoGARSSJgG3rRz8hcfQEfopo3rzdeAMY0ws+fXlHp6u51PP+238rB0Y+/b/NeHzwwuqeIxqVTcbd5E8Va7KESPuuzfIQtKbGuVwFpZzWHmROt312AoxeSwRDpOQibpfBAF59D40+SCl6N64whiVoEgJvOQGYB6BIcunIhSpLSD2YId4=";
    var publicString =
      "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiAxFyffvM0oiga3h2E7XpHtJvu1vTodrn9Y426FOv80YJcMwPkaI5tXY5hnLjgOwsVNSBv9wAhLL4bUfP+TVhdg4dijD2H/3FamheQaPmduimytzQjlHIIfFuZidH12ZyUrOWfDxiHiRFQ3Dd8dlS7MbIsWt/qBIg16ZZazTJTiaSyP/qH305x9iRjrtGRmvE2VMOdc5EhujFMJnQWWgwOnv2C9U9KIchkPCz+TAL4kKJ79BUi4b0+jxL5Cbgyt0bMo27Zx0zQjU7f0ynFIllqZ6new3Q8HYbr4AIkca4pMjfKWrTHkrQBL2cEXHLIHt86C17goKteToqDjphkwImwIDAQAB";
    var keyFactory = KeyFactory.getInstance("RSA");

    byte[] privateDecoded = Base64.getDecoder().decode(privateString);
    PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateDecoded);
    privateKey = keyFactory.generatePrivate(privateSpec);

    byte[] publicDecoded = Base64.getDecoder().decode(publicString);
    X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(publicDecoded);
    publicKey = keyFactory.generatePublic(publicSpec);
  }

  @BeforeEach
  public void setUpKeys() throws IllegalAccessException {
    when(pkiHelper.getPublicKey()).thenReturn(publicKey);
  }

  @BeforeEach
  public void setUpRoleDAO() {
    when(roleDAO.rolesForUser(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.Collections.emptyList());
  }

  @BeforeEach
  public void setUpUserDAO() {
    // Default: every existing test runs with the ROLE-GRANT-STALE-SESSION-02
    // gate as a pass-through (no recorded role change). Tests that exercise
    // the gate override this stub locally.
    when(userDAO.find(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
  }

  @BeforeEach
  public void setUpUriInfo() throws URISyntaxException {
    URI uri = new URI("http://localhost:8080/shepard/api/projects");
    URI baseUri = new URI("http://localhost:8080/shepard/api");
    String relativePath = "/projects";
    when(uriInfo.getPath()).thenReturn(relativePath);
    when(uriInfo.getAbsolutePath()).thenReturn(uri);
    when(uriInfo.getBaseUri()).thenReturn(baseUri);
    when(context.getUriInfo()).thenReturn(uriInfo);
    when(context.getMethod()).thenReturn("GET");
  }

  @Test
  public void testFilterCORS() throws URISyntaxException {
    when(context.getMethod()).thenReturn("OPTIONS");
    filter.filter(context);
    verify(context, never()).abortWith(any());
  }

  @Test
  public void testFilterPublic_publicRoute() throws URISyntaxException {
    when(uriInfo.getPath()).thenReturn("/versionz");
    filter.filter(context);
    verify(context, never()).abortWith(any());
  }

  @Test
  public void testFilterPublic_privateRoute() throws URISyntaxException {
    when(uriInfo.getPath()).thenReturn("/versionsz");
    filter.filter(context);
    verify(context).abortWith(any());
  }

  @Test
  public void testFilterNoHeader() throws URISyntaxException {
    when(context.getHeaderString("Authorization")).thenReturn(null);
    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  public void testFilterWrongHeader() {
    when(context.getHeaderString("Authorization")).thenReturn("sehr falsch");
    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  public void testFilterTokenExpired() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = DateUtils.addMinutes(new Date(), -10);
    Date future = DateUtils.addMinutes(new Date(), -5);

    String jws = Jwts.builder()
      .setSubject("Bob")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(UUID.randomUUID().toString())
      .claim("azp", "testcase")
      .claim("name", "MyName")
      .claim("preferred_username", "MyUserName")
      .claim("given_name", "MyFirstName")
      .claim("family_name", "MyLastName")
      .claim("email", "MyEMail")
      .claim("realm_access", new RolesList(new String[] { "test_role" }))
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  public void testFilterMissingSubject() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim("azp", "testcase")
      .claim("name", "MyName")
      .claim("preferred_username", "MyUserName")
      .claim("given_name", "MyFirstName")
      .claim("family_name", "MyLastName")
      .claim("email", "MyEMail")
      .claim("realm_access", new RolesList(new String[] { "test_role" }))
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  public void testFilterEmptySubject() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim("azp", "testcase")
      .claim("name", "MyName")
      .claim("preferred_username", "MyUserName")
      .claim("given_name", "MyFirstName")
      .claim("family_name", "MyLastName")
      .claim("email", "MyEMail")
      .claim("realm_access", new RolesList(new String[] { "test_role" }))
      .claim("sub", "")
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  public void testFilterMissingRole() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("Bob")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim("azp", "testcase")
      .claim("name", "MyName")
      .claim("preferred_username", "MyUserName")
      .claim("given_name", "MyFirstName")
      .claim("family_name", "MyLastName")
      .claim("email", "MyEMail")
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  public void testFilterWrongRole() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("Bob")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim("azp", "testcase")
      .claim("name", "MyName")
      .claim("preferred_username", "MyUserName")
      .claim("given_name", "MyFirstName")
      .claim("family_name", "MyLastName")
      .claim("email", "MyEMail")
      .claim("realm_access", new RolesList(new String[] { "wrong_role" }))
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  @TestConfigProperty(key = "oidc.role", value = "")
  public void testFilterNoRoleConfigured()
    throws InvalidKeySpecException, NoSuchAlgorithmException, IllegalAccessException {
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("Bob")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim("azp", "testcase")
      .claim("name", "MyName")
      .claim("preferred_username", "MyUserName")
      .claim("given_name", "MyFirstName")
      .claim("family_name", "MyLastName")
      .claim("email", "MyEMail")
      .claim("realm_access", new RolesList(new String[] { "another_role" }))
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context, never()).abortWith(any());
  }

  @Test
  public void testFilterSucessful() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("Bob")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim("azp", "testcase")
      .claim("name", "MyName")
      .claim("preferred_username", "MyUserName")
      .claim("given_name", "MyFirstName")
      .claim("family_name", "MyLastName")
      .claim("email", "MyEMail")
      .claim("realm_access", new RolesList(new String[] { "test_role" }))
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
    var captured = (JWTPrincipal) scCaptor.getValue().getUserPrincipal();
    assertEquals("MyUserName", captured.getUsername());
    assertEquals(keyId.toString(), captured.getKeyId());
  }

  @Test
  public void testFilterSucessfulApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    UUID uid = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("MyUserName")
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(uid.toString())
      .signWith(privateKey)
      .compact();

    User user = new User("MyUserName");

    ApiKey apiKey = new ApiKey(uid);
    apiKey.setName("MyApiKey");
    apiKey.setJws(jws);
    apiKey.setBelongsTo(user);

    JWTPrincipal principal = new JWTPrincipal("MyUserName", uid.toString());
    JWTSecurityContext securityContext = new JWTSecurityContext(context.getSecurityContext(), principal);

    when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
    when(apiKeyService.getApiKey(uid)).thenReturn(apiKey);

    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
    verify(apiKeyLastSeenCache).cacheKey(uid.toString());
    assertEquals(securityContext.getUserPrincipal(), scCaptor.getValue().getUserPrincipal());
  }

  @Test
  public void testFilterMissingSubjectApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    UUID uid = UUID.randomUUID();

    String jws = Jwts.builder()
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(uid.toString())
      .signWith(privateKey)
      .compact();

    User user = new User("MyUserName");

    ApiKey apiKey = new ApiKey(uid);
    apiKey.setName("MyApiKey");
    apiKey.setJws(jws);
    apiKey.setBelongsTo(user);

    when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
    when(apiKeyService.getApiKey(user.getUsername(), uid)).thenReturn(apiKey);

    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  public void testFilterEmptySubjectApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    UUID uid = UUID.randomUUID();

    String jws = Jwts.builder()
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(uid.toString())
      .claim("sub", "")
      .signWith(privateKey)
      .compact();

    User user = new User("MyUserName");

    ApiKey apiKey = new ApiKey(uid);
    apiKey.setName("MyApiKey");
    apiKey.setJws(jws);
    apiKey.setBelongsTo(user);

    when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
    when(apiKeyService.getApiKey(user.getUsername(), uid)).thenReturn(apiKey);

    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  public void testFilterNotYetValidApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date future = DateUtils.addMinutes(new Date(), 5);
    UUID uid = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("MyUserName")
      .setNotBefore(future)
      .setIssuedAt(new Date())
      .setId(uid.toString())
      .signWith(privateKey)
      .compact();

    User user = new User("MyUserName");

    ApiKey apiKey = new ApiKey(uid);
    apiKey.setName("MyApiKey");
    apiKey.setJws(jws);
    apiKey.setBelongsTo(user);

    when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
    when(apiKeyService.getApiKey(user.getUsername(), uid)).thenReturn(apiKey);

    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  public void testFilterNotFoundInDBApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    UUID uid = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("MyUserName")
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(uid.toString())
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
    when(apiKeyService.getApiKey("MyUserName", uid)).thenReturn(null);

    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  public void testFilterWrongFoundInDBApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    UUID uid = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("MyUserName")
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(uid.toString())
      .signWith(privateKey)
      .compact();

    User user = new User("MyUserName");

    ApiKey apiKey = new ApiKey(uid);
    apiKey.setName("MyApiKey");
    apiKey.setJws("Wrong");
    apiKey.setBelongsTo(user);

    when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
    when(apiKeyService.getApiKey(user.getUsername(), uid)).thenReturn(apiKey);

    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }

  @Test
  public void testFilterApiKeyValidUntilFuture() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID uid = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("MyUserName")
      .setNotBefore(now)
      .setIssuedAt(now)
      .setExpiration(future)
      .setId(uid.toString())
      .signWith(privateKey)
      .compact();

    User user = new User("MyUserName");
    ApiKey apiKey = new ApiKey(uid);
    apiKey.setName("MyApiKey");
    apiKey.setJws(jws);
    apiKey.setValidUntil(future);
    apiKey.setBelongsTo(user);

    when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
    when(apiKeyService.getApiKey(uid)).thenReturn(apiKey);

    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
    verify(apiKeyLastSeenCache).cacheKey(uid.toString());
  }

  @Test
  public void testFilterApiKeyExpired() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date past = DateUtils.addMinutes(new Date(), -5);
    Date issued = DateUtils.addMinutes(new Date(), -10);
    UUID uid = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("MyUserName")
      .setNotBefore(issued)
      .setIssuedAt(issued)
      .setExpiration(past)
      .setId(uid.toString())
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("X-API-KEY")).thenReturn(jws);

    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    Response response = responseCaptor.getValue();
    assertEquals(401, response.getStatus());
    String wwwAuthenticate = response.getHeaderString("WWW-Authenticate");
    org.junit.jupiter.api.Assertions.assertNotNull(wwwAuthenticate);
    org.junit.jupiter.api.Assertions.assertTrue(wwwAuthenticate.contains("expired"));
  }

  @Test
  public void testFilterApiKeyNoValidUntilStillAccepted() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    UUID uid = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("MyUserName")
      .setNotBefore(now)
      .setIssuedAt(now)
      .setId(uid.toString())
      .signWith(privateKey)
      .compact();

    User user = new User("MyUserName");
    ApiKey apiKey = new ApiKey(uid);
    apiKey.setName("MyApiKey");
    apiKey.setJws(jws);
    apiKey.setBelongsTo(user);

    when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
    when(apiKeyService.getApiKey(uid)).thenReturn(apiKey);

    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
  }

  @Test
  public void testFilterGracePeriod() throws InvalidKeySpecException, NoSuchAlgorithmException {
    Date now = new Date();
    UUID uid = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("MyUserName")
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(uid.toString())
      .signWith(privateKey)
      .compact();

    JWTPrincipal principal = new JWTPrincipal("MyUserName", uid.toString());
    JWTSecurityContext securityContext = new JWTSecurityContext(context.getSecurityContext(), principal);

    when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
    when(apiKeyLastSeenCache.isKeyCached(uid.toString())).thenReturn(true);

    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
    verify(apiKeyService, never()).getApiKey("MyUserName", uid);
    assertEquals(securityContext.getUserPrincipal(), scCaptor.getValue().getUserPrincipal());
  }

  // ----- A0 + F8: dual-source role resolution + claim-path walk -----

  /**
   * F8: when {@code shepard.oidc.roles-claim-path} points at a path
   * that doesn't exist in the JWT, the filter still authenticates the
   * user (no role is enforced) — the claim-path is for role lookup
   * only.
   */
  @Test
  @TestConfigProperty(key = "oidc.role", value = "")
  @TestConfigProperty(key = "shepard.oidc.roles-claim-path", value = "groups")
  @TestConfigProperty(key = "shepard.instance-admin.role", value = "shepard-admin")
  public void testF8_idpRoleGrantsInstanceAdmin() throws InvalidKeySpecException, NoSuchAlgorithmException {
    when(roleDAO.rolesForUser("alice")).thenReturn(java.util.Collections.emptyList());
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("alice")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim("groups", java.util.List.of("shepard-admin", "users"))
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
    var principal = (JWTPrincipal) scCaptor.getValue().getUserPrincipal();
    org.junit.jupiter.api.Assertions.assertTrue(principal.hasRole("instance-admin"));
  }

  /**
   * Dual-source: only Neo4j edge (no IdP claim) still grants
   * instance-admin via the principal.
   */
  @Test
  @TestConfigProperty(key = "oidc.role", value = "")
  @TestConfigProperty(key = "shepard.instance-admin.role", value = "shepard-admin")
  public void testDualSource_neo4jOnlyGrant() throws InvalidKeySpecException, NoSuchAlgorithmException {
    when(roleDAO.rolesForUser("bob")).thenReturn(java.util.List.of("instance-admin"));
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("bob")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim("realm_access", new RolesList(new String[] { "users" }))
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
    var principal = (JWTPrincipal) scCaptor.getValue().getUserPrincipal();
    org.junit.jupiter.api.Assertions.assertTrue(principal.hasRole("instance-admin"));
  }

  /**
   * Dual-source: both sources agree → still exactly one
   * instance-admin entry on the principal (deduped).
   */
  @Test
  @TestConfigProperty(key = "oidc.role", value = "")
  @TestConfigProperty(key = "shepard.instance-admin.role", value = "shepard-admin")
  public void testDualSource_bothSources_dedupes() throws InvalidKeySpecException, NoSuchAlgorithmException {
    when(roleDAO.rolesForUser("carol")).thenReturn(java.util.List.of("instance-admin"));
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("carol")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim("realm_access", new RolesList(new String[] { "shepard-admin" }))
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
    var principal = (JWTPrincipal) scCaptor.getValue().getUserPrincipal();
    long count = java.util.Arrays.stream(principal.getRoles()).filter("instance-admin"::equals).count();
    org.junit.jupiter.api.Assertions.assertEquals(1, count);
  }

  /**
   * Dual-source: neither source grants the role → principal carries
   * no instance-admin (and authentication still succeeds — the role
   * is only required for `@RolesAllowed`-gated endpoints).
   */
  @Test
  @TestConfigProperty(key = "oidc.role", value = "")
  @TestConfigProperty(key = "shepard.instance-admin.role", value = "shepard-admin")
  public void testDualSource_neither_principalHasNoRole() throws InvalidKeySpecException, NoSuchAlgorithmException {
    when(roleDAO.rolesForUser("dave")).thenReturn(java.util.Collections.emptyList());
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("dave")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim("realm_access", new RolesList(new String[] { "users" }))
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
    var principal = (JWTPrincipal) scCaptor.getValue().getUserPrincipal();
    org.junit.jupiter.api.Assertions.assertFalse(principal.hasRole("instance-admin"));
  }

  /**
   * F8: a deeper claim-path (Pocket ID-style nested claim) walks
   * correctly when configured.
   */
  @Test
  @TestConfigProperty(key = "oidc.role", value = "")
  @TestConfigProperty(key = "shepard.oidc.roles-claim-path", value = "resource_access.shepard.roles")
  @TestConfigProperty(key = "shepard.instance-admin.role", value = "instance-admin")
  public void testF8_nestedClaimPath() throws InvalidKeySpecException, NoSuchAlgorithmException {
    when(roleDAO.rolesForUser("eve")).thenReturn(java.util.Collections.emptyList());
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("eve")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim(
        "resource_access",
        java.util.Map.of("shepard", java.util.Map.of("roles", java.util.List.of("instance-admin")))
      )
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
    var principal = (JWTPrincipal) scCaptor.getValue().getUserPrincipal();
    org.junit.jupiter.api.Assertions.assertTrue(principal.hasRole("instance-admin"));
  }

  /**
   * Static-helper level: claim-path parsing.
   */
  @Test
  public void parseClaimPath_splits() {
    org.junit.jupiter.api.Assertions.assertArrayEquals(
      new String[] { "realm_access", "roles" },
      de.dlr.shepard.auth.security.JwtTokenAuthService.parseClaimPath("realm_access.roles")
    );
    org.junit.jupiter.api.Assertions.assertArrayEquals(new String[0], de.dlr.shepard.auth.security.JwtTokenAuthService.parseClaimPath(""));
    org.junit.jupiter.api.Assertions.assertArrayEquals(
      new String[] { "groups" },
      de.dlr.shepard.auth.security.JwtTokenAuthService.parseClaimPath(" groups ")
    );
  }

  // ---- ROLE-GRANT-STALE-SESSION-02: filter-level gate tests ----

  @Test
  public void testFilterRejectsStaleRoleJwtWithStructuredBody() {
    // JWT issued 10 minutes ago; user's role set changed 1 minute ago.
    Date issuedAt = DateUtils.addMinutes(new Date(), -10);
    long roleChangedAtMillis = System.currentTimeMillis() - 60_000L;

    User u = new User("Bob");
    u.setRoleChangedAt(new Date(roleChangedAtMillis));
    when(userDAO.find("Bob")).thenReturn(u);

    String jws = Jwts.builder()
      .setSubject("Bob")
      .setAudience("account")
      .setExpiration(DateUtils.addMinutes(new Date(), 5))
      .setNotBefore(issuedAt)
      .setIssuedAt(issuedAt)
      .setId(UUID.randomUUID().toString())
      .claim("azp", "testcase")
      .claim("realm_access", new RolesList(new String[] { "test_role" }))
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
    filter.filter(context);

    verify(context).abortWith(responseCaptor.capture());
    Response response = responseCaptor.getValue();
    assertEquals(401, response.getStatus());
    String wwwAuthenticate = response.getHeaderString("WWW-Authenticate");
    org.junit.jupiter.api.Assertions.assertNotNull(wwwAuthenticate);
    org.junit.jupiter.api.Assertions.assertTrue(
      wwwAuthenticate.contains("role_changed"),
      "WWW-Authenticate should carry role_changed error: " + wwwAuthenticate
    );
    var entity = (de.dlr.shepard.common.exceptions.ApiError) response.getEntity();
    assertEquals("role_changed", entity.getException());
    org.junit.jupiter.api.Assertions.assertTrue(
      entity.getMessage().toLowerCase().contains("sign out"),
      "ApiError message should guide the user to sign out + back in: " + entity.getMessage()
    );
  }

  @Test
  public void testFilterApiKeyBypassesRoleChangedGate() throws InvalidKeySpecException, NoSuchAlgorithmException {
    // Even when the affected user's :User node carries a `roleChangedAt`
    // in the future of the API key's `iat`, the X-API-Key path must NOT
    // be blocked — API keys are long-lived service tokens with their own
    // revocation surface (DELETE /v2/apikeys/{appId}).
    Date now = new Date();
    UUID uid = UUID.randomUUID();
    String jws = Jwts.builder()
      .setSubject("MyUserName")
      .setNotBefore(now)
      .setIssuedAt(now)
      .setId(uid.toString())
      .signWith(privateKey)
      .compact();

    User user = new User("MyUserName");
    // roleChangedAt would reject an OIDC Bearer with same iat — but API-key
    // path doesn't even invoke the gate.
    user.setRoleChangedAt(new Date(now.getTime() + 60_000L));
    when(userDAO.find("MyUserName")).thenReturn(user);

    ApiKey apiKey = new ApiKey(uid);
    apiKey.setName("MyApiKey");
    apiKey.setJws(jws);
    apiKey.setBelongsTo(user);

    when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
    when(apiKeyService.getApiKey(uid)).thenReturn(apiKey);

    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
  }

  // ── MFFD-VIDEOREF-SCALE-1 — query-param access_token fallback ───────────

  @Test
  public void testFilterAccessTokenQueryParam_authenticates()
    throws InvalidKeySpecException, NoSuchAlgorithmException {
    // Browser surfaces (HTML5 <video>, <img>, <a download>) cannot inject
    // a custom Authorization header. The query-param fallback lets the
    // JWT travel as ?access_token=… so they can still consume protected
    // routes.
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);
    UUID keyId = UUID.randomUUID();

    String jws = Jwts.builder()
      .setSubject("Bob")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(keyId.toString())
      .claim("azp", "testcase")
      .claim("preferred_username", "Bob")
      .claim("realm_access", new RolesList(new String[] { "test_role" }))
      .signWith(privateKey)
      .compact();

    // No Authorization header, no X-API-KEY.
    when(context.getHeaderString("Authorization")).thenReturn(null);
    when(context.getHeaderString("X-API-KEY")).thenReturn(null);
    var qp = new jakarta.ws.rs.core.MultivaluedHashMap<String, String>();
    qp.add("access_token", jws);
    when(uriInfo.getQueryParameters()).thenReturn(qp);

    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
    var captured = (JWTPrincipal) scCaptor.getValue().getUserPrincipal();
    assertEquals("Bob", captured.getUsername());
  }

  @Test
  public void testFilterHeaderTakesPrecedenceOverQueryParam()
    throws InvalidKeySpecException, NoSuchAlgorithmException {
    // When BOTH the Authorization header and ?access_token are present,
    // the header wins — the query param is a fallback for surfaces that
    // can't send headers. This avoids ambiguity for normal API callers
    // who already use the header.
    Date now = new Date();
    Date future = DateUtils.addMinutes(now, 5);

    String headerJws = Jwts.builder()
      .setSubject("HeaderUser")
      .setAudience("account")
      .setExpiration(future)
      .setNotBefore(now)
      .setIssuedAt(new Date())
      .setId(UUID.randomUUID().toString())
      .claim("realm_access", new RolesList(new String[] { "test_role" }))
      .signWith(privateKey)
      .compact();

    when(context.getHeaderString("Authorization")).thenReturn("Bearer " + headerJws);
    // Add a malformed query-param token: if precedence is wrong, parsing
    // will reject and we get 401 instead of the header-user.
    var qp = new jakarta.ws.rs.core.MultivaluedHashMap<String, String>();
    qp.add("access_token", "this-is-not-a-jwt");
    when(uriInfo.getQueryParameters()).thenReturn(qp);

    filter.filter(context);
    verify(context, never()).abortWith(any());
    verify(context).setSecurityContext(scCaptor.capture());
    var captured = (JWTPrincipal) scCaptor.getValue().getUserPrincipal();
    assertEquals("HeaderUser", captured.getUsername());
  }

  @Test
  public void testFilterEmptyQueryParam_falls_back_to_401()
    throws URISyntaxException {
    // ?access_token= (empty value) is treated as if absent.
    when(context.getHeaderString("Authorization")).thenReturn(null);
    when(context.getHeaderString("X-API-KEY")).thenReturn(null);
    var qp = new jakarta.ws.rs.core.MultivaluedHashMap<String, String>();
    qp.add("access_token", "");
    when(uriInfo.getQueryParameters()).thenReturn(qp);

    filter.filter(context);
    verify(context).abortWith(responseCaptor.capture());
    assertEquals(401, responseCaptor.getValue().getStatus());
  }
}
