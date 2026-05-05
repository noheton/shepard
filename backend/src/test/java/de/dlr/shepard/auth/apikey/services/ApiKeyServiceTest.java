package de.dlr.shepard.auth.apikey.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.apikey.daos.ApiKeyDAO;
import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.apikey.io.ApiKeyIO;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PKIHelper;
import io.jsonwebtoken.Jwts;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class ApiKeyServiceTest extends BaseTestCase {

  @InjectMock
  ApiKeyDAO dao;

  @InjectMock
  UserService userService;

  @InjectMock
  AuthenticationContext authenticationContext;

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  PKIHelper pkiHelper;

  @Inject
  ApiKeyService service;

  private PrivateKey key;

  @BeforeEach
  public void setUpKey() throws NoSuchAlgorithmException, InvalidKeySpecException, IllegalAccessException {
    var privateKey =
      "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCIDEXJ9+8zSiKBreHYTteke0m+7W9Oh2uf1jjboU6/zRglwzA+Rojm1djmGcuOA7CxU1IG/3ACEsvhtR8/5NWF2Dh2KMPYf/cVqaF5Bo+Z26KbK3NCOUcgh8W5mJ0fXZnJSs5Z8PGIeJEVDcN3x2VLsxsixa3+oEiDXpllrNMlOJpLI/+offTnH2JGOu0ZGa8TZUw51zkSG6MUwmdBZaDA6e/YL1T0ohyGQ8LP5MAviQonv0FSLhvT6PEvkJuDK3RsyjbtnHTNCNTt/TKcUiWWpnqd7DdDwdhuvgAiRxrikyN8patMeStAEvZwRccsge3zoLXuCgq15OioOOmGTAibAgMBAAECggEABBqirFIPZDOzUMgnDPhr5ulVMy5EclEBfSPgOTfngT+1n8YAmZBVJumCjoZuro0L8n159v4TqexZPCjTlYDYtB3urhnStqA9muiwF0+MW27Vu+qWooPJ0oBmBZBGBSE0t27LRMlQ7/X7InB02hMoyhzQD7943TqGlXfwFrIc+H1uXN8MrB4boRX71/yEPT8hv8nWB0FLcgfwtl1l+81otJFveMO/RLStHUH3Auomb/Hh4u96H6S6lUZ8TJ6+8jh2LXmg/RpsqHIWhDbZaNQJE1YdySe3bijov3s/PISaE8pRec6l6KaYkUuFUa6RoGP1RnopsFuN+EeLRMXTRtxgEQKBgQDDB1YRjE7YAYUqeuohhMgx9Ms39zsJGrs6KHE4uWtJFR/Jo3Kq093ykGA+IO+DK/IhBXGzy53SIQ9J7WEONpMmaahY6/Bkhn2nRI9biNaPCovHeO+nIpwtIdTUQLg/d+om+jC2My0YLGk71A5TRkIBPBE9NirbITxibo6jwWWOwwKBgQCylI1fx6f1gdEAP0qM7/LVLrZU3Qx+Q9rPcGG2FI1gWYu69o3JBGpSXqKcAc9hxtFVBaOGpaj9sB8+fPfMXWAvM7c808eL0zOmDC6RlQs0N4XmpV/vUeurgkLQfgB4sfUXbVWHQNsAkvB64BVbbmWFEcHzaBMytb2whvU9hcExSQKBgQCDuSjAoWt/KUev8WTBTtWIKDY5jpopBA0AsuAF1/ZGXiYiImsIRiDZ+/mE/OnIRp46/1pUfWoSypFw9Qtgdivc/e/eXzz2KIAlwYCx6jJAWnceOuhiklW5heghk7Td6TgVK1ZLOTVz5ksNRaSHSiS6gL+EAFnhtwj50oI0yCK30QKBgF7k028HADhUYEQaXbogs1AW/2p+/+mEkxxR4opHx4xgaQDTjSo5P2o/wXbW+2VAqfHdCjU9iFwuH5wr+d1N7ROIDqGzA8FIXJSquoA/y/FWY7/ZNu5MAMhlcq2plwSLw+pL/fveOcHHUyRoONEaC7Y3ZnG6ZyE2M/M+88hab/uJAoGARSSJgG3rRz8hcfQEfopo3rzdeAMY0ws+fXlHp6u51PP+238rB0Y+/b/NeHzwwuqeIxqVTcbd5E8Va7KESPuuzfIQtKbGuVwFpZzWHmROt312AoxeSwRDpOQibpfBAF59D40+SCl6N64whiVoEgJvOQGYB6BIcunIhSpLSD2YId4=";
    var kFactory = KeyFactory.getInstance("RSA");
    var decoded = Base64.getDecoder().decode(privateKey);
    var spec = new PKCS8EncodedKeySpec(decoded);
    key = kFactory.generatePrivate(spec);
    authenticationContext.setPrincipal(new JWTPrincipal("bob", "key"));
  }

  @Test
  public void getApiKeyTest() {
    var uid = UUID.randomUUID();
    var key = new ApiKey(uid);
    var user = new User("bob");
    key.setBelongsTo(user);
    when(dao.find(uid)).thenReturn(key);
    var actual = service.getApiKey("bob", uid);
    assertEquals(key, actual);
  }

  @Test
  public void getAllApiKeysTest() {
    var uid = UUID.randomUUID();
    var key = new ApiKey(uid);
    var user = new User("bob");
    user.setApiKeys(List.of(key));

    when(userService.getUser("bob")).thenReturn(user);

    var actual = service.getAllApiKeys("bob");
    assertEquals(List.of(key), actual);
  }

  @Test
  public void getAllApiKeysTest_noUser() {
    when(userService.getUser("bob")).thenThrow(InvalidPathException.class);

    assertThrows(InvalidPathException.class, () -> service.getAllApiKeys("bob"));
  }

  @Test
  public void deleteApiKeyTest() {
    var uid = UUID.randomUUID();
    var key = new ApiKey(uid);
    var user = new User("bob");
    key.setBelongsTo(user);
    when(dao.find(uid)).thenReturn(key);
    when(dao.delete(uid)).thenReturn(true);
    var actual = service.deleteApiKey("bob", uid);

    assertTrue(actual);
  }

  @Test
  public void createApiKeyTest() {
    var uid = UUID.randomUUID();
    var user = new User("bob");
    var date = new Date(30L);
    var jws = Jwts.builder()
      .setSubject("bob")
      .setIssuer("uri")
      .setNotBefore(date)
      .setIssuedAt(date)
      .setId(uid.toString())
      .signWith(key)
      .compact();
    var input = new ApiKeyIO();
    input.setName("MyKey");
    var toCreate = new ApiKey() {
      {
        setBelongsTo(user);
        setCreatedAt(date);
        setName("MyKey");
      }
    };
    var created = new ApiKey() {
      {
        setUid(uid);
        setBelongsTo(user);
        setCreatedAt(date);
        setName("MyKey");
      }
    };
    var signed = new ApiKey() {
      {
        setUid(uid);
        setBelongsTo(user);
        setCreatedAt(date);
        setName("MyKey");
        setJws(jws);
      }
    };

    when(dateHelper.getDate()).thenReturn(date);
    when(userService.getUser("bob")).thenReturn(user);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(signed)).thenReturn(signed);
    when(pkiHelper.getPrivateKey()).thenReturn(key);

    var actual = service.createApiKey(input, "bob", "uri");
    assertEquals(signed, actual);
  }

  @Test
  public void createApiKeyTest_withValidUntil() {
    var uid = UUID.randomUUID();
    var user = new User("bob");
    var date = new Date(30L);
    var validUntil = new Date(date.getTime() + 60_000L);
    var input = new ApiKeyIO();
    input.setName("MyKey");
    input.setValidUntil(validUntil);
    var toCreate = new ApiKey() {
      {
        setBelongsTo(user);
        setCreatedAt(date);
        setName("MyKey");
        setValidUntil(validUntil);
      }
    };
    var created = new ApiKey() {
      {
        setUid(uid);
        setBelongsTo(user);
        setCreatedAt(date);
        setName("MyKey");
        setValidUntil(validUntil);
      }
    };

    when(dateHelper.getDate()).thenReturn(date);
    when(userService.getUser("bob")).thenReturn(user);
    when(dao.createOrUpdate(toCreate)).thenReturn(created);
    when(dao.createOrUpdate(any(ApiKey.class))).thenReturn(created);
    when(pkiHelper.getPrivateKey()).thenReturn(key);

    var actual = service.createApiKey(input, "bob", "uri");
    assertEquals(validUntil, actual.getValidUntil());
  }

  @Test
  public void createApiKeyTest_validUntilInPastRejected() {
    var user = new User("bob");
    var now = new Date(1_000_000L);
    var past = new Date(now.getTime() - 60_000L);
    var input = new ApiKeyIO();
    input.setName("MyKey");
    input.setValidUntil(past);

    when(dateHelper.getDate()).thenReturn(now);
    when(userService.getUser("bob")).thenReturn(user);

    assertThrows(InvalidRequestException.class, () -> service.createApiKey(input, "bob", "uri"));
  }
}
