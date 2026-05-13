package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * Wire-shape mirror of the backend's
 * {@code RefreshOntologiesResultIO} (lives in
 * {@code de.dlr.shepard.v2.admin.semantic.io}). Decoupled so the CLI
 * does not pull in the backend Quarkus stack just for one DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RefreshOntologiesResult {

  private final int requested;
  private final int refreshed;
  private final int alreadyCurrent;
  private final List<BundleError> errors;

  @JsonCreator
  public RefreshOntologiesResult(
    @JsonProperty("requested") int requested,
    @JsonProperty("refreshed") int refreshed,
    @JsonProperty("alreadyCurrent") int alreadyCurrent,
    @JsonProperty("errors") List<BundleError> errors
  ) {
    this.requested = requested;
    this.refreshed = refreshed;
    this.alreadyCurrent = alreadyCurrent;
    this.errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public int getRequested() {
    return requested;
  }

  public int getRefreshed() {
    return refreshed;
  }

  public int getAlreadyCurrent() {
    return alreadyCurrent;
  }

  public List<BundleError> getErrors() {
    return errors == null ? Collections.emptyList() : errors;
  }

  public boolean hasErrors() {
    return errors != null && !errors.isEmpty();
  }

  /** Per-bundle error row. */
  public static final class BundleError {

    private final String bundle;
    private final String reason;

    @JsonCreator
    public BundleError(
      @JsonProperty("bundle") String bundle,
      @JsonProperty("reason") String reason
    ) {
      this.bundle = bundle;
      this.reason = reason;
    }

    public String getBundle() {
      return bundle;
    }

    public String getReason() {
      return reason;
    }
  }
}
