package de.dlr.shepard.plugin;

/**
 * PM1b2 — raised by {@link JarSignatureVerifier#verify} when the JAR
 * cannot be parsed at all: tampered manifest digest, corrupt
 * signature block, or I/O error reading the JAR. Distinct from the
 * {@code UNSIGNED} / {@code UNTRUSTED} statuses (which are normal
 * non-error outcomes the registry handles via the
 * {@code shepard.plugins.signing.required} toggle).
 *
 * <p>Unchecked because the registry catches it as part of its
 * fail-soft discovery — every plugin JAR's verification result is
 * independent, so one tampered JAR doesn't kneecap startup.
 */
public class JarSignatureException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public JarSignatureException(String message) {
    super(message);
  }

  public JarSignatureException(String message, Throwable cause) {
    super(message, cause);
  }
}
