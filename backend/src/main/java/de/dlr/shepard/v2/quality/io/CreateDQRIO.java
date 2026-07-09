package de.dlr.shepard.v2.quality.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TPL10 — request body for creating a new Data Quality Requirement.
 *
 * <p>Submitted as JSON to {@code POST /v2/collections/{appId}/dqr}.
 */
@Schema(description = "Request body for creating a Data Quality Requirement on a Collection.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateDQRIO(

  /** Human-readable name for this requirement. Required. */
  @NotBlank(message = "name must not be blank")
  String name,

  /** Optional longer description. */
  String description,

  /**
   * Rule type — one of {@code ANNOTATION_REQUIRED}, {@code NO_TIMESERIES_GAP},
   * {@code FILE_COUNT_MIN}, {@code CUSTOM_CYPHER}. Required.
   */
  @NotNull(message = "ruleType must not be null")
  @Pattern(
    regexp = "ANNOTATION_REQUIRED|NO_TIMESERIES_GAP|FILE_COUNT_MIN|CUSTOM_CYPHER",
    message = "ruleType must be one of: ANNOTATION_REQUIRED, NO_TIMESERIES_GAP, FILE_COUNT_MIN, CUSTOM_CYPHER"
  )
  String ruleType,

  /**
   * Type-specific parameter string. Required.
   * For ANNOTATION_REQUIRED: the attribute key name (e.g. "status").
   * For NO_TIMESERIES_GAP: max gap in seconds (e.g. "5").
   * For FILE_COUNT_MIN: minimum file count (e.g. "1").
   * For CUSTOM_CYPHER: Cypher predicate fragment.
   */
  @NotBlank(message = "ruleParam must not be blank")
  String ruleParam,

  /**
   * Severity of a violation: {@code ERROR}, {@code WARN}, or {@code INFO}.
   * Defaults to {@code ERROR} when not supplied.
   */
  @Pattern(
    regexp = "ERROR|WARN|INFO",
    message = "severity must be one of: ERROR, WARN, INFO"
  )
  String severity,

  /**
   * Whether the DQR is active. Defaults to {@code true} when not supplied.
   */
  Boolean enabled
) {}
