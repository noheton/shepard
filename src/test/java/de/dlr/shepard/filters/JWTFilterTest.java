package de.dlr.shepard.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.services.ApiKeyService;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.security.JWTPrincipal;
import de.dlr.shepard.security.JWTSecurityContext;
import io.jsonwebtoken.Jwts;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

public class JWTFilterTest extends BaseTestCase {

	private PrivateKey privateKey;

	@Mock
	private ContainerRequestContext context;

	@Mock
	private Request request;

	@Mock
	private UriInfo uriInfo;

	@Mock
	private ApiKeyService apiKeyService;

	@Mock
	private GracePeriodUtil<?> lastSeen;

	@Spy
	private JWTFilter filter;

	@Captor
	private ArgumentCaptor<Response> responseCaptor;

	@Captor
	private ArgumentCaptor<SecurityContext> scCaptor;

	@BeforeEach
	public void setUpUriInfo() throws URISyntaxException {
		URI uri = new URI("http://localhost:8080/shepard/api/projects");
		URI baseUri = new URI("http://localhost:8080/shepard/api");
		when(uriInfo.getAbsolutePath()).thenReturn(uri);
		when(uriInfo.getBaseUri()).thenReturn(baseUri);
		when(context.getUriInfo()).thenReturn(uriInfo);
		when(request.getMethod()).thenReturn("GET");
		when(context.getRequest()).thenReturn(request);
	}

	@BeforeEach
	public void setUpKeys() throws NoSuchAlgorithmException, InvalidKeySpecException, IllegalAccessException {
		var privateString = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCIDEXJ9+8zSiKBreHYTteke0m+7W9Oh2uf1jjboU6/zRglwzA+Rojm1djmGcuOA7CxU1IG/3ACEsvhtR8/5NWF2Dh2KMPYf/cVqaF5Bo+Z26KbK3NCOUcgh8W5mJ0fXZnJSs5Z8PGIeJEVDcN3x2VLsxsixa3+oEiDXpllrNMlOJpLI/+offTnH2JGOu0ZGa8TZUw51zkSG6MUwmdBZaDA6e/YL1T0ohyGQ8LP5MAviQonv0FSLhvT6PEvkJuDK3RsyjbtnHTNCNTt/TKcUiWWpnqd7DdDwdhuvgAiRxrikyN8patMeStAEvZwRccsge3zoLXuCgq15OioOOmGTAibAgMBAAECggEABBqirFIPZDOzUMgnDPhr5ulVMy5EclEBfSPgOTfngT+1n8YAmZBVJumCjoZuro0L8n159v4TqexZPCjTlYDYtB3urhnStqA9muiwF0+MW27Vu+qWooPJ0oBmBZBGBSE0t27LRMlQ7/X7InB02hMoyhzQD7943TqGlXfwFrIc+H1uXN8MrB4boRX71/yEPT8hv8nWB0FLcgfwtl1l+81otJFveMO/RLStHUH3Auomb/Hh4u96H6S6lUZ8TJ6+8jh2LXmg/RpsqHIWhDbZaNQJE1YdySe3bijov3s/PISaE8pRec6l6KaYkUuFUa6RoGP1RnopsFuN+EeLRMXTRtxgEQKBgQDDB1YRjE7YAYUqeuohhMgx9Ms39zsJGrs6KHE4uWtJFR/Jo3Kq093ykGA+IO+DK/IhBXGzy53SIQ9J7WEONpMmaahY6/Bkhn2nRI9biNaPCovHeO+nIpwtIdTUQLg/d+om+jC2My0YLGk71A5TRkIBPBE9NirbITxibo6jwWWOwwKBgQCylI1fx6f1gdEAP0qM7/LVLrZU3Qx+Q9rPcGG2FI1gWYu69o3JBGpSXqKcAc9hxtFVBaOGpaj9sB8+fPfMXWAvM7c808eL0zOmDC6RlQs0N4XmpV/vUeurgkLQfgB4sfUXbVWHQNsAkvB64BVbbmWFEcHzaBMytb2whvU9hcExSQKBgQCDuSjAoWt/KUev8WTBTtWIKDY5jpopBA0AsuAF1/ZGXiYiImsIRiDZ+/mE/OnIRp46/1pUfWoSypFw9Qtgdivc/e/eXzz2KIAlwYCx6jJAWnceOuhiklW5heghk7Td6TgVK1ZLOTVz5ksNRaSHSiS6gL+EAFnhtwj50oI0yCK30QKBgF7k028HADhUYEQaXbogs1AW/2p+/+mEkxxR4opHx4xgaQDTjSo5P2o/wXbW+2VAqfHdCjU9iFwuH5wr+d1N7ROIDqGzA8FIXJSquoA/y/FWY7/ZNu5MAMhlcq2plwSLw+pL/fveOcHHUyRoONEaC7Y3ZnG6ZyE2M/M+88hab/uJAoGARSSJgG3rRz8hcfQEfopo3rzdeAMY0ws+fXlHp6u51PP+238rB0Y+/b/NeHzwwuqeIxqVTcbd5E8Va7KESPuuzfIQtKbGuVwFpZzWHmROt312AoxeSwRDpOQibpfBAF59D40+SCl6N64whiVoEgJvOQGYB6BIcunIhSpLSD2YId4=";
		var publicString = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiAxFyffvM0oiga3h2E7XpHtJvu1vTodrn9Y426FOv80YJcMwPkaI5tXY5hnLjgOwsVNSBv9wAhLL4bUfP+TVhdg4dijD2H/3FamheQaPmduimytzQjlHIIfFuZidH12ZyUrOWfDxiHiRFQ3Dd8dlS7MbIsWt/qBIg16ZZazTJTiaSyP/qH305x9iRjrtGRmvE2VMOdc5EhujFMJnQWWgwOnv2C9U9KIchkPCz+TAL4kKJ79BUi4b0+jxL5Cbgyt0bMo27Zx0zQjU7f0ynFIllqZ6new3Q8HYbr4AIkca4pMjfKWrTHkrQBL2cEXHLIHt86C17goKteToqDjphkwImwIDAQAB";
		var keyFactory = KeyFactory.getInstance("RSA");

		byte[] privateDecoded = Base64.getDecoder().decode(privateString);
		PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateDecoded);
		privateKey = keyFactory.generatePrivate(privateSpec);

		byte[] publicDecoded = Base64.getDecoder().decode(publicString);
		X509EncodedKeySpec pubilcSpec = new X509EncodedKeySpec(publicDecoded);
		PublicKey publicKey = keyFactory.generatePublic(pubilcSpec);
		FieldUtils.writeField(filter, "oidcPublicKey", publicKey, true);
		FieldUtils.writeField(filter, "jwtPublicKey", publicKey, true);
	}

	@BeforeEach
	public void prepareSpy() throws IllegalAccessException {
		when(filter.getApiKeyService()).thenReturn(apiKeyService);
		FieldUtils.writeField(filter, "lastSeen", lastSeen, true);
	}

	@Test
	public void testFilterCORS() throws URISyntaxException {
		when(request.getMethod()).thenReturn("OPTIONS");
		filter.filter(context);
		verify(context, never()).getHeaderString(any());
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

		String jws = Jwts.builder().setSubject("Bob").setAudience("account").setExpiration(future).setNotBefore(now)
				.setIssuedAt(new Date()).setId(UUID.randomUUID().toString()).claim("azp", "testcase")
				.claim("name", "MyName").claim("preferred_username", "MyUserName").claim("given_name", "MyFirstName")
				.claim("family_name", "MyLastName").claim("email", "MyEMail").signWith(privateKey).compact();

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

		String jws = Jwts.builder().setAudience("account").setExpiration(future).setNotBefore(now)
				.setIssuedAt(new Date()).setId(keyId.toString()).claim("azp", "testcase").claim("name", "MyName")
				.claim("preferred_username", "MyUserName").claim("given_name", "MyFirstName")
				.claim("family_name", "MyLastName").claim("email", "MyEMail").signWith(privateKey).compact();

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

		String jws = Jwts.builder().setAudience("account").setExpiration(future).setNotBefore(now)
				.setIssuedAt(new Date()).setId(keyId.toString()).claim("azp", "testcase").claim("name", "MyName")
				.claim("preferred_username", "MyUserName").claim("given_name", "MyFirstName")
				.claim("family_name", "MyLastName").claim("email", "MyEMail").claim("sub", "").signWith(privateKey)
				.compact();

		when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
		filter.filter(context);
		verify(context).abortWith(responseCaptor.capture());
		assertEquals(401, responseCaptor.getValue().getStatus());
	}

	@Test
	public void testFilterSucessful() throws InvalidKeySpecException, NoSuchAlgorithmException {
		Date now = new Date();
		Date future = DateUtils.addMinutes(now, 5);
		UUID keyId = UUID.randomUUID();

		String jws = Jwts.builder().setSubject("Bob").setAudience("account").setExpiration(future).setNotBefore(now)
				.setIssuedAt(new Date()).setId(keyId.toString()).claim("azp", "testcase").claim("name", "MyName")
				.claim("preferred_username", "MyUserName").claim("given_name", "MyFirstName")
				.claim("family_name", "MyLastName").claim("email", "MyEMail").signWith(privateKey).compact();

		JWTPrincipal principal = new JWTPrincipal("account", "testcase", "Bob", "MyFirstName", "MyLastName", "MyEMail",
				keyId.toString(), new String[0]);
		JWTSecurityContext securityContext = new JWTSecurityContext(context.getSecurityContext(), principal);

		when(context.getHeaderString("Authorization")).thenReturn("Bearer " + jws);
		filter.filter(context);
		verify(context, never()).abortWith(any());
		verify(context).setSecurityContext(scCaptor.capture());
		verify(context).setProperty("user", principal);
		assertEquals(securityContext.getUserPrincipal(), scCaptor.getValue().getUserPrincipal());
	}

	@Test
	public void testFilterSucessfulApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
		Date now = new Date();
		UUID uid = UUID.randomUUID();

		String jws = Jwts.builder().setSubject("MyUserName").setNotBefore(now).setIssuedAt(new Date())
				.setId(uid.toString()).signWith(privateKey).compact();

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
		verify(context).setProperty("user", principal);
		verify(lastSeen).elementSeen(uid.toString(), null);
		assertEquals(securityContext.getUserPrincipal(), scCaptor.getValue().getUserPrincipal());
	}

	@Test
	public void testFilterMissingSubjectApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
		Date now = new Date();
		UUID uid = UUID.randomUUID();

		String jws = Jwts.builder().setNotBefore(now).setIssuedAt(new Date()).setId(uid.toString()).signWith(privateKey)
				.compact();

		User user = new User("MyUserName");

		ApiKey apiKey = new ApiKey(uid);
		apiKey.setName("MyApiKey");
		apiKey.setJws(jws);
		apiKey.setBelongsTo(user);

		when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
		when(apiKeyService.getApiKey(uid)).thenReturn(apiKey);

		filter.filter(context);
		verify(context).abortWith(responseCaptor.capture());
		assertEquals(401, responseCaptor.getValue().getStatus());
	}

	@Test
	public void testFilterEmptySubjectApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
		Date now = new Date();
		UUID uid = UUID.randomUUID();

		String jws = Jwts.builder().setNotBefore(now).setIssuedAt(new Date()).setId(uid.toString()).claim("sub", "")
				.signWith(privateKey).compact();

		User user = new User("MyUserName");

		ApiKey apiKey = new ApiKey(uid);
		apiKey.setName("MyApiKey");
		apiKey.setJws(jws);
		apiKey.setBelongsTo(user);

		when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
		when(apiKeyService.getApiKey(uid)).thenReturn(apiKey);

		filter.filter(context);
		verify(context).abortWith(responseCaptor.capture());
		assertEquals(401, responseCaptor.getValue().getStatus());
	}

	@Test
	public void testFilterNotYetValidApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
		Date future = DateUtils.addMinutes(new Date(), 5);
		UUID uid = UUID.randomUUID();

		String jws = Jwts.builder().setSubject("MyUserName").setNotBefore(future).setIssuedAt(new Date())
				.setId(uid.toString()).signWith(privateKey).compact();

		User user = new User("MyUserName");

		ApiKey apiKey = new ApiKey(uid);
		apiKey.setName("MyApiKey");
		apiKey.setJws(jws);
		apiKey.setBelongsTo(user);

		when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
		when(apiKeyService.getApiKey(uid)).thenReturn(apiKey);

		filter.filter(context);
		verify(context).abortWith(responseCaptor.capture());
		assertEquals(401, responseCaptor.getValue().getStatus());
	}

	@Test
	public void testFilterNotFoundInDBApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
		Date now = new Date();
		UUID uid = UUID.randomUUID();

		String jws = Jwts.builder().setSubject("MyUserName").setNotBefore(now).setIssuedAt(new Date())
				.setId(uid.toString()).signWith(privateKey).compact();

		when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
		when(apiKeyService.getApiKey(uid)).thenReturn(null);

		filter.filter(context);
		verify(context).abortWith(responseCaptor.capture());
		assertEquals(401, responseCaptor.getValue().getStatus());
	}

	@Test
	public void testFilterWrongFoundInDBApiKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
		Date now = new Date();
		UUID uid = UUID.randomUUID();

		String jws = Jwts.builder().setSubject("MyUserName").setNotBefore(now).setIssuedAt(new Date())
				.setId(uid.toString()).signWith(privateKey).compact();

		User user = new User("MyUserName");

		ApiKey apiKey = new ApiKey(uid);
		apiKey.setName("MyApiKey");
		apiKey.setJws("Wrong");
		apiKey.setBelongsTo(user);

		when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
		when(apiKeyService.getApiKey(uid)).thenReturn(apiKey);

		filter.filter(context);
		verify(context).abortWith(responseCaptor.capture());
		assertEquals(401, responseCaptor.getValue().getStatus());
	}

	@Test
	public void testFilterGracePeriod() throws InvalidKeySpecException, NoSuchAlgorithmException {
		Date now = new Date();
		UUID uid = UUID.randomUUID();

		String jws = Jwts.builder().setSubject("MyUserName").setNotBefore(now).setIssuedAt(new Date())
				.setId(uid.toString()).signWith(privateKey).compact();

		JWTPrincipal principal = new JWTPrincipal("MyUserName", uid.toString());
		JWTSecurityContext securityContext = new JWTSecurityContext(context.getSecurityContext(), principal);

		when(context.getHeaderString("X-API-KEY")).thenReturn(jws);
		when(lastSeen.elementIsKnown(uid.toString())).thenReturn(true);

		filter.filter(context);
		verify(context, never()).abortWith(any());
		verify(context).setSecurityContext(scCaptor.capture());
		verify(context).setProperty("user", principal);
		verify(apiKeyService, never()).getApiKey(uid);
		assertEquals(securityContext.getUserPrincipal(), scCaptor.getValue().getUserPrincipal());
	}
}
