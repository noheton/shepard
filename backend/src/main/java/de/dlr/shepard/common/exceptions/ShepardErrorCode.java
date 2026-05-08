package de.dlr.shepard.common.exceptions;

import jakarta.ws.rs.core.Response.Status;

/**
 * Catalogue of RFC 7807 error codes surfaced by {@link ShepardExceptionMapper}.
 *
 * <p>Each entry maps to:
 * <ul>
 *   <li>an HTTP {@link Status},</li>
 *   <li>a default {@code title} (short, human-readable summary that doesn't
 *       change between occurrences),</li>
 *   <li>a {@code typeUrlSuffix} that is appended to {@link #TYPE_URL_BASE} to
 *       form the {@code type} URI in the {@code application/problem+json}
 *       response body.</li>
 * </ul>
 *
 * <p>The base URL points at the noheton/shepard Pages site. The actual error
 * documentation pages can be authored later — until they exist, the URL is
 * still a stable, dereferenceable identifier per RFC 7807 §3.1.
 */
public enum ShepardErrorCode {
  // Authentication / authorisation
  AUTH_EXPIRED(Status.UNAUTHORIZED, "Authentication expired", "auth.expired"),
  AUTH_INVALID_TOKEN(Status.UNAUTHORIZED, "Invalid authentication token", "auth.invalid_token"),
  AUTH_UNAUTHENTICATED(Status.UNAUTHORIZED, "Authentication required", "auth.unauthenticated"),
  AUTH_FORBIDDEN(Status.FORBIDDEN, "Forbidden", "auth.forbidden"),

  // Validation
  VALIDATION_BODY(Status.BAD_REQUEST, "Invalid request body", "validation.body"),
  VALIDATION_FIELD(Status.BAD_REQUEST, "Invalid request", "validation.field"),

  // Permissions
  PERMISSION_DENIED(Status.FORBIDDEN, "Permission denied", "permission.denied"),

  // Resource state
  NOT_FOUND_ENTITY(Status.NOT_FOUND, "Resource not found", "not_found.entity"),
  CONFLICT_ENTITY(Status.CONFLICT, "Resource conflict", "conflict.entity"),

  // Server-side
  INTERNAL_UNEXPECTED(Status.INTERNAL_SERVER_ERROR, "Internal server error", "internal.unexpected");

  /**
   * Base URL for shepard's RFC 7807 error documentation. Concatenated with
   * {@link #typeUrlSuffix} to form the {@code type} URI.
   */
  public static final String TYPE_URL_BASE = "https://noheton.github.io/shepard/errors/";

  private final Status status;
  private final String defaultTitle;
  private final String typeUrlSuffix;

  ShepardErrorCode(Status status, String defaultTitle, String typeUrlSuffix) {
    this.status = status;
    this.defaultTitle = defaultTitle;
    this.typeUrlSuffix = typeUrlSuffix;
  }

  public Status status() {
    return status;
  }

  public int httpStatus() {
    return status.getStatusCode();
  }

  public String defaultTitle() {
    return defaultTitle;
  }

  public String typeUrlSuffix() {
    return typeUrlSuffix;
  }

  /** The full {@code type} URI string for the RFC 7807 response. */
  public String typeUrl() {
    return TYPE_URL_BASE + typeUrlSuffix;
  }
}
