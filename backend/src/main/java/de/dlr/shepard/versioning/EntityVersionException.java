package de.dlr.shepard.versioning;

/**
 * ENT1a service-layer exception family. The REST layer maps each
 * subtype-via-{@link Reason} to a corresponding RFC 7807 problem
 * envelope per {@code aidocs/16} ENT1a:
 *
 * <ul>
 *   <li>{@link Reason#NOT_FOUND} → 404 {@code versions.not-found}.</li>
 *   <li>{@link Reason#LABEL_DUPLICATE} → 409 {@code versions.label.duplicate}.</li>
 *   <li>{@link Reason#LABEL_INVALID} → 400 {@code versions.label.invalid}.</li>
 *   <li>{@link Reason#CANNOT_DELETE_ONLY} → 409 {@code versions.cannot-delete-only}.</li>
 *   <li>{@link Reason#KIND_UNSUPPORTED} → 404 {@code versions.kind.unsupported}.</li>
 *   <li>{@link Reason#FORBIDDEN} → 403 (no extra problem-body — JAX-RS default).</li>
 * </ul>
 *
 * <p>The service throws this; the REST resource catches it and
 * projects onto the right HTTP shape. Keeps the service free of
 * JAX-RS imports.
 */
public class EntityVersionException extends RuntimeException {

  public enum Reason {
    NOT_FOUND,
    LABEL_DUPLICATE,
    LABEL_INVALID,
    CANNOT_DELETE_ONLY,
    KIND_UNSUPPORTED,
    FORBIDDEN
  }

  private final Reason reason;

  public EntityVersionException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
