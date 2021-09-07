package de.dlr.shepard.filters;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.apache.http.HttpStatus;

import de.dlr.shepard.exceptions.ApiError;
import de.dlr.shepard.neo4Core.services.ApiKeyService;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.security.JWTPrincipal;
import de.dlr.shepard.security.JWTSecurityContext;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.PKIHelper;
import de.dlr.shepard.util.PropertiesHelper;
import de.dlr.shepard.util.RequestMethod;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.log4j.Log4j2;

@Provider
@Priority(Priorities.AUTHENTICATION)
@Log4j2
public class JWTFilter implements ContainerRequestFilter {

	private static final int FIVE_MINUTES_IN_MILLIS = 5 * 60 * 1000;

	private final PublicKey oidcPublicKey;
	private final PublicKey jwtPublicKey;
	private GracePeriodUtil<?> lastSeen = new GracePeriodUtil<>(FIVE_MINUTES_IN_MILLIS);

	public JWTFilter() throws NoSuchAlgorithmException, InvalidKeySpecException {
		var pHelper = new PropertiesHelper();
		var kFactory = KeyFactory.getInstance("RSA");
		var kcDecoded = Base64.getDecoder().decode(pHelper.getProperty("oidc.public"));
		var kcSpec = new X509EncodedKeySpec(kcDecoded);
		oidcPublicKey = kFactory.generatePublic(kcSpec);

		var pkiHelper = new PKIHelper();
		pkiHelper.init();
		jwtPublicKey = pkiHelper.getPublicKey();
	}

	@Override
	public void filter(ContainerRequestContext requestContext) {

		// Allow CORS preflight requests
		if (RequestMethod.OPTIONS.name().equals(requestContext.getRequest().getMethod())) {
			// Allow all requests with request method OPTIONS
			return;
		}

		JWTPrincipal principal;

		// Get the HTTP Authorization header from the request
		String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
		String apiKeyHeader = requestContext.getHeaderString("X-API-KEY");
		if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
			principal = parseAccessToken(authorizationHeader);
		} else if (apiKeyHeader != null) {
			principal = parseApiKey(apiKeyHeader);
		} else {
			log.warn("Invalid/missing authorization header (Authorization: {}, X-API-KEY: {}) on endpoint {}",
					authorizationHeader, apiKeyHeader, requestContext.getUriInfo().getAbsolutePath());
			requestContext.abortWith(
					Response.status(HttpStatus.SC_UNAUTHORIZED).entity(new ApiError(HttpStatus.SC_UNAUTHORIZED,
							"AuthenticationException", "Invalid/missing authorization header")).build());
			return;
		}

		if (principal == null) {
			requestContext.abortWith(Response.status(HttpStatus.SC_UNAUTHORIZED).entity(
					new ApiError(HttpStatus.SC_UNAUTHORIZED, "AuthenticationException", "Invalid Authentication"))
					.build());
			return;
		}

		var securityContext = new JWTSecurityContext(requestContext.getSecurityContext(), principal);
		requestContext.setSecurityContext(securityContext);
		requestContext.setProperty(Constants.USER, principal);
	}

	private JWTPrincipal parseAccessToken(String header) {
		JWTPrincipal result = null;
		Jws<Claims> jws = parseAccessTokenFromHeader(header);
		if (jws != null) {
			result = parsePrincipalFromAccessToken(jws);
		}
		return result;
	}

	private Jws<Claims> parseAccessTokenFromHeader(String header) {
		// Extract the token from the HTTP Authorization header
		String token = header.replace("Bearer ", "");
		Jws<Claims> jws;

		try {
			jws = Jwts.parserBuilder().setSigningKey(oidcPublicKey).build().parseClaimsJws(token);
			log.debug("Valid token: {}", jws.getBody().getId());
		} catch (JwtException ex) {
			log.warn("Invalid token: {}", ex.getMessage());
			return null;
		}
		return jws;
	}

	private JWTPrincipal parsePrincipalFromAccessToken(Jws<Claims> jws) {
		var body = jws.getBody();
		String keyId = body.getId();
		String subject = body.getSubject();
		String audience = body.getAudience();
		String issuedFor = body.get("azp", String.class);
		String firstName = body.get("given_name", String.class);
		String lastName = body.get("family_name", String.class);
		String eMail = body.get("email", String.class);

		if (subject == null || subject.isEmpty()) {
			log.warn("Token is missing a subject");
			return null;
		}

		// We only want the last part of the subject, since this is usually a human
		// readable user name
		var splitted = subject.split(":");
		String username = splitted[splitted.length - 1];

		var principal = new JWTPrincipal(audience, issuedFor, username, firstName, lastName, eMail, keyId,
				new String[0]);
		return principal;
	}

	private Jws<Claims> parseApiKeyFromHeader(String token) {
		// Extract the api key from the HTTP Authorization header
		Jws<Claims> jws;

		try {
			jws = Jwts.parserBuilder().setSigningKey(jwtPublicKey).build().parseClaimsJws(token);
			log.debug("Valid token: {}", jws.getBody().getId());
		} catch (JwtException ex) {
			log.warn("Invalid token: {}", ex.getMessage());
			return null;
		}
		return jws;
	}

	private JWTPrincipal parseApiKey(String header) {
		ApiKeyService apiKeyService = getApiKeyService();
		JWTPrincipal principal = null;
		Jws<Claims> jws = parseApiKeyFromHeader(header);
		if (jws != null) {
			principal = parsePrincipalFromApiKey(jws);
			if (principal == null)
				return null;
			UUID tokenId = UUID.fromString(jws.getBody().getId());

			if (lastSeen.elementIsKnown(tokenId.toString())) {
				return principal;
			}

			var storedKey = apiKeyService.getApiKey(tokenId);
			if (storedKey == null) {
				log.warn("Token was not found in database");
				return null;
			} else if (!storedKey.getJws().equals(header)) {
				log.warn("Token from header is not equal to the token from database");
				return null;
			}
			lastSeen.elementSeen(tokenId.toString(), null);

		}
		return principal;
	}

	private JWTPrincipal parsePrincipalFromApiKey(Jws<Claims> jws) {
		var body = jws.getBody();
		String subject = body.getSubject();
		String keyId = body.getId();
		if (subject == null || subject.isEmpty()) {
			log.warn("Token is missing a subject");
			return null;
		}

		var principal = new JWTPrincipal(subject, keyId);
		return principal;
	}

	protected ApiKeyService getApiKeyService() {
		return new ApiKeyService();
	}
}
