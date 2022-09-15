package de.dlr.shepard.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.dlr.shepard.exceptions.ShepardProcessingException;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.util.PropertiesHelper;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class OpenIdConfiguration {
	private String issuer;

	@JsonProperty("authorization_endpoint")
	private String authorizationEndpoint;

	@JsonProperty("userinfo_endpoint")
	private String userinfoEndpoint;

	@JsonProperty("jwks_uri")
	private String jwksUri;

	@JsonProperty("response_types_supported")
	private String[] responseTypesSupported;

	@JsonProperty("subject_types_supported")
	private String[] subjectTypesSupported;

	@JsonProperty("id_token_signing_alg_values_supported")
	private String[] idTokenSigningAlgValuesSupported;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class Userinfo {
	private String sub;
	private String name;
	private String email;

	@JsonProperty("given_name")
	private String givenName;

	@JsonProperty("family_name")
	private String familyName;

	@JsonProperty("preferred_username")
	private String preferredUsername;
}

@Slf4j
public class UserinfoService {

	private static final String WELL_KNOWN_PATH = ".well-known/openid-configuration";

	private final String oidcConfidurationUrl;
	private String userinfoEndpoint;

	private Client client = ClientBuilder.newClient();

	public UserinfoService() {
		PropertiesHelper helper = new PropertiesHelper();
		String oidcAuthority = helper.getProperty("oidc.authority");
		this.oidcConfidurationUrl = oidcAuthority + WELL_KNOWN_PATH;
	}

	protected void init() {
		var openIdConfiguration = getOpenIdConfiguration(oidcConfidurationUrl);
		if (openIdConfiguration == null) {
			throw new ShepardProcessingException("Could not fetch openid configuration");
		}
		userinfoEndpoint = openIdConfiguration.getUserinfoEndpoint();
	}

	public User fetchUserinfo(String accessToken) {
		if (userinfoEndpoint == null) {
			init();
		}

		var userinfo = getUserinfo(userinfoEndpoint, accessToken);
		if (userinfo == null) {
			throw new ShepardProcessingException("Could not fetch userinfo");
		}
		User user = parseUserFromUserinfo(userinfo);
		return user;
	}

	private OpenIdConfiguration getOpenIdConfiguration(String uri) {
		var request = client.target(uri).request(MediaType.APPLICATION_JSON).buildGet();

		OpenIdConfiguration response;
		try {
			response = request.invoke(OpenIdConfiguration.class);
		} catch (jakarta.ws.rs.ProcessingException e) {
			log.error("Request was unsuccessful: {}", e.getMessage());
			return null;
		}
		return response;
	}

	private Userinfo getUserinfo(String uri, String bearer) {
		var request = client.target(uri).request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer)
				.buildGet();

		Userinfo response;
		try {
			response = request.invoke(Userinfo.class);
		} catch (jakarta.ws.rs.ProcessingException e) {
			log.error("Request was unsuccessful: {}", e.getMessage());
			return null;
		}
		return response;
	}

	private User parseUserFromUserinfo(Userinfo userinfo) {
		String subject = userinfo.getSub();
		// We only want the last part of the subject, since this is usually a human
		// readable user name
		var splitted = subject.split(":");
		String username = splitted[splitted.length - 1];

		User user = new User(username, userinfo.getGivenName(), userinfo.getFamilyName(), userinfo.getEmail());
		return user;
	}
}
