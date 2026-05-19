package de.dlr.shepard.auth.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Userinfo {

  private String sub;
  private String name;
  private String email;

  @JsonProperty("given_name")
  private String givenName;

  @JsonProperty("family_name")
  private String familyName;

  @JsonProperty("preferred_username")
  private String preferredUsername;

  /**
   * ORCID identifier carried as a custom claim by the IdP (U1g).
   * Keycloak exposes user attributes as OIDC claims via a "User
   * Attribute" protocol mapper; the claim name matches the attribute
   * name ({@code orcid}). Nullable: realms without the mapper leave
   * this null and the auto-sync skips it.
   */
  private String orcid;
}
