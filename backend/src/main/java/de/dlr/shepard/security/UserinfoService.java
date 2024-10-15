package de.dlr.shepard.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.dlr.shepard.exceptions.ShepardProcessingException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.ConfigProvider;

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

@RequestScoped
public class UserinfoService {

  private static final String WELL_KNOWN_PATH = ".well-known/openid-configuration";

  private final String oidcConfidurationUrl;
  private String userinfoEndpoint;

  private Client client = ClientBuilder.newClient();

  public UserinfoService() {
    String oidcAuthority = ConfigProvider.getConfig().getValue("oidc.authority", String.class);
    this.oidcConfidurationUrl = String.format("%s/%s", oidcAuthority, WELL_KNOWN_PATH);
  }

  protected void init() {
    var openIdConfiguration = getOpenIdConfiguration(oidcConfidurationUrl);
    if (openIdConfiguration == null) {
      throw new ShepardProcessingException("Could not fetch openid configuration");
    }
    userinfoEndpoint = openIdConfiguration.getUserinfoEndpoint();
  }

  public Userinfo fetchUserinfo(String accessToken) {
    if (userinfoEndpoint == null) {
      init();
    }

    var userinfo = getUserinfo(userinfoEndpoint, accessToken);
    if (userinfo == null) {
      throw new ShepardProcessingException("Could not fetch userinfo");
    }
    return userinfo;
  }

  private OpenIdConfiguration getOpenIdConfiguration(String uri) {
    var request = client.target(uri).request(MediaType.APPLICATION_JSON).buildGet();

    OpenIdConfiguration response;
    try {
      response = request.invoke(OpenIdConfiguration.class);
    } catch (ProcessingException e) {
      Log.errorf("Request was unsuccessful: %s", e.getMessage());
      return null;
    }
    return response;
  }

  private Userinfo getUserinfo(String uri, String bearer) {
    var request = client
      .target(uri)
      .request(MediaType.APPLICATION_JSON)
      .header(HttpHeaders.AUTHORIZATION, bearer)
      .buildGet();

    Userinfo response;
    try {
      response = request.invoke(Userinfo.class);
    } catch (ProcessingException e) {
      Log.errorf("Request was unsuccessful: %s", e.getMessage());
      return null;
    }
    return response;
  }
}
