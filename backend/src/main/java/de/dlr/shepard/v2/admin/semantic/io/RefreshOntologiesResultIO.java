package de.dlr.shepard.v2.admin.semantic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * N1c — JSON response body of
 * {@code POST /v2/admin/semantic/refresh-ontologies}.
 *
 * <p>Shape is plain JSON (not RFC 7807): per-bundle outcomes are
 * best-effort, so the endpoint returns 200 even when some bundles
 * errored — the {@code errors[]} array carries the per-bundle reason.
 * RFC 7807 is reserved for the all-or-nothing 4xx / 5xx paths the
 * {@code SemanticAdminRest} class wraps separately.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RefreshOntologiesResult")
public class RefreshOntologiesResultIO {

  @Schema(description = "Number of bundles considered (matches the request's filter, or all bundles when no filter was given).")
  private int requested;

  @Schema(description = "Number of bundles whose canonical Turtle was successfully fetched and re-imported into n10s.")
  private int refreshed;

  @Schema(description = "Number of bundles skipped because the canonical Turtle hash already matched the bundled stub (force=false).")
  private int alreadyCurrent;

  @Schema(description = "Per-bundle errors; empty when every refresh succeeded.")
  private List<Error> errors = new ArrayList<>();

  public RefreshOntologiesResultIO() {}

  public RefreshOntologiesResultIO(int requested, int refreshed, int alreadyCurrent, List<Error> errors) {
    this.requested = requested;
    this.refreshed = refreshed;
    this.alreadyCurrent = alreadyCurrent;
    this.errors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
  }

  public int getRequested() {
    return requested;
  }

  public void setRequested(int requested) {
    this.requested = requested;
  }

  public int getRefreshed() {
    return refreshed;
  }

  public void setRefreshed(int refreshed) {
    this.refreshed = refreshed;
  }

  public int getAlreadyCurrent() {
    return alreadyCurrent;
  }

  public void setAlreadyCurrent(int alreadyCurrent) {
    this.alreadyCurrent = alreadyCurrent;
  }

  public List<Error> getErrors() {
    return errors == null ? Collections.emptyList() : errors;
  }

  public void setErrors(List<Error> errors) {
    this.errors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
  }

  @Schema(name = "RefreshOntologiesResultError")
  public static class Error {

    @Schema(description = "Bundle id (e.g. \"prov-o\") that failed to refresh.")
    private String bundle;

    @Schema(description = "Operator-readable reason for the failure.")
    private String reason;

    public Error() {}

    public Error(String bundle, String reason) {
      this.bundle = bundle;
      this.reason = reason;
    }

    public String getBundle() {
      return bundle;
    }

    public void setBundle(String bundle) {
      this.bundle = bundle;
    }

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }
}
