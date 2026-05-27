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

  @Schema(nullable = true, example = "Post-hotfire analysis of turbopump vibration anomaly at t=8s (TR-004)")
  private String description;

  @NoDelimiterInMapKeys
  private Map<String, String> attributes = new HashMap<>();

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    enumeration = {
      // Research lifecycle
      "DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED",
      // Quality / manufacturing extensions (MFG1)
      "NCR_OPEN", "ON_HOLD", "REJECTED", "CERTIFIED", "FAILED"
    },
    example = "IN_REVIEW"
  )
  private String status;

  /**
   * FAIR-1 (LIC1) — SPDX license identifier expression (e.g. {@code "CC-BY-4.0"},
   * {@code "MIT"}, {@code "Apache-2.0"}, {@code "ODbL-1.0"}) or {@code "PROPRIETARY"}
   * for non-SPDX in-house terms. Nullable; null means "not yet declared".
   *
   * <p>Backed by {@code dcterms:license} in the canonical export-shape mapping
   * (see {@code aidocs/semantics/98 §4.1}). Surfaced on both {@code /shepard/api/}
   * and {@code /v2/} surfaces: because of {@code @JsonInclude(NON_NULL)} the field
   * is absent from the wire when null, so upstream v5.2.0 clients see no change in
   * the v1 wire shape until an operator/researcher sets a value.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true)
  private String license;

  /**
   * FAIR-1 (LIC1) — Access-rights enum: one of {@code OPEN}, {@code RESTRICTED},
   * {@code CLOSED}, or {@code EMBARGOED}. Nullable; null means "not yet declared".
   *
   * <p>Backed by {@code dcat:accessRights} in the canonical export-shape mapping
   * (see {@code aidocs/semantics/98 §4.1}). The enum is enforced client-side via
   * a v-select; the server stays permissive (plain String) for additive forward
   * compatibility — adding values requires no migration. Server-side
   * Bean-Validation can be tightened in a follow-up if needed.
   *
   * <p>Same wire-compat property as {@link #license}: absent when null.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true, enumeration = {"OPEN", "RESTRICTED", "CLOSED", "EMBARGOED"})
  private String accessRights;

  /**
   * FAIR2 — ORCID of the researcher who created this DataObject.
   * Server-stamped at creation time from {@code User.orcid}; never accepted
   * as input (read-only). Absent from the wire when null.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    readOnly = true,
    description = "FAIR2: ORCID of the creator, stamped server-side at creation. " +
      "Read-only — not accepted as input. Absent when null or creator had no ORCID set."
  )
  private String createdByOrcid;

  /**
   * FAIR3 — ISO-8601 date string (e.g. {@code "2027-12-31"}) after which the
   * embargo lifts. Only meaningful when {@code accessRights=EMBARGOED}. Nullable;
   * null means no specific end-date has been declared. User-provided (not server-
   * stamped). Absent from the wire when null.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    description = "ISO-8601 date (e.g. '2027-12-31') after which the embargo lifts; " +
      "only meaningful when accessRights=EMBARGOED."
  )
  private String embargoEndDate;

  protected AbstractDataObjectIO(AbstractDataObject dataObject) {
    super(dataObject);
    this.description = dataObject.getDescription();
    this.attributes = dataObject.getAttributes();
    this.status = dataObject.getStatus();
    this.license = dataObject.getLicense();
    this.accessRights = dataObject.getAccessRights();
    this.createdByOrcid = dataObject.getCreatedByOrcid();
    this.embargoEndDate = dataObject.getEmbargoEndDate();
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
      Objects.equals(accessRights, other.accessRights) &&
      Objects.equals(createdByOrcid, other.createdByOrcid) &&
      Objects.equals(embargoEndDate, other.embargoEndDate)
    );
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(description, attributes, status, license, accessRights, createdByOrcid, embargoEndDate);
    return result;
  }
}
