package de.dlr.shepard.v2.admin.config.spi;

import jakarta.ws.rs.core.Response.Status;

/**
 * V2CONV-A4 — thrown by a {@link ConfigDescriptor#applyMergePatch} when a
 * patched value fails validation. Carries the RFC-7807 problem fields so the
 * generic {@link de.dlr.shepard.v2.admin.config.resources.AdminConfigRest}
 * resource can render a precise {@code application/problem+json} envelope
 * identical in shape to what the bespoke REST class produced.
 *
 * <p>This keeps the per-feature validation knowledge inside the descriptor (the
 * only place that understands the feature's fields) while the resource owns the
 * uniform error envelope.
 */
public class ConfigPatchException extends Exception {

  private final String problemType;
  private final String title;
  private final transient Status status;

  /**
   * @param problemType RFC-7807 {@code type} URI (e.g.
   *                    {@code "/problems/ror.config.invalid-ror-id"})
   * @param title       short, stable human-readable summary
   * @param status      the HTTP status to return (typically {@link Status#BAD_REQUEST})
   * @param detail      occurrence-specific human-readable explanation
   */
  public ConfigPatchException(String problemType, String title, Status status, String detail) {
    super(detail);
    this.problemType = problemType;
    this.title = title;
    this.status = status;
  }

  /** Convenience for the common 400 Bad Request case. */
  public static ConfigPatchException badRequest(String problemType, String title, String detail) {
    return new ConfigPatchException(problemType, title, Status.BAD_REQUEST, detail);
  }

  public String getProblemType() {
    return problemType;
  }

  public String getTitle() {
    return title;
  }

  public Status getStatus() {
    return status;
  }

  /** The occurrence-specific detail (the exception message). */
  public String getDetail() {
    return getMessage();
  }
}
