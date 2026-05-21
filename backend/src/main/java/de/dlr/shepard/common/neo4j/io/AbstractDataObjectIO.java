package de.dlr.shepard.common.neo4j.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.common.neo4j.entities.AbstractDataObject;
import de.dlr.shepard.common.neo4j.io.validation.NoDelimiterInMapKeys;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "AbstractDataObject")
public abstract class AbstractDataObjectIO extends BasicEntityIO {

  @Schema(nullable = true)
  private String description;

  @NoDelimiterInMapKeys
  private Map<String, String> attributes = new HashMap<>();

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true, enumeration = {"DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED"})
  private String status;

  /**
   * FAIR-1 — SPDX expression or other license identifier (e.g. "CC-BY-4.0").
   * Nullable; null means "not yet declared".
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true)
  private String license;

  /**
   * FAIR-1 — COAR Access Rights vocabulary term (e.g. "open access",
   * "embargoed access", "restricted access", "metadata only access").
   * Nullable; null means "not yet declared".
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true)
  private String accessRights;

  protected AbstractDataObjectIO(AbstractDataObject dataObject) {
    super(dataObject);
    this.description = dataObject.getDescription();
    this.attributes = dataObject.getAttributes();
    this.status = dataObject.getStatus();
    this.license = dataObject.getLicense();
    this.accessRights = dataObject.getAccessRights();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (!(o instanceof AbstractDataObjectIO)) return false;
    AbstractDataObjectIO other = (AbstractDataObjectIO) o;
    return (
      Objects.equals(description, other.description) &&
      Objects.equals(attributes, other.attributes) &&
      Objects.equals(status, other.status) &&
      Objects.equals(license, other.license) &&
      Objects.equals(accessRights, other.accessRights)
    );
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(description, attributes, status, license, accessRights);
    return result;
  }
}
