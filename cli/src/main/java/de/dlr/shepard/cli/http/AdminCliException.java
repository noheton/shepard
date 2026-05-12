package de.dlr.shepard.cli.http;

/**
 * One-line operator-readable error for any failure that should
 * surface as a {@code shepard-admin} command exiting non-zero.
 * The {@code message} is the line shown on stderr; the optional
 * cause is shown only when {@code --verbose} is set on the
 * top-level command.
 */
public class AdminCliException extends RuntimeException {

  public AdminCliException(String message) {
    super(message);
  }

  public AdminCliException(String message, Throwable cause) {
    super(message, cause);
  }
}
