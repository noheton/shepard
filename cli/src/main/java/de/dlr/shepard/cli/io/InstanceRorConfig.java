package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ROR1 — CLI-side mirror of {@code InstanceRorConfigIO} from
 * {@code GET/PATCH /v2/admin/instance/ror}.
 *
 * <p>All three fields are nullable: the instance may not have a ROR
 * ID configured yet. {@code rorUrl} is server-computed as
 * {@code "https://ror.org/" + rorId}; the CLI does not derive it
 * locally.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class InstanceRorConfig {

  private final String rorId;
  private final String organizationName;
  private final String rorUrl;

  public InstanceRorConfig(
    @JsonProperty("rorId") String rorId,
    @JsonProperty("organizationName") String organizationName,
    @JsonProperty("rorUrl") String rorUrl
  ) {
    this.rorId = rorId;
    this.organizationName = organizationName;
    this.rorUrl = rorUrl;
  }

  public String getRorId() {
    return rorId;
  }

  public String getOrganizationName() {
    return organizationName;
  }

  public String getRorUrl() {
    return rorUrl;
  }
}
